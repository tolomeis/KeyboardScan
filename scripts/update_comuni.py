#!/usr/bin/env python3
"""
Builds app/src/main/assets/comuni.tsv from the ISTAT list of Italian municipalities.

Usage:
    curl -L -o comuni.csv https://www.istat.it/storage/codici-unita-amministrative/Elenco-comuni-italiani.csv
    python3 -I scripts/update_comuni.py comuni.csv app/src/main/assets/comuni.tsv

Output columns: codice catastale, codice ISTAT, denominazione in italiano, sigla provincia.
Source: ISTAT, licensed CC BY 4.0 (https://www.istat.it/classificazione/codici-dei-comuni-delle-province-e-delle-regioni/).
"""
import csv
import sys


def find_column(header, prefix):
    for index, name in enumerate(header):
        if " ".join(name.split()).lower().startswith(prefix.lower()):
            return index
    raise SystemExit(f"column not found: {prefix}")


def main(source, target):
    with open(source, encoding="cp1252", newline="") as f:
        rows = list(csv.reader(f, delimiter=";"))

    header = rows[0]
    catastale = find_column(header, "Codice Catastale del comune")
    istat = find_column(header, "Codice Comune formato alfanumerico")
    name = find_column(header, "Denominazione in italiano")
    sigla = find_column(header, "Sigla automobilistica")

    lines = []
    for row in rows[1:]:
        if len(row) <= max(catastale, istat, name, sigla) or not row[catastale].strip():
            continue
        lines.append("\t".join(value.strip() for value in (row[catastale], row[istat], row[name], row[sigla])))

    with open(target, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(sorted(lines)) + "\n")
    print(f"{len(lines)} comuni written to {target}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
