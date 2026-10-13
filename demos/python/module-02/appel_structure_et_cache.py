"""Module 2 — Premier appel LLM structuré, usage tokens, fenêtre de contexte & prompt caching.

Deux parties :
A) Appel LLM (auto : Claude/Mistral si clé, sinon Ollama local) et lecture de
   l'usage tokens (entrée / sortie).
B) Démo "fenêtre de contexte" + "prompt caching" :
   - Si ANTHROPIC_API_KEY est présent : on fait DEUX appels Claude avec le même
     long préfixe marqué `cache_control` et on observe `cache_read_input_tokens`
     (écriture au 1er appel, lecture ~0.1x au 2e).
   - Sinon : explication mockée chiffrée (réf. prix 2026) pour comprendre le gain.

Lancer :  python appel_structure_et_cache.py
"""

from __future__ import annotations

import os
import sys
import time
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parent.parent))
from common import chat, which_provider  # noqa: E402


def partie_A_appel_structure() -> None:
    print("=" * 70)
    print("A) Appel LLM structuré + usage tokens")
    print("=" * 70)
    provider = which_provider()
    print(f"Provider auto : {provider}")

    res = chat(
        prompt="Donne UNE phrase expliquant ce qu'est la fenêtre de contexte d'un LLM.",
        system="Tu es un formateur concis. Réponds en français, en une seule phrase.",
    )
    print(f"\nRéponse  : {res.text.strip()}")
    print(f"Provider : {res.provider} ({res.model})")
    if res.input_tokens is not None:
        print(f"Usage    : entrée={res.input_tokens} tokens, "
              f"sortie={res.output_tokens} tokens")
    else:
        print("Usage    : (provider sans compteur de tokens)")


def _cache_claude_reel() -> bool:
    """Démo RÉELLE de prompt caching sur Claude. Renvoie True si exécutée."""
    if not os.environ.get("ANTHROPIC_API_KEY"):
        return False
    try:
        import anthropic
    except ImportError:
        return False

    client = anthropic.Anthropic()
    model = os.environ.get("CLAUDE_MODEL", "claude-opus-4-8")

    # Le préfixe DOIT être assez gros pour être cacheable.
    # Réf. 2026 : minimum cacheable Opus 4.8 = 4096 tokens. On génère un gros
    # document stable (le "contexte" partagé entre les deux requêtes).
    gros_contexte = ("RÈGLEMENT INTÉRIEUR (extrait répété pour atteindre la taille "
                     "minimale cacheable). ") * 1200

    def appel(question: str):
        return client.messages.create(
            model=model,
            max_tokens=64,
            system=[
                {
                    "type": "text",
                    "text": gros_contexte,
                    # Marqueur de cache sur le préfixe stable :
                    "cache_control": {"type": "ephemeral"},
                }
            ],
            messages=[{"role": "user", "content": question}],
        )

    print("\nDémo RÉELLE de prompt caching sur Claude :")
    t0 = time.time()
    r1 = appel("Réponds juste 'OK 1'.")
    d1 = time.time() - t0
    u1 = r1.usage
    print(f"  1er appel  : {d1:.2f}s | écriture cache="
          f"{getattr(u1, 'cache_creation_input_tokens', 0)} | "
          f"lecture cache={getattr(u1, 'cache_read_input_tokens', 0)} | "
          f"entrée non cachée={u1.input_tokens}")

    t0 = time.time()
    r2 = appel("Réponds juste 'OK 2'.")
    d2 = time.time() - t0
    u2 = r2.usage
    print(f"  2e appel   : {d2:.2f}s | écriture cache="
          f"{getattr(u2, 'cache_creation_input_tokens', 0)} | "
          f"lecture cache={getattr(u2, 'cache_read_input_tokens', 0)} | "
          f"entrée non cachée={u2.input_tokens}")
    print("  -> Au 2e appel, les tokens du préfixe sont LUS depuis le cache "
          "(~0.1x le prix) au lieu d'être refacturés plein tarif.")
    return True


def _cache_explication_mockee() -> None:
    """Explication chiffrée du prompt caching (sans clé)."""
    print("\nDémo MOCKÉE de prompt caching (pas de ANTHROPIC_API_KEY) :")
    # Réf. prix 2026 : Claude Opus 4.8 entrée = 5.00 $/1M. Cache : écriture ~1.25x,
    # lecture ~0.1x.
    prefixe_tokens = 50_000   # ex : un gros document de contexte stable
    requetes = 20             # 20 questions sur le même document
    prix_in = 5.00 / 1_000_000

    sans_cache = prefixe_tokens * requetes * prix_in
    # Avec cache : 1 écriture (1.25x) + (requetes-1) lectures (0.1x).
    avec_cache = (prefixe_tokens * 1.25 * prix_in
                  + prefixe_tokens * (requetes - 1) * 0.1 * prix_in)

    print(f"  Hypothèse : préfixe stable de {prefixe_tokens:,} tokens, "
          f"{requetes} requêtes.")
    print(f"  Coût SANS cache : ${sans_cache:.4f}")
    print(f"  Coût AVEC cache : ${avec_cache:.4f}")
    economie = (1 - avec_cache / sans_cache) * 100
    print(f"  -> Économie ~{economie:.0f}% sur la partie préfixe.")
    print("  (Pose ANTHROPIC_API_KEY pour voir la mesure réelle.)")


def partie_B_contexte_et_cache() -> None:
    print("\n" + "=" * 70)
    print("B) Fenêtre de contexte & prompt caching")
    print("=" * 70)
    print("Fenêtre de contexte = nb max de tokens (entrée + sortie) que le modèle")
    print("traite d'un coup. Réf. 2026 : Claude jusqu'à 1M tokens de contexte.")
    print("Prompt caching = réutiliser un PRÉFIXE stable pour payer moins/aller")
    print("plus vite sur les requêtes suivantes.")

    if not _cache_claude_reel():
        _cache_explication_mockee()


if __name__ == "__main__":
    partie_A_appel_structure()
    partie_B_contexte_et_cache()
