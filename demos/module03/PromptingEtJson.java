package fr.utopios.formation.module03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.utopios.formation.commun.Llm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PromptingEtJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** La tâche piège : un calcul multi-étapes. Bonne réponse : 53. */
    static final String TACHE =
            "Un panier contient 3 sacs de 12 pommes ; on en retire 7 pommes "
            + "puis on ajoute 2 sacs. Combien de pommes au total ?";

    // ------------------------------------------------------------------ //
    // Partie 1 — techniques de prompting
    // ------------------------------------------------------------------ //
    static void demoPrompting() {
        System.out.println("=".repeat(70));
        System.out.println("PARTIE 1 — zero-shot / few-shot / chain-of-thought / self-consistency");
        System.out.println("           (modèle local : " + Llm.modeleChat() + ", bonne réponse : 53)");
        System.out.println("=".repeat(70));

        // 1) Zero-shot : on pose juste la question, aucune aide.
        System.out.println("\n[zero-shot]");
        String r = Llm.chat(TACHE, null, 0.0);
        System.out.println("   " + r.strip().replace("\n", "\n   "));

        // 2) Few-shot : on montre le FORMAT de réponse attendu via des exemples.
        System.out.println("\n[few-shot]");
        String few =
                "Exemple 1\n"
                + "Q : 2 sacs de 5 billes, on en retire 3. Combien ?\n"
                + "R : 2*5=10, 10-3=7. Réponse : 7\n\n"
                + "Exemple 2\n"
                + "Q : 4 boîtes de 6 stylos, on ajoute 1 boîte. Combien ?\n"
                + "R : 4*6=24, +6=30. Réponse : 30\n\n"
                + "Q : " + TACHE + "\nR :";
        r = Llm.chat(few, null, 0.0);
        System.out.println("   " + r.strip().replace("\n", "\n   "));

        // 3) Chain-of-thought : on demande explicitement le raisonnement.
        System.out.println("\n[chain-of-thought]");
        String cot = TACHE + " Raisonne étape par étape, puis donne la réponse "
                + "finale précédée de \"Réponse :\".";
        r = Llm.chat(cot, null, 0.0);
        System.out.println("   " + r.strip().replace("\n", "\n   "));

        // 4) Self-consistency : N tirages CoT à température > 0, vote majoritaire.
        System.out.println("\n[self-consistency] 5 tirages CoT (température 0.8), vote à la majorité :");
        Map<String, Integer> votes = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            String tirage = Llm.chat(cot, null, 0.8);
            String reponse = extraireReponseFinale(tirage);
            votes.merge(reponse, 1, Integer::sum);
            System.out.println("   tirage " + i + " -> " + reponse);
        }
        String retenue = votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("?");
        System.out.println("   Votes : " + votes + "  -> réponse retenue : " + retenue);

        System.out.println("\n(Note : sur un petit modèle 1b, few-shot et CoT fiabilisent le format");
        System.out.println(" et la démarche, le vote lisse l'aléa ; le gain est encore plus net");
        System.out.println(" sur les gros modèles. Prix du vote : N appels = N fois le coût.)");
    }

    /** Extrait le nombre donné après "Réponse :" (repli : dernier nombre du texte). */
    static String extraireReponseFinale(String texte) {
        Matcher m = Pattern.compile("R[ée]ponse\\s*(finale)?\\s*:?\\s*(-?\\d+)",
                Pattern.CASE_INSENSITIVE).matcher(texte);
        String trouve = null;
        while (m.find()) {
            trouve = m.group(2);          // on garde la DERNIÈRE occurrence
        }
        if (trouve != null) {
            return trouve;
        }
        Matcher n = Pattern.compile("(-?\\d+)").matcher(texte);
        while (n.find()) {
            trouve = n.group(1);
        }
        return trouve == null ? "?" : trouve;
    }

    // ------------------------------------------------------------------ //
    // Partie 2 — sortie JSON validée (Jackson + revalidation champ par champ)
    // ------------------------------------------------------------------ //

    /** Schéma cible : la réponse du LLM DOIT s'y conformer. */
    record FicheProduit(String nom, String categorie, double prixEur, boolean enStock) {

        static final Set<String> CATEGORIES_AUTORISEES =
                Set.of("informatique", "maison", "sport", "alimentaire", "autre");

        /**
         * Valide un JsonNode et construit l'objet typé (équivalent du
         * model_validate de pydantic) : types vérifiés, contraintes
         * appliquées, catégorie inconnue NORMALISÉE en "autre" (robustesse).
         */
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
                categorie = "autre";      // on normalise plutôt que de planter
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

    static void demoJsonValide() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("PARTIE 2 — sortie JSON structurée validée (Jackson + revalidation)");
        System.out.println("=".repeat(70));

        String prompt =
                "Extrais une fiche produit de ce texte et réponds UNIQUEMENT en JSON "
                + "avec les clés exactes : nom (string), categorie (string parmi "
                + "informatique/maison/sport/alimentaire/autre), prix_eur (number), "
                + "en_stock (boolean). Pas de texte autour.\n\n"
                + "Texte : « Le clavier mécanique AZERTY Pro est dispo à 89,90 € et "
                + "actuellement en stock. C'est un accessoire informatique. »";

        // On laisse jusqu'à 3 tentatives : le petit modèle peut rater le format.
        Exception derniereErreur = null;
        for (int tentative = 1; tentative <= 3; tentative++) {
            String r = Llm.chat(prompt, null, 0.0);
            System.out.println("\n[tentative " + tentative + "] réponse brute du modèle :");
            String brut = r.strip().replace("\n", "\n   ");
            System.out.println("   " + brut.substring(0, Math.min(brut.length(), 400)));
            try {
                JsonNode data = extraireJson(r);
                FicheProduit fiche = FicheProduit.valider(data);
                System.out.println("\n  VALIDATION Java : OK");
                System.out.println("  Objet typé : " + fiche);
                return;
            } catch (IllegalArgumentException exc) {
                derniereErreur = exc;
                System.out.println("  Validation échouée : " + exc.getMessage());
                // On renforce la consigne pour la tentative suivante.
                prompt += "\n\nRappel : réponds STRICTEMENT en JSON valide, rien d'autre.";
            }
        }

        System.out.println("\n  Échec après 3 tentatives. Dernière erreur : " + derniereErreur);
        System.out.println("  -> En prod : on rejette, on logge, ou on bascule sur un modèle plus fort");
        System.out.println("     (ou sorties structurées natives côté API : voir réf. Claude/Mistral).");
    }

    public static void main(String[] args) {
        demoPrompting();
        demoJsonValide();
    }
}
