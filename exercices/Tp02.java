package fr.utopios.formation.tp;

import fr.utopios.formation.commun.Llm;

import java.util.Locale;

/**
* TP 02 — Premier appel structuré à un LLM et lecture de l'usage tokens.
*/
public class Tp02 {

    public static void main(String[] args) {
        etape1AppelStructure();
        etape2ContextePayeAChaqueAppel();
        etape3EstimationCaching();
    }

    static void etape1AppelStructure() {
        System.out.println("=== ÉTAPE 1 — Appel structuré + usage tokens ===");
        System.out.println("=".repeat(70));
        System.out.println("ÉTAPE 1 — Appel structuré via la couche Llm et usage tokens");
        System.out.println("=".repeat(70));

        System.out.println("\nModèle de chat : " + Llm.modeleChat()
                + " (Ollama local, surchargeable par OLLAMA_CHAT_MODEL)");

        String reponse = Llm.chat(
                "Donne UNE phrase expliquant ce qu'est la fenêtre de contexte d'un LLM.",
                "Tu es un formateur concis. Réponds en anglais, en une seule phrase.",
                0);

        System.out.println("\nRéponse  : " + reponse.strip());
        System.out.println("Modèle   : " + Llm.modeleChat() + " (Ollama local)");
        System.out.printf("Usage    : entrée=%d tokens, sortie=%d tokens%n",
                Llm.derniersTokensPrompt(), Llm.derniersTokensReponse());

        System.out.println("\nLecture : la couche commune Llm renvoie le texte ET expose les");
        System.out.println("compteurs d'usage (derniersTokensPrompt / derniersTokensReponse,");
        System.out.println("issus de prompt_eval_count / eval_count d'Ollama). En production,");
        System.out.println("on ne logge jamais seulement « la réponse » : on logge aussi l'usage.");
    }

    static void etape2ContextePayeAChaqueAppel() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("ÉTAPE 2 — Le contexte se paie à CHAQUE appel (system court vs long)");
        System.out.println("=".repeat(70) + "\n");

        String question = "Réponds en un seul mot : quelle est la capitale de la France ?";
        String systemCourt = "Tu réponds en français, en un seul mot.";
        // Le meme system, precede d'un gros "reglement" STABLE (prefixe repete
        // pour simuler un long document de contexte) :
        String systemLong = ("RÈGLEMENT INTERNE : toute réponse doit être vérifiable, sourcée, "
                + "concise et respecter la charte de l'entreprise. ").repeat(50)
                + systemCourt;

        String r1 = Llm.chat(question, systemCourt, 0.0);
        int entreeCourt = Llm.derniersTokensPrompt();
        System.out.printf("system COURT : entrée=%d tokens | réponse : %s%n",
                entreeCourt, r1.strip());

        String r2 = Llm.chat(question, systemLong, 0.0);
        int entreeLong = Llm.derniersTokensPrompt();
        System.out.printf("system LONG  : entrée=%d tokens | réponse : %s%n",
                entreeLong, r2.strip());

        System.out.printf("%nSurcoût du préfixe : +%d tokens d'entrée, refacturés à CHAQUE appel.%n",
                entreeLong - entreeCourt);
        System.out.println("Même question, même réponse : seul le préfixe stable gonfle l'entrée.");
        System.out.println("C'est exactement ce que le prompt caching évite de repayer plein tarif.");
    }

    static void etape3EstimationCaching() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("ÉTAPE 3 — Prompt caching : estimation chiffrée du gain");
        System.out.println("=".repeat(70));

        System.out.println("""

                Définitions à restituer :
                 - Fenêtre de contexte : nb max de tokens (entrée+sortie) traités d'un coup
                   (réf. 2026 : Claude jusqu'à 1M).
                 - Prompt caching : réutiliser un PRÉFIXE stable pour payer moins / aller plus
                   vite (écriture ~1,25x au 1er appel, lecture ~0,1x ensuite, TTL 5 min,
                   min cacheable Opus 4.8 = 4096 tokens, un octet changé invalide le cache).
                """);

        int prefixeTokens = 50_000;   // gros document de contexte STABLE
        int requetes = 20;            // 20 questions sur le meme document
        double prixIn = 5.00 / 1_000_000;  // Opus 4.8, ref. 2026

        double sansCache = prefixeTokens * requetes * prixIn;
        double avecCache = prefixeTokens * 1.25 * prixIn                     // 1 ecriture
                + prefixeTokens * (requetes - 1) * 0.1 * prixIn;             // 19 lectures

        System.out.printf(Locale.US, "Hypothèse : préfixe stable de %,d tokens, %d requêtes.%n",
                prefixeTokens, requetes);
        System.out.printf(Locale.US, "Coût SANS cache : $%.4f%n", sansCache);
        System.out.printf(Locale.US, "Coût AVEC cache : $%.4f%n", avecCache);
        System.out.printf(Locale.US, "Économie : ~%.0f%% sur le préfixe%n",
                (1 - avecCache / sansCache) * 100);
        System.out.println("\nAvec une clé Claude : mesure réelle via cache_creation_input_tokens");
        System.out.println("(1er appel) puis cache_read_input_tokens (appels suivants).");
    }
}