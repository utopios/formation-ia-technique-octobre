package fr.utopios.formation.module03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.utopios.formation.commun.Llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Exercice03Solution {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String SYSTEM =
            "Tu es un agent de tri du support client. Ta mission : classer un message "
            + "client entrant et recommander une action. Tu réponds UNIQUEMENT par un objet "
            + "JSON, sans phrase autour.";

    /** Gabarit paramétré : le placeholder {message} est remplacé à l'appel. */
    static final String TEMPLATE = """
            Classe le message client ci-dessous.

            Contraintes STRICTES :
            - "categorie" doit être EXACTEMENT l'une de : "facturation", "technique", \
            "commercial", "reclamation". Si aucune ne convient, utilise "autre".
            - "priorite" doit être EXACTEMENT l'une de : "haute", "moyenne", "basse".
            - "action_recommandee" : une phrase courte, impérative.
            - Réponds par un SEUL objet JSON avec EXACTEMENT ces clés : \
            categorie, priorite, action_recommandee. Rien d'autre.
            

            Exemples :
            Message: "Je n'ai pas reçu ma facture du mois dernier."
            Sortie: {"categorie": "facturation", "priorite": "moyenne", \
            "action_recommandee": "Renvoyer la facture manquante au client."}

            Message: "Je veux résilier, votre service est inacceptable !"
            Sortie: {"categorie": "reclamation", "priorite": "haute", \
            "action_recommandee": "Escalader vers un conseiller pour rétention."}

            Message: "{message}"
            Sortie:""";

    static final String[] MESSAGES_TEST = {
        "Bonjour, ma commande #4821 n'est jamais arrivée, c'est inadmissible, "
        + "je veux un remboursement immédiat.",
        "Est-ce que votre offre Pro inclut le support téléphonique ?",
        "petit bug : le bouton export reste grisé sur Firefox",
    };

    /** Vérifie que la sortie est un JSON parsable aux 3 clés exactes attendues. */
    static String verifierStructure(String sortie) {
        Matcher m = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(sortie);
        if (!m.find()) {
            return "NON : aucun objet JSON dans la sortie";
        }
        try {
            JsonNode n = MAPPER.readTree(m.group());
            for (String cle : new String[] {"categorie", "priorite", "action_recommandee"}) {
                if (!n.hasNonNull(cle)) {
                    return "NON : clé manquante '" + cle + "'";
                }
            }
            return "OUI : JSON parsable aux clés categorie/priorite/action_recommandee";
        } catch (Exception e) {
            return "NON : JSON invalide (" + e.getMessage() + ")";
        }
    }

    public static void main(String[] args) {
        // ================================================================
        // PARTIE 1 — Diagnostic du prompt naïf
        // ================================================================
        System.out.println("=".repeat(70));
        System.out.println("PARTIE 1 — Diagnostic du prompt naïf");
        System.out.println("=".repeat(70));

        System.out.println("\nPrompt naïf de départ : \"Dis-moi ce qu'il faut en faire\"");
        System.out.println("""

                Défauts majeurs (au moins 4 attendus) :
                 1. Aucun FORMAT imposé          -> paragraphe ou liste au hasard, parsing qui plante
                 2. Aucune liste fermée          -> catégories inventées, routage impossible
                 3. Aucun RÔLE ni mission        -> registre et niveau de détail incohérents
                 4. Aucune notion de PRIORITÉ    -> file de traitement impossible à ordonner
                 5. Aucun repli pour l'inclassable -> catégorie forcée ou hallucinée
                 6. Pas de « ne renvoie QUE le format » -> « Bien sûr, voici… » casse le JSON

                Conséquence : sortie non déterministe en STRUCTURE, donc non industrialisable.""");

        // ================================================================
        // PARTIE 2 — Prompt robuste testé sur les 3 messages de l'énoncé
        // ================================================================
        System.out.println("=".repeat(70));
        System.out.println("PARTIE 2 — Prompt robuste testé sur les 3 messages de l'énoncé");
        System.out.println("=".repeat(70));

        System.out.println("\nÉléments imposés dans le prompt robuste :");
        System.out.println(" - SYSTEM : rôle clair (agent de tri) + mission + « UNIQUEMENT du JSON »");
        System.out.println(" - Listes FERMÉES (catégories, priorités) + repli « autre »");
        System.out.println(" - FORMAT parsable : un seul objet JSON, clés exactes, rien d'autre");
        System.out.println(" - 2 exemples few-shot variés (gabarit Java : placeholder {message}");
        System.out.println("   remplacé par replace, pas de doublage d'accolades nécessaire)\n");

        for (String m : MESSAGES_TEST) {
            String prompt = TEMPLATE.replace("{message}", m);
            String sortie = Llm.chat(prompt, SYSTEM, 0.0).strip();
            String extrait = m.substring(0, Math.min(m.length(), 60));
            System.out.println("Message : \"" + extrait + "\"");
            System.out.println("Sortie  : " + sortie.replace("\n", "\n          "));
            System.out.println("Stable ? " + verifierStructure(sortie) + "\n");
        }

        // ================================================================
        // RÉCAPITULATIF — Points clés de la correction
        // ================================================================
        System.out.println("=".repeat(70));
        System.out.println("RÉCAPITULATIF — Points clés");
        System.out.println("=".repeat(70));
        System.out.println("""

                 1. Un prompt de prod est un CONTRAT : entrée isolée, sortie au format imposé,
                    valeurs dans des ensembles fermés.
                 2. Le few-shot fiabilise surtout le FORMAT ; les listes fermées évitent
                    l'invention de catégories.
                 3. Repli explicite (« autre ») pour l'inclassable : pas d'hallucination forcée.
                 4. Sur llama3.2:1b, l'important est la STABILITÉ DE STRUCTURE des 3 sorties
                    (JSON aux mêmes clés), même si un libellé peut se discuter.
                 5. La vraie robustesse = prompt strict + VALIDATION côté code (TP 3, Jackson).
                    Ne jamais faire confiance au seul prompt.
                 6. Lien module 4 : le message client est une DONNÉE non fiable (risque
                    d'injection) ; lien bonus : sorties structurées natives de l'API.
                """);
    }
}
