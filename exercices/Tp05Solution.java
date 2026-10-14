package fr.utopios.formation.solutions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.utopios.formation.commun.Llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public class Tp05Solution {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Pattern OBJET_JSON = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    /** JOURNAL des appels d'outils : qui a appelé quoi, avec quels arguments. */
    private static final List<String> JOURNAL = new ArrayList<>();

    // ======================================================================
    // ETAPE 1 — Les outils Java reels et leur protocole d'appel
    // ======================================================================

    static Map<String, Object> stockProduit(String reference) {
        return switch (reference.toUpperCase()) {
            case "REF-101" -> Map.of("quantite", 42, "entrepot", "Lille");
            case "REF-205" -> Map.of("quantite", 0, "entrepot", "Nantes",
                    "note", "rupture - reapprovisionnement le 15");
            case "REF-330" -> Map.of("quantite", 7, "entrepot", "Lyon");
            default -> throw new IllegalArgumentException("reference inconnue : " + reference);
        };
    }

    static double convertirDevise(double montantEur, String deviseCible) {
        return switch (deviseCible.toUpperCase()) {
            case "USD" -> montantEur * 1.08;
            case "GBP" -> montantEur * 0.85;
            case "JPY" -> montantEur * 161.0;
            default -> throw new IllegalArgumentException("devise inconnue : " + deviseCible);
        };
    }

    /**
     * La "declaration d'outils" : un petit modele local n'a pas de tool
     * calling natif fiable, on impose donc un PROTOCOLE JSON EXPLICITE.
     * C'est la meme information qu'un schema de tool use cloud :
     * nom, description, arguments types.
     */
    private static final String SYSTEME = """
            Tu es un assistant qui peut utiliser des outils.

            Outils disponibles :
            1. stock_produit — donne le stock d'une reference produit.
               Appel : {"outil": "stock_produit", "arguments": {"reference": "REF-XXX"}}
            2. convertir_devise — convertit un montant en euros vers une autre devise.
               Appel : {"outil": "convertir_devise", "arguments": {"montant_eur": nombre, "devise_cible": "USD|GBP|JPY"}}

            Regles STRICTES :
            - Si la question porte sur un stock ou une conversion de devise, reponds
              UNIQUEMENT avec l'objet JSON de l'appel d'outil, sans aucun autre texte.
            - Quand un message "RESULTAT DE L'OUTIL" arrive, n'appelle PLUS d'outil :
              donne la reponse finale en francais, en citant le resultat tel quel.
            """;

    // ======================================================================
    // ETAPE 2 — La boucle d'orchestration robuste
    // ======================================================================

    /** Extrait un appel d'outil du texte du modele, ou null (reponse finale). */
    static ObjectNode extraireAppelOutil(String texte) {
        JsonNode obj = premierObjetJson(texte);
        // Repli robustesse : le 1b oublie parfois l'accolade ouvrante.
        if (nomOutil(obj) == null && texte.contains("\"outil\"")) {
            obj = premierObjetJson("{" + texte.substring(texte.indexOf("\"outil\"")));
        }
        String nom = nomOutil(obj);
        if (nom == null) {
            return null;
        }
        JsonNode args = obj.has("arguments") ? obj.get("arguments") : obj.get("parameters");
        ObjectNode appel = MAPPER.createObjectNode();
        appel.put("outil", nom);
        appel.set("arguments", args == null ? MAPPER.createObjectNode() : args);
        return appel;
    }

    /** Premier objet JSON parsable du texte (tolere une accolade en trop). */
    private static JsonNode premierObjetJson(String texte) {
        Matcher m = OBJET_JSON.matcher(texte);
        if (!m.find()) {
            return null;
        }
        String candidat = m.group();
        for (int essai = 0; essai < 3; essai++) {
            try {
                return MAPPER.readTree(candidat);
            } catch (Exception e) {
                if (!candidat.endsWith("}")) {
                    return null;
                }
                candidat = candidat.substring(0, candidat.length() - 1).strip();
            }
        }
        return null;
    }

    /** Nom d'outil connu porte par l'objet, ou null. */
    private static String nomOutil(JsonNode obj) {
        if (obj == null) {
            return null;
        }
        String nom = obj.path("outil").asText(obj.path("name").asText(null));
        return ("stock_produit".equals(nom) || "convertir_devise".equals(nom)) ? nom : null;
    }

    /** Execute l'outil, coercition de types incluse ; jamais d'exception brute. */
    static String executerOutil(ObjectNode appel) {
        String nom = appel.get("outil").asText();
        JsonNode args = appel.get("arguments");
        JOURNAL.add(nom + "(" + args + ")");    // journal des appels
        try {
            if ("convertir_devise".equals(nom)) {
                // asDouble() convertit aussi "250" (string) -> 250.0 : coercition.
                double resultat = convertirDevise(args.path("montant_eur").asDouble(),
                        args.path("devise_cible").asText());
                return String.valueOf(resultat);
            }
            return MAPPER.writeValueAsString(stockProduit(args.path("reference").asText()));
        } catch (Exception e) {
            // L'erreur d'outil est RENVOYEE au modele, pas propagee.
            return "{\"erreur\": \"" + e.getMessage() + "\"}";
        }
    }

    /** Un tour de conversation multi-messages (POST /api/chat, stream:false). */
    private static String tourDeChat(ArrayNode messages) throws Exception {
        ObjectNode corps = MAPPER.createObjectNode();
        corps.put("model", Llm.modeleChat());
        corps.put("stream", false);
        corps.putObject("options").put("temperature", 0.0);   // 1b + tool calls : temp 0
        corps.set("messages", messages);
        HttpRequest requete = HttpRequest.newBuilder()
                .uri(URI.create(Llm.BASE_URL + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(corps)))
                .build();
        HttpResponse<String> reponse = HTTP.send(requete, HttpResponse.BodyHandlers.ofString());
        if (reponse.statusCode() != 200) {
            throw new IllegalStateException("Ollama a repondu " + reponse.statusCode());
        }
        return MAPPER.readTree(reponse.body()).path("message").path("content").asText();
    }

    private static void empiler(ArrayNode messages, String role, String contenu) {
        messages.addObject().put("role", role).put("content", contenu);
    }

    /** decider -> executer -> renvoyer -> boucler, avec budget de tours. */
    static String assistant(String question, int maxTours) throws Exception {
        System.out.println("\n[Question] " + question);
        ArrayNode messages = MAPPER.createArrayNode();
        empiler(messages, "system", SYSTEME);
        empiler(messages, "user", question);

        for (int tour = 1; tour <= maxTours; tour++) {
            String contenu = tourDeChat(messages);
            ObjectNode appel = extraireAppelOutil(contenu);
            if (appel == null) {
                System.out.println("  (tour " + tour + ") réponse finale.");
                return contenu.strip();
            }
            System.out.println("  (tour " + tour + ") appel : "
                    + appel.get("outil").asText() + "(" + appel.get("arguments") + ")");
            String resultat = executerOutil(appel);
            System.out.println("            -> résultat : " + resultat);
            // Re-empiler la decision de l'assistant AVANT le resultat d'outil.
            empiler(messages, "assistant", contenu);
            empiler(messages, "user", "RESULTAT DE L'OUTIL " + appel.get("outil").asText()
                    + " : " + resultat
                    + "\nDonne maintenant la réponse finale en français en citant précisément les valeurs de ce résultat.");
        }
        return "(limite de tours atteinte sans réponse finale)";
    }

    // ======================================================================
    // ETAPE 3 — Streaming (stream:true, un chunk JSON par ligne)
    // ======================================================================

    static void demoStreaming() throws Exception {
        System.out.print("\nRéponse en flux : ");
        System.out.flush();
        ObjectNode corps = MAPPER.createObjectNode();
        corps.put("model", Llm.modeleChat());
        corps.put("stream", true);
        corps.putArray("messages").addObject().put("role", "user")
                .put("content", "Énumère 3 avantages du streaming, très brièvement.");
        HttpRequest requete = HttpRequest.newBuilder()
                .uri(URI.create(Llm.BASE_URL + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(corps)))
                .build();
        int morceaux = 0;
        HttpResponse<Stream<String>> reponse =
                HTTP.send(requete, HttpResponse.BodyHandlers.ofLines());
        for (String ligne : (Iterable<String>) reponse.body()::iterator) {
            if (ligne.isBlank()) {
                continue;
            }
            System.out.print(MAPPER.readTree(ligne).path("message").path("content").asText());
            System.out.flush();
            morceaux++;
        }
        System.out.println("\n[streaming terminé : " + morceaux + " morceaux reçus]");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("TP 05 (solution) — Assistant à outils : orchestration + streaming");
        System.out.println("=".repeat(70));

        // --- Etape 1 : tester le DETERMINISTE d'abord, hors LLM -----------
        System.out.println("\n### Étape 1 — Outils testés hors LLM ###");
        Map<String, Object> ref101 = stockProduit("REF-101");
        System.out.println("stockProduit(\"REF-101\") = " + ref101);
        double usd = convertirDevise(250, "USD");
        System.out.println("convertirDevise(250, \"USD\") = " + usd);
        if (!ref101.get("quantite").equals(42) || usd != 270.0) {
            throw new IllegalStateException("les outils doivent rendre 42 unites et 270.0 USD");
        }
        System.out.println("Vérification outils : OK");

        // --- Etape 2 : la boucle d'orchestration --------------------------
        System.out.println("\n### Étape 2 — Boucle d'orchestration (le modèle décide, "
                + "le code exécute) ###");
        for (String q : List.of(
                "Utilise l'outil stock_produit pour me donner le stock de la référence REF-101.",
                "Utilise l'outil convertir_devise pour convertir 250 euros en dollars (USD).")) {
            System.out.println("  REPONSE : " + assistant(q, 5));
        }
        System.out.println("\nJournal des appels d'outils : " + JOURNAL);

        // --- Etape 3 : le streaming ---------------------------------------
        System.out.println("\n### Étape 3 — Streaming ###");
        demoStreaming();

        System.out.println("""

                Gestion des tokens (à expliquer en débriefing) : à chaque tour, on
                ré-envoie TOUT l'historique (messages + résultats d'outils) -> l'entrée
                grossit à chaque tour. Pour borner : limiter maxTours, tronquer
                l'historique ancien, et lire prompt_eval_count / eval_count sur les
                réponses non streamées (compteurs affichés par la couche Llm).""");
    }
}
