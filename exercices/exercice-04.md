# Exercice 04 — Risques technique, juridique, sécurité d'un cas d'usage et contre-mesures

> Module : 4 — Intégration des LLM, limites et risques
> Durée estimée : 40 min
> Difficulté : 3 / 5
> Type : Exercice d'application

## Objectifs pédagogiques

À la fin de cet exercice, vous serez capable de :

- Analyser un cas d'usage LLM selon trois axes de risque : technique, juridique, sécurité
- Associer à chaque risque une contre-mesure concrète et réaliste

## Prérequis

- Avoir suivi les parties `Limites des LLM`, `Risques juridiques` et `Risques de sécurité` du module 4
- Environnement : aucun (exercice d'analyse), un document texte pour structurer votre réponse
- Outils : un document ou tableur

## Contexte

Une entreprise déploie un assistant clientèle public, accessible sur son site web. L'assistant :
- répond aux questions des clients sur les produits et le suivi de commande ;
- a accès, via des outils, à la base de commandes (numéro, statut, adresse de livraison) ;
- peut, dans certains cas, déclencher un remboursement automatique jusqu'à 50 € ;
- s'appuie sur une base documentaire interne (FAQ, conditions générales) pour répondre ;
- est utilisé par des dizaines de milliers de visiteurs anonymes par jour.

Ce cas cumule les trois familles de risque vues en cours. Votre mission : les cartographier et proposer des parades, avant la mise en production.

## Énoncé

### Partie 1 — Identifier les risques sur les trois axes

Pour chaque axe, identifiez au moins trois risques SPÉCIFIQUES à ce cas d'usage (pas des généralités). Reliez-les à des éléments concrets de l'énoncé.

- Axe TECHNIQUE : pensez hallucination, réponses fausses sur le statut d'une commande, sur les conditions générales, troncature, dérive de qualité.
- Axe JURIDIQUE : pensez données personnelles (adresses, commandes) et RGPD, engagement de l'entreprise sur une réponse erronée, propriété intellectuelle des contenus générés, traçabilité.
- Axe SÉCURITÉ : pensez injection de prompt directe et indirecte, exfiltration de données d'autres clients, abus de l'outil de remboursement, accès non autorisé à la base de commandes.

Résultat attendu : un tableau à deux colonnes par axe (risque identifié, élément de l'énoncé concerné).

### Partie 2 — Proposer les contre-mesures

Pour chaque risque listé en Partie 1, proposez UNE contre-mesure concrète. Couvrez au moins :

- une mesure de validation / sortie contrainte côté technique ;
- une mesure de protection des données personnelles côté juridique ;
- une mesure de human-in-the-loop ou de plafonnement côté sécurité (notamment sur le remboursement automatique) ;
- une mesure de défense contre l'injection (directe et indirecte) ;
- une mesure d'observabilité / journalisation pour l'audit.

Terminez par une recommandation : parmi tous ces risques, lequel jugez-vous bloquant pour une mise en production, et pourquoi ?

Résultat attendu : la liste des contre-mesures en regard des risques, plus votre recommandation argumentée sur le risque bloquant.

## Indices (à consulter si bloqué)

<details>
<summary>Indice 1</summary>

Le pouvoir de déclencher un remboursement est le point le plus sensible : un LLM qui agit sur le monde réel via un outil est une cible d'injection. Demandez-vous : qu'est-ce qui empêche un client malveillant de formuler un message conçu pour faire rembourser à tort, ou pour faire révéler la commande d'un autre client ?

</details>

<details>
<summary>Indice 2</summary>

Côté données personnelles, l'assistant manipule des adresses et des numéros de commande : interrogez la minimisation (n'exposer que le strict nécessaire), le cloisonnement (un client ne doit jamais voir les données d'un autre) et la traçabilité des accès. Côté technique, rappelez-vous qu'une réponse fausse sur les conditions générales peut engager l'entreprise.

</details>

## Pour aller plus loin (bonus)

Rédigez une « politique d'usage » d'une page pour cet assistant : ce qu'il a le droit de faire seul, ce qui exige une validation humaine, ce qu'il ne doit jamais faire. Ajoutez ensuite un scénario d'attaque par injection indirecte (par exemple via un champ de commande qui contiendrait du texte piégé) et décrivez la chaîne de défense qui doit l'arrêter.
