# Inventory collection - tower and map chapters

Nine original illustrations generated on 2026-09-26 with built-in
`image_gen.imagegen`, one call per item. These are user-requested collection
illustrations, not Figma exports. They preserve the adventure's navy, brass,
wood and parchment materials, with a quiet cream background and no UI or prices.

| Android drawable | Original PNG |
| --- | --- |
| `collection_tower_route` | [Route to the tower](collection_tower_route.png) |
| `collection_tower_lantern` | [Expedition lantern](collection_tower_lantern.png) |
| `collection_tower_fastenings` | [Rope and fastenings](collection_tower_fastenings.png) |
| `collection_tower_bridge_kit` | [Crossing preparation kit](collection_tower_bridge_kit.png) |
| `collection_tower_transport` | [Equipment transport voucher](collection_tower_transport.png) |
| `collection_map_table` | [Cartographer's table](collection_map_table.png) |
| `collection_map_copies` | [Archive materials](collection_map_copies.png) |
| `collection_map_atlas` | [Personal atlas](collection_map_atlas.png) |
| `collection_map_field_kit` | [Field cartography kit](collection_map_field_kit.png) |

[Exact prompts and source provenance](generation-specs.json) record the final
generation inputs and original generated file locations. Source PNGs are copied
unchanged into this folder. [Manifest entries](manifestentries.json) provide
source and runtime SHA-256, source/runtime byte sizes, canvas dimensions, alpha
bounds and conversion settings for merging into the main artwork manifest.

Each generated canvas is 1254 × 1254. The full original canvas is retained;
the cream background is intentionally part of each illustration. Runtime files
are `app/src/main/res/drawable-nodpi/<resource>.webp`. Conversion uses Pillow
12.3.0, `lossless=True`, `exact=True`, `quality=100`, `method=6`, without resizing,
cropping, retouching or compositing. PNG/WebP dimensions and decoded RGBA bytes
were verified identical for every item.

The RGBA checksum matches `scripts/verify_assets.py`:
SHA-256 of width as uint32 big endian, height as uint32 big endian, then RGBA
pixel bytes. Render through the shared `GameArtwork` loader with explicit bounds
and `ContentScale.Fit`. These illustrations do not introduce inventory ownership,
prices, rewards or new gameplay rules.
