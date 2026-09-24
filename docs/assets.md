# Assets

Phase 12 textures and tones are original work for this repository. They use the same MIT license as the mod (`LICENSE`). No Minecraft DLC, marketplace pack, or other third-party sample is included.

## Textures

Drawn in the Phase 8 palette (ink `#1C244A`, indigo `#3D4A8A`, bone `#E6DCC8`, verdigris `#3E8E7E`, ember `#E07A4A`, mute gray `#6E7380`). Block UV regions in [asset-pipeline.md](asset-pipeline.md) are unchanged; the pixels inside those regions gained edges, highlights, and a readable mark.

| File | Size | What changed |
| --- | --- | --- |
| `textures/item/chronicle_lens.png` | 32×32 | Bone ring, indigo rim, verdigris glass with a highlight, bone bracket |
| `textures/item/extraction_needle.png` | 16×16 | Indigo handle, bone shaft, verdigris tip, ink outline |
| `textures/item/catalog_fragment.png` | 16×16 | Torn page, indigo spine, verdigris mark |
| `textures/item/field_guide.png` | 16×16 | Indigo cover, bone page edge, verdigris mark |
| `textures/gui/guide/*.png` | 128×64 | Twelve original diagrams for the field-guide pages |
| `textures/item/imprint_slip.png` | 16×16 | Bone sheet, indigo stripe, fold |
| `textures/item/archival_tablet.png` | 16×16 | Indigo tablet, bone lines, verdigris corner |
| `textures/block/composition_reel.png` | 16×16 | Bevel and grain inside the metal, bone, gel, and indigo quadrants |
| `textures/block/mute_stone.png` | 16×16 | Mute ring on the cap, stone grain on the body strip |
| `textures/block/resonator_trap.png` | 16×16 | Same quadrants as the reel, brighter gel center |

## Sounds

Mono Vorbis, 22050 Hz, synthesized in-repo (sine, a quiet harmonic, and a short noise transient). No music bed. Peak level stays under full scale. `sounds.json` names are unchanged.

| File | Character |
| --- | --- |
| `sounds/extract.ogg` | Rising tone, about 0.42 s, 420 Hz to 980 Hz |
| `sounds/compose_success.ogg` | 523 Hz then 784 Hz, about 0.62 s |
| `sounds/mute_place.ogg` | Low thud, about 0.28 s |
| `sounds/mute_break.ogg` | Higher break with a noise tick, about 0.30 s |
| `sounds/pressure_warn.ogg` | Two low pulses, about 0.46 s |
| `sounds/lens_focus.ogg` | Short 880 Hz chime for a rising pressure band, about 0.28 s |
