# Main menu design assets

Source: [«Питомец Дизайн», node 2101:2](https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2101-2).

The artwork below was exported from the supplied Figma design on 2026-09-17. It is bundled locally, so the menu does not depend on Figma or a network connection at runtime. Raster artwork preserves the original pixels, transparency, and canvas dimensions. The fox image deliberately includes the transparent margins from the design; the Compose layout reproduces the original image placement.

| Android resource | Source dimensions | Figma asset |
| --- | --- | --- |
| `drawable/menu_village` | 941 × 1672 | [PNG](https://www.figma.com/api/mcp/asset/e38476e0-7f81-49ab-8dcf-86ee5b0f4c79.png) |
| `drawable/menu_ryzhik` | 1024 × 1024 | [PNG](https://www.figma.com/api/mcp/asset/a9cd52dd-baf8-4c2e-b690-92d6b7fd5b3e.png) |
| `drawable/menu_coin` | 1254 × 1254 | [PNG](https://www.figma.com/api/mcp/asset/5692cdc3-f04a-43ae-94cc-7f8883c67e7b.png) |
| `drawable/menu_location` | 1254 × 1254 | [PNG](https://www.figma.com/api/mcp/asset/0751865f-5f56-447d-a110-ff8b93a25a5c.png) |
| `drawable/menu_gear` | 1254 × 1254 | [PNG](https://www.figma.com/api/mcp/asset/c1d14654-ffe7-4ce5-b3db-eadad9bb98aa.png) |
| `drawable/menu_tasks` | 1254 × 1254 | [PNG](https://www.figma.com/api/mcp/asset/d3cbbfbe-4a0a-4b47-8acd-60143aa8505e.png) |
| `drawable/menu_goal` | 1254 × 1254 | [PNG](https://www.figma.com/api/mcp/asset/5eeda6dc-d946-4b97-a712-279e5664afea.png) |
| `drawable/menu_chevron` | 14 × 14 | [SVG](https://www.figma.com/api/mcp/asset/c6ac622b-bd4f-4543-8417-82ab6ae094ca.svg) |
| `drawable/menu_star` | 7 × 7 | [SVG](https://www.figma.com/api/mcp/asset/77a46b19-8b20-4500-8240-ed8eefa7fcee.svg) |
| `drawable/menu_ground_shadow` | 151 × 54 | [SVG](https://www.figma.com/api/mcp/asset/356c72a0-baf9-4eec-b0fe-1f74ae8b0c5c.svg) |

The chevron and star are Android vector drawables with the exact SVG path, colors, opacity, and stroke settings. Their original SVGs are retained here. The shadow uses an SVG Gaussian blur unsupported by Android vector drawables; it is rendered directly from the original SVG to a transparent 604 × 216 PNG (4×) using librsvg through Sharp. All PNG resources are stored in `drawable-nodpi` to keep sizing under Compose's control.

## Typography

The bundled static fonts are official Google Fonts TrueType files, downloaded via the [Google Fonts CSS API](https://fonts.googleapis.com/css2?family=Nunito:wght@800&family=Rubik:wght@800). Both have `usWeightClass=800`, contain all Russian Cyrillic letters (including `Ё`/`ё`), and do not require variable-font support.

| Android resource | Family / style | Official source |
| --- | --- | --- |
| `font/nunito_extrabold` | Nunito ExtraBold, upright, 800 | [Google Fonts TTF](https://fonts.gstatic.com/s/nunito/v32/XRXI3I6Li01BKofiOc5wtlZ2di8HDDsmRTM.ttf) · [upstream](https://github.com/google/fonts/tree/main/ofl/nunito) |
| `font/rubik_extrabold` | Rubik ExtraBold, upright, 800 | [Google Fonts TTF](https://fonts.gstatic.com/s/rubik/v31/iJWZBXyIfDnIV5PNhY1KTN7Z-Yh-h4-1UA.ttf) · [upstream](https://github.com/google/fonts/tree/main/ofl/rubik) |

Both fonts use the SIL Open Font License 1.1. Their complete copyright and license notices are distributed with the app in `app/src/main/assets/licenses/nunito-ofl.txt` and `app/src/main/assets/licenses/rubik-ofl.txt`.

## Compose implementation

`app/src/main/java/ru/nksk/lctapp/feature/menu/ui/MainMenuScreen.kt` implements the menu with native Compose layout, text and controls. All seven clickable entries are placeholders: the activity uses the default no-op action handler, so tapping only shows press feedback. There are no destination screens, dialogs, game-state changes or network requests.

The background fills the window while controls respect Android system bars and display cutouts. Native Android status and navigation bars replace the iOS mockup chrome. Landscape uses a side-by-side layout; portrait keeps the fox between the HUD and bottom actions. The bundled fonts support system font scaling. The village pill deliberately uses its visible bounds for both press feedback and hit testing; its surrounding layout space is not interactive. Compose previews cover 390×844, a compact phone, landscape and larger text. Blur uses platform rendering on Android 12+; older versions retain translucent panels.

The starter dependencies require compile SDK 37, so compileSdk was raised from 36.1 to 37.0; targetSdk 36 and minSdk 24 are unchanged.

Validation: debug APK build and lint (no errors), plus emulator instrumentation tests covering menu callbacks, placeholder behavior, village hit bounds, and compact/landscape layouts at 150% text. Native rendering was visually checked on a Pixel 10 Pro emulator running API 36.
