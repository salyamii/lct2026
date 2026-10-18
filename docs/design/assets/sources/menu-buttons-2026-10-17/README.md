# Main menu buttons

Generated on 2026-10-17 with the built-in `image_gen` tool, one generation per
button. The user requested finished illustrated navigation buttons matching the
game, replacing the floating illustrations and separate plain caption pills.

- `menu_action_gear_v2.png`: explorer backpack and blank wooden label plaque.
- `menu_action_tasks_v2.png`: checklist scroll and blank wooden label plaque.
- `menu_action_goal_v2.png`: compass and blank wooden label plaque.

The previous `menu_gear`, `menu_tasks` and `menu_goal` resources were visually
inspected for palette and material style. These are new generated assets, not
Figma exports. Exact prompts are in [prompts.json](prompts.json). All three
generated results were visually inspected before import. Lettering is not baked
into the illustrations; Compose draws the localized labels over their plaques.

PNG originals preserve the full generated 1254 x 1254 canvases and alpha. Runtime
files are lossless exact WebP (`method=6`), without trimming, resizing, color
changes or background removal. RGBA pixel equality was verified after conversion.
File hashes, alpha bounds and converter version are in
[manifest-entries.json](manifest-entries.json), merged into the asset manifest.

Source files were copied from the Codex generated-images directory and originals
left there unchanged. MainMenuActions draws artwork within explicit bounds using
GameArtwork and keeps whole-button touch targets, goal progress and tour anchors.
