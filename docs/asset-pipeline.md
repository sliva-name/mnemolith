# Asset pipeline

Phase 8 art for the objects the player actually holds, places, and fights. Phase 12 redraws the key item and block textures in place and replaces the short tones for extract, compose success, mute, pressure warning, and the lens band chime. Those files are original work under the mod's All Rights Reserved license; see [assets.md](assets.md). UV regions below are unchanged. Geometry stays Minecraft-shaped. Hierarchy and palette do the work. There is no GeckoLib dependency and no `AnimationDefinition` clip. Mob motion is `EntityModel.setupAnim` posing the parts already synced through `MobActions`.

## Palette

| Name | Hex | Use |
| --- | --- | --- |
| Deep indigo | `#3D4A8A` | Cloth, glass rim, stratum body |
| Ink | `#1C244A` | Hood void, metal, shadow |
| Bone | `#E6DCC8` | Paper, lens rim, drum, blank faces |
| Verdigris | `#3E8E7E` | Memory gel, reel core, strider body |
| Ember | `#E07A4A` | Failure, fire, one tick on the lens |
| Mute gray | `#6E7380` / `#3A3E48` | Mute stone |

Spawn eggs use the same hues. They stay simple ovals.

Emissive accents that Phase 7 already lights are unchanged: enchantment glint on the chronicle lens and the imprint slip, archival stratum `lightLevel` 7, and fullbright particles. Block models do not add a second emissive texture.

## Items

Generated item models (`minecraft:item/generated`, `layer0`). The imprint slip is one texture. It is not tinted per tag.

| File | Size | Read |
| --- | --- | --- |
| `textures/item/chronicle_lens.png` | 32×32 | Bone outer ring, indigo ring, verdigris glass, bone bracket. Not an eye |
| `textures/item/extraction_needle.png` | 16×16 | Indigo handle, bone shaft, verdigris tip |
| `textures/item/imprint_slip.png` | 16×16 | Bone sheet, indigo stripe |
| `textures/item/catalog_fragment.png` | 16×16 | Torn page, indigo spine, verdigris mark |
| `textures/item/archivist_bait.png` | 16×16 | Bone hook, verdigris bead |
| `textures/item/archival_tablet.png` | 16×16 | Indigo tablet, bone lines |
| `textures/item/archivist_husk.png` | 16×16 | Empty hood |
| `textures/item/unstable_slip.png` | 16×16 | Slip with an ember crack |
| `textures/item/*_spawn_egg.png` | 16×16 | Low-priority egg oval in that mob's hue |

## Blocks

Parent `minecraft:block/block`. UV coordinates are pixels in a 16×16 atlas. Inventory uses the block model's `display.gui` transform. The composition screen is the reel's open state, so the block itself does not animate.

| Model | Texture regions | Geometry |
| --- | --- | --- |
| `composition_reel` | 0,0–8,8 dark metal; 8,0–16,8 bone; 0,8–8,16 verdigris; 8,8–16,16 indigo | Base, two cheeks, bone drum, verdigris core, axle |
| `mute_stone` | 0,0–16,12 mute ring (top only); 0,12–16,16 plain stone | Inset body, lip, raised cap |
| `archival_stratum` | Horizontal bands, top to bottom: indigo, bone, verdigris, indigo, ink | Core column plus a band that sticks out at y 5–9 |
| `resonator_trap` | Same quadrant layout as the reel, with a brighter gel center | Plate, four posts, verdigris core, bone collar |

Break and place sounds for mute stone and archival stratum are mod events. Step, hit, and fall stay on stone and deepslate. `MemorySoundTypes` resolves the mod events when the sound plays, not while the block is registered.

## Entities

Texture size 64×64. Model y grows downward. Feet sit at y 24. North (−Z) is the face the mob looks along. Cube UVs follow `ModelPart.Cube` with origin `(u, v)`, size `(w, h, d)`, and no mirror:

| Face | U | V |
| --- | --- | --- |
| Down | `u+d` … `u+d+w` | `v` … `v+d` |
| Up | `u+d+w` … `u+d+2w` | `v` … `v+d` |
| West | `u` … `u+d` | `v+d` … `v+d+h` |
| North | `u+d` … `u+d+w` | `v+d` … `v+d+h` |
| East | `u+d+w` … `u+d+w+d` | `v+d` … `v+d+h` |
| South | `u+d+w+d` … `u+d+2w+d` | `v+d` … `v+d+h` |

None of the three has eyes. They are not recolored zombies.

### Echo strider

Pivot of the body is `(0, 15, 0)`. Head and fins are children, so a charge rotates them with the body. Legs stay on the root.

| Part | texOffs | Box | Pivot |
| --- | --- | --- | --- |
| body | 0, 0 | (−10, −2, −3) 20×4×6 | (0, 15, 0) |
| head | 0, 12 | (−3, −3, −3) 6×5×3 | local (0, −1, −3), blank bone plate |
| fin_left | 0, 22 | (0, −2, −4) 1×3×8 | local (10, 0, −1) |
| fin_right | 20, 22 | (−1, −2, −4) 1×3×8 | local (−10, 0, −1) |
| leg0–3 | 0, 34 | (−1, 0, −1) 2×8×2 | (±6, 16, ±3) |

Walk swings opposite leg pairs. `TELEGRAPH` (the charge) leans the body to `xRot` 0.7 and opens the fins. `ATTACK` pushes the plate forward. `PHASE` scales the body to 0.7. A slow bob is the glide. Shimmer is the fin color and that bob. The renderer stays on the cutout path `MobRenderer` already uses.

### Archivist

| Part | texOffs | Box | Pivot |
| --- | --- | --- | --- |
| body | 0, 16 | (−5, 0, −3) 10×10×6 | (0, 6, 0) |
| hood | 0, 0 | (−4, −6, −4) 8×6×8 | local (0, 0, 1), north face is ink |
| cloak | 0, 34 | (−6, 0, 0) 12×14×2 | local (0, −1, 2) |
| satchel | 32, 0 | (0, 0, 0) 3×5×3 | local (4, 4, 2) |
| arms | 32, 16 | 2×9×2 | local (±5, 1, 0) |
| legs | 32, 28 | (−1, 0, −1) 2×8×2 | (±2, 16, 0) |

Walk swings arms and legs. `FLEE` leans the body to `xRot` 0.9 and keeps both arms reaching. That is the snatch, because a successful steal sets `FLEE` and the flee goal holds it. `ATTACK` is the same reach on an upright body for the short window before flee replaces it.

### Moment replicant

The real figure is slim (head 6×6×6, body 4×12×3). A second shell uses the paler UVs and sits at z +2, behind the facing direction.

| Part | texOffs | Box |
| --- | --- | --- |
| head | 0, 0 | (−3, −6, −3) 6×6×6 at (0, 0, 0) |
| body | 0, 14 | (−2, 0, −1) 4×12×3 at (0, 0, 0) |
| arms | 16, 14 | 2×11×2 at (±2, 1, 0) |
| legs | 28, 0 | 2×12×2 at (±1, 12, 0) |
| echo_head | 32, 16 | same box at (0, 0, 2) |
| echo_body | 32, 30 | same box at (0, 0, 2) |
| echo_arms | 48, 0 | same box at (±2, 1, 2) |

North faces are split bone / indigo with an ember seam. The shell is a washed copy of that split. `TELEGRAPH` raises the real arms to −2.5 and leaves the shell near −1.15, with a small sideways drift. `ATTACK` drops the real arms while the shell is still raised. Walk swings legs and the opposite arms.

## Particles

Sprites in `textures/particle/` are 16×16 white shapes with a soft alpha halo: star, dash, plus, X, ring, blob, chevron, hook, diamond. They stay white because `MemoryParticle.setColor` multiplies the sprite. Baking indigo into the PNG would dull that tint. The colors are the Phase 7 values in `ClientParticles`. The memory-graft mote (`graft_mote`) follows the same rule; its five particle types take the temper colors from `Temper.rgb()` (hushed `#B8C6DC`, grave `#B6A2E8`, kindled `#FF9A5C`, plunging `#7FE0CF`, volatile `#FF5E4E`), and the echo body is tinted with the same colors.

## Sounds

Mono Vorbis, 22050 Hz, short tones with a fade. No music bed. `sounds.json` names map to `assets/mnemolith/sounds/<name>.ogg`. Subtitles are `subtitles.mnemolith.<event>` in `en_us` and `ru_ru`.

| Event | File | Character |
| --- | --- | --- |
| `imprint_write` | `imprint_write.ogg` | Short 660 Hz |
| `extract` | `extract.ogg` | Rise 440→880 |
| `compose_success` | `compose_success.ogg` | 523 then 784 (the compose-ok tone) |
| `compose_fail` | `compose_fail.ogg` | Fall 320→160 |
| `lens_focus` | `lens_focus.ogg` | 880 Hz. Sneak-use of the lens, and the client chime when the pressure band rises |
| `pressure_warn` | `pressure_warn.ogg` | Low pulse. Played with the pressure ring |
| `mute_break` / `mute_place` | matching ogg | Thud. Place comes from the block sound, not a second call in `MuteStoneBlock` |
| `stratum_break` / `stratum_place` | matching ogg | Higher stone tone |
| `strider_*` | matching ogg | Low 180 Hz family. Charge sweeps upward |
| `archivist_steal` | `archivist_steal.ogg` | Two ticks. This is the snatch |
| `archivist_ambient` / `hurt` / `death` | matching ogg | Higher than the strider |
| `replicant_*` | matching ogg | Close dissonant pair. Telegraph rises. Blind is the high click |

## Out of this pass

A rebuilt observatory, a decoration set, and a music loop. The Scar and the recollection storm reuse vanilla sounds (amethyst resonance and break, warden emerge and sonic charge) plus `pressure_warn` and `lens_focus`; there are no new sound files. The reel does not have a block-entity idle animation.

## Generated art (tools/art)

`tools/art/graft_art.py` and `tools/art/residue_art.py` draw the newer guide pages and sprites procedurally with Pillow so they match the hand-made panels (indigo striped paper sampled from the existing pages, bone and verdigris frame, flat shapes, 1:1 pixels). `residue_art.py` imports the palette and helpers from `graft_art.py` and writes `textures/entity/residue.png` (kept pale: the client multiplies it by the temper color and an alpha of 0x38 / 0xA0 with the lens / 0xE0 when read), `textures/item/residual_shard.png` and `textures/gui/guide/residues.png`. Both scripts skip their own output pages when sampling the paper (`GENERATED`). `storm_art.py` imports both and writes `textures/entity/scar.png` (the residue UV layout, greyer and cracked; tinted by the client), `textures/item/scar_fragment.png`, `textures/block/scar_glass.png` (partly transparent, so the block uses the translucent layer), `textures/block/scar_heart.png`, `textures/block/scar_heart_base.png`, and `textures/gui/guide/storms.png` and `scar.png`. The Scar adds violet `#9660D6`, deep violet `#5C3696`, light violet `#CEAAF6` and magenta `#E868D6` to the palette. Run them from the repo root with `python tools/art/<script>.py`.

