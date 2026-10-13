"""Couche d'abstraction LLM réutilisée par TOUS les modules de la formation.

Idée directrice (cf. programme) :
- Le CŒUR tourne EN LOCAL via Ollama -> testable hors ligne, sans clé API, sans coût.
- Les API cloud (Claude via `anthropic`, Mistral via `mistralai`) sont OPTIONNELLES :
  si la clé d'environnement est absente, on bascule automatiquement sur Ollama.
- Aucun crash si une clé manque : on retombe toujours sur un provider disponible.

Fonctions exposées :
- chat(prompt, ...)   -> ChatResult (texte + provider + usage tokens si dispo)
- embed(text)         -> liste de floats (embedding) via Ollama (nomic-embed-text)
- which_provider()    -> le provider qui sera choisi en mode "auto"

Sélection du provider en mode "auto" (ordre de préférence) :
    Claude (si ANTHROPIC_API_KEY)  ->  Mistral (si MISTRAL_API_KEY)  ->  Ollama (local).

On peut forcer un provider : chat(prompt, provider="ollama" | "claude" | "mistral").
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Optional

# Modèles par défaut (alignés sur la machine de test : llama3.2:1b + nomic-embed-text).
OLLAMA_CHAT_MODEL = os.environ.get("OLLAMA_CHAT_MODEL", "llama3.2:1b")
OLLAMA_EMBED_MODEL = os.environ.get("OLLAMA_EMBED_MODEL", "nomic-embed-text")
# Modèles cloud (réf. API 2026).
CLAUDE_MODEL = os.environ.get("CLAUDE_MODEL", "claude-opus-4-8")
MISTRAL_MODEL = os.environ.get("MISTRAL_MODEL", "mistral-large-latest")


@dataclass
class ChatResult:
    """Résultat normalisé d'un appel de chat, quel que soit le provider."""

    text: str
    provider: str
    model: str
    # Usage tokens si le provider le fournit (Claude/Mistral). None sinon.
    input_tokens: Optional[int] = None
    output_tokens: Optional[int] = None
    # Métadonnées libres (ex : cache_read_input_tokens pour Claude).
    extra: dict = field(default_factory=dict)

    def __str__(self) -> str:  # affichage lisible en démo
        usage = ""
        if self.input_tokens is not None:
            usage = f" [in={self.input_tokens} out={self.output_tokens}]"
        return f"({self.provider}:{self.model}){usage} {self.text}"


# --------------------------------------------------------------------------- #
# Détection des providers disponibles
# --------------------------------------------------------------------------- #
def _has_anthropic() -> bool:
    return bool(os.environ.get("ANTHROPIC_API_KEY"))


def _has_mistral() -> bool:
    return bool(os.environ.get("MISTRAL_API_KEY"))


def which_provider(provider: str = "auto") -> str:
    """Renvoie le provider effectivement utilisé pour une demande donnée."""
    if provider != "auto":
        return provider
    if _has_anthropic():
        return "claude"
    if _has_mistral():
        return "mistral"
    return "ollama"


# --------------------------------------------------------------------------- #
# Implémentations par provider
# --------------------------------------------------------------------------- #
def _chat_ollama(prompt: str, system: Optional[str], temperature: float) -> ChatResult:
    import ollama

    messages = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": prompt})
    resp = ollama.chat(
        model=OLLAMA_CHAT_MODEL,
        messages=messages,
        options={"temperature": temperature},
    )
    text = resp["message"]["content"]
    # Ollama expose des compteurs de tokens (prompt_eval_count / eval_count).
    in_tok = resp.get("prompt_eval_count")
    out_tok = resp.get("eval_count")
    return ChatResult(
        text=text,
        provider="ollama",
        model=OLLAMA_CHAT_MODEL,
        input_tokens=in_tok,
        output_tokens=out_tok,
    )


def _chat_claude(prompt: str, system: Optional[str], temperature: float,
                 max_tokens: int) -> ChatResult:
    import anthropic

    client = anthropic.Anthropic()  # lit ANTHROPIC_API_KEY
    kwargs = dict(
        model=CLAUDE_MODEL,
        max_tokens=max_tokens,
        messages=[{"role": "user", "content": prompt}],
    )
    if system:
        kwargs["system"] = system
    # Réf. API 2026 : `temperature` est déprécié sur les modèles récents
    # (Opus 4.7/4.8, Fable 5) ; on ne l'envoie donc pas pour Claude.
    resp = client.messages.create(**kwargs)
    # Concaténer les blocs texte (réf. API 2026 : toujours vérifier le type).
    parts = [b.text for b in resp.content if getattr(b, "type", None) == "text"]
    text = "".join(parts)
    usage = resp.usage
    return ChatResult(
        text=text,
        provider="claude",
        model=CLAUDE_MODEL,
        input_tokens=getattr(usage, "input_tokens", None),
        output_tokens=getattr(usage, "output_tokens", None),
        extra={
            "cache_read_input_tokens": getattr(usage, "cache_read_input_tokens", None),
            "stop_reason": resp.stop_reason,
        },
    )


def _chat_mistral(prompt: str, system: Optional[str], temperature: float) -> ChatResult:
    from mistralai import Mistral

    client = Mistral(api_key=os.environ["MISTRAL_API_KEY"])
    messages = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": prompt})
    resp = client.chat.complete(
        model=MISTRAL_MODEL,
        messages=messages,
        temperature=temperature,
    )
    text = resp.choices[0].message.content
    usage = getattr(resp, "usage", None)
    return ChatResult(
        text=text,
        provider="mistral",
        model=MISTRAL_MODEL,
        input_tokens=getattr(usage, "prompt_tokens", None) if usage else None,
        output_tokens=getattr(usage, "completion_tokens", None) if usage else None,
    )


# --------------------------------------------------------------------------- #
# API publique
# --------------------------------------------------------------------------- #
def chat(
    prompt: str,
    provider: str = "auto",
    system: Optional[str] = None,
    temperature: float = 0.2,
    max_tokens: int = 1024,
    fallback: bool = True,
) -> ChatResult:
    """Appelle un LLM et renvoie un ChatResult normalisé.

    provider : "auto" (défaut), "claude", "mistral" ou "ollama".
    fallback : si True (défaut), bascule sur Ollama en cas d'échec d'un provider
               cloud (clé absente, erreur réseau...). On ne plante JAMAIS pour
               une clé manquante.
    """
    chosen = which_provider(provider)
    try:
        if chosen == "claude":
            return _chat_claude(prompt, system, temperature, max_tokens)
        if chosen == "mistral":
            return _chat_mistral(prompt, system, temperature)
        return _chat_ollama(prompt, system, temperature)
    except Exception as exc:  # noqa: BLE001 — on veut un repli robuste en formation
        if fallback and chosen != "ollama":
            print(f"[llm] Provider '{chosen}' indisponible ({exc!r}). "
                  f"Repli sur Ollama local.")
            return _chat_ollama(prompt, system, temperature)
        raise


def embed(text: str, model: Optional[str] = None,
          prefix: Optional[str] = None) -> list[float]:
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


if __name__ == "__main__":
    # Petit auto-test de la couche d'abstraction.
    print("Provider auto choisi :", which_provider())
    r = chat("Réponds en un mot : capitale de la France ?")
    print("Réponse :", r)
    v = embed("bonjour le monde")
    print(f"Embedding : dimension={len(v)}, premiers={[round(x, 3) for x in v[:4]]}")
