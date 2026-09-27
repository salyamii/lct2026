"""Lossless packaging of the ten reviewed built-in ImageGen outputs.

Run from the repository root. Preserves the complete generated canvas.
Does not touch the shared asset manifest/catalog.
"""
import hashlib
import json
from pathlib import Path

import PIL
from PIL import Image

ROOT = Path(__file__).resolve().parents[6]
SOURCE = Path(__file__).resolve().parent
RUNTIME = ROOT / "app/src/main/res/drawable-nodpi"
RECORDS = json.loads((SOURCE / "generation-records.json").read_text(encoding="utf-8"))
ITEM_IDS = {
    "collection_home_entrance": "campaign-researcher-home-v1:entrance",
    "collection_home_roof": "campaign-researcher-home-v1:roof",
    "collection_home_workbench": "campaign-researcher-home-v1:workbench",
    "collection_home_desk": "campaign-researcher-home-v1:desk",
    "collection_home_shelves": "campaign-researcher-home-v1:shelves",
    "collection_expedition_backpack": "campaign-great-expedition-v1:backpack",
    "collection_expedition_compass": "campaign-great-expedition-v1:compass",
    "collection_expedition_light": "campaign-great-expedition-v1:light",
    "collection_expedition_supplies": "campaign-great-expedition-v1:supplies",
    "collection_expedition_transport": "campaign-great-expedition-v1:transport",
}


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


assert len(RECORDS) == 10
entries = []
for record in RECORDS:
    name = record["name"]
    png_path = SOURCE / (name + ".png")
    webp_path = RUNTIME / (name + ".webp")
    prompt_path = SOURCE / (name + ".prompt.txt")
    prompt_path.write_text(record["prompt"] + "\n", encoding="utf-8")
    with Image.open(png_path) as image:
        rgba = image.convert("RGBA")
    original_pixels = rgba.tobytes()
    # Match scripts/verify_assets.py: canvas dimensions are part of the hash.
    canvas_pixels = rgba.width.to_bytes(4, "big") + rgba.height.to_bytes(4, "big") + original_pixels
    if not webp_path.exists():
        rgba.save(webp_path, format="WEBP", lossless=True, exact=True, quality=100, method=6)
    with Image.open(webp_path) as image:
        decoded = image.convert("RGBA")
    assert decoded.size == rgba.size, name
    assert decoded.tobytes() == original_pixels, name
    assert sha256(png_path) == sha256(Path(record["generated_source"])), name
    entries.append({
        "resource": name,
        "category": "Inventory collection",
        "path": webp_path.relative_to(ROOT).as_posix(),
        "format": "webp",
        "width": rgba.width,
        "height": rgba.height,
        "bytes": webp_path.stat().st_size,
        "sha256": sha256(webp_path),
        "rgba_sha256": hashlib.sha256(canvas_pixels).hexdigest(),
        "alpha_bounds": list(rgba.getchannel("A").getbbox()),
        "design_canvas": list(rgba.size),
        "source_path": png_path.relative_to(ROOT).as_posix(),
        "source_sha256": sha256(png_path),
        "source_bytes": png_path.stat().st_size,
        "source_format": "png",
        "source_method": "generated_image",
        "lossless_rgba_verified": True,
        "item_id": ITEM_IDS[name],
        "provenance": {
            "type": "generated-artwork",
            "date": "2026-09-26",
            "tool": "image_gen.imagegen",
            "mode": "built-in",
            "prompt_path": prompt_path.relative_to(ROOT).as_posix(),
            "generation_records": (SOURCE / "generation-records.json").relative_to(ROOT).as_posix(),
            "style_reference": {
                "resource": "prop_fair_explorer_hat",
                "figma_url": "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2654-77",
                "role": "Visual palette/material reference only; generated independently, not a Figma export or edit."
            },
            "details": "One built-in call per asset, no external API/CLI. Intentional opaque warm cream parchment canvas; no transparency extraction, crop, resize or manual retouching."
        },
        "visual_review": record["review"],
        "conversion": {
            "format": "WebP",
            "lossless": True,
            "exact": True,
            "quality": 100,
            "method": 6,
            "resized": False,
            "cropped": False,
            "pillow_version": PIL.__version__,
            "details": "Complete generated canvas retained. Decoded PNG and WebP RGBA pixels match exactly."
        }
    })
    print(f"{name}: {rgba.width}x{rgba.height}, {webp_path.stat().st_size} bytes, exact RGBA verified")

(SOURCE / "manifestentries.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Packaged {len(entries)} assets; runtime bytes {sum(entry['bytes'] for entry in entries)}")
