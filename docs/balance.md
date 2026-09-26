# Balance

Phase 9 tunes the defaults that already existed. Band thresholds stay 20 / 50 / 80, and the soft cap stays 100, so the lens bands mean the same thing. What changed is how fast ordinary play reaches them, how repeats are scored, and who is allowed to show up.

## Target curve

| Play | Pressure | Band |
| --- | --- | --- |
| Walking, or a few placed blocks | under 20 | Calm |
| One death (death + player), or one creeper | about 31, or 24 | Saturated |
| A death and an explosion together | about 55 | Overloaded |
| A failed compose on a quiet chunk | +18, still under 20 | Calm. No replicant |
| A failed compose on an overloaded chunk | about 73 | Overloaded. A replicant is asked |
| A second failure after that | about 91 | Fracture |

A vein adds at most 6, so it never changes the band by itself. Mute stone writes 8 (silence) and then stops further writes in that chunk. Instability cools by 1 every 200 ticks (10 seconds) while a player stands in the chunk, so a spike of 18 is gone in about three minutes. Build, redstone, and path imprints older than 6000 ticks (5 minutes) fade one per pulse. Deaths, explosions, falls, fire, silence, and player imprints stay until extracted.

The first imprint is a path mark after about 12 blocks of walking, or the first block you place once the chunk's 4 second build pause has elapsed. A needle is amethyst, iron, and a stick. A reel is copper, paper, and a crafting table. An observatory chest always contains a lens or a needle. That is the 10–20 minute early loop: walk, die or blow something up, extract, compose.

## Before and after

| Default | Before | After |
| --- | --- | --- |
| Tag weights (fire, fall, death, build, explosion, silence, player, redstone, path) | 6, 8, 12, 3, 10, 5, 4, 3, 2 | 4, 5, 9, 2, 8, 4, 2, 2, 1 |
| One imprint, intensity × weight (same order) | 12, 16, 36, 3, 30, 10, 8, 3, 2 | 8, 10, 27, 2, 24, 8, 4, 2, 1 |
| Repeated tags in the score | Every copy is full | Strongest copy is full. Up to 3 more add a quarter (minimum 1). The rest are stored and add 0 |
| `gameplay.writeDebounceTicks` | 40 | 80 |
| `gameplay.failurePressureSpike` | 8 | 18 |
| `gameplay.extractionDurabilityCost` | 1 | 2 |
| `gameplay.extractionCooldownTicks` | none | 20 |
| `gameplay.instabilityDecay` | none (instability only rose) | 1 |
| `gameplay.instabilityDecayTicks` | none | 200 |
| `gameplay.quietFadeTicks` | none | 6000 |
| Saturated / overloaded / fracture / soft cap | 20 / 50 / 80 / 100 | unchanged |
| Unrecorded / fire trail / landing burst | 200 / 160 / 600 ticks | 300 / 240 / 400 ticks |
| Fire trail speed bonus | +0.08 | +0.04 |
| Landing burst damage multiplier | 0.2 | 0.35 |
| Compose cooldown | none | still none. One slip and the spike are the limiter |
| Failed compose spawns a replicant | always | only if the chunk is then overloaded or fractured |
| Strider damage / weight / min pressure | 4 / 50 / 20 | 4 / 35 / 20 |
| Strider charge telegraph | 40 ticks | 45 ticks. The charge step is unchanged |
| Archivist damage / weight / min pressure | 2 / 40 / 20 | 2 / 25 / 50 |
| Archivist steal cooldown | 200 ticks | 300 ticks |
| Replicant damage / weight / min pressure | 5 / 20 / 80 | 5 / 12 / 80 |
| Replicant telegraph | 40 ticks | 60 ticks |
| Biome spawn weights (strider, archivist, replicant) | 40 / 30 / 8 | unchanged. The config roll and the pressure gate are the knobs |
| `worldGen.archivalVeinChance` | 12 | 8 |
| `worldGen.archivalBleed` / cap | 1 / 6 | unchanged |
| `worldGen.mutePocketChance` | 2 | 4 |
| Observatory spacing / separation | 40 / 16 | 32 / 12 in the structure set. `worldGen.structureSpacing` documents 32 |
| Observatory chest | 3 rolls from one pool. A lens was 2 of 31 | 1 guaranteed lens or needle, then 2 teaching rolls |
| Mute pocket chest | tablet or silence slip | tablet, silence slip, or a rarer needle |
| Archivist loot | catalog every kill, husk at 35% | catalog, paper, or ink; husk at 25% |
| Strider / replicant loot tables | empty (code still drops a path slip, or an unstable slip and sometimes an explosion slip) | the same code drops, plus paper, copper or gunpowder, and amethyst |

## Why

Ordinary building used to be cheap per block and still unbounded: every break wrote a full build imprint and a full player imprint, with only an 8-imprint cap and a 2 second pause. Four places now score about 12 and stay calm, and the pause is 4 seconds. A death farm in one chunk saturates (the fourth death is the last one that adds pressure) and does not fracture.

Loud events were large enough that one death (36) jumped to saturated and a death plus a creeper could approach fracture before any compose. They are now 27 and 24. Two of them overload the chunk. That is the moment archivists are allowed to spawn. Striders remain the saturated-tier threat, and they still refuse muted chunks. Replicants stay on the fracture band for natural spawns. A careless compose on a quiet chunk spends a slip and adds instability. It does not call a replicant.

Formula times are long enough to use and short enough to recast. Fire trail no longer adds most of a player's walk speed. Landing burst still saves a hard fall, and it no longer lasts half a minute or reduces the hit to a fifth. The needle's 64 durability now covers 32 extracts, with a one second rest so a click does not empty a chunk.

Veins were common enough to tint a lot of stone and rare enough that the bleed never mattered. 8% keeps them as a mining find. Mute pockets at 2% were easy to miss for a whole session; 4% makes a silent room a place you can plan around. Observatories moved from 40/16 to 32/12 so a first reel is more likely inside the early loop, and the chest teaches the tools instead of sometimes hiding both of them.

Catalog unlock is unchanged: a tag is learned when you extract it or put it in the reel, and a formula is learned when that compose succeeds. Hints still count unread patterns. They do not list them.

Server config defaults are unchanged (storms allowed, 1 per dimension). Storms now run; their numbers are in [Recollection storms](#recollection-storms-and-the-scar). `server.logPressureChanges` logs band changes other than fracture. Cooling and write strengths live in the common gameplay section.

## Memory grafts

One imprint slip grafted into an echo gives charges; a graft holds two slips' worth. `echoGraftChargeScale` multiplies every number in the charge column.

| Temper (slip) | Charges per slip | One charge buys | Rough value of one slip |
| --- | --- | --- | --- |
| Hushed (silence) | 12 | one swallowed work imprint (with the defaults one imprint per 20 work actions, +3 instability) | ~240 quiet work actions, ~36 instability not written |
| Grave (death) | 24 | one hostile hit taken | a night of decoy duty next to a farm |
| Kindled (fire) | 32 | one ore smelted (own or a neighbour's) | 32 ingots without fuel or a furnace |
| Plunging (fall) | 24 | one long drop (>3.5) or one floor dug over a long drop | a 12-deep shaft in a few steps |
| Volatile (explosion) | 48 | one dug block at ×0.55 time | ~48 blocks of fast digging, then explosion residue (weight 8) per work imprint |

Why these numbers: silence is the cheapest slip to get (a mute stone writes one) and the strongest effect (it hides pressure), so it gets the fewest charges. Death and fall slips need a real risk. Fire is common in the Nether, but ingots are a real economy, so one slip is capped at 32. Explosion slips are rarest and their residue is a cost of its own, so they get the most charges. A worn graft (under half a slip) is gone when it leaves the echo, so topping up is the way to keep one.

## Residual echoes

| Number | Default | Why |
| --- | --- | --- |
| Condense chance per 10 s, overloaded / fracture | 0.2 / 0.5 | about one residue per minute of standing in an overloaded chunk, faster in a fracture; slow enough that the relief is not free |
| Fester period | 60 s | one imprint back per minute: a festering residue refills an overloaded chunk in a few minutes, and gives a slip farm about one slip a minute |
| Strength | 2–6 (imprint intensity; old residues 5) | one fester step each: a strength-4 residue lasts 4 minutes under a mute stone or in a calm chunk |
| Echo drinking | half a slip per fester | a strength-4 residue is worth two slips to a matching echo, the same as a shard graft, but slowly |
| Shard graft | capacity × min(4, max(2, strength)) / 4 | full two slips from strength 4; a weak residue is not better than a slip |
| Needle wear on capture | 2 × extraction cost (4) | capturing is stronger than extracting, so it costs more |
| Lash | radius 3 (1.5 sneaking), every 5 s | a warning, not a killer: 3 magic damage + 3 s wither is the worst |
| Reading | 60 ticks within 16 blocks, cone 0.98 | long enough that you must commit and stand still; pinned for 200 ticks, enough to walk up with the needle |
| Nearby cap | 3 within 48 blocks | a loud base cannot turn into a swarm |
| Act-out zombies | at most 2 within 12 blocks | fracture reshapes the chunk without flooding it |

Why: condensing takes the loudest graftable imprint out, so a residue is relief now and a cost later. The player chooses the cost: starve it (mute stone, calm), spend it (capture, feed, absorb), or farm it (let it fester beside a needle).

## Recollection storms and the Scar

| Number | Default | Why |
| --- | --- | --- |
| `stormAttemptChance` | 0.02 per second per player in a fracture | a storm usually gathers within a minute or two of standing in a fracture; a fracture you pass through rarely breaks |
| `maxStormsPerDimension` | 1 | one storm is an event; two at once would be noise and double the tick work |
| Gathering | 10 s | long enough to see the bar, place a mute stone or step away |
| Area | 3×3 chunks around the centre | the fracture and its neighbours: a base next to a fracture is inside it |
| Waves | 6, one per 10 s | a minute of pressure: enough time to read and capture a few, not an endless siege |
| Storm residues alive | at most 6 | same order as the residue cap; the worst case is six act-outs every 10 s |
| Muted centre | each wave counts twice | containing late still pays off: a raging storm in a muted centre ends in three waves |
| Merge | 3 or more standing after the last wave | ignoring a storm has a cost; draining, capturing or feeding one below 3 avoids the boss |
| Scar health | 60 + 20 per merged residue, at most 180 | three merged is 120 (a strong mob), six merged is the ceiling |
| Read to hurt | 3 s of lens within 20 blocks → 8 s pinned, 5 s unreadable after | the fight alternates reading and hitting, and needs the lens tool you already have |
| Recall | every 3 s, 1 s audible charge, radius 10 | leaving 10 blocks during the charge dodges it; grave and hushed echoes answer it |
| Leash | 20 blocks from the heart | it guards its site; you can always retreat out of it |
| Drops | 1 scar fragment (2 at 5+ merged), one strength-4 shard per merged temper, 50 xp | the boss pays in grafts: a fragment is a permanent upgrade, the shards are two slips' worth each |
| Scar-set capacity | 3 slips instead of 2 | a clear upgrade that does not stack (one per echo) |
| Site reseed | once per in-game day, strength 5, only with a player in the chunk | a lasting reason to return; one residue a day is far below a slip farm's rate |
| Scar glass ward | storms blocked within one chunk; imprints still written | protects a slip farm without muting it |

Why: every storm offers the same choice as a residue at a larger scale. Pay up front (drain, mute, ward), pay during (read, capture, feed echoes, flee out of the area), or let it merge and fight for a permanent echo upgrade and a site that keeps giving.
