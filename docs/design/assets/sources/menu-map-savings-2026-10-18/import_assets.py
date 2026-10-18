"""Import unchanged imagegen PNGs as lossless exact WebP and record provenance."""

import hashlib
import json
from pathlib import Path
import struct

from PIL import Image, __version__ as pillow_version

ROOT = Path(__file__).resolve().parents[5]
SOURCES = Path(__file__).resolve().parent
MANIFEST = ROOT / "docs/design/assets/manifest.json"

ASSETS = (
    ("menu_map_v2", "exec-dc63ccde-2953-40dd-82ce-134a3599e7a7.png", "map-reference.webp"),
    ("menu_savings_frame_v3", None, "savings-narrow-reference.png"),
)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    generation = json.loads((SOURCES / "generation.json").read_text(encoding="utf-8"))
    entries = []
    for resource, generated_name, reference_name in ASSETS:
        source = SOURCES / f"{resource}.png"
        target = ROOT / f"app/src/main/res/drawable-nodpi/{resource}.webp"
        reference = SOURCES / reference_name
        with Image.open(source) as original:
            rgba = original.convert("RGBA")
            rgba.save(target, format="WEBP", lossless=True, exact=True, method=6)
            with Image.open(target) as converted:
                assert converted.size == rgba.size
                assert converted.convert("RGBA").tobytes() == rgba.tobytes(), resource
            entries.append({
                "resource": resource,
                "category": "Generated menu map and narrow savings frame - 2026-10-18",
                "path": target.relative_to(ROOT).as_posix(),
                "format": "webp",
                "width": rgba.width,
                "height": rgba.height,
                "bytes": target.stat().st_size,
                "sha256": sha(target.read_bytes()),
                "rgba_sha256": sha(struct.pack(">II", rgba.width, rgba.height) + rgba.tobytes()),
                "alpha_bounds": rgba.getchannel("A").getbbox(),
                "source_path": source.relative_to(ROOT).as_posix(),
                "source_sha256": sha(source.read_bytes()),
                "provenance": {
                    "type": "imagegen",
                    "date": "2026-10-18",
                    "prompt_path": (SOURCES / "prompts.json").relative_to(ROOT).as_posix(),
                    "prompt_key": resource,
                    "generated_source_filename": generated_name or generation[resource],
                    "reference_source_path": reference.relative_to(ROOT).as_posix(),
                    "reference_source_sha256": sha(reference.read_bytes()),
                },
                "conversion": {
                    "format": "WebP", "lossless": True, "exact": True, "method": 6,
                    "resized": False, "pillow_version": pillow_version,
                },
            })
            print(resource, rgba.size, rgba.getchannel("A").getbbox(), "exact RGBA: OK")
    (SOURCES / "manifest-entries.json").write_text(
        json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    names = {entry["resource"] for entry in entries}
    manifest["assets"] = [entry for entry in manifest["assets"] if entry["resource"] not in names] + entries
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
