# Package commun de la formation IA générative.
# Expose la couche d'abstraction LLM réutilisée par tous les modules.
from .llm import chat, embed, which_provider, ChatResult  # noqa: F401
