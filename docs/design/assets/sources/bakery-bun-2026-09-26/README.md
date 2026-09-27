# Неиспользуемый черновик булочки — 2026-09-26

**Status: unused draft, excluded from Android runtime.** The user requested the
existing Figma artwork, so this generated alternative was superseded on
2026-09-26 by the [original Figma bun](../fair-items-2026-09-26/README.md).
`R.drawable.prop_bakery_bun` refers to that Figma original, not this draft.

Original isolated game prop generated with the built-in `image_gen.imagegen` tool
for the bakery purchase card. One round golden bun, no UI, lettering, price or
background scene. It has no Figma source node and is not an export from Figma.
The illustration does not establish its price or gameplay effects.

- [Exact generation prompt](prompt.txt)
- [Unmodified generated PNG](prop_bakery_bun.png)
- [Provenance, dimensions, SHA-256 hashes and alpha verification](provenance.json)

Visual style was inspected from the bundled `budget_wants` and
`gear_goal_telescope` props; generation used the text prompt without reference
images. The complete generated 1254 × 1254 canvas is preserved, including
transparent margins and antialiased alpha. The central visible silhouette occupies
approximately `(40, 125)` to `(1215, 1132)` at alpha above 127; faint generated edge
pixels are retained unchanged.

Converted with Pillow 12.3.0 using `lossless=True`, `exact=True`, `quality=100`,
`method=6`. No cropping, resizing, retouching or compositing. Decoded source PNG and
runtime WebP dimensions and RGBA bytes match exactly. Alpha spans 0–255 with
650876 fully transparent pixels. All pixels, including the generated near-opaque
interior, are preserved.

The provenance JSON retains hashes for the former temporary WebP conversion as
an audit record. They do not describe the current runtime resource. The original
PNG and prompt are retained only to document the discarded draft.
