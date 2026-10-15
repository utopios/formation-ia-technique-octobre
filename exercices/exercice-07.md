# Exercice 07 — Choisir entre prompt engineering, RAG et fine-tuning

> Module : 7 — Fine-tuning, déploiement self-hosted et écosystème
> Durée estimée : 40 min
> Difficulté : 3 / 5
> Type : Exercice d'application

## Objectifs pédagogiques

À la fin de cet exercice, vous serez capable de :

- Arbitrer entre prompt engineering, RAG et fine-tuning selon la nature d'un besoin
- Justifier votre choix en termes de fraîcheur des données, de coût, de maintenance et de risque

## Prérequis

- Avoir suivi la partie `Fine-tuning : quand fine-tuner vs RAG vs prompt engineering` du module 7
- Environnement : aucun (exercice de décision), un document texte pour structurer l'analyse
- Outils : un document, l'arbre de décision vu en cours

## Contexte

Vous êtes consulté pour cinq projets différents. Pour chacun, l'équipe hésite entre trois approches (qui ne s'excluent pas toujours) :

- Prompt engineering : ajuster les instructions, le format, les exemples — sans toucher au modèle ni à une base externe.
- RAG : brancher le modèle sur une base documentaire récupérée à la volée.
- Fine-tuning : ré-entraîner (totalement ou via LoRA/QLoRA) le modèle sur des données spécifiques.

Rappel des critères d'arbitrage : besoin de CONNAISSANCES à jour ou propriétaires (penser RAG), besoin d'un STYLE/FORMAT/COMPORTEMENT spécifique et stable (penser fine-tuning), besoin simple ou ponctuel (penser prompt engineering), contraintes de coût, de fraîcheur des données, de volume de données d'entraînement disponibles, de maintenance.

Les cinq projets :

1. Un assistant qui répond aux questions des employés sur des procédures internes qui changent toutes les semaines.
2. Un générateur d'e-mails commerciaux qui doit toujours adopter le ton et la charte de marque très particuliers de l'entreprise, sur la base de milliers d'e-mails passés validés.
3. Un outil de reformulation de texte « plus formel / plus concis » pour un usage occasionnel par quelques personnes.
4. Un assistant juridique qui doit citer les clauses exactes de contrats stockés dans un coffre documentaire, avec sources vérifiables.
5. Un modèle spécialisé qui doit comprendre un jargon médical très spécifique et produire des comptes-rendus dans un format réglementé strict, avec un gros corpus annoté disponible et une exigence de fonctionnement 100 % en local (données patients).

## Énoncé

### Partie 1 — Choisir et justifier pour chaque projet

Pour chacun des cinq projets, indiquez l'approche principale recommandée (une ou une combinaison) et justifiez en vous appuyant sur les critères. Adressez explicitement, pour chaque cas : la fraîcheur/nature des connaissances, le besoin de style vs de savoir, le volume de données disponible, et la contrainte de confidentialité/local quand elle s'applique.

Résultat attendu : pour chaque projet, l'approche retenue et une justification structurée par critères.

### Partie 2 — Les combinaisons et les pièges

Répondez aux questions transverses suivantes :

- Pour quels projets une COMBINAISON (ex. fine-tuning + RAG, ou prompt engineering + RAG) est-elle pertinente, et pourquoi ?
- Pourquoi le fine-tuning est-il un mauvais choix pour le projet 1 (procédures changeant chaque semaine) ?
- Pour le projet 5, quelles contraintes de déploiement (self-hosting, quantization, matériel) découlent de l'exigence « 100 % local » ?
- Citez un piège fréquent : un cas où une équipe choisit le fine-tuning alors que le RAG ou le prompt engineering suffirait, et le coût caché de ce mauvais choix.

Résultat attendu : vos réponses argumentées aux quatre questions, reliées aux projets concrets.

## Indices (à consulter si bloqué)

<details>
<summary>Indice 1</summary>

Règle simple à garder en tête : le RAG sert à apporter du SAVOIR (surtout s'il change ou doit être sourcé), le fine-tuning sert à façonner un COMPORTEMENT/STYLE stable, le prompt engineering est le premier réflexe peu coûteux. Si la donnée change souvent ou doit être citée, le fine-tuning est presque toujours le mauvais outil.

</details>

<details>
<summary>Indice 2</summary>

Le fine-tuning a un coût caché : préparer un jeu de données de qualité, ré-entraîner à chaque évolution, et maintenir le modèle. Demandez-vous toujours « est-ce que prompt engineering ou RAG ne donnerait pas 90 % du résultat pour 10 % de l'effort ? ». Pour le projet 5, l'exigence local + corpus annoté + format réglementé est un des rares cas qui justifie vraiment le fine-tuning.

</details>

## Pour aller plus loin (bonus)

Pour le projet que vous jugez le plus complexe, esquissez un plan de mise en œuvre en trois étapes (de la version la moins coûteuse à la plus aboutie) qui commence par prompt engineering, puis ajoute du RAG, puis n'envisage le fine-tuning qu'en dernier recours si les deux premières n'atteignent pas la cible. Précisez comment vous mesureriez à chaque étape si l'objectif est atteint avant de passer à la suivante.
