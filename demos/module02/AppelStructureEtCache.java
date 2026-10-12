package fr.utopios.formation.module02;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.utopios.formation.commun.Llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Module 2 - Premier appel LLM structure, usage tokens, fenetre de contexte
 * et prompt caching (version Java).
 *
 * <p>Deux parties :</p>
 * <ul>
 *   <li>A) Appel LLM via la couche commune {@link Llm} (Ollama local) et
 *       lecture de l'usage tokens (entree / sortie) ;</li>
 *   <li>B) Demo "fenetre de contexte" + "prompt caching" :
 *     <ul>
 *       <li>si ANTHROPIC_API_KEY est presente : DEUX appels Claude avec le
 *           meme long prefixe marque {@code cache_control} et lecture de
 *           {@code cache_read_input_tokens} (ecriture au 1er appel,
 *           lecture ~0.1x au 2e) — appel HTTP direct, sans SDK ;</li>
 *       <li>sinon : explication mockee chiffree (ref. prix 2026) pour
 *           comprendre le gain (~84 % d'economie sur le prefixe).</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Lancement :</p>
 * <pre>
 * cd ~/formation/java
 * mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.module02.AppelStructureEtCache"
 * </pre>
 */
public class AppelStructureEtCache {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        partieAAppelStructure();
        partieBContexteEtCache();
    }

    /**
     * Partie A : un appel LLM "propre" via la couche commune Llm, puis
     * lecture systematique de l'usage tokens (le reflexe de production).
     */
    static void partieAAppelStructure() {
        System.out.println("=".repeat(70));
        System.out.println("A) Appel LLM structuré + usage tokens");
        System.out.println("=".repeat(70));
        System.out.println("Couche commune : fr.utopios.formation.commun.Llm (Ollama local)");
        System.out.println("Modèle de chat : " + Llm.modeleChat());

        String reponse = Llm.chat(
                "Donne UNE phrase expliquant ce qu'est la fenêtre de contexte d'un LLM.",
                "Tu es un formateur concis. Réponds en français, en une seule phrase.",
                0.2);

        System.out.println("\nRéponse  : " + reponse.strip());
        System.out.println("Modèle   : " + Llm.modeleChat() + " (Ollama local)");
        if (Llm.derniersTokensPrompt() >= 0) {
            System.out.printf("Usage    : entrée=%d tokens, sortie=%d tokens%n",
                    Llm.derniersTokensPrompt(), Llm.derniersTokensReponse());
        } else {
            System.out.println("Usage    : (compteur de tokens indisponible)");
        }
    }

    /**
     * Partie B : rappel fenetre de contexte, puis demo du prompt caching —
     * mesure reelle sur Claude si une cle est posee, sinon calcul mocke.
     */
    static void partieBContexteEtCache() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("B) Fenêtre de contexte & prompt caching");
        System.out.println("=".repeat(70));
        System.out.println("Fenêtre de contexte = nb max de tokens (entrée + sortie) que le modèle");
        System.out.println("traite d'un coup. Réf. 2026 : Claude jusqu'à 1M tokens de contexte.");
        System.out.println("Prompt caching = réutiliser un PRÉFIXE stable pour payer moins/aller");
        System.out.println("plus vite sur les requêtes suivantes.");

        if (!cacheClaudeReel()) {
            cacheExplicationMockee();
        }
    }

    /**
     * Demo REELLE de prompt caching sur Claude (appel HTTP direct, sans SDK).
     *
     * @return true si la demo a pu etre executee (cle presente), false sinon
     */
    static boolean cacheClaudeReel() {
        String cle = System.getenv("ANTHROPIC_API_KEY");
        if (cle == null || cle.isBlank()) {
            return false;
        }
        String modele = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-opus-4-8");

        // Le prefixe DOIT etre assez gros pour etre cacheable.
        // Ref. 2026 : minimum cacheable Opus 4.8 = 4096 tokens. On genere un
        // gros document stable (le "contexte" partage entre les deux requetes).
        String grosContexte = ("RÈGLEMENT INTÉRIEUR (extrait répété pour atteindre la taille "
                + "minimale cacheable). ").repeat(1200);

        System.out.println("\nDémo RÉELLE de prompt caching sur Claude :");
        try {
            long t0 = System.nanoTime();
            JsonNode u1 = appelClaude(cle, modele, grosContexte, "Réponds juste 'OK 1'.");
            double d1 = (System.nanoTime() - t0) / 1e9;
            afficherUsageCache("1er appel ", d1, u1);

            t0 = System.nanoTime();
            JsonNode u2 = appelClaude(cle, modele, grosContexte, "Réponds juste 'OK 2'.");
            double d2 = (System.nanoTime() - t0) / 1e9;
            afficherUsageCache("2e appel  ", d2, u2);

            System.out.println("  -> Au 2e appel, les tokens du préfixe sont LUS depuis le cache "
                    + "(~0.1x le prix) au lieu d'être refacturés plein tarif.");
            return true;
        } catch (Exception e) {
            System.out.println("  (Appel Claude impossible : " + e.getMessage() + ")");
            return false;
        }
    }

    /** Affiche la ligne d'usage cache d'un appel Claude (champ usage). */
    private static void afficherUsageCache(String etiquette, double duree, JsonNode usage) {
        System.out.printf(Locale.US,
                "  %s: %.2fs | écriture cache=%d | lecture cache=%d | entrée non cachée=%d%n",
                etiquette, duree,
                usage.path("cache_creation_input_tokens").asInt(0),
                usage.path("cache_read_input_tokens").asInt(0),
                usage.path("input_tokens").asInt(0));
    }

    /**
     * Un appel Claude avec le prefixe stable marque cache_control (ephemeral)
     * dans le bloc system. Retourne le champ "usage" de la reponse.
     */
    private static JsonNode appelClaude(String cle, String modele,
                                        String grosContexte, String question) throws Exception {
        ObjectNode corps = MAPPER.createObjectNode();
        corps.put("model", modele);
        corps.put("max_tokens", 64);
        // Marqueur de cache sur le prefixe stable :
        ObjectNode blocSystem = corps.putArray("system").addObject();
        blocSystem.put("type", "text");
        blocSystem.put("text", grosContexte);
        blocSystem.putObject("cache_control").put("type", "ephemeral");
        corps.putArray("messages").addObject()
                .put("role", "user").put("content", question);

        HttpRequest requete = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", cle)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(corps)))
                .build();
        HttpResponse<String> reponse = HttpClient.newHttpClient()
                .send(requete, HttpResponse.BodyHandlers.ofString());
        if (reponse.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + reponse.statusCode() + " : " + reponse.body());
        }
        return MAPPER.readTree(reponse.body()).path("usage");
    }

    /**
     * Explication chiffree du prompt caching (sans cle) — le repli mocke.
     *
     * <p>Ref. prix 2026 : Claude Opus 4.8 entree = 5.00 $/1M. Cache :
     * ecriture ~1.25x, lecture ~0.1x.</p>
     */
    static void cacheExplicationMockee() {
        System.out.println("\nDémo MOCKÉE de prompt caching (pas de ANTHROPIC_API_KEY) :");
        int prefixeTokens = 50_000;   // ex : un gros document de contexte stable
        int requetes = 20;            // 20 questions sur le meme document
        double prixIn = 5.00 / 1_000_000;

        double sansCache = prefixeTokens * requetes * prixIn;
        // Avec cache : 1 ecriture (1.25x) + (requetes-1) lectures (0.1x).
        double avecCache = prefixeTokens * 1.25 * prixIn
                + prefixeTokens * (requetes - 1) * 0.1 * prixIn;

        System.out.printf(Locale.US, "  Hypothèse : préfixe stable de %,d tokens, %d requêtes.%n",
                prefixeTokens, requetes);
        System.out.printf(Locale.US, "  Coût SANS cache : $%.4f%n", sansCache);
        System.out.printf(Locale.US, "  Coût AVEC cache : $%.4f%n", avecCache);
        double economie = (1 - avecCache / sansCache) * 100;
        System.out.printf(Locale.US, "  -> Économie ~%.0f%% sur la partie préfixe.%n", economie);
        System.out.println("  (Pose ANTHROPIC_API_KEY pour voir la mesure réelle.)");
    }
}
