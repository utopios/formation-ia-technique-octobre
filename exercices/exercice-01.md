# Exercice 01 — Situer des cas d'usage sur la carte IA / ML / DL / NLP / génératif

> Module : 1 — Fondamentaux des technologies d'IA
> Durée estimée : 25 min
> Difficulté : 2 / 5
> Type : Exercice d'application

## Objectifs pédagogiques

À la fin de cet exercice, vous serez capable de :

- Distinguer IA classique, machine learning, deep learning, NLP et IA générative sur des cas concrets
- Justifier pourquoi un cas d'usage relève (ou non) d'une approche générative plutôt que d'une autre

## Prérequis

- Avoir suivi la partie `Carte mentale IA/ML/DL/NLP` du module 1
- Environnement : aucun (exercice papier/réflexion), un éditeur de texte suffit
- Outils : un document texte pour consigner votre classement

## Contexte

Une ETI industrielle lance plusieurs projets « IA ». La direction technique veut clarifier de QUOI on parle dans chaque cas, car « IA » est employé à tort et à travers. Mal qualifier un projet conduit à choisir la mauvaise technologie (et le mauvais budget). Votre rôle : cartographier chaque cas d'usage sur les bonnes catégories.

Rappel des catégories à utiliser (elles s'emboîtent : IA ⊃ ML ⊃ DL ; NLP et génératif sont des familles d'applications) :
- IA classique non apprenante (règles, systèmes experts, algorithmes déterministes)
- Machine Learning « classique » (apprentissage à partir de données, hors réseaux profonds)
- Deep Learning (réseaux de neurones profonds : CNN, RNN, etc.)
- NLP (traitement du langage, qui peut être ML classique ou DL)
- IA générative / LLM (génération de contenu nouveau : texte, image, code)

## Énoncé

### Partie 1 — Classer les cas d'usage

Pour chacun des cas ci-dessous, indiquez la (ou les) catégorie(s) principale(s) et écrivez UNE phrase de justification. Certains cas peuvent relever de plusieurs catégories imbriquées — précisez laquelle est dominante.

1. Un moteur de calcul de prix qui applique une grille tarifaire fixe selon des règles métier écrites par le service commercial.
2. Un modèle qui prédit le risque de panne d'une machine à partir de capteurs (température, vibration) et d'un historique étiqueté.
3. Un système qui détecte des défauts visuels sur des pièces à partir de photos prises en fin de chaîne.
4. Un assistant qui rédige automatiquement un brouillon de réponse e-mail à un client à partir de la question reçue.
5. Un outil qui classe les tickets du support en « urgent / normal / faible » à partir de leur texte, entraîné sur des milliers de tickets passés.
6. Un correcteur orthographique basé sur un dictionnaire et des règles grammaticales codées en dur.
7. Un système qui transcrit la parole d'une réunion en texte.
8. Un générateur d'images de packaging produit à partir d'une description écrite.
9. Un moteur de recommandation de pièces détachées basé sur la similarité d'historiques d'achat.
10. Un assistant interne qui répond aux questions des employés en s'appuyant sur la base documentaire de l'entreprise.

Résultat attendu : un tableau à trois colonnes (cas, catégorie(s) dominante(s), justification d'une phrase).

### Partie 2 — Les pièges de classification

Reprenez les cas 4, 7 et 10. Pour chacun, répondez en quelques lignes :

- Quelle est la catégorie « englobante » et quelle est la catégorie la plus précise qui décrit le mieux le cas ?
- Le cas 10 combine plusieurs familles : lesquelles, et pourquoi ?

Résultat attendu : un court paragraphe par cas expliquant l'emboîtement des catégories.

## Indices (à consulter si bloqué)

<details>
<summary>Indice 1</summary>

Posez-vous d'abord deux questions binaires : (a) le système APPREND-il à partir de données, ou applique-t-il des règles écrites par un humain ? (b) PRODUIT-il du contenu nouveau (texte, image, code), ou se contente-t-il de classer/prédire/transformer ?

</details>

<details>
<summary>Indice 2</summary>

Un même cas peut cocher plusieurs cases imbriquées. Par exemple, classer du texte est du NLP, qui aujourd'hui s'appuie souvent sur du Deep Learning, qui est un sous-ensemble du ML, lui-même un sous-ensemble de l'IA. Cherchez la catégorie la plus précise tout en sachant nommer les englobantes.

</details>

## Pour aller plus loin (bonus)

Ajoutez deux cas d'usage tirés de VOTRE propre contexte professionnel, classez-les, et identifiez pour chacun si une approche générative apporterait une vraie valeur ou si une approche plus simple (règles, ML classique) suffirait. Pour chaque cas, notez une raison de NE PAS utiliser un LLM.
