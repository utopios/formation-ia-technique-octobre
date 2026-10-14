"""Module 5 — Function calling / tool use + boucle d'orchestration + streaming.

Deux démos :

A) ASSISTANT À OUTILS (boucle agentique complète) :
   Le modèle décide d'appeler des outils Python (calculatrice, météo mockée),
   on EXÉCUTE l'outil, on RENVOIE le résultat au modèle, et on boucle jusqu'à la
   réponse finale. Cœur local via Ollama (llama3.2:1b supporte le tool calling).
   Si ANTHROPIC_API_KEY est présent, un chemin Claude équivalent est dispo.

B) STREAMING : on reçoit la réponse token par token (local Ollama).

Lancer :  python function_calling_et_streaming.py
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

import ollama

sys.path.append(str(Path(__file__).resolve().parent.parent))

# --------------------------------------------------------------------------- #
# Les OUTILS Python réels (ce que le modèle peut "appeler")
# --------------------------------------------------------------------------- #
def calculatrice(operation: str, a: float, b: float) -> float:
    """Effectue une opération arithmétique simple."""
    a, b = float(a), float(b)
    return {
        "addition": a + b,
        "soustraction": a - b,
        "multiplication": a * b,
        "division": a / b if b != 0 else float("nan"),
    }[operation]


def meteo(ville: str) -> dict:
    """Météo MOCKÉE (pas d'appel réseau) — pour la démo d'orchestration."""
    faux = {
        "paris": {"temp_c": 18, "ciel": "nuageux"},
        "lyon": {"temp_c": 22, "ciel": "ensoleillé"},
        "lille": {"temp_c": 15, "ciel": "pluvieux"},
    }
    return faux.get(ville.lower(), {"temp_c": 20, "ciel": "inconnu"})


# Registre nom -> fonction.
OUTILS = {"calculatrice": calculatrice, "meteo": meteo}

# Schémas des outils au format Ollama/OpenAI (function calling).
SCHEMAS = [
    {
        "type": "function",
        "function": {
            "name": "calculatrice",
            "description": "Calcule une opération arithmétique entre deux nombres.",
            "parameters": {
                "type": "object",
                "properties": {
                    "operation": {
                        "type": "string",
                        "enum": ["addition", "soustraction",
                                 "multiplication", "division"],
                    },
                    "a": {"type": "number"},
                    "b": {"type": "number"},
                },
                "required": ["operation", "a", "b"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "meteo",
            "description": "Donne la météo actuelle d'une ville.",
            "parameters": {
                "type": "object",
                "properties": {"ville": {"type": "string"}},
                "required": ["ville"],
            },
        },
    },
]


# Paramètres attendus par chaque outil (pour nettoyer les args du modèle).
PARAMS_ATTENDUS = {
    "calculatrice": ("operation", "a", "b"),
    "meteo": ("ville",),
}


def _coercer_args(nom: str, args: dict) -> dict:
    """Nettoie/typage des arguments produits par le modèle.

    Les petits modèles renvoient parfois des clés parasites (ex : le schéma
    lui-même) ou des nombres en string. On NE garde que les paramètres attendus
    et on convertit les types -> robustesse de la boucle d'orchestration.
    """
    # Certains petits modèles nichent les vrais arguments dans "properties".
    if "properties" in args and isinstance(args["properties"], dict):
        fusion = dict(args["properties"])
        fusion.update({k: v for k, v in args.items() if k != "properties"})
        args = fusion
    attendus = PARAMS_ATTENDUS.get(nom, tuple(args.keys()))
    propres = {k: args[k] for k in attendus if k in args}
    if nom == "calculatrice":
        propres["a"] = float(propres.get("a", 0))
        propres["b"] = float(propres.get("b", 0))
    if nom == "meteo":
        # parfois la ville est nichée dans un sous-objet : on aplatit.
        v = propres.get("ville", "")
        if isinstance(v, dict):
            v = v.get("ville") or next(iter(v.values()), "")
        propres["ville"] = str(v)
    return propres


def _tool_call_depuis_texte(texte: str):
    """Extrait un appel d'outil émis en TEXTE JSON (repli pour petits modèles).

    Reconnaît un objet du type {"name": "...", "parameters"/"arguments": {...}}.
    Renvoie une liste au même format que `tool_calls` d'Ollama, ou None.
    """
    import re

    m = re.search(r"\{.*\}", texte, re.DOTALL)
    if not m:
        return None
    try:
        obj = json.loads(m.group(0))
    except json.JSONDecodeError:
        return None
    nom = obj.get("name")
    args = obj.get("arguments") or obj.get("parameters") or {}
    if nom in OUTILS and isinstance(args, dict):
        return [{"function": {"name": nom, "arguments": args}}]
    return None


# --------------------------------------------------------------------------- #
# A) Boucle d'orchestration via Ollama (local)
# --------------------------------------------------------------------------- #
def assistant_ollama(question: str, max_tours: int = 5) -> str:
    print(f"\n[Ollama] Question : {question}")
    messages = [
        {"role": "system",
         "content": "Tu es un assistant qui utilise les outils fournis quand "
                    "c'est utile. Après avoir reçu les résultats d'outils, "
                    "donne une réponse finale claire en français."},
        {"role": "user", "content": question},
    ]

    for tour in range(1, max_tours + 1):
        resp = ollama.chat(model="llama3.2:1b", messages=messages, tools=SCHEMAS)
        msg = resp["message"]
        tool_calls = msg.get("tool_calls") or []

        # Repli robustesse : les petits modèles émettent parfois l'appel d'outil
        # sous forme de TEXTE JSON au lieu d'un vrai tool_call. On le récupère.
        if not tool_calls and msg.get("content"):
            recup = _tool_call_depuis_texte(msg["content"])
            if recup:
                tool_calls = recup

        if not tool_calls:
            # Pas d'appel d'outil -> réponse finale.
            print(f"  (tour {tour}) réponse finale du modèle.")
            return msg.get("content", "")

        # On doit ré-empiler le message assistant avant les résultats d'outils.
        messages.append(msg)
        for tc in tool_calls:
            nom = tc["function"]["name"]
            args = tc["function"]["arguments"]
            print(f"  (tour {tour}) le modèle appelle : {nom}({args})")
            try:
                resultat = OUTILS[nom](**_coercer_args(nom, args))
            except Exception as exc:  # noqa: BLE001
                resultat = {"erreur": str(exc)}
            print(f"            -> résultat exécuté : {resultat}")
            messages.append({
                "role": "tool",
                "name": nom,
                "content": json.dumps(resultat, ensure_ascii=False),
            })

    return "(limite de tours atteinte sans réponse finale)"


# --------------------------------------------------------------------------- #
# A bis) Même boucle côté Claude (si clé) — réf. API 2026
# --------------------------------------------------------------------------- #
def assistant_claude(question: str, max_tours: int = 5) -> str | None:
    if not os.environ.get("ANTHROPIC_API_KEY"):
        return None
    try:
        import anthropic
    except ImportError:
        return None

    client = anthropic.Anthropic()
    model = os.environ.get("CLAUDE_MODEL", "claude-opus-4-8")
    # Claude attend un autre format de schéma (input_schema).
    tools = [{
        "name": s["function"]["name"],
        "description": s["function"]["description"],
        "input_schema": s["function"]["parameters"],
    } for s in SCHEMAS]

    print(f"\n[Claude] Question : {question}")
    messages = [{"role": "user", "content": question}]

    for tour in range(1, max_tours + 1):
        resp = client.messages.create(model=model, max_tokens=512,
                                      tools=tools, messages=messages)
        if resp.stop_reason != "tool_use":
            # Réponse finale : concaténer les blocs texte.
            txt = "".join(b.text for b in resp.content
                          if getattr(b, "type", None) == "text")
            print(f"  (tour {tour}) réponse finale (stop_reason={resp.stop_reason}).")
            return txt

        # Empiler la réponse assistant (contient les blocs tool_use).
        messages.append({"role": "assistant", "content": resp.content})
        resultats = []
        for bloc in resp.content:
            if getattr(bloc, "type", None) == "tool_use":
                print(f"  (tour {tour}) le modèle appelle : "
                      f"{bloc.name}({bloc.input})")
                try:
                    out = OUTILS[bloc.name](**_coercer_args(bloc.name, bloc.input))
                except Exception as exc:  # noqa: BLE001
                    out = {"erreur": str(exc)}
                print(f"            -> résultat exécuté : {out}")
                resultats.append({
                    "type": "tool_result",
                    "tool_use_id": bloc.id,
                    "content": json.dumps(out, ensure_ascii=False),
                })
        # Réf. 2026 : TOUS les tool_result dans UN seul message user.
        messages.append({"role": "user", "content": resultats})

    return "(limite de tours atteinte)"


# --------------------------------------------------------------------------- #
# B) Streaming local
# --------------------------------------------------------------------------- #
def demo_streaming() -> None:
    print("\n" + "=" * 70)
    print("B) Streaming (Ollama, token par token)")
    print("=" * 70)
    print("Réponse en flux : ", end="", flush=True)
    flux = ollama.chat(
        model="llama3.2:1b",
        messages=[{"role": "user",
                   "content": "Énumère 3 avantages du streaming, très brièvement."}],
        stream=True,
    )
    morceaux = 0
    for chunk in flux:
        bout = chunk["message"]["content"]
        print(bout, end="", flush=True)
        morceaux += 1
    print(f"\n[streaming terminé : {morceaux} morceaux reçus]")


def main() -> None:
    print("=" * 70)
    print("MODULE 5 — Function calling / tool use + orchestration + streaming")
    print("=" * 70)

    questions = [
        "Combien font 23 multiplié par 19 ?",
        "Utilise l'outil meteo pour me donner la météo de la ville de Lyon.",
    ]

    print("\n### A) Boucle d'orchestration LOCALE (Ollama) ###")
    for q in questions:
        rep = assistant_ollama(q)
        print(f"  REPONSE : {rep.strip()}")

    # Chemin Claude optionnel (si clé) — démontre le même protocole côté cloud.
    rep_claude = assistant_claude("Combien font 23 multiplié par 19 ?")
    if rep_claude is not None:
        print("\n### A bis) Même boucle côté CLAUDE (clé détectée) ###")
        print(f"  REPONSE : {rep_claude.strip()}")
    else:
        print("\n### A bis) Chemin Claude ignoré (pas de ANTHROPIC_API_KEY) ###")

    demo_streaming()


if __name__ == "__main__":
    main()
