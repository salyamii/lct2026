# Menu HUD objects - 2026-10-18

Three original illustrations generated with the built-in imagegen tool using transparent backgrounds. The PNG sources are retained at full generated canvas size. Runtime files use exact lossless WebP, method 6, without cropping, resizing, repainting or background removal. Decoded PNG and WebP RGBA bytes are identical.

The settings wheel is a standalone warm brass mechanism with a wooden hub, not a blue circular badge. Wallet and name panels have separate tactile materials, wide blank light centers, and no baked text, coin icons or arrows. Native app content is overlaid by Compose.

## Canvas and composition

| Resource | Canvas | Safe content area |
| --- | --- | --- |
| `menu_settings_v3` | 1254 x 1254 | Decorative standalone settings icon |
| `menu_wallet_panel_v3` | 2172 x 724 (3:1) | About x15-85%, y20-80% |
| `menu_name_plaque_v3` | 2172 x 724 (3:1) | About x15-85%, y25-75%; left curl stays outside text |

The safe area describes native overlay placement, not image trimming. Keep full transparent canvas.
Pixel hash convention: SHA-256 of big-endian 4-byte width + big-endian 4-byte height + RGBA bytes, matching the common asset manifest.

## Exact prompts

### menu_settings_v3

Source: [menu_settings_v3.png](menu_settings_v3.png).

```text
Use case: stylized-concept. Production artwork for a cozy fantasy adventure game's HUD, children age 7-11. Warm polished soft 3D storybook game illustration, chunky rounded forms, caramel leather, pale parchment and aged warm brass, clear silhouette and large readable details. Isolated object on REAL fully transparent alpha background, with no scenery, no ground plane, no outside shadow or glow. No text, letters, digits, logos, watermark or drawn UI symbols. This must look like a crafted game object, not a generic flat UI rectangle. One standalone antique brass adjustment mechanism: a large golden-brass gear wheel with broad rounded teeth and a small walnut wooden central turning hub, accompanied by one small neighboring brass cog partly behind its lower right edge. The main gear is clearly dominant and simple, front view with slight sculptural depth, readable as settings at 48dp. No circular blue background, no badge or panel or enclosing border, no wrench, no extra objects. Square canvas. Whole complete mechanism centered, about 90 percent of canvas, modest transparent margins, not cut off.
```

### menu_wallet_panel_v3

Source: [menu_wallet_panel_v3.png](menu_wallet_panel_v3.png).

```text
Use case: stylized-concept. Production artwork for a cozy fantasy adventure game's HUD, children age 7-11. Warm polished soft 3D storybook game illustration, chunky rounded forms, caramel leather, pale parchment and aged warm brass, clear silhouette and large readable details. Isolated object on REAL fully transparent alpha background, with no scenery, no ground plane, no outside shadow or glow. No text, letters, digits, logos, watermark or drawn UI symbols. This must look like a crafted game object, not a generic flat UI rectangle. One wide horizontal wallet-shaped HUD panel, exact 3:1 landscape canvas and object proportions. Front view. A soft plump rounded purse made of PALE warm beige leather, simple double curved material rim and fine stitched seam near outer edge, small warm brass clasp details ONLY at far ends. An expressive tactile three-dimensional wallet silhouette, gently rounded left and right end, NOT a flat frame. The entire central area from 15 to 85 percent width and 20 to 80 percent height is EMPTY, evenly light pale cream leather, calm low-contrast texture suitable for native dark text and an icon overlay. NO drawn coins, circles, medallions, symbols, numbers, seams through center, straps or clasps across center. Preserve extremely spacious blank center. Object fills about 94 percent of this wide canvas, thin fully transparent margin all round. No heavy or dark blue outline.
```

### menu_name_plaque_v3

Source: [menu_name_plaque_v3.png](menu_name_plaque_v3.png).

```text
Use case: stylized-concept. Production artwork for a cozy fantasy adventure game's HUD, children age 7-11. Warm polished soft 3D storybook game illustration, chunky rounded forms, caramel leather, pale parchment and aged warm brass, clear silhouette and large readable details. Isolated object on REAL fully transparent alpha background, with no scenery, no ground plane, no outside shadow or glow. No text, letters, digits, logos, watermark or drawn UI symbols. This must look like a crafted game object, not a generic flat UI rectangle. One wide horizontal name plaque for a pet character, exact 3:1 landscape canvas and object proportions. Front view. A little handcrafted light honey wood name board with a PALE CREAM PARCHMENT face, an expressive softly rolled parchment curl at the far left end and a tiny folded corner at far right, two simple small warm brass pins near far ends. Rounded tactile edges, playful gentle shape, fully distinct from a leather wallet and from a flat generic blue rectangle. The entire central area from 15 to 85 percent width and 20 to 80 percent height is EMPTY light cream parchment with very subtle texture, ready for native dark pet name overlay. NO letters, symbols, engraved decorations, stars, coins, text, notches through the center, blue fill or enclosing dark border. Object fills about 94 percent of this wide canvas, thin fully transparent margin all round. Keep the whole silhouette uncut.
```
