"""Module 1 — Du mot au vecteur : tokenisation + premier embedding local.

Script AUTONOME : tout ce qu'il utilise est défini ici, aucun import du
dossier common/ n'est nécessaire.

Objectifs pédagogiques :
1. Montrer ce qu'est une "tokenisation" (découpage du texte en unités) et pourquoi
   le nombre de tokens compte (coût $ et fenêtre de contexte).
2. Calculer un EMBEDDING en local (Ollama nomic-embed-text) et comparer la
   similarité cosinus de deux phrases proches vs deux phrases éloignées.

Tout tourne EN LOCAL : aucun appel cloud, aucune clé requise.

Lancer :  python tokenisation_embeddings.py
"""

from __future__ import annotations

import os

import numpy as np

# Modèles par défaut (alignés sur l'environnement de formation).
OLLAMA_CHAT_MODEL = os.environ.get("OLLAMA_CHAT_MODEL", "llama3.2:1b")
OLLAMA_EMBED_MODEL = os.environ.get("OLLAMA_EMBED_MODEL", "nomic-embed-text")


def embed(text: str, model: str | None = None,
          prefix: str | None = None) -> list[float]:
    """Calcule l'embedding d'un texte via Ollama (nomic-embed-text par défaut).

    On garde les embeddings EN LOCAL : reproductible, gratuit, sans fuite réseau.

    prefix : nomic-embed-text est ASYMÉTRIQUE — il attend un préfixe de tâche.
             Pour le RAG, on utilise "search_document: " à l'indexation et
             "search_query: " à la recherche. Optionnel (None = pas de préfixe).
    """
    import ollama

    contenu = f"{prefix}{text}" if prefix else text
    resp = ollama.embeddings(model=model or OLLAMA_EMBED_MODEL, prompt=contenu)
    return resp["embedding"]


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
    """Compte les VRAIS tokens du TEXTE SEUL via le tokenizer d'Ollama.

    PIÈGE ÉVITÉ ICI : en mode chat, `prompt_eval_count` inclut le GABARIT de
    conversation du modèle (balises spéciales, en-tête injecté par le template),
    soit ~24 tokens de plancher fixe — « Bonjour. » afficherait 27 tokens !
    On utilise donc /api/generate en mode raw=True : aucun template appliqué,
    le compte correspond au texte lui-même.
    Renvoie None si Ollama est indisponible.
    """
    try:
        import ollama

        # raw=True : pas de gabarit de chat ; num_predict=1 : ne génère presque rien.
        r = ollama.generate(
            model=OLLAMA_CHAT_MODEL,
            prompt=texte,
            raw=True,
            options={"num_predict": 1},
        )
        return r.get("prompt_eval_count")
    except Exception as exc:  # noqa: BLE001
        print(f"[info] comptage de tokens réels indisponible : {exc!r}")
        return None


def compter_tokens_avec_gabarit(texte: str) -> int | None:
    """Compte les tokens tels que FACTURÉS lors d'un vrai appel de chat.

    Même texte, mais envoyé via le mode chat : le template du modèle ajoute
    ses tokens (~24 pour llama3.2). C'est CE nombre que vous verrez dans
    l'usage d'un appel réel — le gabarit aussi se paye.
    """
    try:
        import ollama

        r = ollama.chat(
            model=OLLAMA_CHAT_MODEL,
            messages=[{"role": "user", "content": texte}],
            options={"num_predict": 1},
        )
        return r.get("prompt_eval_count")
    except Exception:  # noqa: BLE001
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

        # Comparaison de quelques textes : tokens du texte vs tokens facturés en chat.
        print("\n    Comparaison texte seul vs appel de chat (gabarit inclus) :")
        print(f"    {'texte seul':>10} | {'via chat':>8} | texte")
        for t in ("Bonjour.", "anticonstitutionnellement", phrase):
            brut = compter_tokens_reels(t)
            avec = compter_tokens_avec_gabarit(t)
            if brut is not None and avec is not None:
                print(f"    {brut:>10} | {avec:>8} | {t[:45]!r}")
        print("    -> l'écart constant (~24 tokens) est le GABARIT de chat du")
        print("       modèle : lors d'un vrai appel, il est facturé lui aussi.")

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
