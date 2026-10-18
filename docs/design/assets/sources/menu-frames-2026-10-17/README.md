# Menu frames - 2026-10-17

Generated with the built-in imagegen tool for the requested observatory-style HUD.
Exact generation prompts are in [prompts.json](prompts.json).

- [Dark brass nameplate](menu_hud_plaque_v2.png): name, money and energy.
- [Light wood-and-brass plate](menu_action_plaque_v2.png): day number and main actions.

Full PNG canvases and original alpha are preserved. Runtime resources are
`menu_hud_plaque_v2.webp` and `menu_action_plaque_v2.webp` in drawable-nodpi.
Pillow lossless WebP with `exact=True`, `method=6`; decoded RGBA bytes match
the source exactly. Dimensions and hashes are recorded in the asset manifest.

`GamePlaque` loads one bounded painter and displays three horizontal slices.
The 16% side regions retain their aspect ratio; only the empty center changes
width. No text is baked into the images. This is reusable presentation, not
an inventory item or an implemented frame customization feature.
