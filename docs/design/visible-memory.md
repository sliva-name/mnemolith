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
| Saturated | One faint cluster of slow, ground-hugging stains — a memory patina where something happened. |
| Overloaded | Two brighter, wider clusters; the dominant kind's silhouette emerges. |
| Fracture | Three clusters at their brightest, and two of every three motes line up along one glowing seam — a cracked line along the ground across the chunk. |

Traces are stains, not dust (improved after PR #114 read as generic sparkles): they spawn at the surface,
barely drift, linger for seconds and dissolve, clustered at a few deterministic spots per chunk with a
gaussian falloff — dense in the middle, thinning out, the way a place remembers events rather than weather.
Motes, size, lifetime, alpha, cluster count and spread all scale strictly with the band (`MemoryTrace.style`,
verified by `tracesqa`), so the four states are distinguishable at a glance with no lens and no HUD — see the
visual tour scenes `traces_calm`, `traces_saturated`, `traces_overloaded`, `traces_fracture` (same staged
chunk, HUD off, raised camera so the silhouettes read).

## Place identity

Each trace wears the loudest memory's colour from the existing glyph palette (`MemoryGlyphs`, the same colours
as the lens pill swatch) and a silhouette grown from its after-image motion (`MemoryTrace.pattern`):
explosions, bosses and portals leave a scorched **ring**; paths, players, sculk and silence a dotted
**trail** line; building and redstone the **outline** of a block; fire, lightning and trade drifting
**embers** over a cluster; death, falls and the forgotten pale **wisps** that sink to the ground and settle
as stains. Colour *and* shape name the kind, and traces, after-images and the lens speak one language.

Honest limit: the snapshot carries no imprint positions, and adding one to `ChunkPressure` would change the
stream codec — a protocol version bump this rework must not make. Cluster centres are therefore chosen
deterministically from the chunk position (stable spots, not the true origins). Exact origins remain the job
of after-images when standing still, which already arrive on the server's per-player particle channel; if a
future protocol revision ever adds a loudest-origin field to the snapshot, clusters should anchor to it.

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

- `/mnemolith tracesqa` (game test `mnemolith:suite_tracesqa`, 13 checks): band mapping monotonic (including
  cluster count and spread), calm empty, fracture seam only, seam and cluster centres deterministic and
  inside their chunk, glyph colour, motion and silhouette per kind, ring/outline points following their
  lines, band-only fallback colour, particle registered, RU/EN lang, mapping cost.
- Visual tour `traces_*` before/after: on PR #114 all scenes show uniformly scattered coloured dust (kinds
  differ only by colour); after, clusters hug the ground, an explosion reads as a scorched ring, a path as a
  dotted trail, a build as a block outline, fire as an ember cluster, death as sinking pale-red wisps, and
  fracture keeps its glowing seam. Per-kind scenes: `traces_kind_{explosion,death,path,build,fire}`.

## Cohesion pass (with the balance pass)

After-echoes were the odd ones out: every kind replayed in the same pale ghost blue, so an explosion's echo
read as white smoke while its traces burned ember. After-image motes now wear the kind's glyph colour washed
35% toward the pale after-image tone (`MemoryParticle.echoColour`) — ghostly, but the same hue language as
traces and the lens pill. Ripple echoes (explosion, boss, portal) are also born on a ring of radius 0.7
instead of in a point, so the scar ring exists from the first tick and only then expands. The storm warning
from the balance pass (action bar + sound at `stormWarningSeconds`) is the third cue in the same story:
traces show the fracture, stillness replays it, the place itself warns before it breaks.
