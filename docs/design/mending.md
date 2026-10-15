# Mending the Scar

Status: stages 1 and 2 done (PRs #74, #75); stage 3, memorial notes, implemented on `feature/memorial-inscription`.

A content line for the end of the storm arc. It turns the Scar site from a dead end into a choice that lasts.

## Why

- **The Scar site is a dead end.** A storm that merges leaves a site that seeds an old residue every day. After that the
  only choice is to break the heart. That ends the seeding and the ward too, and gives nothing back.
- **Storms have no lasting good outcome.** A cleared storm leaves a lull for a day. The Scar, the hardest fight, leaves
  only a wound.
- **Posies are scarce outside the hollows.** Forget-me-nots grow only in Memory Hollows, but a posy now does three jobs:
  it lays faded to rest, calms keepers and settles residues. A world without a hollow nearby has no supply.
- **The lore asks for it.** Every remembering item so far lays something to rest: the posy, the lantern, the
  transcript. The Scar is the one wound in the world that nobody tends.

## The line

### Stage 1: tending and the memorial (`feature/mending-1`)

- Right-click the Scar's heart with a remembrance posy to **tend** it. Each tending spends one posy (not in creative).
- You can tend once per in-game day (`ScarSites.RESEED_TICKS`, 24000 ticks). Tending again sooner is refused, and the
  heart tells you how many minutes are left.
- **The cost:** tending resets the site's seeding day, so that day's old residue (a strength 5 graft) is given up.
- After **three** tendings the site is **mended**:
  - The heart becomes a **memorial stone**: no item, no drops, light 7.
  - The chunk keeps its site as a memorial, which still wards off storms.
  - Old residues stop seeding.
- Breaking the memorial stone forgets the site, the same as breaking the heart.
- Without mobGriefing there is no heart, so a site cannot be tended.
- `ScarSite` gains `tended`, `tended_at` and `memorial`. All three are optional in the codec, so old saves load
  unchanged.
- The `mending` guide page comes right after `scar`. Its strings are in RU and EN.

### Stage 2: the memorial grove (`feature/mending-2`)

- Once per in-game day, while a player stands in its chunk, a memorial grows up to **two** forget-me-nots within 6
  blocks, never more than 12 in all. They grow only on grass, dirt or moss, into air. This gives a renewable posy
  supply that you earn by mending.
- Residual echoes within 8 blocks of a memorial **rest instead of festering**. They dissolve quietly, with no write-back
  and no act-out, and they do not lash. A memorial makes its surroundings calm.
- The guide page and QA grow to cover it.
- As built: the grove grows on the memorial site's daily pulse (`ScarSites.reseed`, which a memorial now uses for the
  grove instead of a residue). The ground must accept a forget-me-not (grass, dirt, moss, podzol, hollow turf).
  `ScarSites.nearMemorial` checks the 3x3 chunks for a memorial stone within 8 blocks. A residue rechecks it every 100
  ticks to skip its lash. At fester it dissolves, including old residues left over from before the mending.
  Legendary residues are untouched.

### Stage 3: memorial notes (`feature/memorial-inscription`)

**Why:** multiplayer depth. Transcripts are spent when read, so on a server the knowledge moves one sheet at a time,
and newcomers depend on someone being online. A mended Scar is a landmark the whole server fought for, which makes it
a natural place for shared notes.

- Use a written chronicle transcript on a memorial stone to carve it in. Its tag and formula bits join what is already
  carved, the newest carver's name signs it, and the sheet is spent (not in creative).
- Touch the stone with an empty hand to learn every carved entry you did not know. Nothing is spent, and the stone can
  be read any number of times. A blank stone says so.
- Stored as an optional `inscription` (a `Transcript`) on `ScarSite`. Breaking the stone forgets the site, carvings
  included.
- This adds no item, block or texture. The mending guide page gains a paragraph (RU/EN).
- Balance: knowledge only, as with any transcript. It needs a mended Scar first, so it is a late, shared reward rather
  than an early shortcut.

## Balance

- Mending costs three posies, three days and three old residues: about three full grafts given up.
- In return:
  - A permanent ward.
  - A calm area.
  - From stage 2, a slow, capped flower supply.
- Breaking the heart stays the quick option. Leaving the Scar stays the greedy one, with an old residue every day.

## QA

- Stage 1: `/mnemolith mendqa` and `mnemolith:suite_mendqa`, plus live `mending_tends_heart`.
- Stage 2: `mend2qa` and live `memorial_grows_flowers`.
