# Memory feel: one visual and audio language

Status: all four stages done (the language, the older systems, ambient cues, lens and circlet overlays).

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

## Stage 2 rules (as built)

- `MemoryFeel.cueThrottled(level, kind, pos, strength, ticks)`: at most one cue of a kind per chunk per window, for
  events that can repeat fast. The map is bounded (cleared past 512 entries).
- Applied:
  - writing any imprint: **written** (4), motes settling into the ground (was the imprint shimmer and write sound);
  - extracting an imprint: **caught** (8) arcing to the player (was the extract sound and burst);
  - a mute stone holding its chunk: **kept** (8) (was the mute haze);
  - Memory Hollows forgetting: **forgotten** (4), at most once per chunk per second;
  - a recollite lantern or remembering circlet keeping a Hollows memory: **kept** (3), at most once per chunk per 3 s;
  - catching a Hollows flicker: **caught** (6);
  - a residue dissolving: **erased** (8), with 8 of its temper motes (was 24 and an amethyst break);
  - capturing a residue: **caught** (8) (was the extract sound);
  - an unwatched storm settling: **forgotten** (12) (was an amethyst resonate);
  - mending a scar site: **kept** (12), beside the end-rod burst.

### QA (stage 2)

- `/mnemolith feel2qa` (9): write, extraction, mute, residue dissolve, storm settle and mend each send the right kind;
  the throttle holds a repeat in the same chunk, lets another kind or chunk through, and opens after its window.
- Live test `write_cue_reaches_player`: a real player places a block by hand; the build imprint sends a written cue there.

## Stage 3 rules (as built)

- `AfterImages` scans each player once a second (20 ticks), reading only the cached pressure of the chunk underfoot.
- **Strong-memory hint:** stepping into a different chunk that is saturated or louder sends that player a
  `StrongMemoryPayload` (network version 9). The client then breathes a faint indigo edge tint (below the pressure
  vignette, about a 5 s cycle) while the player stays in that chunk, and the first time per chunk this session the action
  bar says "This place remembers." / «Это место помнит.» Client toggle `strongMemoryHint`.
- **After-images:** standing still (under 0.3 blocks between scans) for 3 s in such a chunk shows that player only up to
  3 slow pale columns (`memory_afterimage`, 6 motes, 4 s life) at the origins of the chunk's loudest imprints, at most
  once per 3 s. Moving resets the wait. Client toggle `memoryAfterImages` (the particle is not drawn); the particles also
  follow `imprintParticles`, `particleDensity` and `maxParticlesPerTick`.
- One colour for all after-images (pale ghost blue) rather than one per kind: a single particle type keeps it simple.
- `perfqa` budget `afterImageScan`: 50 µs per scan (seen locally: well under 1 µs).

### QA (stage 3)

- `/mnemolith feel3qa` (9): registrations and lang, nothing in a calm chunk, the hint once on entry, after-images only
  after standing still, capped at 3, the cooldown, moving resets.
- Live test `strong_chunk_hints_player`: a real player in a saturated chunk gets one hint and after-images from the
  server's own scan.

## Stage 4 rules (as built)

- Each lens snapshot chunk carries `loudest`, the tag of its strongest imprint (-1 for none or a band-only sample;
  network version 10). `PressureCollector.loudestOrdinal` picks it with `ChunkMemory.highest()`.
- **Lens glyph:** the chronicle lens pill shows, after the band, a small swatch in the tag's glyph colour and the tag's
  name (`MemoryGlyphs.colour`: fire, explosion and lightning ember; death and boss red; fall bone; build and trade
  green; silence indigo; player and path teal; redstone red; portal blue; sculk verdigris; forgotten grey). The name is
  drawn in bone, so it stays readable whatever the swatch colour. Client toggle `lensLoudestGlyph`; `lensOverlay` still
  hides the whole pill.
- **Circlet tint:** while a remembering circlet is worn (not as a spectator) the screen edge holds a faint, steady tint
  in the kept indigo, fading in and out over about a second, below the strong-memory hint and the pressure vignette.
  Client toggle `circletTint`.

### QA (stage 4)

- `/mnemolith feel4qa` (9): the loudest tag survives the wire (and -1), the loudest is the strongest not the newest,
  every tag has a glyph colour and out-of-range has none, the circlet tint only when worn on the head and not as a
  spectator, the toggles' RU/EN lang.
- Live test `lens_reads_loudest_circlet_tints`: a real player with a circlet in a chunk whose loudest memory is a death
  under newer paths: the server's lens snapshot names death, the tint applies, and stops when the circlet comes off.
