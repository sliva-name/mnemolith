# The Unremembered

Status: stage 1 implemented on `feature/unremembered-1`; stages 2 and 3 planned.

## Why

Memory Hollows (stages 1–4) are the mod's biggest place. They have a full loop of their own: flickers you catch,
faded that copy them, and the **forgotten** imprint left behind when a memory is lost. But the biome has no climax.
The forgotten imprint is the hollows' most distinctive memory. Today it has one use (a graft) and otherwise piles
up quietly wherever flickers go uncaught and faded die. The two endgame bosses (the Scar and the Archive Guardian)
belong to storms and shrines, not to the hollows.

The lore already holds the answer. A hollow is a place that forgets. Enough forgetting in one spot gathers into
something: a figure made of everything the place lost. It feeds on memory, and remembering is the only thing that
hurts it. Every tool the hollows taught you then gets a job in one fight:
- the lens sees it;
- the lantern keeps it from feeding;
- the posy lays part of it to rest.

## The line, in three stages

1. **Stage 1, the calling and the fight** (this PR). The hollow effigy calls the boss. The Unremembered is a new boss
   entity with a lens-gated weakness, memory feeding that a lantern blocks, a forgetting pulse, faded called in its
   second half, and posy strikes. On death the hollow remembers: forgotten imprints around it clear.
2. **Stage 2, the reward line.** It drops a *Lethe tear*. The tear crafts the *remembering circlet*, a helmet:
   - it reveals faded and the Unremembered without a lens;
   - it wards off the forgetting pulse;
   - the chunk you stand in does not forget.
   Stage 2 also adds advancements.
3. **Stage 3, multiplayer and servers.** Health scales with the players taking part, and each of them gets a tear.
   Server config covers whether it can be called, its health, and how many forgotten imprints a call needs. A
   dedicated-server live test with two players covers the boss bar and the loot.

## Stage 1 rules

### The hollow effigy (`hollow_effigy`)

- **Recipe (shaped):** hollowstone bricks above and below, a recollite shard on each side, and a block of recollite in
  the middle. It stacks to 1.
- **Use** it on a block (server side). It calls the Unremembered when all of these hold:
  - The spot is in Memory Hollows.
  - The chunk holds at least **3 forgotten imprints** (`CALL_FORGOTTEN`). Forgetting has to gather first, so you
    grow a calling site by letting flickers go uncaught, or by killing faded there.
  - No recollite lantern guards the spot: a place that is kept cannot forget enough to gather.
  - No other Unremembered stands within 64 blocks.
- **What a call does:**
  - The chunk's forgotten imprints are taken up into the figure (removed).
  - The figure rises on top of the clicked block with soul particles and a deep breath.
  - The effigy is spent (not in creative).
- **Refusals:** each failed condition gives its own overlay line, and nothing is spent.

### The Unremembered (`unremembered`)

- **Body and stats:**
  - A towering faded (FadedModel at 1.8×, its own texture: dark lilac cloth, a crown of recollite splinters).
  - 160 health, armour 4, attack 7, knockback resistance 0.6.
  - Persistent; 60 XP.
  - Purple boss bar, shown to players within 32 blocks.
- **Unseen or seen:** like a faded, it takes half of a player's damage while unseen. A raised lens looking at it
  within 16 blocks reveals it for 5 s (glowing), and then it takes 1.5×.
- **Feeding** (every 10 s): it eats the loudest imprint of its chunk and heals 8. If a recollite lantern guards
  where it stands, it cannot feed: the place keeps its memories. Fighting by a lantern is the counter.
- **Forgetting pulse** (every 8 s): players within 10 blocks get Darkness (4 s) and Slowness (3 s).
- **Second half** (at or below 50% health): every 15 s, while fewer than 3 faded stand within 16 blocks, it calls a
  faded beside itself. The faded is angry at its target.
- **Posy:** a remembrance posy given to it while it is revealed lays part of it to rest: 20 damage from that player,
  and one posy is spent. Unrevealed, it refuses the posy and nothing is spent.
- **Leash:** with no player within 48 blocks for 60 s, it sinks back into the turf. No loot, and the forgotten
  imprints it took are not returned.
- **Death:** the hollow remembers. Every forgotten imprint in the 3×3 chunks around it clears, and players within 32
  blocks are told. It drops a forgotten slip and recollite shards (loot table).
- **Sounds:** the faded's voice, an octave down.

### Not in stage 1

No tear, circlet, advancements, player scaling or config (stages 2–3). Debug summon: `/mnemolith spawn unremembered`.

## QA

- `/mnemolith unrememberedqa` checks every rule above on a flat test world painted with `fillbiome`.
- Live test `unremembered_called_and_seen`: two real players. One uses the effigy on a prepared hollow
  (`gameMode.useItemOn`), and both players are on the boss bar.
