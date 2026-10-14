"""Module 6 — LE script clé : fuite inter-services dans un RAG partagé, puis correction (Démo 8).

Scénario : une entreprise met TOUS ses documents (RH + Commercial) dans le MÊME
index RAG. Un utilisateur du service COMMERCIAL pose une question. Sans contrôle
d'accès, la récupération par similarité peut remonter des chunks RH confidentiels
(salaires, dossiers disciplinaires...) -> FUITE DE DONNÉES inter-services.

Ce script DÉMONTRE :
  1) RAG NAÏF (vulnérable) : la récupération ignore les permissions -> fuite.
  2) RAG SÉCURISÉ : chaque chunk porte des métadonnées d'autorisation
     (services autorisés) ; à la récupération, on FILTRE selon le rôle de
     l'utilisateur (principe du besoin d'en connaître) -> plus de fuite.

On VÉRIFIE programmatiquement que des chunks RH sensibles fuient dans le cas
naïf et qu'ils DISPARAISSENT après filtrage.

100 % local (embeddings Ollama + cosinus numpy). Lancer :
    python rag_fuite_et_correction.py
"""

from __future__ import annotations

import sys
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

sys.path.append(str(Path(__file__).resolve().parent.parent))
from common import embed  # noqa: E402

PREF_DOC = "search_document: "
PREF_QUERY = "search_query: "


@dataclass
class Chunk:
    """Un morceau de document + ses MÉTADONNÉES d'autorisation."""

    texte: str
    services_autorises: set[str]   # qui a le droit de VOIR ce chunk
    sensible: bool = False         # marqueur pédagogique pour la vérification
    vecteur: np.ndarray = field(default=None, repr=False)


# Base mélangeant RH (confidentiel) et Commercial (diffusable).
CHUNKS = [
    # --- RH : CONFIDENTIEL, réservé au service RH ---
    Chunk("Salaire annuel brut de Jean Martin (développeur senior) : 72 000 €.",
          {"RH"}, sensible=True),
    Chunk("Dossier disciplinaire : avertissement adressé à Sophie Durand en mars.",
          {"RH"}, sensible=True),
    Chunk("Grille des augmentations RH 2026 : enveloppe globale de 3,2 %.",
          {"RH"}, sensible=True),
    # --- Commercial : diffusable au service Commercial ---
    Chunk("Le tarif catalogue du produit Alpha est de 1 200 € HT.",
          {"Commercial"}),
    Chunk("Remise commerciale maximale autorisée pour un grand compte : 15 %.",
          {"Commercial"}),
    Chunk("Le cycle de vente moyen pour l'offre Entreprise est de 45 jours.",
          {"Commercial"}),
    # --- Document partagé (les deux services) ---
    Chunk("La cantine d'entreprise est ouverte de 12h00 à 14h00.",
          {"RH", "Commercial"}),
]


def construire_index(chunks: list[Chunk]) -> None:
    """Calcule et stocke les embeddings normalisés (in place)."""
    print(f"[index] embeddings de {len(chunks)} chunks (RH + Commercial mélangés)...")
    for c in chunks:
        v = np.array(embed(c.texte, prefix=PREF_DOC), dtype=np.float64)
        c.vecteur = v / np.linalg.norm(v)


def _scores(question: str, chunks: list[Chunk]) -> list[tuple[float, Chunk]]:
    q = np.array(embed(question, prefix=PREF_QUERY), dtype=np.float64)
    q = q / np.linalg.norm(q)
    paires = [(float(c.vecteur @ q), c) for c in chunks]
    paires.sort(key=lambda p: p[0], reverse=True)
    return paires


# --------------------------------------------------------------------------- #
# 1) Récupération NAÏVE — ignore les permissions (VULNÉRABLE)
# --------------------------------------------------------------------------- #
def recuperer_naif(question: str, chunks: list[Chunk], k: int = 3) -> list[Chunk]:
    return [c for _, c in _scores(question, chunks)[:k]]


# --------------------------------------------------------------------------- #
# 2) Récupération SÉCURISÉE — filtre par permissions AVANT le top-k
# --------------------------------------------------------------------------- #
def recuperer_securise(question: str, chunks: list[Chunk],
                       service_utilisateur: str, k: int = 3) -> list[Chunk]:
    # Principe du besoin d'en connaître : on ne considère QUE les chunks
    # autorisés pour le service de l'utilisateur, puis on classe.
    autorises = [c for c in chunks if service_utilisateur in c.services_autorises]
    paires = _scores(question, autorises)
    return [c for _, c in paires[:k]]


def afficher(titre: str, chunks: list[Chunk]) -> int:
    """Affiche les chunks récupérés et renvoie le nb de chunks SENSIBLES fuités."""
    print(f"\n  {titre}")
    fuites = 0
    for c in chunks:
        tag = "  <<< SENSIBLE RH" if c.sensible else ""
        services = "/".join(sorted(c.services_autorises))
        print(f"    [{services:>16}] {c.texte}{tag}")
        if c.sensible:
            fuites += 1
    return fuites


def main() -> None:
    print("=" * 70)
    print("MODULE 6 — Fuite inter-services dans un RAG partagé, et sa correction")
    print("=" * 70)
    construire_index(CHUNKS)

    # L'utilisateur est du COMMERCIAL et pose une question qui, sémantiquement,
    # est proche de contenus RH sensibles (rémunération).
    service = "Commercial"
    question = ("Quelles sont les informations sur les rémunérations et "
                "augmentations de salaire ?")
    print(f"\nUtilisateur du service : {service}")
    print(f"Question posée         : {question!r}")

    # --- CAS 1 : RAG NAÏF (sans contrôle d'accès) ---
    print("\n" + "-" * 70)
    print("CAS 1 — RAG NAÏF (la récupération ignore les permissions)")
    print("-" * 70)
    naif = recuperer_naif(question, CHUNKS, k=3)
    fuites_naif = afficher("Chunks renvoyés au LLM (puis à l'utilisateur Commercial) :",
                           naif)
    print(f"\n  >>> {fuites_naif} chunk(s) RH SENSIBLE(S) ont fuité vers le Commercial !")

    # --- CAS 2 : RAG SÉCURISÉ (filtrage par permissions) ---
    print("\n" + "-" * 70)
    print("CAS 2 — RAG SÉCURISÉ (filtrage par métadonnées d'autorisation)")
    print("-" * 70)
    secur = recuperer_securise(question, CHUNKS, service_utilisateur=service, k=3)
    fuites_secur = afficher("Chunks renvoyés au LLM (après filtrage par rôle) :",
                            secur)
    print(f"\n  >>> {fuites_secur} chunk(s) RH sensible(s) renvoyé(s) au Commercial.")

    # --- VÉRIFICATION automatique de la démo ---
    print("\n" + "=" * 70)
    print("VÉRIFICATION")
    print("=" * 70)
    print(f"  Fuite RH dans le RAG NAÏF     : {fuites_naif} chunk(s) sensible(s)")
    print(f"  Fuite RH dans le RAG SÉCURISÉ : {fuites_secur} chunk(s) sensible(s)")

    assert fuites_naif > 0, ("La démo devait PROVOQUER une fuite dans le cas naïf "
                             "(aucun chunk sensible remonté).")
    assert fuites_secur == 0, ("Le filtrage par permissions devait SUPPRIMER toute "
                               "fuite (un chunk sensible a quand même fuité).")
    print("\n  RÉSULTAT : la fuite EXISTE sans contrôle d'accès, et DISPARAÎT")
    print("  après filtrage par permissions (métadonnées d'autorisation + besoin")
    print("  d'en connaître). C'est la contre-mesure centrale d'un RAG d'entreprise.")
    print("\n  Pour aller plus loin : cloisonner les index par service (un index")
    print("  par périmètre), RBAC à la récupération, audit/traçabilité des accès.")


if __name__ == "__main__":
    main()
