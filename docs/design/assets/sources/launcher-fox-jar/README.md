# Launcher icon: fox with a jar of coins

Approved by the user on 2026-09-28, APP-ICON-D-001; background export corrected
under APP-ICON-D-002 on the same date.

- [Original PNG](source.png): unchanged built-in imagegen output, 1254 × 1254.
- [Adaptive master](adaptive-source.png): 1254 × 1254 imagegen outpainting of
  the original, with natural garden scenery covering the entire overscan.
  [Intermediate](outpaint-step1.png) and [exact edit prompts](outpainting-prompts.md)
  preserve its provenance. This adaptive master is the actual export input.
- [Preview](preview.png): simulated circular and rounded-square masks alongside
  the full adaptive layer, including 48 px thumbnails; not a device screenshot.
- Identity reference: `ryzhik_mvp_idle_copper`, also aliased by the teen base
  character from [Figma 56:65](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=56-65).
- Original generation filename: `exec-ee34b53c-d1e8-4a16-9ec5-16b2aab63cd6.png`.
- [Monochrome SVG](monochrome.svg): locally authored geometric jar with a paw coin.
  This is the implementation's themed-icon symbol, not a separately approved
  character redesign. Android tints its alpha mask with the user's theme.

## Android packaging

The outpainted raster remains one scene, without character segmentation.
The adaptive icon uses an opaque composite background and a
transparent foreground, so it supports system shapes without moving the fox
independently from the garden. It does not provide separate character parallax.

The full adaptive master maps directly to the 108 dp layer. The nominal 72 dp
viewport is the central two-thirds, leaving naturally painted garden scenery in
the surrounding overscan. The composition keeps the ears, face, paws and jar
inside the circular preview. No edge-pixel extrusion, mirrored padding or
intermediate rescaling is used. The color adaptive layer has no baked mask or drop shadow;
Android applies its own shape. Themed icons use a dedicated vector rather than
tinting an opaque photograph into a solid square.

Legacy Android 7 resources use that same 72 dp viewport, resized to 48 dp at
mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi, with separate rounded-square and circular masks.
The existing manifest's `android:icon` and `android:roundIcon` already reference
the correct names. No application ID or saved-game data changes.

Run `python3 scripts/import_launcher_icon.py` with Pillow 12.3.0 to regenerate
resources and their checksums, then `python3 scripts/verify_assets.py` and
`./gradlew :app:assembleDebug :app:lintDebug` to verify them.
Each raster output is resampled directly from the master once, using Lanczos
and lossless exact WebP. Density resources are intended for their Android display
size; the full-resolution adaptive master is available above for inspection.
Checksums and
decoded-pixel hashes are recorded in the main asset manifest's `launcher_icons`.

Reference: [Android adaptive icon requirements](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive),
read through `android docs` on 2026-09-28.

## Original generation prompt

Create ONE finished square mobile game app icon concept in a warm friendly premium 3D casual-game style, for a children's financial adventure game ages 7–11. The attached image is the exact character identity reference for Ryzhik, the ONLY character. Preserve his recognizable copper-orange fox design: tall triangular ears with dark brown rims and pale inner fur, amber-brown eyes, slender tapered ivory muzzle, pointed cream cheek tufts, small dark nose, slender young fox proportions, ivory chest, dark brown paws. Adapt rendering into attractive polished casual mobile game key art like Gardenscapes / Royal Kingdom, without copying any of their characters. MAIN CONCEPT: smiling Ryzhik lovingly holds a transparent glass savings jar full of golden coins with BOTH paws, close to his chest. Fox's face is primary focus, the jar second. The jar is a simple appealing wide rounded clear glass jar with a short neck and clean rolled glass lip, no labels, no writing, no elaborate lid, no handles, not a piggy bank. Make the jar unmistakably transparent: show several large readable chunky gold coins stacked and loosely overlapping inside, a few embossed with a simple paw symbol, with soft clear reflections along the glass edges. Coins should be visible THROUGH the jar; do not turn it into an opaque golden pot. Paws naturally grip the sides and support the base, anatomically clear and plausible. Warm gentle proud closed-mouth smile, kind engaging eye contact, modest head tilt, soft expressive eyebrows, friendly without a greedy or exaggerated expression. Bust composition, no awkward lower body. Warm honey sunlight, soft peach highlights on cheeks, rich natural orange fur and creamy ivory, clean sculpted volume with restrained readable fur detail. Background is a softly blurred emerald/sage magical garden with warm sunlit bokeh and a barely suggested medieval cottage silhouette, simplified so the subject stands out at icon size. Cozy, wholesome and inviting; no dramatic dark atmosphere, no neon colors, no overexposed glow. Strong clear silhouette, balanced color contrast. Keep entire ear tips and the whole jar visible with generous breathing room; fit the important features inside an imaginary centered circular crop. Full-bleed opaque square art, no visible circle, no rounded frame, no border, no text, no logo, no mockup, no additional characters or decorative floating coins. One image only.
