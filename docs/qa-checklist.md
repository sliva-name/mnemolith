# QA checklist

`/mnemolith qa` is a gamemaster command. It runs on the dedicated server, on surface columns east of the command source, and does not use the chunks owned by `/mnemolith smoke` (the source chunk), `/mnemolith perf` (48 blocks east), or `/mnemolith mpsmoke` (96 blocks east). One log line is the result:

```
Mnemolith qa writes=true bands=true extract=true formulas=true quietFail=true loudFail=true mute=true lens=true catalog=true recipe=true guide=true vein=true pocket=true observatory=true loot=true strider=true archivist=true replicant=true dimension=minecraft:overworld
```

A second line, `Mnemolith qa observatory registered=true chest=true located=<pos>`, records the structure search. The chat message is `QA <passed> of 18`. Every flag above has to be true. Multiplayer flags stay on `/mnemolith mpsmoke` and are not folded into this command.

`/mnemolith smoke` is unchanged. The Phase 9 line is still `pressure=75 band=OVERLOADED muted=true writeBlocked=true compose=3`.

## Matrix

| # | System | Where it is proved |
| --- | --- | --- |
| 1 | Imprint write: walk, build, death, explosion, fall, silence | `writes`. Walk calls `PathLedger.note` across 16 blocks. The other five call `ImprintWriter.tryWrite` and require the tag on that chunk |
| 2 | Pressure bands calm → saturated → overloaded → fracture | `bands`. Defaults stay 20 / 50 / 80, soft cap 100, storm scale 1. `MemoryPressure.band` is checked on each edge. A live spike of 19 stays calm, 20 is saturated, and 80 fractures the chunk and asks for a replicant. The column gets a temporary forced chunk ticket so that replicant is visible to the same column query `/mnemolith mpsmoke` uses; the ticket is removed before the command returns |
| 3 | Extract needle → imprint slip + catalog tag | `extract`. One fall imprint becomes a fall slip in the inventory. `CatalogFragmentItem.payloadFor` has only the fall bit |
| 4 | Compose reel: every shipped formula, quiet fail, loud fail | `formulas` composes unrecorded, fire trail, landing burst, and archivist bait, and the discovery mask has all four bits. `quietFail` mismatches build+build on an empty chunk: pressure equals `failurePressureSpike` (18), the band stays calm, and no replicant is asked. `loudFail` mismatches on a death+explosion chunk (overloaded) and the replicant count rises by one. That column uses the same temporary forced ticket as `bands` |
| 5 | Mute stone: silence, then the write block | `mute`. Placing the block writes silence, then a build write is refused |
| 6 | Lens band matches the server; dimension stamp | `lens`. `PressureSync.originBand` equals the cached band, and that band is saturated for one explosion imprint. The first lens walk runs, `perfStampMatches` is true for this dimension and chunk, and the next walk is skipped. The log's `dimension=` is the stamp. A dimension change drops it (`PressureSync.forget` on logout and dimension change; the client drops its snapshot when `snapshotDimension` no longer matches) |
| 7 | Catalog fragment opens only discovered tags and formulas | `catalog`. An empty player sends bits `0,0`. After extracting explosion, the payload is only that tag. After composing death+silence, the formula bit is unrecorded, death and silence are set, and fire stays clear. `CatalogScreen` draws a row only when that bit is set |
| 8 | Worldgen: vein, mute pocket, observatory locate, chest loot | `vein` lays stone along both axes (the feature replaces stone, and a repeat pass would already be stratum), force-places a vein, and requires stratum marks. `pocket` fills a stone volume at the forced floor (the feature replaces stone or air, and a surface column is dirt), force-places the pocket, and requires that floor to be muted. `observatory` requires the structure registry entry, a placed template whose chest loot id is `mnemolith:chests/chronicle_observatory` and whose unpacked chest contains a lens or a needle, and `findNearestMapStructure` from the command source. `loot` requires the loaded loot JSON to name `mnemolith:catalog_fragment` (weight 1 in the teaching pool) |
| 9 | Mobs: spawn plus one action | `strider` summons an echo strider and `beginCharge` sets the telegraph pose. `archivist` summons an archivist, `snatch` empties the container, and the pose is flee. `replicant` summons a moment replicant and `beginTelegraph` sets the telegraph pose |
| 10 | Phase 11 multiplayer invariants | `/mnemolith mpsmoke`, not this command. The line is `sameBand=true discoveryIsolated=true steal=true reel=true muteBlocks=true replicants=true guarded=true` |
| — | Catalog fragment can be crafted | `recipe`. The recipe manager has `mnemolith:catalog_fragment`, and the loaded JSON result id is that item. The shaped pattern is paper, amethyst shard, ink sac, stacked |
| — | Field guide is registered | `guide`. The recipe manager has `mnemolith:field_guide` (book over an amethyst shard). The observatory loot JSON names it at weight 2. The page table has 18 ids, each with a title key and a `textures/gui/guide/<id>.png` path. Language files and those diagrams are client assets, so this dedicated check does not open the screen |

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

`/mnemolith qa` stays at 18 of 18 and does not include these checks.

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

The last line of `mnemolith jobqa` must be `Echo job check: 12 of 12`. `mnemolith qa` stays at 18 of 18 and `mnemolith echoqa` at 11 of 11.

## Echo stage 3 QA

`/mnemolith echo3qa` is a gamemaster command for stage 3. Like `jobqa`, it uses a fake-player owner, builds its own test areas next to the command source, ticks entities through the level's own entity tick, and cleans up. The chat message is `Echo stage 3 check <passed> of 13`, and each check logs a detail line:

```
misfire pressure=52 strainSeen=true misfires=11 wrongPlaced=true noticeSeen=true ticks=338 wrong=0 echoItemsLeft=0 groundDrops=0 label="Memory: Overloaded"
farming ticks=128 harvested=10 planted=20/20 chestWheat=10 echoSeeds=17 chestSeeds=0 groundDrops=0 label="Farm: waiting for Wheat · 10"
```

| Flag | What is proved |
| --- | --- |
| `upgrades` | Chorus raises the echo limit 1→2→3 and a third is refused; long take raises recording frames 500→900 and a third is refused; sturdy adds 2×10 health and a third is refused. All three recipes exist and the field guide has 19 pages |
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

The last line of `mnemolith echo3qa` must be `Echo stage 3 check: 13 of 13`. `qa` 18/18, `echoqa` 11/11 and `jobqa` 12/12 stay green.

### Manual stage 3 checks (client)

`/mnemolith echodemo <scene>` (operator) builds a scene about 6 blocks south of you and runs your own echo in it.

- [ ] `farm`: raise the lens on the echo. The label reads «Ферма: Пшеница · N», later «Ферма: ждёт урожая (Пшеница) · 10»; the wheat is in the chest.
- [ ] `overload`: the label shows «Память: Перегрузка» as a second line above the head, and now and then «Сбой: пропустил блок» / «Сбой: не те семена» in orange. No items are lost.
- [ ] Lens hint: with the lens raised on your echo, three chips «ЛКМ — вселиться», «Z — стой · R — за мной · B — к точке», «Отголосок Dev · N м» sit above the pressure pill without overlapping; with an action-bar message both lift.
- [ ] `Z`, `R`, `B` on your own echo: «Стоит по команде», «Идёт за вами», «Возвращается к точке». On someone else's echo nothing happens.
- [ ] `door`, `gate`, `ladder`: the echo passes the wooden door or gate and closes it behind itself, or climbs the ladder over the wall.
- [ ] A zombie or husk near a working echo attacks it: «Стоп: атакован» in orange, the echo runs, then resumes.
- [ ] Craft the three upgrade slips in the 2×2 grid; the tooltip shows «Впитано: N из M»; RMB absorbs, a slip beyond the limit stays in hand.

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
