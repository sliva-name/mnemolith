# Visible memory: pressure you can read from the world

Implemented on `feature/visible-memory`. Simulation, saves and the network protocol are untouched: this is a
presentation layer over the pressure snapshot the client already receives. The lens pill is unchanged and stays
the precision instrument; screen effects stay optional seasoning.

## What the player sees

A chunk's band is grown on the ground itself as pale memory traces (client-side particles only, no per-block
world mutation, works on a dedicated server):

| Band | Reading |
| --- | --- |
| Calm | Nothing grows. The absence is the reading. |
| Saturated | Sparse, faint, low motes drift over the ground — a memory patina. |
| Overloaded | The patina thickens: more, larger, brighter motes, rising faster. |
| Fracture | Brightest and densest, and most motes line up along one glowing seam across the chunk that pulses. |

Density, size, lifetime, rise and alpha all scale strictly with the band (`MemoryTrace.style`, verified by
`tracesqa`), so the four states are distinguishable at a glance with no lens and no HUD — see the visual tour
scenes `traces_calm`, `traces_saturated`, `traces_overloaded`, `traces_fracture` (same staged chunk, HUD off).

## Place identity

Each trace wears the loudest memory's colour from the existing glyph palette (`MemoryGlyphs`, the same colours
as the lens pill swatch: fire/explosion/lightning warm ember, death pale, explosion ring scars, …) and borrows
its motion from the after-image shapes (`AfterEcho.motionFor`: columns rise, explosions ripple outward, death
sinks, paths crawl along the ground, builds outline). Traces, after-images and the lens therefore speak one
language instead of three.

Where exactly the loudest memory sits is deliberately *not* what traces answer — the snapshot carries no
imprint positions, and inventing them client-side would lie. Traces locate the chunk, its band and its kind;
standing still for the usual 60 ticks doubles the traces in your own chunk at the same moment after-images
pinpoint the loudest origins and after-echoes faintly sound them, so stillness + traces + sound tell the story
together. The lens then gives the exact number and name.

## Cost and toggles

- Client only: a spawn round every 5 ticks, at most 12 attempts, chunks within 2 of the player, all inside the
  existing `MemoryFxBudget` (per-tick cap, distance falloff). `particleDensity` scales traces and 0 stills them;
  `imprintParticles` off stills them too. The server does no new per-tick work; `perfqa` is unchanged and green.
- New client toggle `memoryTraces` (default on), deliberately separate from `pressureEffectStrength`:
  setting the screen-effect strength to 0 must not hide the traces that replace those effects.
- To let a lens-less player read the saturated band, the server's band-only snapshot now also includes
  saturated chunks. It still sends pressure 0, state NORMAL and no loudest tag: no numbers, mute, archival or
  kind information leak — only the band the world now shows anyway. Payload format and protocol version are
  unchanged.

## Verification

- `/mnemolith tracesqa` (game test `mnemolith:suite_tracesqa`): band mapping monotonic, calm empty, fracture
  seam only, seam deterministic and inside its chunk, glyph colour and motion per kind, band-only fallback
  colour, particle registered, RU/EN lang, mapping cost.
- Visual tour `traces_*` before/after: on the old code all four scenes show the same bare platform (only the
  lens pill and, at fracture, after-images differ); after, each band reads from the ground alone.
