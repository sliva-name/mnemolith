# Memory feel: one visual and audio language

Status: stage 1 done (the language; brazier, hushling, loom, angling); stage 2 next.

## Why

Mnemolith has grown many systems that change memory: writes, the mute stones, Memory Hollows' forgetting and flickers,
the ember brazier, hushlings, the hush loom, memory angling, residues, storms, mending. Each one grew its own effects,
often a vanilla particle (soul fire, white ash, bubbles) and a vanilla sound. A player cannot tell from the effect alone
that the thing that just happened was *memory*. This line gives memory one language, so every event of the same kind
looks and sounds the same wherever it comes from, and adds a few quiet cues that make memory felt where it is strong.

## The language

Five event kinds. Each has one particle family, one colour, one motion and one sound. Systems call one API
(`MemoryFeel.<kind>(level, pos, strength)`) instead of picking particles and sounds themselves.

| Kind | When | Particle (texture, tint) | Motion | Sound |
| --- | --- | --- | --- | --- |
| **Written** | an imprint is written | `imprint_shimmer`, shimmer blue `#6E7CC4` | a few motes settle downward into the ground | `memory.written`: the imprint write sample, soft |
| **Erased** | an imprint is removed (brazier burn, hushling thinning, angling, mending, write cap) | `imprint_extract`, ash bone `#E6DCC8` | motes rise and scatter upward, fading fast | `memory.erased`: the extract sample, low pitch |
| **Kept** | an imprint or tag is protected (hush loom threading, mute stone, selective mute, lantern) | `mute_haze`, indigo `#3D4A8A` | a slow ring that closes inward | `memory.kept`: the mute place sample, high and short |
| **Caught** | a memory is taken into the hand (angled slip, caught flicker, extraction) | `graft_mote`, verdigris `#3E8E7E` | motes arc toward the player | `memory.caught`: the flicker catch sample |
| **Forgotten** | memory decays into forgetting (Hollows forgetting, the Unremembered's pulse, a storm's wave) | `imprint_shimmer`, faded grey `#8A8696` | motes hang still, then vanish | `memory.forgotten`: the hollows chime, slow and low |

Rules:

- **Strength** scales count (1–12 motes) and volume, never colour, so kinds stay readable.
- **Edge-triggered only.** Calls happen when the event happens. Nothing runs every tick on the server; ambient cues
  (below) are client-side and polled at the lens interval.
- Vanilla flavour effects may stay *beside* the memory cue where they explain the cause (the brazier still shows
  fire), but the memory part is always the shared cue.
- **Sounds** are new sound events (`memory.*`) mapped to the existing samples with their own pitch and volume, with
  subtitles. No new audio files.

## Ambient cues (later stages)

- **After-images.** Standing still for a few seconds in a chunk that is saturated or louder shows one to three faint,
  slow "replays" at the origins of its loudest imprints: a long-lived ghostly mote column in the imprint's kind
  colour. Server-sent at most once per 3 s per player, only to that player.
- **Strong-memory hint.** In a saturated chunk the screen edge gets a faint, slow breathing tint (below the existing
  overloaded vignette), and the first time per chunk a short action-bar line ("This place remembers.") appears.
- **Lens and circlet overlays.** The chronicle lens shows the chunk's loudest kind as a small glyph in the pill; the
  remembering circlet tints the view edge with the kept colour while it wards.

## Accessibility and performance

- Existing client toggles keep working: `imprintParticles`, `particleDensity`, `maxParticlesPerTick` (all memory cues
  go through the same budget), `pressureVignette`, `lensOverlay`.
- New client toggles: `memoryEventSounds` (mute all `memory.*` sounds on this client), `memoryAfterImages`,
  `strongMemoryHint`. All default on and can be turned off without touching the server.
- `perfqa` gets a budget for the server side of each new cue (the after-image scan per player).

## Stages

1. **The language:** `MemoryFeel`, the five particle types and sounds, the `memoryEventSounds` toggle; applied to the
   ember brazier, hushling thinning and hush, the hush loom and memory angling.
2. **Across the older systems:** Memory Hollows forgetting and flickers, the recollite lantern, residues, storms,
   mending, mute stones, extraction.
3. **Ambient cues:** after-images and the strong-memory hint, with their toggles and perf budgets.
4. **Lens and circlet overlays.**

Each stage: a QA suite (the right cue for the right event, toggles honoured), docs, by-hand checks (every sound is a
by-hand check, since the test server has no audio).

## Stage 1 rules (as built)

- `MemoryFeel.cue(level, kind, pos, strength)` with the five kinds above; particle types `memory_written`,
  `memory_erased`, `memory_kept`, `memory_caught`, `memory_forgotten` (existing sprites, new tints, own motion);
  sound events `memory.written`, `memory.erased`, `memory.kept`, `memory.caught`, `memory.forgotten` (existing
  samples, own pitch and volume, subtitles RU/EN).
- Client toggle `memoryEventSounds` (default on) mutes exactly the `mnemolith:memory.*` sounds. The particles follow
  `imprintParticles`, `particleDensity` and `maxParticlesPerTick` like every memory particle.
- Applied:
  - ember brazier burn: **erased** (6), with three soul-fire motes for the cause (was 8 soul flames and fire ambient);
  - hushling thinning: **erased** (5) (was white ash);
  - a hushling's hush: **forgotten** (4) on the player (was an amethyst chime);
  - hush loom threading: **kept** (8), a soft wool thread under it (was white ash and a loud wool sound);
  - memory angling: **caught** (6) arcing to the angler, a few bubbles for the water (was a bubble column sound).
- A burn or thinning that finds nothing sends no cue.

### QA (stage 1)

- `/mnemolith feelqa` (9): registrations, sounds.json and subtitles, the toggle matching only memory sounds, the right
  kind from each stage 1 system, no cue when nothing happened.
- Live test `loom_cue_reaches_player`: a real player threads a loom; one kept cue at the loom.
