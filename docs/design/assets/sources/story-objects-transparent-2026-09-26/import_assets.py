"""Import the original transparent Figma fills without trimming or resampling."""
import hashlib
import json
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
MANIFEST = ROOT / "docs/design/assets/manifest.json"
CATEGORY = "Transparent story objects — 2026-09-26"
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
entries = []
for source in json.loads((HERE / "provenance.json").read_text(encoding="utf-8")):
    name = source["resource"]
    original = HERE / f"{name}.png"
    raw = original.read_bytes()
    assert hashlib.sha1(raw).hexdigest() == source["hash"], (name, "Not the active original image fill")
    rgba = Image.open(original).convert("RGBA")
    assert rgba.getchannel("A").getextrema()[0] == 0, (name, "Expected a transparent object")
    pixel_hash = hashlib.sha256(rgba.width.to_bytes(4, "big") + rgba.height.to_bytes(4, "big") + rgba.tobytes()).hexdigest()
    duplicates = [asset["resource"] for asset in manifest["assets"] if asset.get("rgba_sha256") == pixel_hash and asset["resource"] != name]
    assert not duplicates, (name, "Exact existing artwork should be reused", duplicates)
    target = ROOT / "app/src/main/res/drawable-nodpi" / f"{name}.webp"
    rgba.save(target, format="WEBP", lossless=True, exact=True, method=6)
    converted = Image.open(target).convert("RGBA")
    assert converted.size == rgba.size and converted.tobytes() == rgba.tobytes()
    output = target.read_bytes()
    entries.append(dict(resource=name, category=CATEGORY,
        figma=dict(file_key="bAod1cKtTX9Q8omQ067q3q", node_id=source["id"], node_name=source["name"],
            collection_id=None, url="https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=" + source["id"].replace(":", "-")),
        source_method="figma_download_assets_original_image_bytes",
        source_notes="Transparent original active IMAGE fill from object catalog 3238:421; presentation cards excluded. Full canvas and exact RGBA preserved. Authored scenes: " + source["source"],
        figma_image_sha1=source["hash"], source_path=original.relative_to(ROOT).as_posix(),
        source_sha256=hashlib.sha256(raw).hexdigest(), source_bytes=len(raw), source_format="png",
        format="webp", width=rgba.width, height=rgba.height, design_canvas=list(rgba.size), bytes=len(output),
        sha256=hashlib.sha256(output).hexdigest(), rgba_sha256=pixel_hash,
        alpha_bounds=list(rgba.getchannel("A").getbbox() or []), lossless_rgba_verified=True,
        path=target.relative_to(ROOT).as_posix()))
names = {entry["resource"] for entry in entries}
manifest["assets"] = [entry for entry in manifest["assets"] if entry["resource"] not in names] + entries
manifest["collections"] = [collection for collection in manifest["collections"] if collection["name"] != CATEGORY] + [dict(id=None, name=CATEGORY, expected_assets=len(entries))]
MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(HERE / "manifest-entries.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Imported {len(entries)} transparent original fills with exact RGBA verification")
