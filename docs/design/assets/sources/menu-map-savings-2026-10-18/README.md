# Parchment map and narrow savings frame - 2026-10-18

Generated with the built-in `image_gen` tool as edits of the existing menu
artwork, following MENU-MAP-UI-D-001 and SAVINGS-UI-D-004. These are generated
game assets, not Figma exports. The exact prompt set is in [prompts.json](prompts.json).

| Runtime resource | Unchanged PNG original | Full canvas | Change |
| --- | --- | --- | --- |
| `menu_map_v2` | [PNG](menu_map_v2.png) | 1536 x 1024 | Curled parchment map, with the wooden backing and physical compass removed |
| `menu_savings_frame_v3` | [PNG](menu_savings_frame_v3.png) | 1024 x 1536 | Slimmer brass housing, transparent empty chamber and two blank caption panels |

The map preserves the lighthouse, islands, mountains, forests, painted compass
rose, route and red X. Its [reference](map-reference.webp) is the original
user-supplied menu resource; no original Figma node was provided.

The savings [original reference](savings-original-reference.webp) is the v2
frame. The first narrower generation is retained as
[savings-narrow-reference.png](savings-narrow-reference.png). A second targeted
edit gives the blank panels more room for native text. Both prompts are retained.
The existing horizontal coin asset is reused; no money, coin count, title or
amount is baked into the new frame.

The common SavingsGauge draws two stable vertical stacks, with up to 48
illustrative coins. This count represents the same fraction of real savings,
not a new currency or capacity limit. Text remains native, including large-font
wrapping. Only caption bands stretch vertically; the full frame canvas is kept.

Safe runtime coordinates refer to the complete 1024 x 1536 frame canvas:

- Empty chamber: x0.32-0.68, y0.15-0.74.
- Native caption width: x0.31-0.69; top y0.058-0.106, bottom y0.848-0.909.
- Two coin centers: 26% and 74% of the chamber width; each complete coin canvas
  spans 60% of that width, including the coin's original transparent margins.

The generated PNGs are copied byte-for-byte from the Codex generated-images
directory. [generation.json](generation.json) records their filenames. Originals
there are unchanged. [import_assets.py](import_assets.py) converts only the
final PNGs to lossless exact WebP, method 6, without cropping, resizing, recoloring
or changing alpha. Decoded dimensions and every RGBA pixel are compared to the PNG.
Hashes, alpha bounds and converter version are recorded in
[manifest-entries.json](manifest-entries.json) and the [main manifest](../../manifest.json).
RGBA hashes include width and height as two big-endian 4-byte integers before
the decoded pixel bytes.
