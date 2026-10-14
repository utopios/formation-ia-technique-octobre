"""Module 6 — RAG « maison » complet en local (Démo 7).

Pipeline RAG minimal et 100 % local :
  1. INGESTION   : une petite base documentaire.
  2. CHUNKING    : ici chaque doc = 1 chunk (simple, suffisant pour la démo).
  3. INDEXATION  : embeddings via Ollama (nomic-embed-text) -> matrice numpy.
  4. RÉCUPÉRATION : recherche par similarité COSINUS (numpy).
  5. GÉNÉRATION  : on injecte les meilleurs chunks dans le prompt de llama3.2:1b.

Aucune clé API, aucune dépendance lourde. Lancer :  python rag_maison.py
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.append(str(Path(__file__).resolve().parent.parent))
from common import chat, embed  # noqa: E402

# nomic-embed-text est asymétrique : préfixes différents indexation/recherche.
PREF_DOC = "search_document: "
PREF_QUERY = "search_query: "

# Base documentaire d'exemple (politique interne fictive).
DOCUMENTS = [
    "La politique de télétravail autorise jusqu'à 3 jours par semaine à distance.",
    "Les notes de frais doivent être soumises avant le 5 du mois suivant.",
    "Le code de conduite interdit tout harcèlement et garantit l'égalité de traitement.",
    "Les congés payés s'acquièrent à raison de 2,5 jours ouvrables par mois travaillé.",
    "La salle de réunion 'Mont Blanc' se réserve via l'intranet, 30 min minimum.",
]


class RAG:
    """Index RAG minimal en mémoire (embeddings + recherche cosinus)."""

    def __init__(self, documents: list[str]):
        self.documents = documents
        print(f"[index] calcul des embeddings de {len(documents)} documents...")
        vecteurs = [embed(d, prefix=PREF_DOC) for d in documents]
        # Matrice normalisée pour une similarité cosinus = simple produit scalaire.
        m = np.array(vecteurs, dtype=np.float64)
        self.matrice = m / np.linalg.norm(m, axis=1, keepdims=True)

    def rechercher(self, question: str, k: int = 2) -> list[tuple[float, str]]:
        q = np.array(embed(question, prefix=PREF_QUERY), dtype=np.float64)
        q = q / np.linalg.norm(q)
        scores = self.matrice @ q  # cosinus car tout est normalisé
        ordre = np.argsort(scores)[::-1][:k]
        return [(float(scores[i]), self.documents[i]) for i in ordre]

    def repondre(self, question: str, k: int = 2) -> str:
        contexte = self.rechercher(question, k=k)
        print(f"\n[récup] top-{k} chunks pour : {question!r}")
        for score, doc in contexte:
            print(f"   ({score:.3f}) {doc}")
        bloc = "\n".join(f"- {doc}" for _, doc in contexte)
        prompt = (
            "Tu es un assistant interne. Le CONTEXTE ci-dessous provient de "
            "documents internes officiels et fiables : tu peux t'y fier. "
            "Réponds à la question en t'appuyant UNIQUEMENT sur ce contexte, de "
            "façon directe. Si l'information n'y figure pas, dis-le simplement.\n\n"
            f"CONTEXTE :\n{bloc}\n\nQUESTION : {question}\nRÉPONSE :"
        )
        # Cœur local : génération via Ollama.
        return chat(prompt, provider="ollama", temperature=0.0).text.strip()


def main() -> None:
    print("=" * 70)
    print("MODULE 6 — RAG maison local (embeddings Ollama + cosinus numpy)")
    print("=" * 70)
    rag = RAG(DOCUMENTS)

    for q in ["Combien de jours de télétravail sont autorisés ?",
              "Comment réserver une salle de réunion ?"]:
        reponse = rag.repondre(q)
        print(f"\n=> RÉPONSE : {reponse}")
        print("-" * 70)

    print("\nNote pédagogique : la RÉCUPÉRATION (cœur du RAG) est fiable — observez")
    print("les bons chunks remontés avec un score cosinus élevé. La qualité de la")
    print("GÉNÉRATION dépend du modèle : un modèle 1b peut paraphraser ou se tromper")
    print("sur un chiffre ; un modèle plus gros (ou Claude/Mistral) rend une réponse")
    print("plus fidèle au contexte. Le RAG fournit le bon contexte ; le modèle le rédige.")


if __name__ == "__main__":
    main()
