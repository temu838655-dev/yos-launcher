#!/usr/bin/env python3
"""Build a read-only index of upstream CleanYourTV device examples."""
import json
import re
from pathlib import Path

ROOT = Path("upstream/examples")
OUT = Path("upstream/launcher/app/src/main/assets/youclean/model_catalog.json")
records = []

for tv_file in sorted(ROOT.glob("*/tv.json")):
    folder = tv_file.parent
    try:
        data = json.loads(tv_file.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"Invalid device metadata: {tv_file}: {exc}")
    debloat = folder / "debloat.txt"
    header = debloat.read_text(encoding="utf-8", errors="replace")[:12000] if debloat.exists() else ""
    fingerprint = ""
    build = ""
    for line in header.splitlines():
        match = re.search(r"Fingerprint\s+([^\s]+)", line, re.I)
        if match and not fingerprint:
            fingerprint = match.group(1).rstrip(".,")
        match = re.search(r"build\s+([A-Za-z0-9_.-]+)", line, re.I)
        if match and not build:
            build = match.group(1).rstrip(".,")
    packages = []
    for line in header.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
    if debloat.exists():
        for line in debloat.read_text(encoding="utf-8", errors="replace").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            package = line.split("#", 1)[0].strip().split()
            if package and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+", package[0]):
                packages.append(package[0])
    records.append({
        "id": folder.name,
        "tv": data.get("tv", folder.name),
        "brand": data.get("brand", ""),
        "os": data.get("os", ""),
        "notes": data.get("notes", ""),
        "fingerprint": fingerprint,
        "build": build,
        "debloatPackageCount": len(packages),
        "source": f"upstream/examples/{folder.name}/",
    })

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps({
    "schemaVersion": 1,
    "source": "Gathin23/CleanYourTV vendored examples",
    "warning": "Reference only. A matching brand/model does not prove firmware compatibility or safety.",
    "devices": records,
}, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
print(f"Wrote {len(records)} device records to {OUT}")
