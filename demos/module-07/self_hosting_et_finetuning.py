"""Module 7 — Self-hosting (Ollama) : 2 façons d'appeler + génération d'un JSONL de fine-tuning.

Trois parties :

A) INTERROGER OLLAMA via la LIB native `ollama`.
B) INTERROGER LE MÊME MODÈLE via l'API COMPATIBLE OPENAI exposée par Ollama
   (http://localhost:11434/v1) — montre que le self-hosting est "drop-in" pour
   du code écrit pour OpenAI. On utilise `requests` (pas de SDK requis).
C) GÉNÉRER UN FICHIER JSONL de fine-tuning (FORMAT chat uniquement, SANS
   entraînement) et le relire pour vérifier sa validité.

100 % local. Lancer :  python self_hosting_et_finetuning.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import ollama
import requests

MODELE = "llama3.2:1b"
BASE_OPENAI = "http://localhost:11434/v1"


# --------------------------------------------------------------------------- #
# A) Lib native ollama
# --------------------------------------------------------------------------- #
def via_lib_ollama(prompt: str) -> str:
    r = ollama.chat(model=MODELE, messages=[{"role": "user", "content": prompt}],
                    options={"temperature": 0.0})
    return r["message"]["content"].strip()


# --------------------------------------------------------------------------- #
# B) API compatible OpenAI exposée par Ollama
# --------------------------------------------------------------------------- #
def via_api_openai(prompt: str) -> str:
    # Même endpoint que l'API OpenAI : /v1/chat/completions.
    payload = {
        "model": MODELE,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0.0,
    }
    resp = requests.post(f"{BASE_OPENAI}/chat/completions", json=payload, timeout=120)
    resp.raise_for_status()
    data = resp.json()
    return data["choices"][0]["message"]["content"].strip()


# --------------------------------------------------------------------------- #
# C) Génération d'un JSONL de fine-tuning (format chat) — SANS entraînement
# --------------------------------------------------------------------------- #
def generer_jsonl_finetuning(chemin: Path) -> int:
    """Écrit un dataset minimal au format 'messages' (chat) en JSONL.

    Chaque ligne = un exemple {"messages": [system, user, assistant]}.
    C'est le format attendu par la plupart des plateformes de fine-tuning
    (OpenAI, Mistral...) ; on ne fait QUE produire/valider le fichier.
    """
    system = ("Tu es l'assistant interne d'Utopios. Tu réponds en français, "
              "de façon concise et professionnelle.")
    exemples = [
        ("Comment poser un jour de congé ?",
         "Rendez-vous sur l'intranet RH > Congés, sélectionnez les dates et "
         "soumettez ; votre manager valide sous 48 h."),
        ("Quel est le délai pour une note de frais ?",
         "Les notes de frais se soumettent avant le 5 du mois suivant la dépense."),
        ("Puis-je télétravailler 4 jours ?",
         "Le télétravail est limité à 3 jours par semaine selon la politique en vigueur."),
        ("Comment réserver une salle ?",
         "Via l'intranet, rubrique Salles, par créneaux de 30 minutes minimum."),
    ]

    with chemin.open("w", encoding="utf-8") as f:
        for question, reponse in exemples:
            ligne = {
                "messages": [
                    {"role": "system", "content": system},
                    {"role": "user", "content": question},
                    {"role": "assistant", "content": reponse},
                ]
            }
            f.write(json.dumps(ligne, ensure_ascii=False) + "\n")
    return len(exemples)


def valider_jsonl(chemin: Path) -> int:
    """Relit le JSONL et vérifie que chaque ligne est un exemple chat valide."""
    n = 0
    with chemin.open(encoding="utf-8") as f:
        for i, ligne in enumerate(f, start=1):
            obj = json.loads(ligne)  # lève si JSON invalide
            assert "messages" in obj, f"ligne {i} : clé 'messages' manquante"
            roles = [m["role"] for m in obj["messages"]]
            assert roles[-1] == "assistant", f"ligne {i} : doit finir par assistant"
            n += 1
    return n


def main() -> None:
    print("=" * 70)
    print("MODULE 7 — Self-hosting Ollama (2 appels) + JSONL de fine-tuning")
    print("=" * 70)

    prompt = "En une phrase : qu'est-ce que la quantization d'un modèle ?"

    print("\n[A] Via la LIB native `ollama` :")
    print("   ", via_lib_ollama(prompt))

    print("\n[B] Via l'API COMPATIBLE OPENAI locale (/v1/chat/completions) :")
    try:
        print("   ", via_api_openai(prompt))
        print("   -> Même modèle, deux interfaces : le self-hosting Ollama est")
        print("      'drop-in' pour du code conçu pour OpenAI (on change juste l'URL).")
    except Exception as exc:  # noqa: BLE001
        print(f"    [info] endpoint OpenAI local indisponible : {exc!r}")

    print("\n[C] Génération d'un dataset de fine-tuning (format, sans entraînement) :")
    chemin = Path(__file__).resolve().parent / "finetuning_dataset.jsonl"
    n_ecrits = generer_jsonl_finetuning(chemin)
    n_valides = valider_jsonl(chemin)
    print(f"    Fichier écrit  : {chemin.name} ({n_ecrits} exemples)")
    print(f"    Validation     : {n_valides}/{n_ecrits} lignes conformes (format chat)")
    print("    Aperçu 1re ligne :")
    print("     ", chemin.open(encoding="utf-8").readline().strip()[:160], "...")
    print("\n    Rappel arbitrage (réf. module 7) : prompt engineering -> RAG ->")
    print("    fine-tuning, par ordre de coût/complexité croissant. On ne fine-tune")
    print("    que si le besoin est stable et que prompt+RAG ne suffisent pas.")


if __name__ == "__main__":
    main()
