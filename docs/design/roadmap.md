# Mnemolith design roadmap

Written after reading the code on `main` at d3e0db6 (stage 3 echoes plus the audit fixes). It describes what the game
is now, where it is thin, and which systems should come next and in what order. Numbers are defaults from
`CommonConfig`; see `docs/balance.md` and `docs/echo-design.md` for the full tables.

## 0. Status (updated with living memory on main)

| System | Status | Where |
| --- | --- | --- |
| Memory grafts | Implemented, draft PR #22 (`feature/echo-grafts`), not merged | `echo.graft`, `graftqa` 12/12, [echo-design.md](../echo-design.md) §12 |
| Residual echoes | Implemented on `feature/residual-echoes`, stacked on #22 (draft PR, retargets to `main` after #22 merges) | `echo.residue`, `ResidueEntity`, `residual_shard`, `residueqa` 18/18, §13 |
| Gametest harness | Implemented on `feature/gametests`, stacked on residual echoes (#23) and grafts (#22) (draft PR, retargets as they merge) | `com.mnemolith.gametest`, `runGameTestServer` in CI, 7 suites + 8 live residue tests, [qa-checklist.md](../qa-checklist.md#automated-game-tests-ci) |
| Recollection storm and the Scar | Implemented on `feature/recollection-storm`, stacked on the harness (#24), residual echoes (#23) and grafts (#22) (draft PR, retargets as they merge) | `echo.storm`, `ScarEntity`, `scar_fragment`, `scar_glass`, `scar_heart`, `stormqa` 20/20, 4 live storm tests (unwatched storms fade: [faded-storms.md](faded-storms.md)), [echo-design.md](../echo-design.md) §14 |
| Echo relay, archive vault | Implemented on `feature/relay-vault`, stacked on the storm (#25), the harness (#24), residual echoes (#23) and grafts (#22) (draft PR, retargets as they merge) | `echo.relay`, `vault`, `relay_thread`, `archive_vault`, `relayqa` 19/19, 3 live relay/vault tests, [echo-design.md](../echo-design.md) §15 |
| Living memory, stage 1 | Done on main (PR #33). Field guide page `noticed` | [living-memory.md](living-memory.md), `recall` config, `/mnemolith recallqa` |
| Living memory, stage 2 | Done on main (PR #34). Field guide page `traces` | [living-memory.md](living-memory.md), `investigate` config, `/mnemolith investigateqa` |
| Living memory, stage 3 | Done on main (PR #35): an offer from this play, a silhouette that helps or lies, a cost to mute / take / store / leave. Field guide page `offer` | [living-memory.md](living-memory.md), `use` config, `/mnemolith useqa` |
| Living memory, stage 4 | Done on main (PR #35): a scar fragment rewrites an imprint's tag, and a lie leaves a pale residue that cannot be kept. Field guide page `rewrite` | [living-memory.md](living-memory.md), `intervene` config, `/mnemolith interveneqa` |
| Echo guard job | First stage (#56): lesson from melee hits, post radius, weapon durability, grave/volatile/hushed grafts, field guide page `guard`, advancement `echo_guard` | [echo-guard.md](echo-guard.md), `guardqa` 12/12, `guard_kills_husk_spares_bystanders`, [echo-design.md](../echo-design.md) §16 |
| Echo guard stage 2 | `feature/echo-guard-2`: bows and crossbows with inventory arrows and a line-of-fire check, friendly-fire-safe arrows, shield blocking, "guard me" escort mode | [echo-guard-2.md](echo-guard-2.md), `guardqa` 19/19, `guard_archer_holds_fire_then_shoots`, `guard_shield_blocks_frontal_hit`, [echo-design.md](../echo-design.md) §16.1 |
| Echo guard stage 3 | `feature/guard-3`: hostile mobs notice and fight guards (fair reach, provocation, grave first), a guard under fire from beyond its leash keeps its shield on the shooter, axes disable an echo's shield, echoes wear the best armor from their inventory (wear, render, drop on death) | [echo-guard-3.md](echo-guard-3.md), `guardqa` 24/24, `guard_husk_fights_back`, `guard_axe_disables_shield`, [echo-design.md](../echo-design.md) §16.2 |
| Memory Hollows, stage 1 | `feature/memory-hollows`: a rare pale biome over plains/forest/meadow/taiga hosts via the `mnemolith:biome_region` biome modifier (two biome-source mixins, no terrain changes), faded turf over hollowstone, remnants and sinks, recollite ore and shard (works as amethyst in every Mnemolith recipe), forget-me-nots, memory flickers from chunk imprints, field guide page `hollows`, advancements `hollows_found` and `recollite` | [memory-hollows.md](memory-hollows.md), `hollowsqa` 22/22 |
| Memory Hollows, stage 2 | `feature/hollows-2`: the sunken archive (hollows-only structure, 8/3 spread, two loot chests, seeded path/trade/player memory), catching flickers into imprint slips with needle + lens, the recollite lens (×1.5 flickers, reach 9), synthesised biome music and ambience, field guide page `sunken`, advancements `sunken_archive`, `flicker_caught`, `recollite_lens`; amethyst still makes the early lens, needle and guide | [memory-hollows-2.md](memory-hollows-2.md), `hollows2qa` 18/18, live `hollows_flicker_delivered_and_caught` |
| 32x art pass | Done | `tools/art/build.py`, [asset-pipeline.md](../asset-pipeline.md) |

Residual echoes were re-scoped from the plan below (§3.2): instead of a stranger's recording to copy, a residue is the
chunk's own loudest memory condensed into a drifting fragment. That keeps ownership and protection simple (no
foreign fake player acting in your world), costs no save space for recordings, and ties the system to pressure,
grafts, the lens, the needle, mute stones, archivists, possession and observatories. The "stranger recording" idea is
parked, not dropped: a residue could later carry a short replay. The game test harness now exists and can cover replays with real players.

What residual echoes fixed from §2: weakness 3 (pressure is now also an opportunity: an overloaded chunk yields
residues, a festering residue is a slip farm), weakness 4 (every observatory holds an old residue worth a full graft),
weakness 5 in part (the lens gets a second active use, reading; the needle gets a new target), and weakness 1 again
(echoes drink residues, possessed bodies absorb them).

## 1. The core loop today

Two loops share one world.

**Memory loop.** World events (death, explosion, fall, fire, mute stone, block changes, walking) write imprints on
the chunk where they happened. The imprints add up to memory pressure: calm, saturated (20), overloaded (50),
fracture (80). The chronicle lens reads the band. The extraction needle lifts the strongest imprint into an imprint
slip. Two slips on the composition reel seal into a residual shard of strength 4 (a full volatile, kindled, plunging, or hushed graft) or a failure that spikes pressure. Three memory mobs live on pressure: the echo strider (saturated), the
archivist (overloaded, steals slips) and the moment replicant (fracture, copies your last action). Worldgen feeds
the loop with archival veins, mute pockets and chronicle observatories.

**Echo loop.** An echo slip records 25 s of you. The recording becomes an echo: a translucent body that replays the
recording, then stays as a helper with its own inventory. A recording that mines, builds or farms teaches a lesson,
and the echo repeats that job on its own (A* navigation, chest link, blueprint ghost). You can possess an echo
through the lens and give it orders (stay, follow, return). Work writes build imprints and instability; in overload
the echo misfires, in fracture it stops; hostile mobs hunt it, archivists rob it, replicants undo its blocks, striders
shadow it. Three crafted slips raise the echo limit, recording length and body health.

## 2. Weaknesses

1. **The two halves barely touch.** Pressure reaches echoes only as a penalty (misfire, stop, mobs). Nothing in the
   echo loop consumes imprint slips, and the memory loop has only four formula outputs. After the first hour slips
   pile up with no use, so there is little reason to go and harvest a loud chunk.
2. **Echoes drift toward "worker NPC".** Every echo is the same pink body with three jobs. Nothing about one echo
   is different from the next except its lesson. The special identity of an echo (a body made of memory) does not
   show in play; possession is used for swapping bodies and little else.
3. **Pressure is only a cost.** There is no reason to seek a loud place or cause a loud event on purpose, except to
   compose. The world reacts, but the player cannot turn that reaction into a tool.
4. **Few reasons to explore.** One observatory gives the tools; veins are a small bleed; mute pockets are a room. No
   place in the world holds something you cannot make at home.
5. **Items with thin use.** Unstable slip, archival tablet and archivist husk only feed the upgrade recipes. The
   needle has one target (a block).
6. **The guide lags the code.** The echo slip recipe, recording, possession and the lens thermal view are not
   explained in the book. The quick sheet lists only 7 of 11 recipes.
7. **Visual consistency.** Items and blocks were 16x16 while the lens was 32x32, and the guide art was flat diagrams.
   The 32× pass (see §3.6) put them on one density and one palette. Echoes still share a body; a temper is a tint, not a new mesh.
8. **Technical.** No automated tests (only in-game QA commands); the gametest run crashes without a registered test.

## 3. Candidate systems (ranked)

### 1. Memory grafts (chosen for this run)

Graft an imprint slip into your echo. The memory becomes the echo's **temper** and changes how that body relates to
the world: a silence graft hushes it and the echoes around it, a death graft turns it into a decoy that holds hostile
mobs, a fire graft makes it fireproof and smelts the ore it mines, a fall graft lets it drop and dig down, an
explosion graft makes it dig fast and loud and burst when it dies. Possessing a grafted echo gives you that temper
for as long as it lasts.

- *Player motivation:* specialise echoes for a plan (a quiet mining crew, a lava-side smelter, a decoy that holds a
  base, a stealth body to scout in). Slips gain a steady sink, so loud chunks become worth harvesting and causing.
- *Interactions:* needle (unpicks a graft), pressure (charges come from slips; kindled and volatile work writes loud
  imprints; fracture rejects grafts back into the chunk), mute stone, archivist (steals grafts), strider and
  replicant (ignore hushed echoes), hostile mobs (decoy), possession, compose (seals two slips into a strength-4 shard, a full graft).
- *Items:* none new. Imprint slips are the graft; the needle unpicks. This is deliberate: it gives the existing
  slips and needle a second job instead of adding a parallel item set.
- *Risks/costs:* charges run out; a graft is lost when worn below half; volatile and kindled work makes the chunk
  loud; fracture rejects the graft; a volatile blast hurts nearby echoes and you.
- *Scope:* medium. One new server class set, hooks in existing job/threat/possession code, entity data, UI line,
  renderer tint and particles, guide pages, docs, a QA suite.

### 2. Residual echoes (world echoes to find and capture) — implemented, re-scoped (see §0)

A chunk that reaches fracture, and some observatory ruins, carry a **residue**: a stranger's echo that replays the
last minutes of whoever lived there. Record it with an echo slip while it plays and you get a recording you could not
make yourself (an old builder's blueprint, a miner's lesson for an ore you have not found, a path through a vein).
- *Motivation:* exploration with a payoff you cannot craft; fracture becomes a place worth visiting.
- *Interactions:* grafts (a residue's own temper), pressure (appears only at fracture), observatory structure pools,
  archivist (competes for residue recordings), catalog (notes found residues).
- *Items:* **Residue recording** (from recording a residue; not craftable). Possibly an **attuned echo slip**
  (echo slip + archival tablet) required to hold a foreign recording.
- *Risks:* stranger recordings must respect ownership and protection (fake player owned by the finder); replay
  collision; save size. *Scope:* large.

### 3. Echo relay (echo-to-echo hand-offs)

Link two of your echoes so one hands its output to the other (miner -> carrier -> chest) instead of each walking to a
chest. A relay line through a muted corridor becomes a quiet logistics chain; a line through a loud chunk misfires.
- *Interactions:* job chest system, pressure misfires, hush grafts, archivist theft in transit.
- *Items:* **Relay thread** (string + echo slip + copper): linking tool. *Scope:* medium.

*Done* on `feature/relay-vault`, re-scoped. A hand-off chain (miner → carrier → chest) would have been a second item
logistics system beside hoppers and the job chest I/O, and nothing about it needs an echo. The relay instead links two
echoes through what only echoes have: a linked end stands inside its partner's hush and kindle aura at any distance,
drinks residues for its partner, lets the possessing player hop between the ends (sneak + return key, no new packet),
and repeats the possessing player's breaks and places at the same offset (the recorded hands, through the job hands).
The pressure misfire survived as noise: a fracture under either end cuts the thread, and a dying end shocks the other
and writes a death under it. Archivists raid vaults instead of threads.

### 4. Recollection storm and the Scar

With residual echoes in place a storm has a natural body: a fractured chunk condenses every graftable imprint at
once into residues that act out together, and the Scar is what is left when they merge. Lens reading, mute stones,
hush grafts and shard grafts become the defensive kit.

*Done* on `feature/recollection-storm`. A fracture can gather a storm (a 2% roll per second per player, or a freed
shard); it gathers 10 s (a mute stone or scar glass contains it), then six waves condense the 3×3 area's loud memories
into storm residues that act out, unless starved, drunk by grafted echoes, read, or hushed. Three or more survivors
merge into the Scar: a boss that must be read with the lens before it can be hurt, whose recalls grave and hushed
echoes answer. It drops the scar fragment (a third graft slip and fracture immunity for one echo, as planned) and
leaves a lasting site with a heart, scar glass (a craft-free ward block) and a daily residue. Replaying imprints as
hostile *echoes* (the older idea below) was dropped in favour of residues, which already exist, are cheap, and are
read and captured with the tools players have. The first live runs found two test-setup bugs (a placement aimed at
air instead of the ground; a player kept following the Scar into terrain), no gameplay bug.

Original plan: fracture currently only logs and may call one replicant. A storm would replay a fractured chunk's imprints as
hostile echoes for a short time and can end with the Scar (the boss the architecture doc reserves). Mute stones,
hush grafts and decoys become the defensive kit.
- *Items:* **Scar fragment** (boss drop) enabling a higher-tier graft slot. *Risks:* server load, griefing, fairness
  in multiplayer. *Scope:* large.

### 5. Archive vault (moving memory)

Archival stratum plus tablets build an **archive** block that stores up to N imprints taken out of loaded chunks
nearby, so pressure can be banked and moved (drain a base, feed a graft workshop).
- *Interactions:* needle, pressure, grafts, archivist raids on vaults. *Scope:* medium.

*Done* on `feature/relay-vault`. The archive vault (4 archival stratum, 4 amethyst, extraction needle) draws the
loudest imprint of the 3×3 chunks every 10 s up to 12, but deletes nothing: 15% bleeds into its own chunk, a fracture
there spills half each tick, an explosion spills all, a carried vault leaks every minute, and archivists raid it. It is
spent through the needle (slips), a discharge (write it all somewhere on purpose) or by feeding grafted echoes. It
reuses chunk memory, the needle, `ImprintWriter` and the pressure score; the only new storage is its block entity.
The live tests found one real bug before commit: any held item clicking the vault toggled it, so the needle never
reached the vault (the block now passes non-empty clicks to the item).

### 6. Unified 32x art pass

Restyle every item and block texture to 32x32 with one palette and material language (bone paper, indigo ink,
verdigris copper, amethyst glass), multi-element models for the reel, resonator and stratum, and richer guide art.
*Scope:* medium, no gameplay risk; do it as a dedicated run so every asset changes at once.

*Done.* Item sprites and block faces are 32× (a 16-unit face is a 32-texel region on a 64×64 sheet). The reel,
resonator, stratum, mute stone, vault and scar heart are multi-element models; their ground plates cover the full
footprint. The field guide is 30 pages, including the living-memory pages `noticed`, `traces`, `offer`, and
`rewrite`, redrawn in the same language. The reference plate includes scar glass and the scar heart. Armor, weapons
and the three role mobs are the same pipeline (`mnart/armory.py`). The pleading
chair stays the baked easter-egg scan. Regenerate with `python tools/art/build.py`.

### 7. Gametest harness

Move the checks behind `/mnemolith qa|echoqa|jobqa|echo3qa|graftqa` into NeoForge game tests so CI runs them.
*Scope:* medium; removes the "gametest run crashes" known issue.

*Done* on `feature/gametests`. All seven suites (`mpsmoke` included) run as game tests through the same `check`
code as the commands, plus eight live residue tests with real server players. CI fails on a failing test. The
first runs found a real echo build bug (the echo's own body blocking the cell it was placing) and two QA-setup bugs
(sites read from unloaded heightmaps; the mpsmoke column not entity-ticking).

## 4. Order

1. Memory grafts (done, PR #22).
2. Residual echoes (done, stacked PR on #22).
3. Gametest harness (done, stacked PR on #23), before the storm work, because storms touch many systems at once.
   All QA suites now run in CI as game tests, plus live residue tests with real players.
4. Recollection storm and the Scar (done, stacked PR on #24).
5. Echo relay and archive vault (done, stacked PR on #25).
6. Living memory, stages 1–4 («Мир меня заметил» through «Я могу вмешиваться»), done on main (PRs #33, #34, and #35). The field guide explains each stage: `noticed`, `traces`, `offer`, `rewrite`. The plan is in [living-memory.md](living-memory.md).
7. The 32x art pass (done): one palette, 32× items and block faces, multi-element plates, guide art from the same pipeline.
