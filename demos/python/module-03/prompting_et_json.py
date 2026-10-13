"""Module 3 — Techniques de prompting + sortie JSON structurée validée (pydantic).

Partie 1 — Montée en gamme du prompting sur llama3.2:1b (local) :
  - zero-shot      : on demande directement.
  - few-shot       : on donne des exemples résolus.
  - chain-of-thought (CoT) : on demande de raisonner étape par étape.

Partie 2 — Sortie JSON structurée validée par pydantic :
  on force le modèle à produire du JSON, on l'extrait, on le VALIDE avec un
  schéma pydantic. Si le modèle se trompe, on le voit (et on peut re-demander).

Tout tourne en local (Ollama). Lancer :  python prompting_et_json.py
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from pydantic import BaseModel, Field, ValidationError, field_validator

sys.path.append(str(Path(__file__).resolve().parent.parent))
from common import chat  # noqa: E402

# On force Ollama pour que la démo soit reproductible en local (et gratuite),
# même si une clé cloud est présente dans l'environnement.
PROVIDER = "ollama"


# --------------------------------------------------------------------------- #
# Partie 1 — techniques de prompting
# --------------------------------------------------------------------------- #
def demo_prompting() -> None:
    print("=" * 70)
    print("PARTIE 1 — zero-shot vs few-shot vs chain-of-thought (llama3.2:1b)")
    print("=" * 70)

    question = ("Marie a 3 boîtes de 4 stylos. Elle en donne 5. "
                "Combien lui reste-t-il de stylos ?")

    # 1) Zero-shot
    print("\n[zero-shot]")
    r = chat(question, provider=PROVIDER)
    print("  ", r.text.strip().replace("\n", "\n   "))

    # 2) Few-shot : on montre le format de réponse attendu via des exemples.
    print("\n[few-shot]")
    few = (
        "Q: J'ai 2 sacs de 3 pommes, j'en mange 1. Combien reste-t-il ? "
        "R: 2*3=6, 6-1=5. Réponse: 5\n"
        "Q: 4 paquets de 2 gâteaux, j'en donne 3. Combien reste-t-il ? "
        "R: 4*2=8, 8-3=5. Réponse: 5\n"
        f"Q: {question} R:"
    )
    r = chat(few, provider=PROVIDER)
    print("  ", r.text.strip().replace("\n", "\n   "))

    # 3) Chain-of-thought : on demande explicitement le raisonnement.
    print("\n[chain-of-thought]")
    cot = question + " Raisonne étape par étape, puis donne la réponse finale."
    r = chat(cot, provider=PROVIDER)
    print("  ", r.text.strip().replace("\n", "\n   "))

    print("\n(Note : sur un petit modèle 1b, few-shot et CoT fiabilisent le format")
    print(" et la démarche ; le gain est encore plus net sur les gros modèles.)")


# --------------------------------------------------------------------------- #
# Partie 2 — sortie JSON validée par pydantic
# --------------------------------------------------------------------------- #
class FicheProduit(BaseModel):
    """Schéma cible : la réponse du LLM DOIT s'y conformer."""

    nom: str = Field(..., min_length=1)
    categorie: str
    prix_eur: float = Field(..., ge=0)
    en_stock: bool

    @field_validator("categorie")
    @classmethod
    def categorie_connue(cls, v: str) -> str:
        autorisees = {"informatique", "maison", "sport", "alimentaire", "autre"}
        if v.lower() not in autorisees:
            # On normalise plutôt que de planter : robustesse.
            return "autre"
        return v.lower()


def extraire_json(texte: str) -> dict:
    """Extrait le premier objet JSON d'une réponse LLM (souvent bavarde)."""
    # On cherche le premier bloc {...} équilibré le plus simple.
    match = re.search(r"\{.*\}", texte, re.DOTALL)
    if not match:
        raise ValueError("Aucun JSON trouvé dans la réponse du modèle.")
    return json.loads(match.group(0))


def demo_json_valide() -> None:
    print("\n" + "=" * 70)
    print("PARTIE 2 — sortie JSON structurée validée par pydantic")
    print("=" * 70)

    prompt = (
        "Extrais une fiche produit de ce texte et réponds UNIQUEMENT en JSON "
        "avec les clés exactes : nom (string), categorie (string parmi "
        "informatique/maison/sport/alimentaire/autre), prix_eur (number), "
        "en_stock (boolean). Pas de texte autour.\n\n"
        "Texte : « Le clavier mécanique AZERTY Pro est dispo à 89,90 € et "
        "actuellement en stock. C'est un accessoire informatique. »"
    )

    # On laisse jusqu'à 3 tentatives : le petit modèle peut rater le format.
    derniere_erreur = None
    for tentative in range(1, 4):
        r = chat(prompt, provider=PROVIDER, temperature=0.0)
        print(f"\n[tentative {tentative}] réponse brute du modèle :")
        print("  ", r.text.strip().replace("\n", "\n   ")[:400])
        try:
            data = extraire_json(r.text)
            fiche = FicheProduit.model_validate(data)
            print("\n  VALIDATION pydantic : OK")
            print("  Objet typé :", fiche.model_dump())
            return
        except (ValueError, ValidationError, json.JSONDecodeError) as exc:
            derniere_erreur = exc
            print(f"  Validation échouée : {exc}")
            # On renforce la consigne pour la tentative suivante.
            prompt += "\n\nRappel : réponds STRICTEMENT en JSON valide, rien d'autre."

    print("\n  Échec après 3 tentatives. Dernière erreur :", derniere_erreur)
    print("  -> En prod : on rejette, on logge, ou on bascule sur un modèle plus fort")
    print("     (ou sorties structurées natives côté API : voir réf. Claude/Mistral).")


if __name__ == "__main__":
    demo_prompting()
    demo_json_valide()
