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
