# Performance budgets

Status: implemented on `quality/server-safety`.

## Why
No content gap this round was worth new art-heavy work, so this is quality work. The server-side hot paths added over
the last rounds (storm gates, lull lookups, memorial calm checks, lantern guards) had no regression guard. A slow
change would only show up as tick lag on a busy server.

## What
- `/mnemolith perfqa` (gamemaster) times 2000 calls of each path on the live server and fails if a per-call mean goes
  over its budget. Budgets are about 10x or more the measured mean, so the check catches real regressions and not machine noise.
- Hot-code fix: `HollowLanterns.guarded` used to read all 2601 blocks around a spot (73 µs a call). It now skips every
  chunk section whose palette cannot hold a recollite lantern, so the common "no lantern" answer costs about 0.75 µs.
  Behaviour is unchanged: it checks the same box, loaded chunks only, and `lanternqa` still passes.

| Path | Measured mean | Budget |
|---|---|---|
| stormGate | ~0.3 µs | 50 µs |
| lullLookup | ~0.3 µs | 20 µs |
| calmNone | ~1.2 µs | 50 µs |
| calmMemorial | ~16 µs | 400 µs |
| lanternGuard (none near) | ~0.75 µs (was 73 µs) | 50 µs |
| lanternFound | ~0.9 µs | 400 µs |

## Server compatibility
A static scan found no client classes referenced outside the client packages. The GameTest server runs as a dedicated server.

- `afterImageScan` (memory feel, stage 3): the once-a-second ambient scan for one player in a calm chunk; budget 50 µs, seen about 0.14 µs.
