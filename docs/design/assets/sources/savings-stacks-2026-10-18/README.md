# Savings instrument and horizontal coin - 2026-10-18

Generated with the built-in `image_gen` tool and visually inspected before import.
These are game illustrations derived from existing resources, not new Figma
exports. Exact instructions are retained in [prompts.json](prompts.json).

| Resource | Original | Canvas | Purpose |
| --- | --- | --- | --- |
| `menu_savings_frame_v2` | [PNG](menu_savings_frame_v2.png) | 1024 x 1536, 2:3 | One connected brass instrument with an empty upper label, transparent central opening and empty lower amount panel |
| `menu_savings_coin_flat` | [PNG](menu_savings_coin_flat.png) | 1774 x 887, 2:1 | One horizontal green coin with a gold star and visible gold edge, for repeated vertical stacks |

The panels contain no lettering or numbers. Captions and exact amounts are
native UI text. The frame contains no coins. The coin is one complete object,
not a pre-rendered stack or a stretched upright coin.

## Coordinate guide

Coordinates below refer to the complete source canvases, not trimmed artwork.
Right and bottom bounds are exclusive. Keep the full canvases when rendering.

| Frame region | Pixel bounds | Normalized bounds |
| --- | --- | --- |
| Safe transparent opening | x163-860, y230-1136 | x0.16-0.84, y0.15-0.74 |
| Upper blank text area | x153-870, y89-162 | x0.15-0.85, y0.058-0.106 |
| Lower blank text area | x153-870, y1302-1396 | x0.15-0.85, y0.848-0.909 |

The opening's alpha is 0-1 throughout the stated safe region: no visible glass
sheet or haze covers the native coin stacks. RGB behind transparent pixels is
preserved, so an alpha-ignoring preview can look filled. Text zones exclude the
decorative edges; their heights must be considered when sizing native labels.

The coin's full nonzero-alpha bounds are `(0, 30, 1754, 839)`, including very
faint alpha-1 pixels. Its visible body at alpha >16 is `(176, 80, 1595, 827)`:
x0.0992-0.8991, y0.0902-0.9324 of the complete canvas. This is about 80% of
the canvas width; preserve that margin when deriving a stack's spacing.
The thin golden front edge allows repeated coins to overlap vertically while
retaining a distinct layered silhouette. No pixel trimming or background
removal was applied during import.

## Generation and provenance

The first frame generation edited the
[previous frame source](../savings-gauge-2026-10-17/menu_savings_gauge_frame.png).
Its output is retained as [frame-reference.png](frame-reference.png), with the
initial prompt under `menu_savings_frame_v2_initial`. A second targeted edit
widened the blank panels and cleared the window; its prompt is under
`menu_savings_frame_v2`. The coin was generated in one edit of the existing
[`menu_coin` resource](../../../../../app/src/main/res/drawable-nodpi/menu_coin.webp).

Generated-image originals remain unchanged:

- `exec-c676b173-f2b1-4312-95e3-efff53b91cdc.png`: first frame and edit reference.
- `exec-ee6ac036-d7ae-46e2-a664-1f1a1230ca84.png`: final frame.
- `exec-dff65d51-986f-471a-9738-baa53fe03f32.png`: horizontal coin.

Final PNGs were copied without changes and converted to lossless exact WebP,
method 6, without resizing, cropping, recoloring or altering transparency.
Decoded dimensions and all RGBA bytes were verified identical after conversion.
File and reference hashes, alpha bounds and converter version are recorded in
[manifest-entries.json](manifest-entries.json) and the main
[asset manifest](../../manifest.json). The RGBA hash includes big-endian
4-byte width and height before the decoded pixel bytes.
