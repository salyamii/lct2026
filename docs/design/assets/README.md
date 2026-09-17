# Android artwork guide

Use the [asset catalog](catalog.md) to find artwork and the
[manifest](manifest.json) for its source, Android resource, dimensions, hashes,
conversion details and import status. The source is
[«Питомец Дизайн» in Figma](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/).
The [main menu notes](../README.md) describe the existing menu composition.

The catalog records each bundled resource and its source. If a future refresh
cannot retrieve an export, list it separately under `missing` in the manifest;
that entry does not define an Android resource. Resume from its recorded node
and clear the entry after verification. Do not substitute a different pose,
age or character without an explicit design decision.

## Imported collections

| Collection | Figma | Artwork entries |
| --- | --- | --- |
| MVP poses, colors and standalone equipment | [56:2](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=56-2) | 20 |

The catalog also covers the existing eight menu bitmaps, two vector icons and
two fonts. Further collections will be added with their matching provenance.

## Resource locations

Paths below are relative to the repository root. Android assigns `R` IDs to
resources in `res`; files in `assets` are accessed differently. Follow the
[Android resource directory rules](https://developer.android.com/guide/topics/resources/providing-resources#ResourceTypes).

| Content | Location | Project convention |
| --- | --- | --- |
| Characters, equipment, locations, NPCs and raster effects | `app/src/main/res/drawable-nodpi/*.webp` | Lossless WebP, preserving the exported canvas and alpha |
| Simple vector icons | `app/src/main/res/drawable/*.xml` | Android vector drawables; keep `menu_chevron` and `menu_star` |
| Aliases for identical artwork | `app/src/main/res/values/*.xml` | One stored drawable, additional semantic resource names |
| Font files | `app/src/main/res/font/` | Bundled fonts, referenced through `R.font` |
| Font license notices | `app/src/main/assets/licenses/` | Retain the complete notices with the app |
| Launcher icons | `app/src/main/res/mipmap-*` | Launcher resources only |
| SVG originals and provenance | `docs/design/` and this directory | Design sources and documentation, outside runtime resources |

`drawable-nodpi` prevents Android from rescaling these bitmaps according to device
density. It is a deliberate choice for artwork whose bounds are controlled by
Compose; it does not define the displayed size. Set layout bounds in dp and
choose `ContentScale` explicitly. Use density-qualified drawables only when
supplying a deliberate set of density variants. See
[Android's density guidance](https://developer.android.com/training/multiscreen/screendensities).

## Format and canvas preservation

Use lossless WebP with exact preservation of transparent pixels for raster
imports. Decode the source export and result to RGBA and verify identical
dimensions and pixel bytes before replacing the source resource. The app's
`minSdk = 24` supports lossless WebP with alpha; Android supports these features
from API 18. See [WebP conversion](https://developer.android.com/studio/write/convert-webp).

Preserve the complete 512 × 512 canvas of character exports, including the
transparent area, shared origin and position of the character or overlay inside
it. These coordinates keep poses, faces and equipment aligned. Preserve each
asset's recorded canvas when it differs: existing `menu_ryzhik` is 1024 × 1024,
and source location artwork can be 941 × 1672. Do not trim transparent borders,
recenter individual layers, stretch the art or export Figma labels and preview
card backgrounds as part of the sprite. A different-size export must record its
scale and coordinate relationship in the manifest.

The `body_accessory_*` sprites include the entire character wearing the item;
they are alternatives to the base body, not accessory-only overlays. The
`gear_*` assets are standalone item illustrations. Face patches and masks retain
the common canvas and clipping from Figma; use the matching character collection
and explicit shared bounds when composing them.

Some source artwork needs review before compositing. For example,
`ryzhik_adult_face_patch_curious` contains an opaque white area inside the face
clip in Figma; it is not a clean transparent overlay. Inspect the actual asset
before layering it in UI, and correct the design source before refreshing it
instead of automatically removing pixels or trusting the layer name.
`ryzhik_cub_mask_iris_color` contains a full character with transparent eye
holes, whereas the teen iris mask contains white ellipses. Those different
source representations are preserved; do not assume the masks are interchangeable.

Keep simple path icons as vector XML and retain the original SVG under
`docs/design`. Verify path geometry, viewport, colors, alpha and strokes after
conversion. SVG filters such as blur are unsupported by the Android SVG
importer; render these effects to transparent raster images at the documented
scale. `menu_ground_shadow` follows that rule. See
[SVG import support](https://developer.android.com/studio/write/vector-asset-studio#svg).

WebP compression reduces packaged bytes, not the number of decoded pixels.
Choose image dimensions and loading behavior for the actual display size, and
measure memory if many illustrations are shown together. The current full
canvases are intentional; any derived smaller variants must preserve alignment
and have their own provenance. See
[bitmap optimization](https://developer.android.com/develop/ui/compose/graphics/images/optimization).

## Names, reuse and Compose

Use stable lowercase ASCII names with underscores. Organize by meaning through
prefixes, since Android resource folders do not support arbitrary nested groups:
`ryzhik_cub_...`, `ryzhik_teen_...`, `ryzhik_adult_...`, `ryzhik_senior_...`,
`gear_...`, `location_...` and `npc_...`. Add the documented pose, color or item
variant when needed. Preserve existing `menu_*` resource names used by the UI.

Search the catalog before importing or drawing a replacement. If two entries
have identical full RGBA pixels and dimensions, retain one canonical drawable
and declare the other name as a drawable resource alias in `res/values`.
Record the alias target and both source nodes in the manifest. Similar-looking
artwork, different canvas placement and different resolutions are not exact
duplicates. Keep resource names unique across raster and XML definitions.

Load bundled artwork with `painterResource(R.drawable.<resource_name>)` inside
`Image`; use explicit bounds and the intended scaling mode. Display multicolor
art without an icon tint. Provide a localized content description when it
conveys information, or `null` when decorative. Do not load Figma export URLs at
runtime. Compose supports bitmap and vector drawables through the same API;
see [resources in Compose](https://developer.android.com/develop/ui/compose/resources#vector-assets).

Artwork names describe design variants. They do not establish gameplay states,
age transitions, unlock rules, prices or rewards. Follow the
[app state-machine specification](../app-state-machine.md) for domain behavior.

## Adding or updating artwork

1. Search the catalog and manifest for an existing resource or a pending export.
2. Record the Figma file and node ID with a stable node link. Export the intended
   artwork or full aligned layer, preserving its documented canvas. Temporary
   download URLs alone are insufficient provenance.
3. Convert using the rules above. Record source and output SHA-256 hashes,
   dimensions, byte sizes, export scale and conversion settings. Check for exact
   duplicates before adding a runtime file.
4. Add the resource or alias in its Android directory and update the manifest
   and human catalog together. Keep unavailable entries separate from imports;
   retain licenses and original SVGs where applicable.
5. Verify decoding, alpha, canvas alignment, aliases and local documentation
   links. Run `./gradlew :app:assembleDebug :app:lintDebug` for resource changes;
   inspect affected UI when rendering or placement changes. Apply relevant
   behavior tests if a separate UI or domain change accompanies the asset work.

For a character pack, export each named 512 × 512 layer, not its presentation
card or the whole board. The manifest distinguishes configured Figma exports
from isolated Figma renders at the node's natural size. Both preserve the
actual node clipping and shared canvas. For locations, download the active
image fill's original bytes and verify their SHA-1 against Figma's image hash;
the download response can also contain thumbnails and obsolete hidden fills.
Keep the original source resolution instead of the smaller card preview.
Some copied Figma labels are inaccurate, so inspect NPC and event artwork before
assigning a semantic name. Preserve the original label in the manifest.

The [verification script](../../../scripts/verify_assets.py) checks the catalog's
files, names, checksums, decoded pixels, canvas dimensions, aliases and local
documentation links. It also fails if the manifest still has missing exports.
It requires Python 3 and Pillow; use an isolated environment if Pillow is not
already installed:

```sh
python3 -m venv /tmp/lct-assets-venv
/tmp/lct-assets-venv/bin/pip install Pillow==12.3.0
/tmp/lct-assets-venv/bin/python scripts/verify_assets.py
./gradlew :app:assembleDebug :app:lintDebug
```

Development prompt:

> Read `docs/design/assets/README.md`, search `catalog.md` and `manifest.json`,
> and reuse the matching bundled Android artwork. Add any required new asset
> using the documented naming, format and provenance rules, then update both
> indexes. Preserve full canvas alignment and existing resource names. Report
> missing exports explicitly, and use the state-machine specification for
> behavior instead of deriving rules from artwork names.
