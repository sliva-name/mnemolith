# Memory Hollows, stage 4

Stage 3 ([memory-hollows-3.md](memory-hollows-3.md)) gave the hollows the faded, the lectern reading and the recollite
needle. Stage 4 gives the biome a memory of its own, the **forgotten** imprint, a use for it, and a guard for the
sunken archive's chests. The recollite pickaxe from the stage 3 list is left for later.

## The forgotten imprint

- **What.** `ImprintTag.FORGOTTEN` (`forgotten`), appended after `trade` (ordinal 14; ordinals are saved, so append
  only). Weight 1, intensity 3: a quiet tag, but not one that fades on its own (`ChunkMemory.fadeQuiet` leaves it,
  like silence), so it stays until it is caught or extracted.
- **Hollows only.** `ImprintWriter.write` and `ImprintWriter.restore` refuse the tag outside Memory Hollows, so no
  path (a vault spilling, a datapack formula, a debug write) can put it anywhere else. `HollowForgetting.forget`
  checks the biome first as well.
- **How it is written** (`HollowForgetting.forget(level, pos, replaced)`: the strongest imprint of `replaced` leaves
  the chunk, then a forgotten imprint is written there):
  - *An uncaught flicker.* When a flicker's catch window (90 ticks) closes, `HollowFlickers.expire` drops it; if it
    replayed an imprint (not a faint walk and not itself forgotten), the chunk forgets that memory with a 25% chance
    (`HollowForgetting.FORGET_CHANCE`). The check runs with the once-a-second flicker roll and whenever a flicker is
    sent, so it costs one list walk per second.
  - *A dying faded.* A faded that dies in a hollow (any cause) takes its copy with it: the chunk forgets the tag it
    last copied (or just gains a forgotten imprint if it never copied anything).
- **Its scene.** `HollowFlickers.FADE` (scene 5, `SCENES` is now 6). The figure stands still for a quarter of the
  replay, then sinks into the turf while it thins out (`FlickerRenderer`), grey (`0xA9ADBC`). A faded copying it
  bows its head and lets its arms hang (`FadedModel`).
- **Caught** like any flicker: needle and lens (or the recollite needle) give a forgotten slip. The slip has its own
  sprite (pale seal, a figure breaking up from the feet) and the tag its own icon in `tags.png`.

## The forgotten temper

- `Temper.FORGOTTEN`: a forgotten slip grafts onto an echo (16 charges, tint `0xCFCBDD`, mote `graft_forgotten`).
- **Work.** Its work writes no imprint. Instead each work imprint it would have written lets go of the **loudest**
  imprint of the chunk it works in (`EchoGrafts.forgetAt`, `ChunkMemory.removeHighest`), one charge each. In a chunk
  with nothing left it spends nothing. It is a slow, deliberate way to bring a loud base or a fracture down, paid for
  by catching flickers in the hollows. The memory is gone, not banked (the vault and the needle keep theirs).
- **Possessed.** Invisibility while the body carries the graft (charged every 10 s, like the others).
- **Formula.** *Forgetting*: forgotten + silence on the composition reel seal into a full forgotten graft (shard of
  strength 4), as sculk + silence does for deep. Datapack file `composition_formula/forgetting.json`.
- **Hollows only.** `Temper.hollowsOnly()`: observatories never seed a forgotten residue. A storm in a hollow can
  still condense one, since it only ever works from imprints that are already there.

## The archive keeper

- **Woken.** `ArchiveKeepers` listens to `PlayerInteractEvent.RightClickBlock`. The first time a survival or
  adventure player opens a chest whose loot table is still `mnemolith:chests/sunken_archive` (loot not yet rolled), a
  keeper rises 2–3 blocks from it, on solid ground, with a soul puff and the faded's breath at a low pitch. Once the
  loot is rolled the chest wakes nothing. A chest whose keeper still stands within 16 blocks only turns that keeper on
  the new opener. Creative and spectator players wake nothing.
- **What.** A faded with the keeper flag (`Faded.makeKeeper`): 32 health, 4.5 attack, persistent, bound to the chest
  (`setHomeTo`, radius 12, with `MoveTowardsRestrictionGoal`), angry at the opener for 30 s and for as long as the
  opener stays near the chest. It does not wander off to copy flickers. It is drawn a fifth larger and slate-blue
  (`FadedRenderer`), and named *Archive Keeper*. The lens works on it as on any faded.
- **Loot.** The faded loot table, plus a forgotten slip every time a player kills it. Like any faded, its death leaves
  a forgotten imprint in the hollow.
- **Saved.** The keeper flag and its chest position are saved with the entity.

## Guide, docs, data

- Field guide page `forgotten` after `faded` (40 pages): the forgotten flicker, the graft and formula, the keeper.
  Art from `tools/art/mnart/guide.py` (`p_forgotten`).
- Lang (EN/RU): the tag, the temper and its hint, the formula, the keeper's name, the page.

## Tests

- `hollows4qa` (18 checks, also a GameTest suite): the tag's ordinal, name, weight, scene and intensity; outside the
  biome neither a write nor `forget` puts a forgotten imprint; inside, `forget` replaces a path imprint; an expired
  trade flicker at chance 1 is forgotten, at chance 0 is not, and a forgotten flicker is not forgotten again; a faded
  that copied a fall dies and the fall becomes forgotten; the temper (data, hollows only); a forgotten echo's work
  removes the chunk's death imprint, writes nothing and costs one charge, and costs nothing in an empty chunk; the
  formula; the keeper wakes (health, home, persistence, target where the world allows it), wakes once, not for a
  creative player, drops a forgotten slip, and survives a save; the particle; the guide page.
- Live: `hollows_keeper_rises_at_archive_chest` (a real player opens the chest through the real block use; one
  keeper bound to the chest, the loot rolled, a second open wakes nothing, the kill drops a forgotten slip and leaves a
  forgotten imprint); `hollows_forgotten_flicker_caught` (a real player sees the fade scene and catches a forgotten
  slip with needle and lens).
- Manual, still to do on a normal world: the fade scene and the keeper's look, the keeper fight in a real sunken
  archive, an echo with the forgotten graft quieting a loud chunk over time.

## Next stages

- The recollite pickaxe that reads the chunk while mining.
