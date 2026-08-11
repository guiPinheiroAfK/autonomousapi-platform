"""Exporta o OpenAPI do geo-api (contrato interno code-first, ADR 0002).

Uso: python scripts/export_openapi.py [saida.json]
Não precisa de banco — usa apenas o app FastAPI em memória.
"""

import json
import os
import sys

# Permite rodar de qualquer cwd: adiciona a raiz do serviço (…/geo-api) ao path.
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.main import app  # noqa: E402  (precisa vir após ajustar o sys.path)


def main() -> None:
    out = sys.argv[1] if len(sys.argv) > 1 else "openapi.json"
    spec = app.openapi()
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(spec, fh, indent=2, sort_keys=True, ensure_ascii=False)
        fh.write("\n")
    print(f"OpenAPI escrito em {out}")


if __name__ == "__main__":
    main()
