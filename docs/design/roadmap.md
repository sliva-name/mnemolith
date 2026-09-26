# Mnemolith design roadmap

Written after reading the code on `main` at d3e0db6 (stage 3 echoes plus the audit fixes). It describes what the game
is now, where it is thin, and which systems should come next and in what order. Numbers are defaults from
`CommonConfig`; see `docs/balance.md` and `docs/echo-design.md` for the full tables.

## 0. Status (updated with the recollection storm)

| System | Status | Where |
| --- | --- | --- |
| Memory grafts | Implemented, draft PR #22 (`feature/echo-grafts`), not merged | `echo.graft`, `graftqa` 12/12, [echo-design.md](../echo-design.md) §12 |
| Residual echoes | Implemented on `feature/residual-echoes`, stacked on #22 (draft PR, retargets to `main` after #22 merges) | `echo.residue`, `ResidueEntity`, `residual_shard`, `residueqa` 18/18, §13 |
| Gametest harness | Implemented on `feature/gametests`, stacked on residual echoes (#23) and grafts (#22) (draft PR, retargets as they merge) | `com.mnemolith.gametest`, `runGameTestServer` in CI, 7 suites + 8 live residue tests, [qa-checklist.md](../qa-checklist.md#automated-game-tests-ci) |
| Recollection storm and the Scar | Implemented on `feature/recollection-storm`, stacked on the harness (#24), residual echoes (#23) and grafts (#22) (draft PR, retargets as they merge) | `echo.storm`, `ScarEntity`, `scar_fragment`, `scar_glass`, `scar_heart`, `stormqa` 19/19, 3 live storm tests, [echo-design.md](../echo-design.md) §14 |
| Echo relay, archive vault | Next | — |
| 32x art pass | Its own run | — |

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
slip. Two slips on the composition reel make one of four formulas (Unrecorded, Fire trail, Landing burst, Archivist
bait) or a failure that spikes pressure. Three memory mobs live on pressure: the echo strider (saturated), the
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
   needle has one target (a block). Archivist bait has one use.
6. **The guide lags the code.** The echo slip recipe, recording, possession and the lens thermal view are not
   explained in the book. The quick sheet lists only 7 of 11 recipes.
7. **Visual consistency.** Items and blocks are 16x16 while the lens is 32x32; guide art is flat diagrams; every echo
   looks identical. There is no visual language for "what kind of memory is this".
8. **Technical.** No automated tests (only in-game QA commands); the gametest run crashes without a registered test;
   the README still describes "Phase 12" and stage 1 echoes.

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
  replicant (ignore hushed echoes), hostile mobs (decoy), possession, compose (competes for the same slips).
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

### 6. Unified 32x art pass

Restyle every item and block texture to 32x32 with one palette and material language (bone paper, indigo ink,
verdigris copper, amethyst glass), multi-element models for the reel, resonator and stratum, and richer guide art.
*Scope:* medium, no gameplay risk; do it as a dedicated run so every asset changes at once.

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
5. Echo relay and archive vault, depending on how players use grafts. Next.
6. The 32x art pass as its own run, once the item list is stable enough that nothing is drawn twice.
