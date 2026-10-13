package fr.utopios.formation.solutions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.utopios.formation.commun.Llm;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Tp03Solution {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ================================================================
    // ÉTAPE 1 — Techniques de prompting : zero-shot / few-shot / CoT
    // ================================================================
    static void etape1() {
        System.out.println("=".repeat(70));
        System.out.println("ÉTAPE 1 — Zero-shot vs few-shot vs chain-of-thought");
        System.out.println("=".repeat(70));

        String question = "Marie a 3 boîtes de 4 stylos. Elle en donne 5. "
                + "Combien lui reste-t-il de stylos ?";
        System.out.println("\nProblème : " + question);
        System.out.println("Bonne réponse attendue : 7 (3x4=12, 12-5=7)\n");

        System.out.println("[zero-shot]");
        System.out.println("  " + Llm.chat(question, null, 0.7).strip()
                .replace("\n", "\n  "));

        System.out.println("\n[few-shot]");
        String few = "Q: J'ai 2 sacs de 3 pommes, j'en mange 1. Combien reste-t-il ? "
                + "R: 2*3=6, 6-1=5. Réponse: 5\n"
                + "Q: 4 paquets de 2 gâteaux, j'en donne 3. Combien reste-t-il ? "
                + "R: 4*2=8, 8-3=5. Réponse: 5\n"
                + "Q: " + question + " R:";
        System.out.println("  " + Llm.chat(few, null, 0.7).strip()
                .replace("\n", "\n  "));

        System.out.println("\n[chain-of-thought]");
        String cot = question + " Raisonne étape par étape, puis donne la réponse finale.";
        System.out.println("  " + Llm.chat(cot, null, 0.7).strip()
                .replace("\n", "\n  "));

        System.out.println("\nLecture : sur un 1b, le zero-shot peut se tromper ; le few-shot fiabilise");
        System.out.println("le FORMAT (gabarit de réponse) ; le CoT rend le RAISONNEMENT explicite.");
        System.out.println("On observe l'EFFET des techniques, on n'exige pas que le 1b trouve 7.");
    }

    // ================================================================
    // ÉTAPE 2 — Bibliothèque de prompts paramétrés (gabarit / valeurs)
    // ================================================================
    static final Map<String, String> PROMPTS = Map.of(
            "resume",
            "Résume le texte suivant en {n} phrases maximum, en français, "
            + "sans introduction.\n\nTEXTE :\n{texte}",
            "traduction",
            "Traduis le texte suivant en {langue}. Ne renvoie QUE la traduction.\n\n{texte}",
            "extraction_mots_cles",
            "Donne exactement {k} mots-clés du texte, séparés par des virgules, "
            + "sans phrase autour.\n\n{texte}");

    /** Rend un gabarit : remplace chaque placeholder {cle} par sa valeur. */
    static String rendre(String nom, Map<String, String> variables) {
        String gabarit = PROMPTS.get(nom);
        if (gabarit == null) {
            throw new IllegalArgumentException("gabarit inconnu : " + nom);
        }
        for (var e : variables.entrySet()) {
            gabarit = gabarit.replace("{" + e.getKey() + "}", e.getValue());
        }
        if (gabarit.matches("(?s).*\\{[a-z_]+\\}.*")) {
            throw new IllegalArgumentException(
                    "placeholder non renseigné dans le gabarit '" + nom + "'");
        }
        return gabarit;
    }

    static void etape2() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("ÉTAPE 2 — Templates paramétrés");
        System.out.println("=".repeat(70));

        String texte = "L'IA générative permet d'automatiser la rédaction, mais introduit des "
                + "risques de fuite de données et d'hallucination qu'il faut encadrer.";

        System.out.println("\n[resume]");
        System.out.println("  " + Llm.chat(
                rendre("resume", Map.of("n", "1", "texte", texte)), null, 0.0).strip()
                .replace("\n", "\n  "));

        System.out.println("\n[extraction_mots_cles]");
        System.out.println("  " + Llm.chat(
                rendre("extraction_mots_cles", Map.of("k", "3", "texte", texte)), null, 0.0)
                .strip().replace("\n", "\n  "));

        System.out.println("\nLecture : on sépare le GABARIT (versionnable, testable) des VALEURS");
        System.out.println("injectées. C'est la base d'une bibliothèque de prompts réutilisables.");
    }

    // ================================================================
    // ÉTAPE 3 — Sortie JSON validée (Jackson) + boucle de re-tentative
    // ================================================================

    /** Schéma cible : la réponse du LLM DOIT s'y conformer. */
    record FicheProduit(String nom, String categorie, double prixEur, boolean enStock) {

        static final Set<String> CATEGORIES_AUTORISEES =
                Set.of("informatique", "maison", "sport", "alimentaire", "autre");

        /** Validation champ par champ (équivalent Java du model_validate pydantic). */
        static FicheProduit valider(JsonNode n) {
            if (!n.hasNonNull("nom") || !n.get("nom").isTextual()
                    || n.get("nom").asText().isBlank()) {
                throw new IllegalArgumentException("champ 'nom' manquant ou vide");
            }
            if (!n.hasNonNull("categorie") || !n.get("categorie").isTextual()) {
                throw new IllegalArgumentException("champ 'categorie' manquant ou non textuel");
            }
            if (!n.hasNonNull("prix_eur") || !n.get("prix_eur").isNumber()) {
                throw new IllegalArgumentException("champ 'prix_eur' manquant ou non numérique");
            }
            double prix = n.get("prix_eur").asDouble();
            if (prix < 0) {
                throw new IllegalArgumentException("prix_eur négatif : " + prix);
            }
            if (!n.hasNonNull("en_stock") || !n.get("en_stock").isBoolean()) {
                throw new IllegalArgumentException("champ 'en_stock' manquant ou non booléen");
            }
            String categorie = n.get("categorie").asText().toLowerCase();
            if (!CATEGORIES_AUTORISEES.contains(categorie)) {
                categorie = "autre";     // on NORMALISE au lieu de planter
            }
            return new FicheProduit(n.get("nom").asText(), categorie,
                    prix, n.get("en_stock").asBoolean());
        }
    }

    /** Extrait le premier objet JSON d'une réponse LLM (souvent bavarde). */
    static JsonNode extraireJson(String texte) {
        Matcher m = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(texte);
        if (!m.find()) {
            throw new IllegalArgumentException("Aucun JSON trouvé dans la réponse du modèle.");
        }
        try {
            return MAPPER.readTree(m.group());
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON invalide : " + e.getMessage());
        }
    }

    static void etape3() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("ÉTAPE 3 — Forcer JSON -> extraire -> valider (Jackson) -> réessayer");
        System.out.println("=".repeat(70) + "\n");

        String prompt = "Extrais une fiche produit de ce texte et réponds UNIQUEMENT en JSON "
                + "avec les clés exactes : nom (string), categorie (string parmi "
                + "informatique/maison/sport/alimentaire/autre), prix_eur (number), "
                + "en_stock (boolean). Pas de texte autour.\n\n"
                + "Texte : « Le clavier mécanique AZERTY Pro est dispo à 89,90 € et "
                + "actuellement en stock. C'est un accessoire informatique. »";

        Exception derniere = null;
        boolean valide = false;
        for (int tentative = 1; tentative <= 3 && !valide; tentative++) {
            System.out.println("Tentative " + tentative + "...");
            String r = Llm.chat(prompt, null, 0.0);
            try {
                FicheProduit fiche = FicheProduit.valider(extraireJson(r));
                System.out.println("VALIDATION Java : OK -> " + fiche);
                valide = true;
            } catch (IllegalArgumentException exc) {
                derniere = exc;
                System.out.println("  échec (" + exc.getMessage()
                        + ") -> on renforce la consigne et on réessaie");
                prompt += "\n\nRappel : réponds STRICTEMENT en JSON valide, rien d'autre.";
            }
        }
        if (!valide) {
            System.out.println("Échec après 3 tentatives (attendu parfois sur un 1b). "
                    + "Dernière erreur : " + derniere.getMessage());
            System.out.println("L'INVARIANT tient : aucune sortie non validée n'a été propagée.");
        }

        // Test de robustesse : une catégorie hors liste est NORMALISÉE, pas un crash.
        System.out.println("\nTest de robustesse : catégorie hors liste (« jardinage »)");
        try {
            JsonNode test = MAPPER.readTree(
                    "{\"nom\": \"Tondeuse X\", \"categorie\": \"jardinage\", "
                    + "\"prix_eur\": 199.0, \"en_stock\": true}");
            FicheProduit ficheTest = FicheProduit.valider(test);
            System.out.println("  categorie normalisée -> " + ficheTest.categorie());
            if (!"autre".equals(ficheTest.categorie())) {
                throw new IllegalStateException("la normalisation n'a pas fonctionné");
            }
            System.out.println("  OK : la validation normalise en 'autre' au lieu de planter.");
        } catch (Exception e) {
            throw new IllegalStateException("test de robustesse en échec", e);
        }
    }

    public static void main(String[] args) {
        etape1();
        etape2();
        etape3();

        // ================================================================
        // RÉCAPITULATIF — Points clés de la correction
        // ================================================================
        System.out.println("\n" + "=".repeat(70));
        System.out.println("RÉCAPITULATIF — Points clés");
        System.out.println("=".repeat(70));
        System.out.println("""

                 1. Few-shot fiabilise le FORMAT ; CoT explicite le RAISONNEMENT ; les deux
                    gagnent surtout sur gros modèle.
                 2. Séparer gabarit et valeurs (templates) rend les prompts versionnables et
                    testables (base d'une bibliothèque de prompts).
                 3. Chaîne maîtresse : forcer JSON -> extraire ({...}) -> valider (Jackson,
                    champ par champ) -> réessayer en renforçant la consigne. On ne propage
                    JAMAIS une sortie non validée.
                 4. La validation NORMALISE (catégorie inconnue -> 'autre') au lieu de
                    planter : robustesse.
                 5. En prod : préférer les sorties structurées NATIVES de l'API
                    (messages.parse + output_format côté Claude, JSON mode côté Mistral).
                 6. Lien module 4 : la validation est le socle des garde-fous (TP 4).
                """);
    }
}
