"""Module 1 — Du mot au vecteur : tokenisation + premier embedding local.

Objectifs pédagogiques :
1. Montrer ce qu'est une "tokenisation" (découpage du texte en unités) et pourquoi
   le nombre de tokens compte (coût $ et fenêtre de contexte).
2. Calculer un EMBEDDING en local (Ollama nomic-embed-text) et comparer la
   similarité cosinus de deux phrases proches vs deux phrases éloignées.

Tout tourne EN LOCAL : aucun appel cloud, aucune clé requise.

Lancer :  python tokenisation_embeddings.py
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

# Import de la couche commune (embeddings via Ollama).
sys.path.append(str(Path(__file__).resolve().parent.parent))
from common import embed  # noqa: E402


def tokenisation_naive(texte: str) -> list[str]:
    """Tokenisation 'mots' très simple, pour l'intuition.

    ATTENTION : ce n'est PAS la vraie tokenisation d'un LLM (qui utilise du
    sous-mot via BPE/SentencePiece). C'est juste pour visualiser l'idée de
    découpage. On compte ensuite les vrais tokens via Ollama plus bas.
    """
    return texte.replace(".", " .").replace(",", " ,").split()


def cosine(a: list[float], b: list[float]) -> float:
    """Similarité cosinus entre deux vecteurs (1 = identiques, 0 = orthogonaux)."""
    va, vb = np.array(a), np.array(b)
    return float(np.dot(va, vb) / (np.linalg.norm(va) * np.linalg.norm(vb)))


def compter_tokens_reels(texte: str) -> int | None:
    """Compte les VRAIS tokens via le tokenizer d'Ollama (si dispo).

    On envoie un mini-prompt et on lit `prompt_eval_count` renvoyé par Ollama.
    Renvoie None si Ollama est indisponible.
    """
    try:
        import ollama

        # On demande 1 seul token de sortie pour ne pas générer inutilement.
        r = ollama.chat(
            model="llama3.2:1b",
            messages=[{"role": "user", "content": texte}],
            options={"num_predict": 1},
        )
        return r.get("prompt_eval_count")
    except Exception as exc:  # noqa: BLE001
        print(f"[info] comptage de tokens réels indisponible : {exc!r}")
        return None


def main() -> None:
    print("=" * 70)
    print("MODULE 1 — Tokenisation et embeddings (100 % local)")
    print("=" * 70)

    phrase = "Les modèles de langage découpent le texte en tokens."

    # --- 1) Tokenisation naïve (intuition) ---
    print("\n[1] Tokenisation naïve (par mots) — juste pour l'intuition :")
    toks = tokenisation_naive(phrase)
    print(f"    Texte  : {phrase!r}")
    print(f"    Tokens : {toks}")
    print(f"    -> {len(toks)} 'mots'. Mais un LLM découpe en SOUS-MOTS (BPE).")

    # --- 2) Vrai comptage de tokens (tokenizer du modèle) ---
    n = compter_tokens_reels(phrase)
    if n is not None:
        print(f"\n[2] Vrais tokens (tokenizer llama3.2) pour cette phrase : {n}")
        print("    Pourquoi ça compte : le coût et la FENÊTRE DE CONTEXTE se")
        print("    mesurent en tokens, pas en mots.")
        # Petite extrapolation de coût (chiffres réf. 2026, Claude Opus 4.8).
        prix_entree_par_million = 5.00  # $/1M tokens
        cout = n / 1_000_000 * prix_entree_par_million
        print(f"    Ex. coût en entrée sur Claude Opus 4.8 : ~${cout:.8f} "
              f"(soit {n} tokens).")

    # --- 3) Embeddings + similarité cosinus ---
    print("\n[3] Embeddings locaux (nomic-embed-text) et similarité cosinus :")
    # On choisit des phrases THÉMATIQUEMENT distinctes : la similarité cosinus
    # d'un petit modèle d'embedding capte surtout le sujet/domaine.
    p1 = "J'adore développer des applications en Python."
    p2 = "La programmation logicielle est ma passion."   # même thème que p1
    p3 = "La recette du gâteau au chocolat est délicieuse."  # thème éloigné

    v1, v2, v3 = embed(p1), embed(p2), embed(p3)
    print(f"    Dimension des vecteurs : {len(v1)}")
    sim_proche = cosine(v1, v2)
    sim_loin = cosine(v1, v3)
    print(f"\n    P1 : {p1!r}")
    print(f"    P2 : {p2!r}")
    print(f"    P3 : {p3!r}")
    print(f"\n    similarité(P1, P2) = {sim_proche:.3f}   (sens proche)")
    print(f"    similarité(P1, P3) = {sim_loin:.3f}   (sens éloigné)")

    # Vérification pédagogique : le sens proche doit être plus similaire.
    assert sim_proche > sim_loin, "Échec : la phrase proche devrait scorer plus haut !"
    print("\n    OK : deux phrases de sens proche ont une similarité plus élevée,")
    print("    MÊME sans partager les mêmes mots. C'est la magie des embeddings,")
    print("    et c'est la base du RAG (module 6).")


if __name__ == "__main__":
    main()
