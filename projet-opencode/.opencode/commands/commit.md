---
description: Rédige le message de commit des changements en cours
agent: archi
---

Regarde `git diff --staged` (ou `git diff` s'il n'y a rien de stagé),
charge la skill `commit-maison`, et propose le message de commit
correspondant.

Si les changements couvrent plusieurs sujets logiques, propose un
découpage en plusieurs commits avec les commandes `git add` associées.

Ne commite pas toi-même : affiche le message et attends ma validation.
