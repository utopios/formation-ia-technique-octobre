---
name: revue-api-maison
description: Conventions de sécurité et de qualité spécifiques aux APIs
  REST de l'équipe — middleware d'authentification requireAuth, validation
  Zod obligatoire, format d'erreur RFC7807, règles de logging et de masquage
  des données personnelles. À charger avant toute revue de code sur
  src/api/, src/middleware/ ou src/schemas/.
license: MIT
compatibility: opencode
metadata:
  audience: developpeurs
  scope: backend
  version: "2.1"
---

## Ce que je couvre

Les règles internes de l'équipe que le modèle ne peut pas déduire en
lisant le code. Je ne couvre pas les bonnes pratiques générales de
sécurité web : elles sont supposées connues.

## Authentification

- Toute route hors `/health`, `/auth/login` et `/auth/refresh` passe par
  le middleware `requireAuth`. Une route sans ce middleware est un
  **BLOQUANT**, sans exception et sans discussion sur le caractère
  "public" des données.
- Le payload JWT contient exactement `sub`, `roles`, `exp`, `iat`. Tout
  autre champ est une fuite d'information — notamment `email`, `name`,
  `plan` ou tout identifiant interne.
- Durée de vie access token : 15 minutes. Refresh : 7 jours, rotatif à
  chaque usage.
- L'algorithme est vérifié explicitement côté serveur. Un `jwt.verify`
  sans option `algorithms` est un **BLOQUANT** (attaque `alg: none`).
- Le secret vient de `process.env.JWT_SECRET`. Toute valeur littérale
  dans le code, y compris en fallback `|| "dev"`, est un **BLOQUANT**.

## Autorisation

- L'appartenance de la ressource est vérifiée en plus du rôle. Un
  utilisateur authentifié ne doit pas pouvoir lire la ressource d'un
  autre par changement d'identifiant dans l'URL.
- La vérification se fait en base, jamais sur la seule foi du token.

## Validation d'entrée

- Chaque handler valide son body avec un schéma Zod exporté depuis
  `src/schemas/`. Pas de validation inline dans le handler.
- `.passthrough()` est interdit sur les schémas d'entrée : il laisse
  passer des champs non déclarés jusqu'à la couche persistance.
- Les paramètres de requête numériques (`limit`, `offset`, `page`) sont
  bornés. Un `limit` non borné est un **MAJEUR**.

## Accès aux données

- Aucune concaténation de chaîne dans une requête SQL. Requêtes
  paramétrées uniquement. Toute interpolation de variable dans du SQL est
  un **BLOQUANT**.
- Les `SELECT *` sont interdits sur les tables contenant des données
  personnelles : on énumère les colonnes.

## Format d'erreur

- Format RFC7807 uniquement, via le helper `problem()` de
  `src/lib/problem.ts`.
- Aucun message d'erreur ne remonte de détail d'infrastructure au client :
  nom de table, requête SQL, stack trace, version de dépendance.
- Les erreurs d'authentification renvoient un message générique
  identique en cas d'utilisateur inconnu et de mot de passe faux
  (pas d'énumération de comptes).

## Logging

Interdiction absolue de logger : mots de passe, tokens (même partiels),
numéros de carte, emails complets, adresses postales.

Masquages imposés :
- Email → `j***@domaine.fr`
- Identifiant utilisateur → autorisé en clair
- Token → jamais, même tronqué

## Sévérité par défaut

| Catégorie | Niveau |
|---|---|
| Route sans `requireAuth` | BLOQUANT |
| Secret en dur ou en fallback | BLOQUANT |
| SQL concaténé | BLOQUANT |
| Donnée personnelle loggée | BLOQUANT |
| Absence de vérification d'appartenance | BLOQUANT |
| Validation Zod absente ou `.passthrough()` | MAJEUR |
| `limit` non borné | MAJEUR |
| `SELECT *` sur table personnelle | MAJEUR |
| Format d'erreur non RFC7807 | MINEUR |

## Pour aller plus loin

La liste complète des anti-patterns déjà rencontrés dans ce dépôt, avec
exemples de code avant/après, est dans `checklist.md` (même dossier).
Consulte-la si un constat te semble ambigu.

## Quand ne pas m'utiliser

Je ne suis pas pertinente pour les scripts de build, les tests, ou le
code front. Dans ces cas, ne me charge pas.
