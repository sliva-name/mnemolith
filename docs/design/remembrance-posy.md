# The remembrance posy

Status: implemented on `feature/remembrance-posy`. A peaceful answer to the faded
([memory-hollows-3.md](memory-hollows-3.md)) and to stage 4 forgetting ([memory-hollows-4.md](memory-hollows-4.md)).

## Lore

A faded is a figure its place half forgot. Killing one finishes the forgetting: the chunk lets go of what it copied.
The forget-me-not, the hollow's own flower, is the other way: someone remembers it, and that is enough for it to rest.

## Item

- `mnemolith:remembrance_posy`: three forget-me-nots and a string, shapeless; stacks to 16. The recipe unlocks with a
  forget-me-not. Hint tooltip, creative tab, field guide page `posy` after `lantern` (`PAGE_COUNT` 43).

## Use (`RemembrancePosy`)

Right-click a faded with a posy in the main hand (`PlayerInteractEvent.EntityInteract`):

- **A faded is laid to rest.** It is removed without dying, so `Faded.die` never runs and nothing is forgotten. The
  memory it was copying is written back into its chunk (`ImprintWriter.write`), and it leaves one recollite shard. A
  faded that copied nothing writes nothing.
- **An archive keeper is calmed** (`Faded.calm`): no target, no anger, no scene. It stays, still guards its chest, and
  opening the chest again wakes it as before. A keeper killed later still drops its forgotten slip.
- Each use spends one posy (creative keeps it). A dead or removed faded takes nothing.
- Soul and end-rod particles and a low amethyst chime.

## Balance

The posy trades the faded's drops (recollite, forget-me-nots, sometimes a slip) for one shard and a peaceful end.
It costs three flowers that grow only in hollows. It is the choice for players who want to keep a hollow's memories
(together with the recollite lantern) rather than farm forgotten imprints.

## QA

- `/mnemolith posyqa` and `mnemolith:suite_posyqa` (10 checks): item and recipe; rest writes the copied memory back
  with nothing forgotten, leaves a shard and spends a posy; resting a faded with no copy writes nothing; a keeper is
  calmed and stays; creative keeps the posy; a dead faded takes nothing; the guide page.
- Live `posy_lays_faded_to_rest`: a real player right-clicks a faded in a hollow through the real entity interaction.
- Manual: the particles, chime and posy texture in game; the RU/EN text.
