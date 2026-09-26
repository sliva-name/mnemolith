# Performance

Phase 10 keeps the Phase 9 pressure numbers and cuts work that showed up on a dedicated server. There was no full loaded-chunk scan to remove: cooling already ran only for the chunk under a player, every 200 ticks. The wins below are the ones a scripted load could measure, plus the scans that now run less often in play.

`/mnemolith perf` (gamemaster) uses a chunk 48 blocks from the command source, so `/mnemolith smoke` still owns the source chunk. One run writes 32 unthrottled path imprints, attempts 128 throttled build writes, scores the same eight-tag chunk 1000 times, walks a lens-sized neighborhood twice, and runs 200 path-sensor scans. `score=85` is the check that those eight tags still add 8+10+27+2+24+8+4+2.

## Timings

Nanoseconds, dedicated server, same world, `/mnemolith perf`. The before line is the warmed third run on the Phase 9 build. The after lines are three runs on this branch, in order. The third after run is the warmed comparison.

| Section | Before | After 1 | After 2 | After 3 |
| --- | ---: | ---: | ---: | ---: |
| `pathNs` (32 accepted path writes) | 623234 | 963520 | 501149 | 815883 |
| `buildNs` (128 attempts, 1 accepted) | 79469 | 166065 | 99761 | 62968 |
| `scoreNs` (1000 scores) | 506001 | 721807 | 147029 | 129810 |
| `syncNs` (two lens walks) | 30166 | 10695 | 9492 | 11500 |
| `syncSkipped` | 0 | 1 | 1 | 1 |
| `sensorNs` (200 scans) | 128915 | 336197 | 236725 | 140659 |
| `score` | 85 | 85 | 85 | 85 |

`pathNs` includes the imprint log line, the sound, and the particle send, so it moves with logging more than with the allocation change. It is not a claimed win. `buildNs` on the warmed run is lower because a rejected build write returns before it allocates a tag list or creates chunk memory. `scoreNs` drops from about 0.51 ms to about 0.13 ms for 1000 scores: the server thread reuses one top-4 buffer instead of building an `EnumMap` and copying the imprint list. A nested score still allocates its own arrays. `syncNs` is one walk plus one skip; the Phase 9 walk never skipped.

`sensorNs` is the cost of calling `nearestImprint` and `higherPressure` 200 times in a row. In play, the archivist resonator scan, the flee pressure scan, and idle strider repaths wait `mobs.sensorInterval` ticks (default 10) unless the path has just finished. A charge still aims every tick, so the hit does not wait on that interval.

## What changed

- A chunk is marked unsaved, and the lens epoch moves, only when cached pressure or the fractured flag actually changes. Loading a chunk that recomputes to the same score no longer dirties the attachment.
- Cooling sets a flag when instability or a build, redstone, or path imprint is present. The pulse returns immediately when the flag is clear. Loading a chunk rebuilds the flag. It is not saved.
- A lens request that still matches the player's memory epoch, chunk, and lens or ambient flags does not walk chunks and does not resend the snapshot. Vein particles still repeat every `gameplay.veinShimmerTicks` (default 40) while the lens is held. 0 repeats only when the snapshot is new. A changed chunk still shimmers on the next poll.
- The client draws saturated shimmer from the cached snapshot, so a skipped packet does not stop the motes. Shimmer past 48 blocks is dropped. The overlay rebuilds its text only when pressure, band, chunk state, sneak, or the numeric toggle changes. The catalog builds tag and formula labels once and refreshes discovery on the screen tick.
- Custom particles still go through `MemoryFxBudget`. Density at or below 0 returns before the cap, so it still silences them. Otherwise the cap is `min(round(48 * density), visuals.maxParticlesPerTick)`. The default cap is 48, which is the old hard cap.
- Path steps do not allocate a position until the player has moved at least 8 blocks. Sensor scans read imprints by index.

## Knobs

| Key | Default | What it does |
| --- | --- | --- |
| `visuals.particleDensity` | 1.0 | Scales custom memory particles. 0 disables them. |
| `visuals.maxParticlesPerTick` | 48 | Hard cap after the density scale. |
| `visuals.lensPollInterval` | 20 | Ticks between lens and fracture-feel pressure requests. Crossing into another chunk sends the next request on that tick. |
| `gameplay.veinShimmerTicks` | 40 | Ticks between vein particle repeats on an unchanged snapshot. 0 repeats only on a new snapshot. |
| `mobs.sensorInterval` | 10 | Ticks between resonator scans, flee pressure scans, and idle repaths. 1 checks every tick. A charge still aims every tick. |

A dedicated view distance of 8 to 10 matches this loop. The lens packet is a radius of 2 chunks, or 3 when the player's chunk has archival strata, and it is capped at 49 chunks. A larger view distance loads more chunks for mobs and for the per-player cool pulse. It does not widen that packet.
