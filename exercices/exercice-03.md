# Exercice 03 — Transformer un prompt naïf en prompt robuste

> Module : 3 — Panorama des outils et prompt engineering
> Durée estimée : 40 min
> Difficulté : 3 / 5
> Type : Exercice d'application

## Objectifs pédagogiques

À la fin de cet exercice, vous serez capable de :

- Diagnostiquer les faiblesses d'un prompt naïf (ambiguïté, format incontrôlé, absence de garde-fous)
- Réécrire un prompt robuste avec rôle, contraintes, format de sortie imposé et exemples few-shot

## Prérequis

- Avoir suivi les parties `Prompt engineering fondamentaux` et `techniques avancées` du module 3
- Environnement : Ollama (`llama3.2:1b`) pour tester localement, clé Claude/Mistral optionnelle
- Outils : un éditeur, le projet Maven `~/formation/java` (la classe `fr.utopios.formation.commun.Llm` fournit `Llm.chat(prompt, system, temperature)`)

## Contexte

Une équipe support veut automatiser le tri des messages clients entrants. Un développeur a écrit ce prompt, branché sur un LLM, et se plaint que « les résultats sont incohérents, parfois c'est un paragraphe, parfois une liste, et le parsing plante ».

Prompt naïf actuel :
```
Voici un message client. Dis-moi ce qu'il faut en faire.

Message : "{message_client}"
```

Exemples de messages réels à traiter :
- « Bonjour, ma commande #4821 n'est jamais arrivée, c'est inadmissible, je veux un remboursement immédiat. »
- « Est-ce que votre offre Pro inclut le support téléphonique ? »
- « petit bug : le bouton export reste grisé sur Firefox »

Le résultat doit être exploitable par un programme : catégorie, priorité, et une action recommandée, dans un format STABLE et parsable.

## Énoncé

### Partie 1 — Diagnostic du prompt naïf

Listez au moins quatre défauts précis du prompt actuel qui expliquent l'incohérence des sorties. Pour chaque défaut, dites en une phrase ce qu'il provoque concrètement (ex. format variable, hallucination de catégories, ton inapproprié, etc.).

Résultat attendu : une liste de défauts identifiés avec leur conséquence.

### Partie 2 — Réécriture du prompt robuste

Réécrivez le prompt en intégrant explicitement les éléments suivants. Chaque élément doit être visible dans votre prompt final :

- Un ROLE clair donné au modèle (qui il est, sa mission).
- Des CONTRAINTES : liste FERMÉE de catégories autorisées (ex. « facturation », « technique », « commercial », « réclamation »), liste fermée de priorités (« haute », « moyenne », « basse »).
- Un FORMAT DE SORTIE imposé et parsable (JSON avec des clés précises), avec consigne de ne renvoyer QUE ce format.
- Une instruction de repli explicite si le message est inclassable (catégorie « autre »).
- Au moins DEUX exemples few-shot montrant entrée -> sortie attendue.

Testez ensuite votre prompt sur les trois messages du contexte, en local avec Ollama. Le plus simple : une petite classe Java (par exemple `fr.utopios.formation.module03.MonExercice03`) qui met votre prompt dans une constante avec un placeholder `{message}`, le remplace par `String.replace` pour chacun des trois messages, et appelle `Llm.chat(prompt, system, 0.0)` :

```bash
cd ~/formation/java
mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.module03.MonExercice03"
```

Observez si la sortie est stable et parsable sur les trois (vous pouvez la parser avec Jackson : `new ObjectMapper().readTree(...)` et vérifier les clés).

Résultat attendu : le prompt final complet, plus la sortie obtenue sur les trois messages de test.

## Indices (à consulter si bloqué)

<details>
<summary>Indice 1</summary>

Le format incontrôlé est le problème numéro un pour du code en aval. Imposez un schéma JSON explicite et donnez un exemple littéral de la sortie attendue. Demandez au modèle de NE PRODUIRE que le JSON, sans phrase d'introduction.

</details>

<details>
<summary>Indice 2</summary>

Les listes fermées évitent que le modèle invente des catégories. Écrivez noir sur blanc : « catégorie doit être exactement l'une de : ... ; si aucune ne convient, utilise "autre" ». Les exemples few-shot servent à montrer le comportement attendu sur des cas variés (une réclamation, une question commerciale).

</details>

## Pour aller plus loin (bonus)

Transformez votre prompt en utilisant les sorties structurées de l'API (par exemple un schéma JSON imposé côté API plutôt que via le texte du prompt) et comparez la fiabilité du parsing. Ajoutez un champ « justification » d'une phrase et observez si demander une courte justification améliore la qualité du classement (effet chain of thought léger).
