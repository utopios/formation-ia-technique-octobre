package fr.utopios.formation.module06;

import fr.utopios.formation.commun.Llm;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MODULE 6 — Optimiser un RAG : chunking, recherche hybride (BM25 + vecteurs, fusion RRF),
 * reranking par le modele.
 *
 * Complement de RagMaison : la, un document = un chunk. Ici on part d'un VRAI document
 * de plusieurs sections et on mesure l'effet de trois choix d'ingenierie :
 *   1. la strategie de decoupage (taille fixe / chevauchement / structure) ;
 *   2. la recherche hybride : lexicale (BM25, exacte sur les identifiants) + vectorielle
 *      (semantique, robuste aux paraphrases), fusionnees par Reciprocal Rank Fusion ;
 *   3. le reranking : un second passage, plus couteux, qui reclasse les meilleurs
 *      candidats en lisant vraiment la question et le passage.
 *
 * Lancement : mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.module06.ChunkingEtRerankHybride"
 */
public class ChunkingEtRerankHybride {

    // ------------------------------------------------------------------
    // Le document : une politique interne de six sections (texte continu)
    // ------------------------------------------------------------------
    static final String DOCUMENT = String.join("\n",
            "# Télétravail",
            "Le télétravail est autorisé jusqu'à 3 jours par semaine pour les salariés en CDI, après la "
                    + "période d'essai. La demande se fait via le formulaire RH-TT-02 et doit être validée "
                    + "par le manager.",
            "# Notes de frais",
            "Les notes de frais doivent être soumises avant le 5 du mois suivant dans l'outil Expensya. "
                    + "Le plafond des repas est de 25 euros par jour ; au-delà, une justification est requise.",
            "# Congés",
            "Les congés payés s'acquièrent à raison de 2,5 jours ouvrables par mois travaillé. Les demandes "
                    + "se font 15 jours à l'avance, sauf urgence validée par le manager.",
            "# Salles de réunion",
            "La salle Mont Blanc (12 places) se réserve via l'intranet, 30 minutes minimum. La salle "
                    + "Vanoise (6 places) est en accès libre.",
            "# Sécurité informatique",
            "La procédure SEC-017 impose l'authentification à deux facteurs pour l'accès au VPN. Les mots "
                    + "de passe comportent au moins 14 caractères et sont renouvelés tous les 180 jours.",
            "# Support",
            "Le support informatique est joignable au poste 4521 du lundi au vendredi de 8h à 19h, et par "
                    + "un ticket intranet le week-end.");

    /** Un chunk indexe : texte, vecteur, et ses tokens pour BM25. */
    record Chunk(int id, String texte, double[] vecteur, List<String> tokens) { }

    /** Un candidat classe : chunk + score (le sens du score depend de la methode). */
    record Candidat(Chunk chunk, double score) { }

    // ==================================================================
    // PARTIE 1 — Trois strategies de chunking
    // ==================================================================

    /** Taille fixe en caracteres, avec chevauchement optionnel (0 = aucun). */
    static List<String> chunkerTailleFixe(String texte, int taille, int chevauchement) {
        String plat = texte.replace("\n", " ");
        List<String> chunks = new ArrayList<>();
        int pas = taille - chevauchement;
        for (int debut = 0; debut < plat.length(); debut += pas) {
            chunks.add(plat.substring(debut, Math.min(plat.length(), debut + taille)).strip());
            if (debut + taille >= plat.length()) break;
        }
        return chunks;
    }

    /** Par structure : une section Markdown (titre + paragraphe) = un chunk autonome. */
    static List<String> chunkerParStructure(String texte) {
        List<String> chunks = new ArrayList<>();
        for (String section : texte.split("(?m)^# ")) {
            if (!section.isBlank()) chunks.add("Section " + section.replace("\n", " : ").strip());
        }
        return chunks;
    }

    /** Indexe une liste de textes : embeddings (prefixe search_document) + tokens BM25. */
    static List<Chunk> indexer(List<String> textes) {
        List<Chunk> index = new ArrayList<>();
        for (int i = 0; i < textes.size(); i++) {
            index.add(new Chunk(i, textes.get(i), Llm.embedDocument(textes.get(i)), tokeniser(textes.get(i))));
        }
        return index;
    }

    /** Recherche vectorielle : cosinus contre la question (prefixe search_query). */
    static List<Candidat> rechercheVectorielle(String question, List<Chunk> index) {
        double[] q = Llm.embedQuery(question);
        List<Candidat> r = new ArrayList<>();
        for (Chunk c : index) r.add(new Candidat(c, Llm.cosine(c.vecteur(), q)));
        r.sort(Comparator.comparingDouble(Candidat::score).reversed());
        return r;
    }

    static void comparerChunking(String question, String attendu) {
        System.out.println("\nQuestion : \"" + question + "\"   (la réponse contient « " + attendu + " »)");
        Map<String, List<String>> strategies = new LinkedHashMap<>();
        strategies.put("taille fixe 120 car., sans chevauchement", chunkerTailleFixe(DOCUMENT, 120, 0));
        strategies.put("taille fixe 120 car., chevauchement 40  ", chunkerTailleFixe(DOCUMENT, 120, 40));
        strategies.put("par structure (1 section = 1 chunk)     ", chunkerParStructure(DOCUMENT));
        for (Map.Entry<String, List<String>> e : strategies.entrySet()) {
            List<Chunk> index = indexer(e.getValue());
            Candidat meilleur = rechercheVectorielle(question, index).get(0);
            boolean ok = meilleur.chunk().texte().contains(attendu);
            System.out.printf("  %-42s %2d chunks | top-1 cos=%.3f | réponse dans le top-1 : %s%n",
                    e.getKey(), index.size(), meilleur.score(), ok ? "OUI" : "NON");
            System.out.println("      « " + abreger(meilleur.chunk().texte(), 95) + " »");
        }
    }

    // ==================================================================
    // PARTIE 2 — Recherche hybride : BM25 + vecteurs, fusion RRF
    // ==================================================================

    /** Tokenisation minimale : minuscules, sans accents, mots de 2 caracteres et plus. */
    static List<String> tokeniser(String texte) {
        String sansAccents = Normalizer.normalize(texte.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        List<String> tokens = new ArrayList<>();
        for (String t : sansAccents.split("[^a-z0-9-]+")) if (t.length() >= 2) tokens.add(t);
        return tokens;
    }

    /** BM25 (k1 = 1,5 ; b = 0,75) : score lexical classique, fort sur les termes rares et exacts. */
    static List<Candidat> rechercheBm25(String question, List<Chunk> index) {
        double k1 = 1.5, b = 0.75, n = index.size();
        double longueurMoyenne = index.stream().mapToInt(c -> c.tokens().size()).average().orElse(1);
        Map<String, Integer> docFreq = new HashMap<>();
        for (Chunk c : index) for (String t : new java.util.HashSet<>(c.tokens())) docFreq.merge(t, 1, Integer::sum);
        List<Candidat> r = new ArrayList<>();
        for (Chunk c : index) {
            double score = 0;
            for (String t : tokeniser(question)) {
                int df = docFreq.getOrDefault(t, 0);
                if (df == 0) continue;
                long tf = c.tokens().stream().filter(t::equals).count();
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                score += idf * (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * c.tokens().size() / longueurMoyenne));
            }
            r.add(new Candidat(c, score));
        }
        r.sort(Comparator.comparingDouble(Candidat::score).reversed());
        return r;
    }

    /** Reciprocal Rank Fusion : score = somme 1 / (60 + rang) sur chaque liste. Pas de normalisation a regler. */
    static List<Candidat> fusionRrf(List<List<Candidat>> listes) {
        Map<Integer, Double> scores = new HashMap<>();
        Map<Integer, Chunk> chunks = new HashMap<>();
        for (List<Candidat> liste : listes) {
            for (int rang = 0; rang < liste.size(); rang++) {
                Chunk c = liste.get(rang).chunk();
                chunks.put(c.id(), c);
                scores.merge(c.id(), 1.0 / (60 + rang + 1), Double::sum);
            }
        }
        List<Candidat> r = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : scores.entrySet()) r.add(new Candidat(chunks.get(e.getKey()), e.getValue()));
        r.sort(Comparator.comparingDouble(Candidat::score).reversed());
        return r;
    }

    static void comparerHybride(String question, List<Chunk> index, int attenduId) {
        System.out.println("\nQuestion : \"" + question + "\"   (bon chunk : #" + attenduId + ")");
        List<Candidat> lex = rechercheBm25(question, index);
        List<Candidat> vec = rechercheVectorielle(question, index);
        List<Candidat> rrf = fusionRrf(List.of(lex, vec));
        afficherTop("BM25 (lexical)   ", lex, 3, attenduId, "%.2f");
        afficherTop("Vectoriel (cos)  ", vec, 3, attenduId, "%.3f");
        afficherTop("Hybride (RRF)    ", rrf, 3, attenduId, "%.4f");
    }

    static void afficherTop(String nom, List<Candidat> liste, int k, int attenduId, String fmt) {
        StringBuilder sb = new StringBuilder("  " + nom + " : ");
        for (int i = 0; i < Math.min(k, liste.size()); i++) {
            Candidat c = liste.get(i);
            sb.append(i == 0 ? "" : "  >  ").append("#").append(c.chunk().id())
                    .append(" (").append(String.format(fmt, c.score())).append(")");
        }
        int rang = -1;
        for (int i = 0; i < liste.size(); i++) if (liste.get(i).chunk().id() == attenduId) rang = i + 1;
        boolean nul = liste.isEmpty() || liste.get(0).score() == 0;
        sb.append(nul ? "   -> aucun terme commun : BM25 muet" : "   -> bon chunk au rang " + rang);
        System.out.println(sb);
    }

    // ==================================================================
    // PARTIE 3 — Reranking par le modele (pointwise, note 0-10)
    // ==================================================================

    /**
     * Reranking LISTWISE : un seul appel, les candidats numerotes, le modele les classe.
     * Un petit modele juge mal un passage isole (« contient-il la reponse ? » -> il dit NON
     * a tout, meme au bon) mais compare correctement plusieurs passages entre eux.
     */
    static List<Candidat> reranker(String question, List<Candidat> candidats) {
        StringBuilder liste = new StringBuilder();
        for (int i = 0; i < candidats.size(); i++) {
            liste.append("[").append(i + 1).append("] ").append(candidats.get(i).chunk().texte()).append('\n');
        }
        String prompt = "Voici une question et " + candidats.size() + " passages numérotés.\n"
                + "QUESTION : " + question + "\n\n" + liste
                // Pas d'exemple chiffre dans la consigne : le 1b le recopie tel quel (« 3,1,4,2 »).
                + "\nClasse les passages du plus utile au moins utile pour répondre à la question, "
                + "même si les mots employés diffèrent. Réponds UNIQUEMENT par les numéros des passages, "
                + "séparés par des virgules, sans autre texte.";
        String brut = Llm.chat(prompt, null, 0.0);
        List<Candidat> ordre = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(brut);
        while (m.find()) {
            int n = Integer.parseInt(m.group()) - 1;
            if (n >= 0 && n < candidats.size() && ordre.stream().noneMatch(c -> c.chunk().id() == candidats.get(n).chunk().id())) {
                ordre.add(new Candidat(candidats.get(n).chunk(), candidats.size() - ordre.size()));
            }
        }
        for (Candidat c : candidats) {   // ce que le modele a oublie garde son ordre initial, en queue
            if (ordre.stream().noneMatch(o -> o.chunk().id() == c.chunk().id())) ordre.add(new Candidat(c.chunk(), 0));
        }
        System.out.println("  Réponse du modèle   : " + abreger(brut.strip().replace("\n", " "), 110));
        return ordre;
    }

    static String abreger(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    public static void main(String[] args) {
        if (args.length > 0) {   // mode sonde : "idAttendu:question" ... -> classements seulement
            List<Chunk> index = indexer(chunkerParStructure(DOCUMENT));
            for (String a : args) {
                int sep = a.indexOf(':');
                comparerHybride(a.substring(sep + 1).strip(), index, Integer.parseInt(a.substring(0, sep)));
            }
            return;
        }
        System.out.println("=".repeat(72));
        System.out.println("MODULE 6 — Optimiser un RAG : chunking, recherche hybride, reranking");
        System.out.println("Modèle de chat : " + Llm.modeleChat() + " | embeddings : " + Llm.MODELE_EMBEDDING);
        System.out.println("=".repeat(72));

        System.out.println("\n### PARTIE 1 — La stratégie de chunking change ce que l'on retrouve ###");
        System.out.println("Même document (" + DOCUMENT.length() + " caractères, 6 sections), même question, trois découpages.");
        comparerChunking("Combien de jours de télétravail par semaine ?", "3 jours");
        comparerChunking("Combien de caractères minimum pour un mot de passe ?", "14 caractères");
        comparerChunking("Quel est le numéro de poste du support informatique ?", "4521");
        System.out.println("\nÀ retenir : aucune stratégie ne gagne partout. Le découpage fixe coupe au milieu des");
        System.out.println("phrases (« poste 4521 » séparé de « support ») ; le chevauchement répare ces coupes ;");
        System.out.println("une section entière est autonome mais, trop longue, elle DILUE l'embedding (la section");
        System.out.println("Sécurité parle de 2FA, VPN, mots de passe ET renouvellement : la question sur les mots");
        System.out.println("de passe lui échappe). Règle : des chunks autonomes, courts, avec chevauchement — et");
        System.out.println("on MESURE sur ses propres questions, on ne choisit pas une stratégie sur catalogue.");

        System.out.println("\n### PARTIE 2 — Recherche hybride : lexical + vectoriel, fusion RRF ###");
        List<Chunk> index = indexer(chunkerParStructure(DOCUMENT));
        for (Chunk c : index) System.out.println("  #" + c.id() + " " + abreger(c.texte(), 70));
        comparerHybride("À quoi sert la procédure SEC-017 ?", index, 4);
        comparerHybride("Puis-je travailler à distance ?", index, 0);
        comparerHybride("Combien de jours de remote par semaine ?", index, 0);
        comparerHybride("Je peux bosser depuis chez moi combien de jours ?", index, 0);
        System.out.println("\nÀ retenir : le lexical est imbattable sur un identifiant exact (SEC-017) mais MUET dès");
        System.out.println("qu'aucun mot n'est commun (« travailler à distance ») ; le vectoriel comprend la paraphrase");
        System.out.println("mais se laisse distraire par « jours » (congés). La fusion RRF prend le meilleur des deux");
        System.out.println("sans pondération à régler — et quand les deux se trompent (« bosser depuis chez moi »),");
        System.out.println("il reste le reranking.");

        System.out.println("\n### PARTIE 3 — Reranking : relire les meilleurs candidats avec le modèle ###");
        String question = "Je peux bosser depuis chez moi combien de jours ?";
        List<Candidat> premiers = fusionRrf(List.of(rechercheBm25(question, index),
                rechercheVectorielle(question, index))).subList(0, 4);
        System.out.println("Question : \"" + question + "\"");
        System.out.println("  Avant rerank (RRF)  : " + premiers.stream()
                .map(c -> "#" + c.chunk().id()).reduce((a, b2) -> a + " > " + b2).orElse(""));
        List<Candidat> reclasses = reranker(question, premiers);
        System.out.println("  Après rerank        : " + reclasses.stream()
                .map(c -> "#" + c.chunk().id()).reduce((a, b2) -> a + " > " + b2).orElse(""));
        System.out.println("  Coût : 1 appel au modèle avec les " + premiers.size() + " passages dans le prompt — "
                + "on ne rerank que les 4 à 20 premiers, jamais tout l'index.");
        System.out.println("\nÀ retenir : le reranking est un second passage précis mais coûteux ; il lit vraiment la");
        System.out.println("question et le passage, là où cosinus et BM25 ne comparent que des surfaces. Il faut un");
        System.out.println("modèle qui sache comparer ; en production on utilise un modèle de rerank dédié");
        System.out.println("(cross-encoder), plus petit et plus rapide qu'un LLM généraliste.");
    }
}
