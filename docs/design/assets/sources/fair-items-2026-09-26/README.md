# Предметы ярмарки из Figma — 2026-09-26

Five original image fills imported from the existing event cards in
[«Питомец Дизайн»](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/).
The `figma_download_assets` tool returned one `rawImages` PNG for each image
node. The SHA-1 of every downloaded original exactly matches the image hash
recorded in Figma. Rendered cards and exported node thumbnails are not runtime
assets.

| Android resource | Original PNG | Figma image node |
| --- | --- | --- |
| `prop_bakery_bun` | [Булочка](prop_bakery_bun.png) | [2654:29](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2654-29) |
| `prop_fair_explorer_hat` | [Кепка](prop_fair_explorer_hat.png) | [2654:77](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2654-77) |
| `prop_fair_ring_toss` | [Кольцеброс](prop_fair_ring_toss.png) | [2654:125](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2654-125) |
| `prop_fair_compass_keychain` | [Брелок-компас](prop_fair_compass_keychain.png) | [2654:173](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2654-173) |
| `prop_fair_toy_boat` | [Кораблик](prop_fair_toy_boat.png) | [2654:221](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2654-221) |

All five images retain their original 1254 × 1254 canvas and real alpha,
including transparent margins and faint original edge pixels. No existing
resource has identical full RGBA bytes and dimensions, so each has its own
drawable. The bun replaces the [unused generated draft](../bakery-bun-2026-09-26/README.md).

Converted with Pillow 12.3.0 using `lossless=True`, `exact=True`, `quality=100`,
`method=6`, without resizing, cropping, retouching or compositing. Source PNG and
runtime WebP decode to identical RGBA pixels and dimensions. See
[provenance.json](provenance.json) for source SHA-1/SHA-256, runtime SHA-256,
RGBA hashes, alpha bounds, byte sizes and conversion details.

The artwork does not define prices, ownership or effects. Use the current game
catalog for behavior. Render with the shared `GameArtwork` loader, explicit
bounds and `ContentScale.Fit`, without a tint. No temporary download URL is used
at runtime or needed to identify the originals.
