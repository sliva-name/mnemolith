# QA checklist

## Automated: game tests (CI)

`./gradlew runGameTestServer` starts a headless NeoForge game test server, runs every Mnemolith game test and exits with the number of failed required tests; CI (`.github/workflows/build.yml`) runs it after `./gradlew build`, so any failure fails the job. The JUnit-style report is `build/gametest/report.xml` and the log `run/gametest/logs/latest.log` (both uploaded as the `gametest-report` artifact). A full run takes about 7 seconds of game time and about 20 seconds of wall time on top of the build. In a dev world, `/test runmultiple mnemolith:` runs the same tests by hand.

The world is a deep superflat (bedrock, 60 stone, 3 dirt, grass, plains, surface at y=0) from the test-only datapack in `src/gametest/packs` (see its README). The vanilla game test world is 4 blocks deep and would put the vein, pocket and mining sites in the void.

**Suites.** Each command suite is one required test that calls the same `check(level, origin)` the command calls (`com.mnemolith.gametest.SuiteTests`), so there is one copy of every check. The test fails with the names of the checks that did not pass and the first notes; the full `Mnemolith <suite> ...` lines are in the log as for the command. Each suite runs from its own lane (4096 blocks apart) so suites never share chunks.

| Test | Suite | Notes |
| --- | --- | --- |
| `mnemolith:suite_qa` | `/mnemolith qa` (19 checks) | `locate` is waived: the game test server always creates its world with structure generation off, so `findNearestMapStructure` returns nothing. Its result is still logged (`Mnemolith gametest qa waived locate ...`). Run `/mnemolith qa` on a real world for it |
| `mnemolith:suite_echoqa` | `/mnemolith echoqa` (11) | |
| `mnemolith:suite_jobqa` | `/mnemolith jobqa` (12) | |
| `mnemolith:suite_mineqa` | `/mnemolith mineqa` (6) | Mining job regression: nearest targets first, digging down, no skipped blocks |
| `mnemolith:suite_echo3qa` | `/mnemolith echo3qa` (13) | |
| `mnemolith:suite_graftqa` | `/mnemolith graftqa` (12) | |
| `mnemolith:suite_residueqa` | `/mnemolith residueqa` (18) | |
| `mnemolith:suite_stormqa` | `/mnemolith stormqa` (19) | Pauses natural storms for the pass (the `mnemolith:live` setup does the same) |
| `mnemolith:suite_guardqa` | `/mnemolith guardqa` (24) | Fake-player owner; the fight is stepped through `EchoJob.tick` with mobs that have no AI |
| `mnemolith:suite_hollowsqa` | `/mnemolith hollowsqa` (22) | The flat test world has no multi-noise source, so the biome hooks are checked on overworld and nether sources built from the presets; features run on a pad in a cleared chunk |
| `mnemolith:suite_hollows2qa` | `/mnemolith hollows2qa` (18) | Fake-player catcher; flickers are registered with `HollowFlickers.send` at known spots. The template, loot and recipe checks read the real data |
| `mnemolith:suite_hollows3qa` | `/mnemolith hollows3qa` (19) | Fake-player catcher and attacker; faded are spawned without AI next to a `fillbiome` patch and stepped by direct calls. The loot, recipe and advancement checks read the real data |
| `mnemolith:suite_relayqa` | `/mnemolith relayqa` (19) | Fake-player owner; possession and the hop run through `EchoPossession` directly |
| `mnemolith:suite_mpsmoke` | `/mnemolith mpsmoke` (7) | Two fake players, as the command |
| `mnemolith:suite_recallqa` | `/mnemolith recallqa` (14) | Gesture buffer, old-vs-fresh selection, replicant RECALL (including the held item), mute. Gamemaster-only; no player tutorial |
| `mnemolith:suite_investigateqa` | `/mnemolith investigateqa` (15) | Anchors from this play, a lens origin reading, catalog bits unlocked only by that reading. Gamemaster-only; no player tutorial |
| `mnemolith:suite_useqa` | `/mnemolith useqa` (13) | A legendary offer from this play: a true guide and a lie, then the cost of leaving, muting, taking or storing it. Gamemaster-only; no player tutorial |
| `mnemolith:suite_interveneqa` | `/mnemolith interveneqa` (9) | A scar fragment rewrites an imprint's tag (silence, a scarred echo's temper, or the next temper). A lie also leaves a pale residue that cannot be kept. Gamemaster-only; no player tutorial |
| `mnemolith:pleading_chair` | village chair | Closed room gets a floor spot even with the air removed (legacy houses drop air), an open yard does not, the voice cooldown holds, plains small houses are wired and farms are not. Does not spawn a chair into the shared suite world |
| `mnemolith:village_chair` | village house wiring | Own batch. Places `plains_small_house_1` through the pool element and requires a chair on a solid floor. An already explored village does not gain chairs; generate new chunks or a new world. `/mnemolith spawn pleading_chair` still places one for QA |

**Live residue tests** (`com.mnemolith.gametest.ResidueLiveTests`). Real server players join through `PlayerList.placeNewPlayer` on an in-memory connection negotiated as a NeoForge client, stand in survival and are ticked every game tick the way the network layer ticks a connected player (`ServerPlayer.doTick`), so `PlayerTickEvent`, item use and `level.players()` are the real paths. Setup writes chunk memory and places residues directly; what is under test runs on its own. Players and forced chunks are removed by the `mnemolith:live` environment's teardown, pass or fail.

| Test | What is proved | Budget |
| --- | --- | --- |
| `residue_forms_where_player_stands` | A player standing in a fractured chunk with death and fire memories: the player tick's pulse condenses death (the louder) into one residue; the death imprint leaves the chunk, fire stays | 4400 ticks (22 pulses at 1 in 2) |
| `residue_lashes_player_beside` | A survival player walking 2 blocks beside an unread death residue is lashed by the residue's own tick: hurt and withered | 200 |
| `residue_sneaking_halves_reach` | Sneaking at 2.2 blocks: 60 ticks unharmed; standing up at the same distance: lashed | 300 |
| `residue_needle_slips_when_unread` | The needle through `ServerPlayer.interactOn` on an unread fire residue: no shard, the residue stays, the player burns | 40 |
| `residue_lens_reads_then_needle_captures` | The player raises the chronicle lens with a real item use (`gameMode.useItem`) 8 blocks away and keeps looking at the drifting residue: within 3 s it is pinned, fire is noted in the player's discovery, no lash; then the needle takes it as a fire/4 shard | 300 |
| `residue_fester_writes_back` | Left alone in an overloaded chunk, the residue festers on its own 60 s timer and writes its fall memory back, strength unchanged | 1400 |
| `residue_mute_stone_starves` | A mute stone placed in the chunk (a real block placement) mutes it; the next fester costs 1 strength and writes nothing | 1400 |
| `residue_observatory_seeds_once` | The observatory template placed with its worldgen processor list marks the reel's chunk; a player walking in makes the next pulse seed one old strength-5 residue by the reel; two more pulses seed nothing | 800 |

**Live storm tests** (`com.mnemolith.gametest.StormLiveTests`). The same real server players, each test in its own environment (`mnemolith:storm_...`, `mnemolith:scar_read_then_hurt`) so its storm never shares a batch. Setup clears three chunks of memory and lays a flat stone pad with open air; the storm then runs on the real server tick. Teardown ends leftover storms, discards the Scar and raised zombies, and unpauses natural storms.

| Test | What is proved | Budget |
| --- | --- | --- |
| `storm_shard_call_merges_into_scar` | A player frees a fall residual shard (`gameMode.useItemOn`) in a fractured chunk loaded with death, fire and fall memories: a storm is called and adopts the residue, the server tick gathers it and runs its waves, three or more storm residues are left, and they merge: the chunk is a Scar site with its heart, and one Scar stands over it | 1700 |
| `storm_mute_stone_contains` | While a called storm gathers, the player places a mute stone in the centre chunk (a real block placement): the storm ends `CONTAINED` before it rages, the freed residue stays as an ordinary residue | 300 |
| `storm_unwatched_fades` | A storm with a centre 6000 chunks away (never loaded) counts unwatched ticks on the real server tick, then ends `FADED` with the cap free and the centre remembered, without loading it | 200 |
| `scar_read_then_hurt` | A Scar and a survival player 6 blocks off: a hit before reading does nothing; the player raises the lens (`gameMode.useItem`) and keeps looking: within the read time the Scar is pinned, and `ServerPlayer.attack` then takes health off | 400 |

**Live echo guard test** (`com.mnemolith.gametest.GuardLiveTests`), in the `mnemolith:live` batch.

| Test | What is proved | Budget |
| --- | --- | --- |
| `guard_archer_holds_fire_then_shoots` | A pure archer (bow, 16 arrows), a husk 7 blocks west with no AI, a villager in the line of fire: no shot in 80 ticks; the villager steps aside and the archer shoots the husk dead; the villager keeps full health and arrows left = 16 − shots | 700 |
| `guard_husk_fights_back` | A guard with a wooden sword and no shield, a husk with its own AI 6 blocks away: the husk picks the guard before the guard hits it, lands at least one hit (the guard's health drops), and the guard kills it and lives | 400 |
| `guard_axe_disables_shield` | A guard with a sword and a shield against a 400-health zombie with no AI; once the shield is up, a 4-damage hit from a vindicator holding an iron axe is blocked (no damage) and knocks the shield down for 100 ticks; the shield stays down for 95 ticks, then goes back up | 400 |
| `guard_shield_blocks_frontal_hit` | A guard with a sword and a shield walks to a zombie; once the shield is up (vanilla `isBlocking`), a 4-damage frontal hit does no damage, is counted as blocked and wears the shield | 200 |
| `guard_kills_husk_spares_bystanders` | A guarding echo with an iron sword, a husk 4 blocks from its post, a villager and a cow beside it, a creative owner: on the real server tick the echo walks to the husk and kills it; the villager and the cow keep full health, the sword lost durability, the echo is still guarding | 600 |

**Live relay and vault tests** (`com.mnemolith.gametest.RelayLiveTests`), in the `mnemolith:live` batch on their own pads (south of the other sites; the teardown also discards echoes and dropped items there).

| Test | What is proved | Budget |
| --- | --- | --- |
| `relay_thread_links_then_hop` | The player right-clicks two of their echoes with a relay thread (`Player.interactOn`): both carry one link and one thread is used. Possessing one end, sneaking and pressing the return key (the same `EchoRelays.returnKey` the unpossess payload calls) puts the player in the other end, and the body left behind stands as the linked echo; the return key without sneaking returns the player | 100 |
| `relay_mirror_break` | Possessing one end, the player breaks a stone beside them with a pickaxe (`gameMode.destroyBlock`, the real break event): on the next server tick the other end breaks the stone at the same offset beside itself | 100 |
| `vault_draws_then_spends` | The player places a vault (`gameMode.useItemOn` with the item), switches it on with an empty hand; its own block entity tick draws the death and fire imprints out of the area within two intervals; the second empty-hand click switches it off; the needle (`useItemOn`, the vault passes the click to the item) takes the loudest (death) as a slip; a sneaking empty-hand click discharges the rest into the chunk | 700 |

**Live Memory Hollows test** (`com.mnemolith.gametest.HollowsLiveTests`), in the `mnemolith:live` batch on its own site (the setup turns it into Memory Hollows with `fillbiome`).

| Test | What is proved | Budget |
| --- | --- | --- |
| `hollows_flicker_delivered_and_caught` | Three real players: a seer in a hollow with path imprints in the 5×5 chunks around, a friend 6 blocks off, a stranger 144 blocks off. One offer at density 4: the `HollowFlickerPayload` read off the seer's connection is a path (walk) scene 6–20 blocks away; the friend receives the same payload, the stranger none. The seer then uses an extraction needle with a chronicle lens in the off hand (`gameMode.useItem`): one path slip, the `CAUGHT` payload to seer and friend, no active flicker left, the *Hold That Thought* advancement | 200 |

**Still manual.** Everything drawn on a client (the `Manual (client)` lists below, fracture feel, GUI contrast, the storm's sky, bar and ring, the Scar's look), a natural storm start (a 2% roll per second; `stormqa` checks the gate and the shard call instead), the structure `locate` check, real-world terrain (hills, water, caves: the game test world is flat), a real second client for multiplayer, and restart persistence of a whole world (the suites cover NBT round trips of the entities and chunk memory, not a server restart).

**Proving the gate.** Break a check on purpose (for example, make `Residues.lash` skip the wither) and `./gradlew runGameTestServer` fails with the test name and message and a non-zero exit; revert it and it passes.

## `/mnemolith qa`

`/mnemolith qa` is a gamemaster command. It runs on the dedicated server, on surface columns east of the command source, and does not use the chunks owned by `/mnemolith smoke` (the source chunk), `/mnemolith perf` (48 blocks east), or `/mnemolith mpsmoke` (96 blocks east). One log line is the result:

```
Mnemolith qa writes=true bands=true extract=true formulas=true quietFail=true loudFail=true mute=true lens=true catalog=true recipe=true guide=true vein=true pocket=true observatory=true locate=true loot=true strider=true archivist=true replicant=true dimension=minecraft:overworld
```

Two more lines record the observatory: `Mnemolith qa observatory chest=true` (the template) and `Mnemolith qa observatory registered=true located=<pos>` (the structure search). The chat message is `QA <passed> of 19`. Every flag above has to be true. Multiplayer flags stay on `/mnemolith mpsmoke` and are not folded into this command.

`/mnemolith smoke` is unchanged. The Phase 9 line is still `pressure=75 band=OVERLOADED muted=true writeBlocked=true compose=3`.

## Matrix

| # | System | Where it is proved |
| --- | --- | --- |
| 1 | Imprint write: walk, build, death, explosion, fall, silence | `writes`. Walk calls `PathLedger.note` across 16 blocks. The other five call `ImprintWriter.tryWrite` and require the tag on that chunk |
| 2 | Pressure bands calm → saturated → overloaded → fracture | `bands`. Defaults stay 20 / 50 / 80, soft cap 100, storm scale 1. `MemoryPressure.band` is checked on each edge. A live spike of 19 stays calm, 20 is saturated, and 80 fractures the chunk and asks for a replicant. The column gets a temporary forced chunk ticket so that replicant is visible to the same column query `/mnemolith mpsmoke` uses; the ticket is removed before the command returns |
| 3 | Extract needle → imprint slip + catalog tag | `extract`. One fall imprint becomes a fall slip in the inventory; a second fall from another block joins it (count 2). Two older death slips saved with different places collapse into one stack. `CatalogFragmentItem.payloadFor` has only the fall bit |
| 4 | Compose reel: every shipped formula, quiet fail, loud fail | `formulas` composes all four formulas. Each success notes that formula bit and puts a strength-4 residual shard in the inventory: explosion, fire, fall, then silence. The discovery mask has all four bits. `quietFail` mismatches build+build on an empty chunk: pressure equals `failurePressureSpike` (18), the band stays calm, and no replicant is asked. `loudFail` mismatches on a death+explosion chunk (overloaded) and the replicant count rises by one. That column uses the same temporary forced ticket as `bands` |
| 5 | Mute stone: silence, then the write block | `mute`. Placing the block writes silence, then a build write is refused |
| 6 | Lens band matches the server; dimension stamp | `lens`. `PressureSync.originBand` equals the cached band, and that band is saturated for one explosion imprint. The first lens walk runs, `perfStampMatches` is true for this dimension and chunk, and the next walk is skipped. The log's `dimension=` is the stamp. A dimension change drops it (`PressureSync.forget` on logout and dimension change; the client drops its snapshot when `snapshotDimension` no longer matches) |
| 7 | Catalog fragment opens only discovered tags and formulas | `catalog`. An empty player sends bits `0,0`. After extracting explosion, the payload is only that tag. After composing death+silence, the formula bit is unrecorded, death and silence are set, and fire stays clear. `CatalogScreen` draws a row only when that bit is set |
| 8 | Worldgen: vein, mute pocket, observatory locate, chest loot | `vein` lays stone along both axes (the feature replaces stone, and a repeat pass would already be stratum), force-places a vein, and requires stratum marks. `pocket` fills a stone volume at the forced floor (the feature replaces stone or air, and a surface column is dirt), force-places the pocket, and requires that floor to be muted. `observatory` requires a placed template whose chest loot id is `mnemolith:chests/chronicle_observatory` and whose unpacked chest contains a lens or a needle. `locate` requires the structure registry entry and `findNearestMapStructure` from the command source (split from `observatory` so the game test can waive only the search). `loot` requires the loaded loot JSON to name `mnemolith:catalog_fragment` (weight 1 in the teaching pool) |
| 9 | Mobs: spawn plus one action | `strider` summons an echo strider and `beginCharge` sets the telegraph pose. `archivist` summons an archivist, `snatch` empties the container, and the pose is flee. `replicant` summons a moment replicant and `beginTelegraph` sets the telegraph pose |
| 10 | Phase 11 multiplayer invariants | `/mnemolith mpsmoke`, not this command. The line is `sameBand=true discoveryIsolated=true steal=true reel=true muteBlocks=true replicants=true guarded=true` |
| — | Catalog fragment can be crafted | `recipe`. The recipe manager has `mnemolith:catalog_fragment`, and the loaded JSON result id is that item. The shaped pattern is paper, amethyst shard, ink sac, stacked |
| — | Field guide is registered | `guide`. The recipe manager has `mnemolith:field_guide` (book over an amethyst shard). The observatory loot JSON names it at weight 2. The page table has `GuideBook.PAGE_COUNT` (30) ids, each with a title key and a `textures/gui/guide/<id>.png` path. Language files and those diagrams are client assets, so this dedicated check does not open the screen |

## Echo QA

`/mnemolith echoqa` is a gamemaster command for the echo stage 1 paths. It runs on a flat test pad next to the command source, uses a temporary fake-player stand-in as the owner (so it works from RCON with nobody online), and cleans up its echoes, shells, and blocks. One log line is the result, and the chat message is `Echo QA <passed> of 11`:

```
Mnemolith echoqa spawn=true emptyInventory=true replayFakePlayer=true giveItems=true replayWithGear=true possessSwap=true bodyDied=true logout=true crashRecover=true dimension=true shellKilled=true
```

| Flag | What is proved |
| --- | --- |
| `spawn` | A synthetic recording spawns a registered echo with the owner's profile |
| `emptyInventory` | All 41 echo slots are empty at spawn, while the owner holds gear |
| `replayFakePlayer` | Replay with an empty echo: dirt is broken and its drop lands in the echo inventory, stone is kept (no fitting tool), planks are not placed (no item), the lever flips, and the break event actor is a fake player with the owner's UUID |
| `giveItems` | Items moved into the echo inventory leave the giver; the total count is unchanged |
| `replayWithGear` | With a pickaxe and planks: stone is broken with the echo's tool, cobblestone is kept, planks are placed and consumed |
| `possessSwap` | Possess, then unpossess: the owner's real items, the body items, and the total are identical before and after, and the shell appears and goes |
| `bodyDied` | Lethal damage while possessed: the owner returns, every body item drops where it died, and chunk instability rises by `echoDeathPressureSpike` |
| `logout` | The logout path swaps back with no lost or extra items |
| `crashRecover` | The possession attachment survives a codec round trip (as after a crash) and the join path restores it with no lost or extra items |
| `dimension` | A dimension change while possessed is cancelled and swaps back |
| `shellKilled` | Damage to the shell is written to the stored real health; killing the shell returns the owner |

`/mnemolith qa` stays at 19 of 19 and does not include these checks.

## Echo job QA (stage 2)

`/mnemolith jobqa` is a gamemaster command for the stage 2 jobs. Like `echoqa`, it uses a fake-player stand-in as the owner, builds its own test areas next to the command source, ticks the echo through the level's own entity tick (so movement has real collisions), and cleans up. The chat message is `Echo job check <passed> of 12`. Each check also logs a detail line with counts:

```
mining ticks=350 status=nothing_left mined=6 oresLeft=0 rawIron=5(echo 5, chest 0) coal=1 cobble=8 tunnelled=8 pickDamage=14
buildExact ticks=166 rotation=CLOCKWISE_90 status="Done: 15/15" wrong=0 stairsFacing=south logAxis=z echoItemsLeft=5
```

| Flag | What is proved |
| --- | --- |
| `mineLesson` | A synthetic recording that breaks 3 iron ore and 1 coal ore gives a mining lesson with both types, most common first |
| `mining` | Radius 8. In a 21×21 stone box with buried iron ores (one enclosed) and a coal ore, the echo pathfinds, tunnels only through stone, mines every taught ore in range with its own pickaxe, and the drops are in the echo or the chest. The pickaxe took damage |
| `deposit` | With a nearly full inventory, the echo walks to the linked chest and deposits everything except the pickaxe and food. The stick total before and after is equal (33) |
| `safety` | An ore next to water and one next to lava stay, no fluid is opened, the rim that holds the echo stays, a chest in the box stays, and untaught copper and an out-of-radius ore stay |
| `minePersist` | Mid-job, the echo is saved to NBT and loaded as a new entity. It resumes in MINE mode and finishes |
| `noTool` | Without a pickaxe, the job stops with `no_tool` / "Stopped: no pickaxe" |
| `buildLesson` | A synthetic recording that places 15 blocks (stone bricks, planks, logs, stairs, glass), one placed then broken, gives a 15-entry blueprint with the right facing |
| `buildExact` | Placed with a 90° rotation and enough blocks, every position matches the rotated blueprint (stairs facing, log axis), and the status is "Done: 15/15" |
| `buildMissing` | With 2 glass and 1 stair short, the status lists "Missing: 2× Glass, 1× Oak Stairs" and those 3 positions stay empty |
| `buildResume` | After an NBT reload, the missing blocks are added to the linked chest. The echo fetches them, finishes the build exactly, and leaves the rest in the chest |
| `clientSummary` | The recording's network form is a small summary (241 bytes vs 549 for the full 25-frame recording), and a creative-style client round trip restores the full recording |
| `strangerRefused` | Another player cannot stop the echo, unlink its chest, or open it |

The last line of `mnemolith jobqa` must be `Echo job check: 12 of 12`. `mnemolith qa` stays at 19 of 19 and `mnemolith echoqa` at 11 of 11.

### Mining job QA (`/mnemolith mineqa`)

A gamemaster command (also `mnemolith:suite_mineqa`) for the mining job on the shapes players teach. It builds its own sealed areas (obsidian walls and floor) next to the command source, gives the echo a diamond pickaxe, drives it through the level's own entity tick and cleans up. The last line must be `Mining QA: 6 of 6`.

| Check | What passes |
| --- | --- |
| `stoneDigsDown` | Stone lesson, radius 2, echo on a stone floor: both layers under it (y−1 and y−2) are mined out |
| `stoneNoSkip` | Same pass: no stone cell in the radius is left |
| `oreLine` | Iron ore lesson in solid stone: a continuous line of 6 ores at the echo's feet is mined, none skipped |
| `oreDown` | Same pass: a vein of 4 ores straight down under the echo is mined |
| `nearFirstDeep` | Stone lesson, default radius 16, over a 37×37×20 stone mass, with the work point 6 blocks above a chunk section's bottom. After 120 blocks it is still mining, it has dug at least 3 down, and nothing more than 6 blocks from the work point was opened |
| `nearFirstShallow` | Same with the work point 1 block above a section's bottom |

The two `nearFirst` checks are the regression for "echoes skip blocks and only dig horizontally". Before the fix, the scan kept the first 256 targets in section storage order, which for stone is one flat layer at the section's bottom. Offset 6 tunnelled 7 down to that layer and opened 130 cells more than 6 blocks out. Offset 1 never went below y−1 and opened 44 far cells.

**In the client (manual):**

- [x] Record yourself breaking a few stone blocks. Put the echo in a stone area, give it a pickaxe and press **Mining** (radius 16). It clears the blocks next to it first and works outward and down in a compact pit, with no untouched blocks left between the ones it took.
- [x] The same with an ore lesson on a real vein: the whole vein goes, including the ores under the echo.
- [x] Next to a drop of more than 3 blocks it does not break the block it stands on. Next to water or lava it leaves the block that would open the fluid.

## Echo stage 3 QA

`/mnemolith echo3qa` is a gamemaster command for stage 3. Like `jobqa`, it uses a fake-player owner, builds its own test areas next to the command source, ticks entities through the level's own entity tick, and cleans up. The chat message is `Echo stage 3 check <passed> of 13`, and each check logs a detail line:

```
misfire pressure=52 strainSeen=true misfires=11 wrongPlaced=true noticeSeen=true ticks=338 wrong=0 echoItemsLeft=0 groundDrops=0 label="Memory: Overloaded"
farming ticks=128 harvested=10 planted=20/20 chestWheat=10 echoSeeds=17 chestSeeds=0 groundDrops=0 label="Farm: waiting for Wheat · 10"
```

| Flag | What is proved |
| --- | --- |
| `upgrades` | Chorus raises the echo limit 1→2→3 and a third is refused; long take raises recording frames 500→900 and a third is refused; sturdy adds 2×10 health and a third is refused. All three recipes exist and the field guide has `GuideBook.PAGE_COUNT` (30) pages |
| `farmLesson` | A recording with 2 hoe tills, 2 plantings and 2 mature harvests gives a farm lesson (wheat) and no mining lesson or blueprint |
| `replicantMimic` | A replicant mimicking a build takes back at most 3 placed blocks, one item each back into the echo; the echo rebuilds, the result is exact (wrong=0), with no extra items and no ground drops |
| `workPressure` | 60 work actions in one chunk write 3 BUILD imprints and add pressure (+10 with the defaults) |
| `misfire` | In an overloaded chunk the build misfires (skips and a wrong block), the wrong block is fixed later, the result is exact, items are conserved and the label shows the band |
| `fractureStop` | In a fractured chunk the job stops with "Stopped: memory fracture" and items are conserved |
| `farming` | The farm job harvests 10 mature wheat, replants all 20 plots from its own seeds, puts the wheat in the chest, with no ground drops |
| `striderShadow` | An echo strider trails a working echo (distance 8.6→~3), the notice shows and the echo is not hurt |
| `archivistSteal` | The archivist steals one non-tool stack (8 cobblestone), the hoe stays, the label shows it, and the loot drops on the archivist's death |
| `mobAttack` | A husk targets the working echo; after a hit the echo stops ("Stopped: attacked"), flees 8 blocks, resumes after the calm delay, and never attacks back |
| `lensCommands` | Orders need a raised lens; stay, follow (and giving up when the owner is too far) and return-to-point work; a stranger is refused |
| `navigation` | The echo wades shallow water, climbs a 4-block ladder, and opens and closes a wooden door and a fence gate behind itself |
| `persistence` | After an NBT reload the order, the farm lesson, max health 40 and the upgrade levels are kept |

The last line of `mnemolith echo3qa` must be `Echo stage 3 check: 13 of 13`. `qa` 19/19, `echoqa` 11/11 and `jobqa` 12/12 stay green.

## Memory graft QA

`/mnemolith graftqa` (gamemaster) checks memory grafts on a dedicated server with a fake-player owner, two echoes, a pig, a husk and an archivist in two cleared chunks next to the command source. The last line must be `Memory graft check: 12 of 12`.

| Flag | What is proved |
| --- | --- |
| `rules` | A silence slip grafts *hushed* with 12 charges, a second tops it up to 24/24, a third is refused as full (slip kept), a path slip is refused as too faint (slip kept) |
| `hush` | The hushed echo swallows its own work imprint and a neighbour's within the aura (1 charge each); out of the aura the neighbour writes BUILD |
| `replace` | A fire slip replaces the hush with *kindled* (32); the old silence graft is written back into the chunk |
| `kindled` | Two iron ores broken by the kindled echo and by a neighbour in its aura both drop iron ingots (no raw iron), 2 charges spent; the kindled echo is fire immune and lava-proof and its work writes FIRE |
| `unpick` | The needle unpicks the graft into one fire slip; a graft worn below half a slip is spent (no slip, no imprint) |
| `volatileBurst` | *Volatile* digs at ×0.55, pays 1 per dug block, and its body death blasts a pig next to it without breaking the floor |
| `plunge` | A *plunging* echo dropped from 8 blocks lands without damage, pays 1 charge; its max drop is 12 |
| `graveDecoy` | An idle *grave* echo draws a husk's target; a hit costs 1 charge and it does not flee |
| `fractureReject` | Spiking the chunk to fracture rejects the graft: in a fractured chunk it condenses into a residual echo (or goes into the chunk, or drops as a slip where muted) |
| `archivistSteal` | The archivist steals the graft (as a death slip) before the echo's cobblestone and drops it on death |
| `possession` | Possessing a volatile echo carries the graft, gives Haste II, spends 1 charge per 200 ticks; the returned body has the remaining charge and the effect is gone |
| `persistence` | The graft (temper, charge, imprint) survives an NBT reload |

Manual (client):

- [x] Right-click your echo with a death slip: purple motes, the label over its head reads «Прививка: Могильный · 24/48», the echo inventory screen shows the same line. One death slip is 24 of a two-slip graft (48). A silence slip is the 12 then 24/24 case; 24/24 on a single death slip was a checklist error.
- [x] A volatile echo flickers red; a kindled echo stands in lava unhurt.
- [x] With the needle on your grafted echo you get the slip back; a stranger's echo refuses.
- [x] Field guide pages «Запись и вселение» and «Прививки памяти» render with pictures in RU and EN.

## Residual echo QA

`/mnemolith residueqa` (gamemaster) checks residual echoes on a dedicated server with a fake-player owner, one echo and an archivist in three cleared chunks next to the command source. The last line must be `Residual echo QA: 18 of 18`. The live game tests above cover formation, lash, sneaking, lens reading, the needle, festers and the observatory seed with real players; this command keeps the rest. The fake player is not in the level's player list, so the lash and lens checks hand it to the same `ResidueEntity.sense` code the tick uses (the lens check runs only that sensing step each tick, since a full tick would first sense the empty player list and decay the reading); festers are called directly instead of waiting 60 seconds. The archivist check is not scripted: the archivist is ticked and walks to the residue on its own.

| Flag | What is proved |
| --- | --- |
| `form` | An overloaded chunk with death and fire condenses the death imprint (the louder one) into a residue: the imprint is gone, fire stays, pressure drops; a second residue is refused in the same column |
| `faintNoForm` | An overloaded chunk holding only path and build condenses nothing |
| `festerWrites` | In a loud chunk a fester writes the residue's tag back |
| `muteStarves` | Under a mute stone a fester costs 1 strength and writes nothing |
| `calmFades` | In a calm chunk a strength-1 residue dissolves; an old one holds its strength |
| `lash` | An unread fire residue sets a survival player within 3 blocks on fire |
| `lensRead` | The fake player raises the lens (`startUsingItem`) and looks at the residue while it ticks; within 80 ticks it is pinned and the tag is discovered |
| `needleSlips` | The needle on an unread residue slips: it stays, the player is blinded (silence lash), the needle takes no wear |
| `needleCaptures` | The needle on a pinned residue gives a residual shard with the same tag and strength (not an imprint slip), costs twice the extraction wear, removes the residue |
| `shardGraft` | Right-clicking the echo with a strength-4 explosion shard grafts *volatile* at full capacity (96) and consumes the shard |
| `shardRelease` | A shard used on a block releases a residue with its tag and strength |
| `echoDrinks` | A kindled echo within 6 blocks drinks a fire residue on its fester: +half a slip of charges, residue strength −1 |
| `graftCondenses` | A full volatile graft released in an overloaded chunk becomes a strength-6 residue (result `residue`) |
| `archivist` | An archivist walks to an unread residue on its own, archives it as a shard and drops it on death |
| `possessionAbsorb` | A possessed player right-clicks a fall residue with an empty hand: the body wears *plunging* at full capacity and the returned echo keeps it |
| `observatorySeed` | An observatory chunk seeds one old strength-5 residue beside its reel, once |
| `actOut` | A death residue festering in a fracture writes death and raises one zombie; a fire residue on short grass leaves a fire block (air above grass would be removed the same tick) |
| `persistence` | The residue (tag, strength, old, origin) survives an NBT reload; `ChunkMemory` round-trips `residue_seeded`, and a save without the field loads as unseeded |

`qa` 19/19, `echoqa` 11/11, `jobqa` 12/12, `echo3qa` 13/13, `graftqa` 12/12 and `mpsmoke` stay green (`graftqa` `fractureReject` now accepts the graft condensing into a residue, which is what a fracture does with it).

Manual (client):

- [ ] Overload a chunk and stand in it: within a minute or two a faint fragment in a temper color drifts out; holding the lens makes it clear.
- [x] Raise the lens on it: the label reads «Осадок · Огонь · сила 3» and «чтение N%»; after 3 s it glows and «прочитан — игла возьмёт».
- [x] Needle on it before reading: you are lashed and nothing is taken. After reading: «Осколок осадка» with tooltip «Осадок: … · сила N».
- [x] Right-click your echo with the shard: the graft line shows the full capacity. Use a shard on the ground: the residue comes back.
- [x] Visit an observatory: an old residue floats by the reel.
- [x] Field guide page «Осадки памяти» renders with its picture in RU and EN.

## Echo relay and archive vault QA

`/mnemolith relayqa` (gamemaster) checks the echo relay and the archive vault on a dedicated server, in cleared chunks next to the command source, with a fake-player owner. The last line must be `Relay QA: 19 of 19`. Everything the pass builds (echoes, vaults, drops, an archivist) is removed. The notes also log `breakUs` (one mirrored break) and `drawUs` (one vault draw).

| Flag | What is proved |
| --- | --- |
| `threadLinks` | A real relay thread on one echo remembers it; on a second echo 48 blocks away it refuses (`relayLinkRange` 16) and keeps the thread; within range it links both (one shared id) and uses one thread |
| `cut` | Sneak-clicking with the thread cuts both ends |
| `auraConduit` | Unlinked, an echo's work writes a build imprint; linked to a hushed echo 54 blocks away, the same work is swallowed and the far end pays one charge |
| `residueDrink` | A silence residue next to the unlinked-temper end is drunk for its hushed far partner (`Residues.stormWave` → `FED`, the far charge rises) |
| `noisy` | A fracture under one end: the conduit stops (work writes again, the far charge is untouched); after the chunk calms, the link carries again |
| `deathShock` | One end dies: the other loses 4 health, a DEATH imprint is under it, and it is no longer linked |
| `hop` | Possessing one end, sneak + return key moves the owner into the other end (at its position; that entity is taken over); the left body is the new partner, standing where the owner was; a second hop at once is refused by the cooldown |
| `mirror` | While possessing, a break beside the owner is repeated beside the partner, a place is repeated with a block from the partner's inventory, and a target out of the partner's reach is refused (`FAR`) |
| `vaultDraws` | A vault draws the loudest imprint (death before fire) out of its chunk, then the next, then finds nothing; the drawing state is on the block |
| `bleedLoad` | Filling it raises the chunk's `vault_load` to `ceil(sum × vaultBleed)` and the chunk's pressure by exactly that much |
| `rupture` | In a fractured chunk the vault's own tick spills half (loudest first) back into the chunk |
| `explosionSpill` | An explosion at a filled vault breaks it and spills everything into its chunk |
| `needleExtract` | The extraction needle used on the vault (real `useOn`) gives the loudest imprint as a slip and leaves the rest |
| `discharge` | Under a mute stone discharging is refused and nothing is lost; without it, all three are written into the chunk and the load drops to 0 |
| `echoFeed` | An idle vault feeds a hushed echo within 4 blocks one silence imprint (charge rises), and keeps the death imprint |
| `carryLeak` | A carried vault item with two imprints leaks one where the carrier stands |
| `archivistRaid` | An archivist next to a filled vault finds it (`raidTarget`), takes the loudest as its loot, and with full hands takes nothing more |
| `itemKeepsContents` | Breaking the vault with a pickaxe (`gameMode.destroyBlock`, the real loot table) drops a vault item with the three imprints; placing that item back restores them and the chunk's load |
| `persistence` | The vault's contents and an echo's link survive a save and reload |

Manual (client):

- [ ] Craft a relay thread (string, echo slip, copper ingot) and an archive vault (4 stratum, 4 amethyst, needle in the middle); the tooltips show purpose and source.
- [x] Right-click one echo with the thread: «Первый конец завязан…», the thread tooltip says so; right-click another echo within 16 blocks: «Связаны…»; a faint lavender-blue thread of motes between them (`MemoryPalette.SHIMMER`, `#6E7CC4`).
- [x] Right-click an echo you own: its status line ends with «Связь: есть, N м».
- [x] Possess one end, sneak and press `V`: «Вы переходите по нити…», you are in the other; the HUD hint mentions the hop. Break a block: the other end breaks the same spot beside itself.
- [x] Place the vault, right-click with an empty hand: it lights up (pink drawers) and the action bar shows «втягивает, 0/12»; wait: the count rises and the area's imprints disappear from the lens.
- [x] Break it with a pickaxe: the item tooltip shows «Хранит отпечатков: N»; carry it for a minute: «Хранилище у вас в руках протекает…».
- [x] Field guide pages «Связь отголосков» and «Архивное хранилище» render with pictures in RU and EN.

## Recollection storm QA

`/mnemolith stormqa` (gamemaster) checks recollection storms and the Scar on a dedicated server, in cleared chunks next to the command source, with a fake-player owner and one echo. The last line must be `Recollection storm QA: 20 of 20`. Storms already running are set aside and put back afterwards; natural rolls are paused for the pass; every storm, site, block and entity the pass makes is removed. Storms are stepped tick by tick (`Storms.step`) instead of waiting in real time. The notes also log `gatherTickNs` (the average gathering tick) and `firstWaveUs` (the tick that breaks the storm and runs its first wave).

| Flag | What is proved |
| --- | --- |
| `gates` | A calm chunk is `CALM`, a fractured one `OK`, a mute stone makes it `MUTED`, scar glass nearby `WARDED`; with one storm running the cap (default 1) refuses another |
| `shardCalls` | A shard used through `ResidualShardItem` on a fractured chunk calls a gathering storm that adopts the freed residue; with that storm running, another call is refused by the cap |
| `muteContains` | A mute stone placed in the centre chunk while it gathers ends the storm `CONTAINED`; the freed residue loses its storm flag |
| `waveCondenses` | The first wave turns the area's loud graftable imprints (one of them in a neighbouring chunk) into three storm residues and removes those imprints |
| `hushSwallows` | A hushed echo within 8 blocks swallows a storm residue's act-out for one charge |
| `chokeStarves` | A muted centre: the wave condenses nothing, its residues starve (strength −1 each), and it costs two waves |
| `spent` | Nothing standing and nothing to condense: the storm ends `SPENT` |
| `passed` | Two survivors after the last wave: `PASSED`, and they stay as ordinary residues |
| `scarForms` | Three survivors merge: `SCAR`, the chunk is a Scar site, heart and a glass ring (with mobGriefing), one Scar entity, the site now wards storms |
| `griefingOff` | With mobGriefing off the site still forms, but no heart and no glass are placed |
| `scarNeedsReading` | An unread Scar takes no damage; a lens reading (the fake player's gaze through `ScarEntity.sense`) pins it after 60 ticks and it can then be hurt |
| `scarRecall` | Its recall reaches the player within 10 blocks and lashes (silence: blinded) |
| `graveDecoy` | A grave-grafted echo within 16 blocks draws the recall onto itself for one charge; the owner is not hit |
| `scarDrops` | Death drops one scar fragment and a strength-4 shard per merged temper; six merged would drop two fragments |
| `fragmentSetsEcho` | A scar fragment on your echo scar-sets it: graft capacity 64→96, a second fragment is refused, a fracture keeps the graft, and the flag saves |
| `wardBlocks` | Placed scar glass wards: the gate is `WARDED`, and imprints are still written there |
| `siteReseeds` | A player in a Scar site chunk makes the pulse seed one strength-5 residue of a merged temper; not again the same day; not under a mute stone |
| `heartHeals` | Breaking the heart heals the site (no longer a scar chunk) |
| `persistence` | The storm (`StormData` codec), the chunk's scar fields, the Scar entity and a storm residue's flag survive a save and reload |
| `fadesUnwatched` | A storm whose centre chunk is not loaded holds for the unwatched limit, then ends `FADED` (cap free, centre remembered, nothing loaded); the faded centre waits until its chunk loads; over a loaded fracture the settle halves the area's instability and leaves the fracture band; `unwatched` and the faded entry survive the codecs |

Manual (client):

- [x] Stand in a fractured chunk for a minute or two (or free a shard there): «Собирается буря воспоминаний…», the bar «Буря воспоминаний: собирается», the sky darkens, the edge of the 3×3 area flickers.
- [x] Place a mute stone in the centre chunk while it gathers: «Буря воспоминаний сдержана.» and the bar goes.
- [x] Let one rage: every 10 s storm residues appear and act out (zombie, fire, small blast with mobGriefing, lift, darkness); the bar counts the storm residues.
- [x] Let three or more survive: the Scar rises, violet and cracked, its tint drifting through the merged tempers; heart and scar glass around it (mobGriefing on).
- [x] Hit it unread: nothing. Raise the lens for 3 s: «Прочитано: Шрам скован…», it slows and can be hurt for 8 s. Step out of 10 blocks to dodge a recall.
- [ ] Hear the charge before each recall.
- [x] Kill it: scar fragment(s), shards and xp. Use a fragment on your echo: «закалён Шрамом», the graft line shows 3 slips' worth.
- [x] Come back the next in-game day: an old residue floats by the heart. Mine scar glass with a pickaxe and place it by a fracture: no storm gathers there.
- [ ] Start a storm, then go far enough that its centre unloads and stay away for `stormUnwatchedTicks` (2 minutes): the log says `end=FADED`, and a storm can gather in another fracture at once. Come back: «Здесь прошла буря воспоминаний без свидетелей. В округе стало тише.», and the lens pill shows the area out of the fracture band.
- [x] Field guide pages «Бури воспоминаний» and «Шрам» render with pictures in RU and EN.

## Echo guard QA

`/mnemolith guardqa` (gamemaster) checks the guard job ([design note](design/echo-guard.md)) on a dedicated server in a cleared chunk next to the command source, with a fake-player owner. The last line must be `guardqa: 24 of 24`. Stage 2 and stage 3 flags are in the next tables ([stage 2](design/echo-guard-2.md), [stage 3](design/echo-guard-3.md)). Mobs in the fight checks have no AI and their hurt cooldown is cleared between steps, because the suite steps the job itself instead of waiting for the server tick.

| Flag | What is proved |
| --- | --- |
| `lesson` | A real recorder session with two hits on a zombie, one on a cow and a kill teaches a guard lesson (2 hits, 1 kill, foes = zombie); one zombie hit plus cow hits teaches nothing |
| `filter` | Zombies and skeletons are foes; a villager, a cow, a tamed wolf, a creeper, the owner, another echo and a calm zombified piglin are not; the piglin becomes one once it targets the echo |
| `damage` | Iron sword 4.5 (6 × 0.75); volatile 6.75; hushed 6.75 on an unaware mob, 4.5 on one that targets the echo; diamond more than iron; a stick or dirt is no weapon |
| `refusals` | No lesson: `NO_LESSON`; lesson but only sticks: stop «нет оружия» (`NO_TOOL`, detail `weapon`) |
| `fight` | A zombie two blocks from the post dies by the guard; `defeated` is 1 and the job is still `GUARD` |
| `bystanders` | The villager, the cow, the tamed wolf and the owner nearby keep full health |
| `durability` | The sword lost exactly one point per landed hit |
| `leash` | Post radius 8; a zombie 13 blocks out is ignored, and hitting the echo from there does not pull it off its post; a zombie 6 blocks out is picked |
| `fightBack` | A zombie hits the guard: no alarm (no flight), the zombie becomes its target |
| `graveTaunt` | A grave guard striking a zombie that chases a villager turns the zombie onto the echo |
| `volatileSpends` | A volatile guard spends one charge per landed hit |
| `persistence` | The job save (mode, lesson, kills) and an echo-home snapshot keep the guard lesson through the codecs |

Stage 2 flags:

| Flag | What is proved |
| --- | --- |
| `noArrows` | A bow with no arrows and no melee weapon: stop «нет стрел» (`NO_TOOL`, detail `arrows`) |
| `bowShot` | A pure archer shoots a zombie 7 blocks out at once: one shot, 4 of 5 arrows left, the bow worn by 1, one arrow in flight that can be picked up and is not on fire, base damage 2; shot ticks 35 for a bow, 49 for a crossbow |
| `lineOfFire` | A villager between the archer and the zombie blocks the line of fire and no arrow flies for 80 ticks; with the villager back in place the line is clear |
| `arrowGuard` | The impact of an echo arrow on a villager, the owner, a tamed wolf or a cow is canceled (it flies through); on the zombie it is not |
| `shieldRaised` | A guard with a sword and a shield moves the shield to its off hand and raises it (uses it from the off hand) while a zombie is near; with nothing to fight it lowers it |
| `escort` | "Guard me": the post is the owner's spot, moves when they move, radius 5, status `guard_escort` |
| `escortWaits` | The owner 40 blocks away: status `guard_waiting`, the post stays where they were last seen |

Stage 3 flags:

| Flag | What is proved |
| --- | --- |
| `provoke` | A zombie with no AI that targets a villager turns to the guard once the guard's hit lands (the explicit hook, not vanilla's hurt-by goal) |
| `underFire` | A skeleton 14 blocks out (outside the leash) hurts the guard: no chase, the guard faces it (yaw −90) with the shield raised and stays on post; with no new hits, after 3 s it lets go and lowers the shield |
| `armorEquip` | Out of a leather and a diamond chestplate, an iron helmet, iron boots, an elytra and a carved pumpkin, with bound leather boots worn: diamond chest and iron helmet go on, the bound boots stay, legs stay empty, nothing is lost (7 items), a second pass moves nothing |
| `armorProtects` | 11 armor: a 10-damage zombie hit costs 7.2 health; the chestplate and the helmet both lose durability |
| `armorDrops` | The echo dies: the diamond chestplate and the iron helmet lie on the ground next to it |

Manual (client):

- [x] `/mnemolith echodemo guard`, then `/summon zombie` and `/summon skeleton` inside the radius: «Охрана: бой — Зомби · 0», the echo walks over and swings the sword; back on post afterwards with the kill count.
- [x] A mob outside the radius (12 blocks) is left alone; teleported to 6 blocks it is attacked.
- [x] A mob that runs out of the leash is let go and the echo walks back: a husk under attack teleported 16 blocks from the post keeps its wounds (11 HP), the echo is back on post («Охрана: на посту»).
- [x] The sword loses durability (`minecraft:damage` on the echo's sword grows with each hit).
- [x] `/mnemolith echodemo worn`: the first hit breaks the sword and the echo screen shows «Стоп: инструмент сломался»; pressing «Охрана» with an empty inventory shows «Стоп: нет оружия».
- [x] Grave: a zombie turns to the echo and charges drop. Volatile: harder hits (6.53 vs 4.43 on a helmeted zombie), one charge per hit. Hushed: the first strike on an unaware zombie is 1.5× and spends a charge; later hits are normal.
- [x] Villagers, cows and a tamed wolf inside the radius are never hit; creepers are left alone.
- [x] The guard dies to a zombie hit (`/damage … by` a zombie): the usual echo death, its sword drops where it fell.
- [x] Echo screen: the «Охрана» button, its tooltips, the lesson line; the field guide page «Отголосок на охране» renders in RU.
- [x] The field guide page renders in EN ("Echo guard", with the stage 2 paragraph after scrolling).

Manual stage 2 (client):

- [x] `/mnemolith echodemo archer` with a husk 7 blocks away and a villager in the line: «Охрана: бой — Кадавр · 0», no shot while the villager stands there; after it steps aside the archer kills the husk (6 arrows, bow worn by 6), the villager keeps 20 HP.
- [x] An arrow summoned with the echo as owner flies through a villager (20 HP, the arrow lands behind it); the same arrow without an owner hits it (14 HP).
- [x] The archer runs out of arrows: "Stopped: no arrows" («Стоп: нет стрел»).
- [x] A guard with a shield raises it while fighting (visible pose); a 5-damage `/damage … by` a husk in front while it is up does nothing ("invulnerable") and the shield shows wear (damage 6).
- [x] `/mnemolith echodemo escort`: «Охрана: рядом с вами · 0»; walking 17 blocks the echo stays within about 3 blocks; it kills a husk that comes at you.
- [x] Echo screen: the ☺ button next to «Охрана» with the "Guard me" tooltip (EN).
- [x] An axe hit disables the guard's shield and it stays down for 5 s. A vindicator with an iron axe against the armored guard: the server log shows `echo shield disabled … by=minecraft:vindicator ticks=100` (it was never disabled before stage 3: vanilla does this only for players).
- [x] A skeleton shooting a grave guard: the guard runs at it while it is inside the leash; from beyond the leash it stays on post with its shield turned to the skeleton (shield worn to 10, arrows stuck around it, health back to 20). Charges dropped from 24 to 16, one for each arrow that got through.

Manual stage 3 (client):

- [x] `/mnemolith echodemo armored`: within 2 s the echo wears the iron set (not the leather chestplate), it renders on the model, and WTHIT shows the armor bar.
- [x] A husk against the armored guard with a shield: the husk walks up and lands a hit (red flash, every armor piece loses 1 durability), the guard kills it.
- [x] A vindicator with an iron axe kills the armored guard in about 3 s; the iron set, the shield, the sword and the spare leather chestplate all drop where it fell.
- [x] The field guide page «Отголосок на охране» shows the stage 3 paragraph (RU).
- [x] Echo screen: the four armor slots hold the worn iron set with durability bars, the worn shield sits in the off-hand slot, and the spare leather chestplate stays in the bag.

### Manual stage 3 checks (client)

`/mnemolith echodemo <scene>` (operator) builds a scene about 6 blocks south of you and runs your own echo in it.

- [x] `farm`: raise the lens on the echo. The label reads «Ферма: Пшеница · N», later «Ферма: ждёт урожая (Пшеница) · 10»; the wheat is in the chest.
- [x] `overload`: the label shows «Память: Перегрузка» as a second line above the head, and now and then «Сбой: пропустил блок» / «Сбой: не те семена» in orange. No items are lost.
- [x] Lens hint: with the lens raised on your echo, three chips «ЛКМ — вселиться», «Z — стой · R — за мной · B — к точке», «Отголосок Dev · N м» sit above the pressure pill without overlapping; with an action-bar message both lift.
- [x] `Z`, `R`, `B` on your own echo: «Стоит по команде», «Идёт за вами», «Возвращается к точке». On someone else's echo nothing happens.
- [ ] `door`, `gate`, `ladder`: the echo passes the wooden door or gate and closes it behind itself, or climbs the ladder over the wall.
- [ ] A zombie or husk near a working echo attacks it: «Стоп: атакован» in orange, the echo runs, then resumes.
- [ ] Craft the three upgrade slips in the 2×2 grid; the tooltip shows «Впитано: N из M»; RMB absorbs, a slip beyond the limit stays in hand.

## Fracture feel (client)

`/mnemolith qa` still proves the server side of fracture: the `bands` flag spikes a column to 80, sets the fractured flag, and asks for a replicant. That column is not under your feet, and the command does not draw pixels. The feel check is a client standing in the chunk.

`/mnemolith inspect` prints the server band for the chunk you are in. Reach overloaded (a death and a creeper, or any mix `/mnemolith inspect` reports as Overloaded), then fracture (a failed compose once pressure is at least 62 with the defaults, or enough loud imprints to reach 80). The server log shows `Mnemolith fracture` the first time, and `Mnemolith fx pressure` when the band rises into overloaded or fracture. That rise also plays `pressure_warn` for nearby players. It does not repeat while you simply stand there.

With the lens put away, the three client toggles at their defaults, and `gameplay.allowAmbientPressure` at its default (false), so the client runs on the band-only snapshot:

- [ ] Overloaded: the screen edge darkens (ink) and the camera trembles. The world is not desaturated yet.
- [ ] Fracture, standing in that chunk: the edge goes crimson and breathes, the tremble is stronger, and the world desaturates with a thin color fringe. One chunk away is weaker. Two chunks away is a hint. Beyond the lens snapshot there is nothing, because the client never receives those chunks.
- [ ] The lens pill is absent until you hold a chronicle lens. The feel stays.
- [ ] Standing in a fractured chunk, pick up and put away the lens a few times. The feel does not blink. The pill appears only while the lens is held and shows the full reading (number, sneak state) within one round trip.
- [ ] Without the lens, and with `visuals.ambientWithoutLens` on, a saturated chunk does not shimmer (band-only carries no saturated chunks). Set `gameplay.allowAmbientPressure = true` and it shimmers.
- [ ] Without the lens, standing on an archival vein next to a fracture three chunks away: no feel from that chunk (the band-only radius is 2 and does not grow for strata).
- [ ] Set `visuals.pressureVignette`, `visuals.stormScreenShake`, and `visuals.fractureFringe` to false in `mnemolith-client.toml` or on the config screen. Each one stops on the next frame. All three off, with no lens and with ambient shimmer off, stops the extra pressure poll.
- [ ] The client log from `Mnemolith client setup` includes `Mnemolith fracture feel vignette=true shake=true fringe=true` when the defaults are on. A dedicated server log does not contain that line.
- [ ] If `post_effect/fracture.json` fails to load, one warning names `mnemolith:fracture` and the vignette and shake still run.

## Art pass (client)

Offline, `python tools/art/validate_assets.py --vanilla <extracted client assets>` must report 0 errors and `python tools/art/build.py --check` 0 differences (PNG equality is the pixels). The rest needs a physical client.

- [ ] Client log after resource load: no `Missing texture`, `Unable to load model`, `Unable to resolve texture` or `missing model` line for `mnemolith:`.
- [ ] Creative tab at GUI scale 2 and 3: every item and block icon reads as its object; no magenta-black tile; the lens and needle show the flat sprite in the inventory, as a dropped item, in an item frame and on a shelf.
- [ ] Hold the chronicle lens and the extraction needle in first and third person, both hands: the 3D models sit in the grip like vanilla handheld items (compare with an iron sword), the lens glass is see-through.
- [ ] Place mute stone, composition reel, resonator trap, archive vault (idle and drawing), archival stratum, scar heart, scar glass: models are lit correctly from all sides, no z-fighting, the vault drawer labels light up while drawing, scar glass stays translucent.
- [ ] Known, not fixed here: the non-full models (mute stone, reel, trap, stratum) keep full occlusion, so a neighbour's hidden face can show as a gap at their base.
- [ ] Archivist, echo strider, moment replicant, residue (with the lens and without), the Scar, an echo and a grafted echo: textures sit on the right boxes at 128×128, no seams or stretched faces.
- [ ] Particles (extract, compose ok/fail, mute haze, strider trail, graft motes): shapes are crisp and still tinted.
- [ ] Field guide, all 30 pages in EN and RU, including the living-memory pages: pictures fill the art box, no blur (nearest filtering), no stretched aspect.

## GUI contrast

The archival panel chrome is ink `#1C244A`. Catalog, lens, and reel glyphs are bone or a light accent. The drop shadow is drawn one pixel down-right by `GuiArt.label` and `GuiArt.paragraph`. The font's own shadow flag stays off.

The field-guide page is the bone center of that panel (`#E6DCC8`). Title, page number, and body sit on that page, so they use near-black ink and a light shadow. Catalog, lens, and reel do not use those two constants.

| Constant | Hex | Role |
| --- | --- | --- |
| `GuiArt.BONE` | `#E6DCC8` | Catalog title and rows, lens pill, reel title, inventory label, idle compose status |
| `GuiArt.SHADOW` | `#070B18` | Shadow for those bone glyphs. Darker than the panel, not `#000000`, not the glyph color |
| `GuiArt.INK` | `#1C244A` | Panel chrome and the lens pill fill. Not a glyph color |
| `GuiArt.VERDIGRIS` | `#8ED9C8` | Success status and the unread-formula count |
| `GuiArt.FAIL` | `#FFB089` | Fail and disabled status |
| `GuiArt.CHIP` | `#101628` | Unread formula chip, darker than the panel, with bone text |
| `GuiArt.GUIDE_INK` | `#1A1520` | Field-guide title, page number, and body |
| `GuiArt.GUIDE_SHADOW` | `#F5F0E6` | Shadow under that guide text. Light bone, not the glyph, not `#070B18` |
| `GuiArt.GUIDE_DOT` | `#6B6258` | Inactive field-guide page dot on the bone page |

A physical client logs `Mnemolith gui contrast glyph=ffe6dcc8 shadow=ff070b18 panel=ff1c244a accent=ff8ed9c8 fail=ffffb089` from `MnemolithClient`. That class is not loaded on a dedicated server.

## Memory Hollows QA

`/mnemolith hollowsqa` (gamemaster) checks stage 1 of Memory Hollows ([design note](design/memory-hollows.md)). `/mnemolith hollows survey [radius]` samples the overworld biome source every 64 blocks and prints the share of hollows among all samples and among host land plus the nearest hit; `/mnemolith hollows flicker [scene]` sends one flicker 6 blocks ahead; it carries an imprint of that chunk (so it can be caught) and, without a scene, shows that imprint's scene.

| Flag | What is proved |
| --- | --- |
| `biome`, `region`, `hosts` | The biome is registered, the `biome_region` modifier registered its region, the host tag has its 11 biomes |
| `possibleOverworld`, `possibleNether` | A fresh overworld multi-noise source lists the biome among its possible biomes; a nether source does not |
| `windowReplaces`, `outsideStays`, `nonHostStays` | A host biome inside the climate window becomes Memory Hollows; outside the window, or for a non-host biome, vanilla's pick stays |
| `ground`, `ore` | The ground pass turns grass into faded turf and stone near the surface into hollowstone, and seeds recollite clusters inside the square |
| `remnant`, `sink` | A remnant stands on sturdy ground and refuses water; a sink carves a dish on flat ground and refuses slopes |
| `turfHoldsFlower` | A forget-me-not survives on faded turf |
| `oreDrops`, `turfDrops` | Ore drops 1–2 shards without silk touch and itself with it; turf drops dirt, or itself with silk touch |
| `flickerScenes`, `flickerPick`, `densityOff` | Tags map to the five scenes; a spot is picked only in the biome and in a loaded chunk; density 0 sends nothing |
| `recipes`, `advancements` | Brick, stonecutting, smelting, blasting, block and dye recipes and the two advancements load |
| `crystalTag` | The shard is a memory crystal; every Mnemolith ingredient that takes an amethyst shard also takes a recollite shard |
| `guidePage` | The guide has the `hollows` page and `PAGE_COUNT` matches |

Manual (fresh normal world, seed 424242, 2026-10-10):

- [x] `/mnemolith hollows survey 4096` after tuning: 0.69% of all samples, 1.62% of host land; `/locate biome mnemolith:memory_hollows` finds one 706 blocks from spawn. The first window (weirdness 0.05..0.3, erosion 0.1..0.45) gave 7.4% of host land, too common.
- [x] Fly there: pale turf with ragged 4-block biome edges, no chunk-square seams, vanilla terrain and rivers unchanged around it.
- [x] Remnant pillars and an arch of hollowstone brick, sinks, fallen birches, forget-me-nots.
- [x] Clearing hollowstone out of a 13×27×13 box leaves recollite ore floating (6 blocks there, 17 in a 31×31 box); stone is hollowstone down to about 18 blocks under the surface.
- [x] A flicker walks toward the player and fades; natural ones show up in the log at about one per 8 s with density 1.
- [x] All new blocks placed, including a double slab (its blockstate first pointed to a missing model; fixed).
- [x] JEI: shard from the block, smelting and blasting, the info page for ore and shard; hollowstone works as stone in vanilla recipes; bricks into stairs, slab and wall, plus stonecutting. RU and EN.
- [x] Field guide page «Лощины памяти» / "Memory Hollows" with its picture, RU and EN. The advancement «Там, где стёрся цвет» is granted on entering the biome.
- [x] MSPT with density 0, 1 and 4: no difference (see [performance.md](performance.md#memory-hollows)).

## Memory Hollows stage 2 QA

`/mnemolith hollows2qa` (gamemaster) checks stage 2 ([design note](design/memory-hollows-2.md)).

| Flag | What is proved |
| --- | --- |
| `structure`, `hollowsOnly`, `spacing` | `mnemolith:sunken_archive` is registered, its biome set is exactly Memory Hollows, its set is a random spread 8/3 |
| `template` | The template is 14 high with two chests, and no block in it is in `#base_stone_overworld` or `#stone_ore_replaceables` (ores would eat it) |
| `loot` | 30 rolls of `chests/sunken_archive` all give items, most give recollite shards |
| `needleCatch`, `catchOnce` | The needle's own use with a lens in the off hand catches a path flicker: one path slip, the path imprint leaves the chunk, the trade one stays; the same flicker cannot be caught again |
| `faint`, `gone` | A tagless flicker is too faint; a flicker whose imprint already left slips away and gives nothing |
| `reachLens`, `reachRecollite`, `noLens` | At 7 blocks a chronicle lens misses and a recollite lens catches; without a lens nothing is caught |
| `lensRecipe`, `lensIsLens` | The recollite lens recipe matches and makes the lens; it counts as a held chronicle lens |
| `earlyAmethyst` | The chronicle lens, extraction needle and field guide take an amethyst shard and none of their slots is recollite-only |
| `audio` | Four sounds registered; the biome has its music and ambience attributes |
| `advancements`, `guidePage` | The three advancements load; the `sunken` page sits right after `hollows` |

Manual (fresh normal world, seed 424242, 2026-10-10, screenshots in `playtest-shots/feature-hollows-2/`):

- [x] `/locate structure mnemolith:sunken_archive` from spawn: `[-704, ~, 32]`, 704 blocks away (with the first 12/4 spacing it was 1 152 blocks off and most patches had none).
- [x] The archive: roof with its hole, broken belfry over the west doorway and clerestory stumps above the turf; inside the stair behind its parapet, shelf rows, the apse with lectern, chest and recollite plinth. Dry, not flooded. The first build lost wall and floor blocks to dirt and andesite ore blobs; fixed by dropping plain hollowstone from the template.
- [x] Chest loot: an imprint slip, forget-me-nots and an extraction needle.
- [x] A flicker (`/mnemolith hollows flicker` over 4 path imprints) caught with the needle in the main hand and a chronicle lens in the off hand: it bursts into shimmer, «Вспышка поймана: Путь», a path slip in the hotbar, the *Hold That Thought* goal toast; the chunk's calm reading drops by one.
- [x] Recollite lens: tooltip with its hollows line, JEI crafting recipe, *Sharper Recollection* on pickup.
- [x] Field guide page «Погребённые архивы» / "Sunken Archives" (36/38) with its picture, RU and EN; the reference page is still 38/38 and fits.
- [ ] Music and ambience by ear: the Xvfb client has no audio device (sound engine off). The sounds are registered, the OGGs decode (48 s, 1.8 s, 3 s, 0.9 s) and no missing-sound warnings appear.

## Memory Hollows stage 3 QA

`/mnemolith hollows3qa` (gamemaster) checks stage 3 ([design note](design/memory-hollows-3.md)).

| Flag | What is proved |
| --- | --- |
| `fadedType`, `fadedLoot` | `mnemolith:faded` is a registered monster, not allowed in peaceful; `entities/faded` loads |
| `spawnHollowsOnly`, `spawnCap` | `MobSpawns.fadedSpotOk` refuses a plains spot and accepts it once `fillbiome` makes it Memory Hollows, but not 2 blocks under the ground (still the biome); `FadedSpawner.trySpawnAt` places three faded at surface spots and refuses a fourth (the spot is closed), removing them opens it again |
| `mimic`, `provoke` | A trade flicker 5 blocks away makes an idle faded copy the tag and the spot; a catch nearby sets the catcher as its target and drops the copy |
| `damageScale` | A 4-damage player hit takes 2 health unseen and 6 once revealed |
| `lensReveal` | A raised lens looking at a faded 6 blocks away sees it; lowered, or turned away, it does not |
| `slipDrop` | 12 player kills of a faded that copied fire: some but not all drop a fire slip, no other slip drops |
| `lecternPlays`, `lecternCooldown`, `lecternHoldings` | A slip on a lectern plays (replay counted, nothing catchable even with a recollite lens, slip kept); within the cooldown it refuses; a non-lectern or a non-slip does nothing; the chunk reading counts tags ("×2") |
| `needleUnaided`, `needleReach` | The recollite needle's own use catches a path flicker at 6 blocks with no lens (reach 7); at 8 blocks it misses, and catches with a recollite lens in the off hand; the plain needle without a lens has no reach |
| `needleRecipe`, `needleItem` | The plus recipe around the extraction needle makes the recollite needle; it catches unaided and has double durability |
| `audio`, `advancements`, `guidePage` | Four sounds registered; `faded_revealed`, `lectern_replay`, `recollite_needle` and the recipe advancement load; the `faded` page sits right after `sunken` |

Manual (fresh normal world, seed 424242, 2026-10-10, screenshots in `playtest-shots/feature-hollows-3/`):

MANUAL_RESULTS

## Debug commands

`/mnemolith debug` is operator-only (permission level 2, the same gate as the QA suites). It does not run during normal play. Feedback strings are `mnemolith.debug.*` in `en_us` and `ru_ru`.

| Command | What it does |
| --- | --- |
| `/mnemolith debug pressure <amount\|calm\|saturated\|overloaded\|fracture>` | Sets the chunk's instability so its memory pressure lands on that score or band, including fracture (80). Imprints already in the chunk are kept. |
| `/mnemolith debug storm start` | Raises the chunk to fracture if it is not already, then starts a recollection storm the same way the game does (`Storms.start`: boss bar darkens the sky, gathering message). |
| `/mnemolith debug storm step <ticks>` | Advances that storm by up to 400 ticks. |
| `/mnemolith debug storm stop` | Ends the storm here as passed. |
| `/mnemolith debug storm scar` | Seeds three pinned residues and runs the storm through its waves so they merge into the Scar. Peaceful difficulty never spawns the boss, so this raises the world to easy. |
| `/mnemolith debug vault fill [count]` | Places a vault if there is none nearby, keeps that many death imprints, and turns drawing on. |
| `/mnemolith debug vault leak` | Puts a filled vault in the hand if needed and leaks one imprint the way carrying one does. |
| `/mnemolith debug relay link` | Ties the two nearest echoes with `EchoRelays.link`. |
| `/mnemolith debug relay thread` | Draws the relay motes between a linked echo and its partner. |
| `/mnemolith debug relay possess` | Possesses the nearest echo you own. |
| `/mnemolith debug relay hop` | Possesses if needed, then hops the thread (`EchoRelays.hop`). |
| `/mnemolith debug misfire <skip\|wrong\|extra\|seed>` | Shows that misfire line on the nearest echo, including «не те семена». |
| `/mnemolith debug residue <tag> [strength]` | Spawns a residue of that tag (fire, explosion, death, …). |
| `/mnemolith debug residue old [tag]` | Spawns an old residue (strength 5). |
| `/mnemolith debug residue observatory` | Marks the chunk as an observatory, places a reel if the spot is empty, and seeds its old residue. |
| `/mnemolith debug graft <temper>` | Grafts that temper onto the nearest echo with a real slip (grave, kindled, volatile, …). |
| `/mnemolith debug farm grow` | Grows crops within 6 blocks to their last age. |
| `/mnemolith debug farm show <count>` | Sets the nearest echo's farm line to wheat × count and puts that much wheat in a nearby chest. |
| `/mnemolith debug lava` | Sets lava at the nearest echo. A kindled echo is fireproof. |
| `/mnemolith debug shard graft` | Grafts a strength-4 death shard onto the nearest echo. |
| `/mnemolith debug shard release` | Releases a fire shard onto the ground the way using one does. |
| `/mnemolith debug stranger` | Hands the nearest echo to an offline player named Stranger, so `Z`/`R`/`B` and the needle can be checked against someone else's echo (both must refuse it). |
| `/mnemolith debug imprint <tag> <count>` | Writes that many imprints of one tag where you stand (`ImprintWriter.write`), so a storm there has fire, fall or silence memories to condense. |

