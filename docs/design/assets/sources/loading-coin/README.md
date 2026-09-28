# Loading coin — 2026-09-28

Source: [Coin by Daily Scoop](https://lottiefiles.com/free-animation/coin-MHXcO7DuOl).
Approved use: LOADING-D-001/002 in the [decision register](../../../decisions.md).

The original [dotLottie archive](coin.lottie) was downloaded from
https://assets-v2.lottiefiles.com/a/aa995c28-6adb-11ef-ba75-c35e73fa55e9/zzu0t6E7mY.lottie.
Its `animations/b0d87fd3-b988-46b7-9986-bf540f062af5.json` is copied byte-for-byte to
`app/src/main/res/raw/loading_coin.json`. No conversion, cropping, recoloring or
timing changes. The canvas is 480 × 480, 60 fps, frames 0–179 (3 seconds);
there are no external images or fonts. The source is Lottie JSON 4.8.0.

The app bundles the [license notice](../../../../../app/src/main/assets/licenses/lottie-coin.txt).
Source and runtime SHA-256 hashes and byte counts are recorded in the asset manifest.

`GameLoadingIndicator` uses Lottie Compose, a cached asynchronous composition and
explicit `ContentScale.Fit` bounds. Default size is 80 dp; startup uses 120 dp,
inline status uses 40–48 dp, and buttons use 24 dp. Playback loops while the
loading UI is composed and its lifecycle is resumed, respecting the system
animation scale. While parsing, or if parsing fails, the exact static first frame remains visible,
with the localized loading semantics.

The system launch window displays the same first frame on `#120F30` before
Compose starts (LOADING-D-002). Both use full-window centering and a 120 dp
original canvas; no artificial minimum display duration is added. Determinate savings bars keep their meaning.
Onboarding and debug modules receive the common indicator through UI slots.

## Static first frame and splash handoff

[Export script](../../../../../scripts/export_loading_coin_frame.py) reads the
approved JSON and emits [frame-zero.svg](frame-zero.svg), `loading_coin_frame.xml`
and `loading_coin_splash.xml`. It checks the source SHA-256 before converting.
Visible cubic paths, fills, gradients and layer ordering are retained. At frame
zero the stars are transparent and the light strip is entirely outside its matte;
all visible layer transforms are translations, including the coin's children.
The 480 × 480 canvas is not trimmed.

The frame drawable uses a 480-unit viewport at 120 dp. The system drawable uses
a 1152-unit viewport at 288 dp, with a 336-unit margin on each side: the original
480-unit canvas still occupies exactly 120 dp and fits inside Android's icon mask.
These are deterministic derivatives under the same Lottie Simple License.

`MainActivity` installs AndroidX SplashScreen before `super.onCreate`, then
removes its overlay without an exit transform. Startup playback begins after
removal; activity recreation does not wait for a nonexistent exit callback.
Until asynchronous Lottie parsing completes, Compose paints the same vector.
The startup backdrop, window background and system splash share one color
resource; startup content is centered without safe-area padding. Error controls
retain their safe-area padding. Existing startup routing decides when to leave.

Build checks cover debug/release. Static SVG rendering checks the derived artwork;
the app and emulators are not launched under the project's verification preference.

## Alignment across startup stages — 2026-09-28

`GameLoadingScreen` is shared by the startup check, navigation budget gate, menu
data loading, menu artwork loading, and unfinished-budget loading. Each uses the
same 120 dp canvas, full-window center and background, with no label participating
in the coin layout. The budget's Back button is a separate bottom overlay.
Compact indicators inside other screens retain their contextual sizes.

`LoadingScreenAlignmentTest` compares the real menu-loading layout against the
original canvas inside the system splash vector, in portrait/landscape with
large fonts and asymmetric system insets. Only compilation is performed under
the build-only verification preference.
