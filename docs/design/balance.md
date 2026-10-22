# Balance pass (post-0.6.1): agency, sinks, and paying bosses

Audit first, numbers second. The full pre-pass audit lives in [../balance.md](../balance.md); this note records
what this pass changed and why. Everything is config-driven where a pattern existed; saves and protocol v10 are
untouched (one recipe and one loot table are data; storm strain is runtime state only).

## Audit summary (old defaults)

| System | Was | Problem |
| --- | --- | --- |
| Bands / soft cap | 20 / 50 / 80 / 100 | fine — the lens language depends on them; unchanged |
| Natural storm | 2%/s roll from the first second in a fracture (mean ~50 s, can fire in 1 s) | pure RNG: a storm could start because you crossed a fracture; no warning, no agency |
| Instability decay | 1 per 200 ticks with a player present | fine: an 18-point failed-compose spike cools in ~60 s of presence; unchanged |
| Echo slip | paper + amethyst/redstone ×2 | echoes (a top-tier helper) craftable in the first hour from surface amethyst |
| Slip sink | grafts (8 of 15 kinds), seals, compose | non-graftable kinds and surplus slips accumulated with no sink |
| Ember brazier | embers only (4 burns each) | no use for surplus slips |
| Scar drops | fragment + one shard per merged temper, 50 XP | shards are graft-only value; nothing for early/mid players |
| Unremembered drops | tear (circlet only) + recollite 3–5 + flowers + forgotten slip | the headline drop (tear) feeds exactly one item |
| Lens / needle | 1 amethyst each | intended early loop (observatory teaches them); unchanged |

## Changes

| Change | Old → New | Reasoning |
| --- | --- | --- |
| Storm build-up | first-second rolls → `stormBuildupSeconds` 45 s of standing in the *same* fractured chunk before rolls begin (`stormAttemptChance` 0.02 thereafter; expected ≈ 45 + 50 ≈ 95 s) | sustained presence is a choice; passing through a fracture never storms. Expected time stays in the old "minute or two" band |
| Storm warning | none → `stormWarningSeconds` 20 s: action-bar line + gather sound, once per visit, only when the storm gate is open | the player can leave, mute the centre, or prepare — the fracture seam traces already show *where* |
| Slip sink | brazier refuses slips → any imprint slip feeds an ember brazier for `emberBrazierChargePerSlip` 2 burns (half an ember; 0 refuses) | surplus slips of every kind become pressure relief: extract → burn → calmer chunk; a loop, not a trash can |
| Scar drops | fragment + shards → + amethyst shards (2 + merged) | amethyst is spent at every stage (lens, needle, seals); a boss pays broadly, not only grafters |
| Unremembered loot | + amethyst shards 4–8 pool | same reasoning; the tear stays the unique drop |
| Echo slip recipe | amethyst → recollite shard, **reverted in the 0.7.0 bug hunt** to `#mnemolith:memory_crystals` (amethyst or recollite) | recollite only comes from the rare Memory Hollows (archival veins do not drop it), so on most seeds echoes and every echo craft were unobtainable. The guide always said amethyst |
| Silence cost density | each extra mute stone in a chunk added +1 void per pulse → **capped at 3** (0.7.0 bug hunt); spectators no longer pulse | a natural mute pocket (~40 stones) or hush chapel raised a Silence Mirror boss ~10 s after a player arrived on a fresh world. Now a lone pocket takes ~100 s of standing (warning at ~50 s); dense neighbouring muted chunks still add their bonus |
| Storm guide / tooltips | still described 0.6.1 “2 in 100 from first second of standing” → **0.7.0 bug hunt** rewrote EN+RU guide storms + reference and config tooltips for buildup/warning | without the rewrite, the in-game guide contradicted `stormBuildupSeconds` / `stormWarningSeconds` and taught players the old unplayable storm cadence |

## Invariants (`/mnemolith balanceqa`, game test `mnemolith:suite_balanceqa`)

Thresholds still map to the four bands; a failed-compose spike (18) cools within 10 min–2 h of presence
(actual: 3600 ticks); buildup > 0, 0 < warning < buildup, expected storm time within 60–240 s (actual ≈ 95 s);
a slip feeds the brazier for exactly `emberBrazierChargePerSlip` (0 < that < per-ember); Scar drops include
fragment + residual shard + amethyst; the Unremembered loot table pays recollite and amethyst; the echo slip recipe accepts `#mnemolith:memory_crystals` (amethyst or recollite); the lens recipe still uses
memory crystals; RU/EN lang for the three new config keys and the warning line; the field guide storms page
(and reference / config tooltips) must mention buildup and warning, not “2 in 100 from the first second”.
