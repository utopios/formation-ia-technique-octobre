---
name: commit-maison
description: Convention de messages de commit de l'équipe — Conventional
  Commits en français avec référence Jira obligatoire. À charger avant
  tout git commit ou avant de rédiger un message de PR.
license: MIT
compatibility: opencode
metadata:
  audience: developpeurs
---

## Format

`<type>(<scope>): <description> [<ticket>]`

## Types autorisés

`feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`

Aucun autre type. Pas de `update`, pas de `wip`, pas de `misc`.

## Scopes du dépôt

`auth`, `api`, `db`, `schemas`, `config`, `ci`

## Règles

- Description en **français**, à l'impératif, sans majuscule initiale,
  sans point final.
- 72 caractères maximum sur la première ligne, ticket compris.
- Le ticket Jira est obligatoire, format `FACT-123`. Si tu ne le trouves
  pas dans la branche courante ou dans la conversation, **demande-le**.
  Ne l'invente jamais, n'utilise jamais de placeholder.
- Un commit = un changement logique. Si tu dois écrire « et » dans la
  description, fais deux commits.
- Corps de commit optionnel, séparé par une ligne vide, uniquement pour
  expliquer le *pourquoi*. Le *quoi* est déjà dans le diff.
- Les changements de sécurité portent le type `fix` et mentionnent
  `[sécurité]` en début de corps.

## Exemples valides

```
feat(auth): ajouter la rotation des refresh tokens [FACT-412]
fix(api): corriger le code retour 500 sur body vide [FACT-418]
refactor(db): extraire les requêtes facture dans un repository [FACT-401]
test(schemas): couvrir les cas limites de validation montant [FACT-422]
```

## Exemples invalides

```
Update auth                          → pas de type, anglais, vague
feat: various fixes [FACT-1]         → "various" = plusieurs commits
fix(auth): corriger le bug.          → point final
feat(auth): ajouter X et corriger Y  → deux changements
fix(api): corriger le token [XXX-0]  → ticket inventé
```

## Trouver le ticket

Dans l'ordre : nom de la branche (`feature/FACT-412-rotation-tokens`),
puis la conversation en cours. Sinon, demander.
