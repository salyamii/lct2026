#!/usr/bin/env python3
"""Validate bundled artwork against its catalog. Requires Python 3 and Pillow."""

import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs/design/assets"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def main():
    manifest = json.loads((DOCS / "manifest.json").read_text())
    assets = manifest["assets"]
    errors = []
    names = [asset["resource"] for asset in assets]
    if len(set(names)) != len(names):
        errors.append("Duplicate catalog resource names")
    by_name = {asset["resource"]: asset for asset in assets}
    for collection in manifest["collections"]:
        entries = [asset for asset in assets if asset.get("figma", {}).get("collection_id") == collection["id"]]
        if len(entries) != collection["expected_assets"]:
            errors.append(f"Incomplete Figma collection {collection['id']}: {len(entries)}/{collection['expected_assets']}")
        nodes = [asset["figma"]["node_id"] for asset in entries]
        if len(nodes) != len(set(nodes)):
            errors.append(f"Repeated Figma source in collection {collection['id']}")
    alias_file = ROOT / "app/src/main/res/values/artwork_aliases.xml"
    aliases = {
        item.attrib["name"]: item.text.removeprefix("@drawable/")
        for item in ET.parse(alias_file).getroot()
        if item.attrib.get("type") == "drawable"
    }
    expected_aliases = {
        asset["resource"]: asset["alias_of"]
        for asset in assets if "alias_of" in asset
    }
    if aliases != expected_aliases:
        errors.append("Drawable aliases disagree with manifest")

    checked = {}
    for asset in assets:
        name = asset["resource"]
        if not re.fullmatch(r"[a-z][a-z0-9_]*", name):
            errors.append(f"Invalid resource name: {name}")
        path = ROOT / asset["path"]
        if not path.is_file():
            errors.append(f"Missing file: {asset['path']}")
            continue
        if path not in checked:
            checked[path] = sha256(path.read_bytes())
        if checked[path] != asset["sha256"] or path.stat().st_size != asset["bytes"]:
            errors.append(f"File checksum/size mismatch: {name}")
        if "alias_of" in asset:
            canonical = by_name.get(asset["alias_of"])
            if not canonical or "alias_of" in canonical:
                errors.append(f"Invalid canonical alias: {name}")
            elif any(asset[key] != canonical[key] for key in ("path", "width", "height", "rgba_sha256")):
                errors.append(f"Alias is not an exact pixel duplicate: {name}")
            if any(p.stem == name for p in (ROOT / "app/src/main/res").glob("drawable*/*")):
                errors.append(f"Alias collides with a drawable file: {name}")
        elif path.stem != name:
            errors.append(f"Filename does not define the expected resource: {name}")

        if asset["format"] == "webp":
            with Image.open(path) as image:
                if image.format != "WEBP":
                    errors.append(f"Not WebP: {name}")
                rgba = image.convert("RGBA")
                data = rgba.width.to_bytes(4, "big") + rgba.height.to_bytes(4, "big") + rgba.tobytes()
                if rgba.size != (asset["width"], asset["height"]) or sha256(data) != asset["rgba_sha256"]:
                    errors.append(f"Canvas/pixel mismatch: {name}")
                if list(rgba.getchannel("A").getbbox() or []) != list(asset["alpha_bounds"] or []):
                    errors.append(f"Transparency bounds mismatch: {name}")
                if "design_canvas" in asset and list(rgba.size) != asset["design_canvas"]:
                    errors.append(f"Design canvas changed: {name}")
        elif asset["format"] == "vector_xml":
            if ET.parse(path).getroot().tag != "vector":
                errors.append(f"Not an Android vector: {name}")

        for key in ("source_path", "license_path"):
            if key in asset and not (ROOT / asset[key]).is_file():
                errors.append(f"Missing {key}: {name}")
        if "source_path" in asset and (ROOT / asset["source_path"]).is_file():
            if sha256((ROOT / asset["source_path"]).read_bytes()) != asset["source_sha256"]:
                errors.append(f"Source checksum mismatch: {name}")

    catalog_paths = {str(ROOT / a["path"]) for a in assets}
    for path in (ROOT / "app/src/main/res/drawable-nodpi").iterdir():
        if path.is_file() and str(path) not in catalog_paths:
            errors.append(f"Uncatalogued raster: {path.name}")
    for path in [ROOT / "AGENTS.md", ROOT / "docs/design/README.md", *DOCS.glob("*.md")]:
        for target in re.findall(r"\]\(([^)]+)\)", path.read_text()):
            if "://" in target or target.startswith("#"):
                continue
            target = target.split("#", 1)[0]
            if target and not (path.parent / target).exists():
                errors.append(f"Broken local link in {path.relative_to(ROOT)}: {target}")
    if manifest["missing"]:
        errors.append(f"{len(manifest['missing'])} Figma exports still unavailable")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Verified {len(assets)} catalog entries, {len(checked)} files, {len(aliases)} aliases; no missing exports.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
