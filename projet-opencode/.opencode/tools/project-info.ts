import { tool } from "@opencode-ai/plugin"

/**
 * Plusieurs tools dans un même fichier : chaque export devient
 * `<fichier>_<export>`. Ce fichier crée donc `project-info_stack` et
 * `project-info_context`.
 */
export const stack = tool({
  description:
    "Retourne la stack technique du projet et les commandes npm disponibles. " +
    "À appeler quand tu ne sais pas quelle commande lancer pour tester ou builder.",
  args: {},
  async execute() {
    return [
      "Runtime      : Node 20 + TypeScript 5",
      "Framework    : Express 4",
      "Validation   : Zod 3",
      "Base         : PostgreSQL (pg)",
      "Tests        : Vitest",
      "",
      "npm run test       # suite complète",
      "npm run test:watch # mode watch",
      "npm run lint       # eslint",
      "npm run typecheck  # tsc --noEmit",
    ].join("\n")
  },
})

export const context = tool({
  description:
    "Retourne le contexte d'exécution courant de l'agent (session, agent, " +
    "répertoire, worktree). Utile pour déboguer une config OpenCode.",
  args: {},
  async execute(_args, ctx) {
    return JSON.stringify(
      {
        agent: ctx.agent,
        sessionID: ctx.sessionID,
        messageID: ctx.messageID,
        directory: ctx.directory,
        worktree: ctx.worktree,
      },
      null,
      2,
    )
  },
})
