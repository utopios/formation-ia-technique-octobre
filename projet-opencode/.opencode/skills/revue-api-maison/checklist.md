# Anti-patterns déjà rencontrés dans ce dépôt

Ce fichier est le niveau 3 du chargement progressif : il n'est lu que si
le SKILL.md y renvoie et que l'agent en a besoin.

---

## 1. Le fallback de secret

```ts
// AVANT — vu en production pendant 4 mois
const secret = process.env.JWT_SECRET || "dev-secret"

// APRÈS
const secret = process.env.JWT_SECRET
if (!secret) throw new Error("JWT_SECRET manquant")
```

Le fallback ne se voit pas en revue parce qu'il fonctionne. Il ne se voit
qu'en incident.

---

## 2. L'IDOR par identifiant d'URL

```ts
// AVANT — authentifié, donc "sécurisé"
app.get("/invoices/:id", requireAuth, async (req, res) => {
  const invoice = await db.query("SELECT * FROM invoices WHERE id = $1", [req.params.id])
  res.json(invoice)
})

// APRÈS
const invoice = await db.query(
  "SELECT id, amount, status, created_at FROM invoices WHERE id = $1 AND owner_id = $2",
  [req.params.id, req.auth.sub]
)
if (!invoice) return problem(res, 404, "Facture introuvable")
```

`requireAuth` répond à "qui es-tu", pas à "as-tu le droit sur cette
ressource". Les deux questions sont distinctes.

---

## 3. La concaténation qui a l'air sûre

```ts
// AVANT — "c'est juste un tri, pas une donnée utilisateur"
const rows = await db.query(`SELECT * FROM users ORDER BY ${req.query.sort}`)

// APRÈS
const ALLOWED_SORT = { name: "name", date: "created_at" } as const
const column = ALLOWED_SORT[req.query.sort as keyof typeof ALLOWED_SORT] ?? "created_at"
const rows = await db.query(`SELECT id, name, created_at FROM users ORDER BY ${column}`)
```

Le paramétrage ne s'applique pas aux noms de colonnes. Il faut une liste
blanche.

---

## 4. Le log de debug oublié

```ts
// AVANT
console.log("login attempt", { email: req.body.email, password: req.body.password })

// APRÈS
logger.info("login attempt", { email: maskEmail(req.body.email) })
```

Un log part dans un agrégateur, y reste 90 jours, et est lisible par
toute l'équipe support.

---

## 5. L'énumération de comptes

```ts
// AVANT
if (!user) return problem(res, 404, "Utilisateur inconnu")
if (!valid) return problem(res, 401, "Mot de passe incorrect")

// APRÈS
if (!user || !valid) return problem(res, 401, "Identifiants invalides")
```

Deux messages différents permettent de savoir quels emails ont un compte.

---

## 6. Le passthrough silencieux

```ts
// AVANT
const schema = z.object({ name: z.string() }).passthrough()
await db.users.update(id, parsed)   // parsed contient aussi role: "admin"

// APRÈS
const schema = z.object({ name: z.string() }).strict()
```

`.passthrough()` transforme n'importe quel `update` en élévation de
privilèges potentielle.
