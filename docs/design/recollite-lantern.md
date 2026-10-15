# The recollite lantern

Status: implemented on `feature/recollite-lantern`. A new Memory Hollows block that answers stage 4
([memory-hollows-4.md](memory-hollows-4.md)): a hollow lets its memories go, and the lantern is how a player chooses
what it keeps.

## Lore

The forget-me-not is the hollow's own flower. Set it in recollite and hollowstone and it holds the light on what
the place remembers. Under that light a memory does not wear out.

## Block

- `mnemolith:recollite_lantern`: a full block with a hollowstone frame and a recollite pane, light 13, strength 1.5,
  amethyst sounds, mined with a pickaxe (it drops itself; with no tool it drops nothing, like recollite blocks).
- Recipe: hollowstone bricks at the corners, recollite shards at the sides, a forget-me-not in the middle. The recipe
  unlocks with a recollite shard.
- Hint tooltip, creative tab, field guide page `lantern` after `pickaxe` (`PAGE_COUNT` 42).

## What it does (`HollowLanterns`)

Within `RADIUS` 8 blocks across and `HEIGHT` 4 blocks up or down of a lantern:

- **Nothing is forgotten.** `HollowForgetting.forget` returns early: the memory stays in the chunk and no forgotten
  imprint is written. This covers both stage 4 sources, an uncaught flicker and a dying faded (or archive keeper).
- **Flickers linger.** A flicker sent at a guarded spot stays catchable for twice `CATCH_WINDOW` (180 ticks instead
  of 90).

The lantern does not stop flickers or the faded, and it changes nothing outside Memory Hollows (forgetting only
happens there). It is a choice between a hollow that keeps its memories and one that wears down into forgotten
imprints, which feed the forgotten graft. Take the lanterns away and the hollow forgets again.

## Cost

`guarded` scans 17 x 17 x 9 blocks in loaded chunks. It runs only when a memory is about to be forgotten or a flicker is
sent: a few times a minute per player in a hollow, never every tick.

## QA

- `/mnemolith lanternqa` and `mnemolith:suite_lanternqa` (10 checks): block data and loot, the recipe, the guard
  radius, forgetting kept beside it and working 22 blocks away, the doubled and normal catch windows, a guarded expiry
  that forgets nothing, forgetting back once the lantern is gone, and the guide page.
- Live `lantern_keeps_hollow_memory`: a real player places a lantern, the hollow keeps a path memory beside it, the
  player mines the lantern back, and the hollow forgets.
- Manual: the lantern's light and look in a real hollow, and the guide page in RU and EN.
