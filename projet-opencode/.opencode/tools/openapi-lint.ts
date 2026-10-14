import { tool } from "@opencode-ai/plugin"
import path from "path"

/**
 * Le nom du FICHIER devient le nom du tool : ici `openapi-lint`.
 *
 * La `description` est la seule chose sur laquelle le modèle se base pour
 * décider d'appeler ce tool. Elle est aussi importante que le code.
 */
export default tool({
  description:
    "Valide un fichier OpenAPI contre les règles maison : operationId en " +
    "camelCase, présence d'une réponse 4xx sur chaque opération, exemples " +
    "obligatoires sur les schémas de requête, aucune route sans security. " +
    "Retourne la liste des violations, une par ligne.",
  args: {
    spec: tool.schema
      .string()
      .describe("Chemin du fichier OpenAPI, relatif à la racine du dépôt"),
    strict: tool.schema
      .boolean()
      .default(false)
      .describe("Si true, les avertissements sont remontés comme des erreurs"),
  },
  async execute(args, context) {
    // context expose : agent, sessionID, messageID, directory, worktree
    const script = path.join(context.worktree, ".opencode/tools/lint_openapi.py")
    const target = path.join(context.worktree, args.spec)

    try {
      const flags = args.strict ? ["--strict"] : []
      const out = await Bun.$`python3 ${script} ${target} ${flags}`.text()
      return out.trim() || "Aucune violation détectée."
    } catch (e: any) {
      return `Échec du lint : ${e.stderr?.toString() ?? e.message}`
    }
  },
})
