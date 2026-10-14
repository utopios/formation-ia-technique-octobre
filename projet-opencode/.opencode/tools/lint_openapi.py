#!/usr/bin/env python3
"""
Linter OpenAPI maison — volontairement minimaliste.

Le but pédagogique est de montrer qu'un tool OpenCode peut invoquer
n'importe quel langage : la définition TypeScript n'est qu'une enveloppe.
"""
import re
import sys

PUBLIC_PATHS = {"/health", "/auth/login", "/auth/refresh"}


def load(path):
    try:
        import yaml
        with open(path, encoding="utf-8") as f:
            return yaml.safe_load(f)
    except ImportError:
        print("AVERTISSEMENT: pyyaml absent, lint superficiel uniquement")
        return None
    except FileNotFoundError:
        print(f"ERREUR: fichier introuvable: {path}")
        sys.exit(1)


def lint(spec, strict):
    problems = []
    for path, ops in (spec.get("paths") or {}).items():
        for method, op in (ops or {}).items():
            if method not in {"get", "post", "put", "patch", "delete"}:
                continue
            label = f"{method.upper()} {path}"

            op_id = op.get("operationId")
            if not op_id:
                problems.append(f"ERREUR  {label}: operationId manquant")
            elif not re.fullmatch(r"[a-z][a-zA-Z0-9]*", op_id):
                problems.append(f"ERREUR  {label}: operationId '{op_id}' non camelCase")

            responses = op.get("responses") or {}
            if not any(str(c).startswith("4") for c in responses):
                problems.append(f"ERREUR  {label}: aucune réponse 4xx déclarée")

            if path not in PUBLIC_PATHS and "security" not in op:
                problems.append(f"ERREUR  {label}: pas de bloc security sur une route non publique")

            body = op.get("requestBody")
            if body:
                content = body.get("content") or {}
                for mime, media in content.items():
                    if "example" not in media and "examples" not in media:
                        lvl = "ERREUR " if strict else "AVERTIR"
                        problems.append(f"{lvl} {label}: pas d'exemple pour {mime}")
    return problems


def main():
    if len(sys.argv) < 2:
        print("usage: lint_openapi.py <spec.yaml> [--strict]")
        sys.exit(1)
    strict = "--strict" in sys.argv
    spec = load(sys.argv[1])
    if spec is None:
        return
    problems = lint(spec, strict)
    if not problems:
        print("Aucune violation détectée.")
    else:
        for p in problems:
            print(p)
        print(f"\n{len(problems)} violation(s).")


if __name__ == "__main__":
    main()
