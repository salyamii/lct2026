# Energy instrument and narrow checklist - 2026-10-18

Generated with the built-in `image_gen` tool and visually reviewed before import.
These are generated game illustrations, not Figma exports. Exact generation and
editing instructions are retained in [prompts.json](prompts.json).

| Resource | Original | Canvas | Purpose |
| --- | --- | --- | --- |
| `menu_energy_frame_v4` | [PNG](menu_energy_frame_v4.png) | 2172 x 724, 3:1 | One wooden instrument with a blank top rail for the native label and an empty recessed channel for the native energy fill |
| `menu_action_tasks_v3` | [PNG](menu_action_tasks_v3.png) | 1254 x 1254 | Narrow upright checklist above its unchanged-width blank wooden label plaque |

The energy label and fill remain native UI, not painted text or a fixed status.
The native amber segments follow the source channel at y50-68%, inset from
the preserved wooden ends. Nine-slice rendering grows the empty middle for
larger text while keeping corners proportional; label and fill use the same
coordinate mapping. The checklist
label also remains native and uses the plaque's source coordinates.

The checklist is an imagegen edit of the retained
[previous checklist source](../menu-buttons-2026-10-17/menu_action_tasks_v2.png).
Only the illustrated scroll proportions were changed by generation; the import
does not squeeze, crop or reposition any part of the returned image.

Sources were copied unchanged from the generated-images directory:

- `exec-77644165-ced7-4b38-8f59-104d5eda80e7.png`: energy instrument.
- `exec-8010cba9-43bd-460f-a57f-7a3eaebd4144.png`: narrow checklist.

Both PNG originals preserve the complete generated canvas and transparent
margins. Runtime files use lossless exact WebP, method 6, without cropping,
resizing, color changes or background removal. Decoded PNG and WebP dimensions
and all RGBA bytes were verified identical. The originals remain unchanged in
the generated-images directory as well.

File sizes, SHA-256 hashes, alpha bounds, source/reference hashes and converter
version are recorded in [manifest-entries.json](manifest-entries.json) and the
main [asset manifest](../../manifest.json). The RGBA hash includes the width
and height as two big-endian 4-byte integers before the decoded pixel bytes.
