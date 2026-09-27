# Home and expedition collection illustrations

Generated on 2026-09-26 with the built-in `image_gen.imagegen` tool, one independent call per asset. No CLI/API fallback was used. All ten generated outputs were visually inspected and accepted.

The palette/material reference was the existing [Figma explorer hat](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2654-77), already bundled as `prop_fair_explorer_hat`: navy fabric, warm leather and brass. These are newly generated illustrations, not Figma exports. The warm cream parchment canvas and subtle contact shadow are intentional. No background removal, trimming, resizing, compositing or manual retouching was performed.

The approved product requirement is persistent purchased items with illustrated detail pages (FINANCE-UI-D-021). These particular compositions are implementation choices. They introduce no prices, item properties, rewards, equipment slots or gameplay rules.

| Resource | Owned goal item |
| --- | --- |
| `collection_home_entrance` | `campaign-researcher-home-v1:entrance` |
| `collection_home_roof` | `campaign-researcher-home-v1:roof` |
| `collection_home_workbench` | `campaign-researcher-home-v1:workbench` |
| `collection_home_desk` | `campaign-researcher-home-v1:desk` |
| `collection_home_shelves` | `campaign-researcher-home-v1:shelves` |
| `collection_expedition_backpack` | `campaign-great-expedition-v1:backpack` |
| `collection_expedition_compass` | `campaign-great-expedition-v1:compass` |
| `collection_expedition_light` | `campaign-great-expedition-v1:light` |
| `collection_expedition_supplies` | `campaign-great-expedition-v1:supplies` |
| `collection_expedition_transport` | `campaign-great-expedition-v1:transport` |

The compass is a full navigation instrument, not the cosmetic compass/keychain. The backpack likewise does not grant a cosmetic look. The supplies kit depicts a blanket, water flask and packed tent, not food. The ticket uses a carriage/mountain picture with no dates, prices or lettering.

Each `.png` here is the unmodified output copied from Codex's generated-images folder. Its `.prompt.txt` records the exact prompt. [generation-records.json](generation-records.json) preserves the original output location and visual review. [manifestentries.json](manifestentries.json) contains item mappings, sizes, hashes, provenance and conversion evidence for merging into the main asset manifest by the parent task.

`rgba_sha256` follows the shared verifier convention: SHA-256 of width as a four-byte unsigned big-endian integer, height in the same format, then the decoded RGBA bytes. Source and runtime `sha256` values hash the original file bytes.

[convert_assets.py](convert_assets.py) packages the full canvases as WebP with `lossless=True`, `exact=True`, `quality=100`, `method=6`, verifies identical decoded RGBA pixels and leaves existing matching resources intact. Runtime copies live in `app/src/main/res/drawable-nodpi/`. Run from the repository root; no Android build, app launch or tests are needed for this conversion.
