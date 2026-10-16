# The Unremembered

Status: stage 1 done (PR #79); stage 2 done (PR #80); stage 3 done (PR #81); the guide picture shows the tear and circlet (stage 4 polish).

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

1. **Stage 1, the calling and the fight** (done, PR #79). The hollow effigy calls the boss. The Unremembered is a new boss
   entity with a lens-gated weakness, memory feeding that a lantern blocks, a forgetting pulse, faded called in its
   second half, and posy strikes. On death the hollow remembers: forgotten imprints around it clear.
2. **Stage 2, the reward line** (done, PR #80). It drops a *Lethe tear*. The tear crafts the *remembering circlet*, a helmet:
   - it reveals faded and the Unremembered without a lens;
   - it wards off the forgetting pulse;
   - the chunk you stand in does not forget.
   Stage 2 also adds advancements.
3. **Stage 3, multiplayer and servers** (done, PR #81). Health scales with the players taking part, and each of them gets a tear.
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

## Stage 2 rules

- **Lethe tear** (`lethe_tear`, stacks to 16, rare). The Unremembered drops one when a player brings it down (killed
  by player), on top of its stage 1 loot. Nothing else drops it.
- **Remembering circlet** (`remembering_circlet`). A helmet:
  - Recipe (shaped): gold ingot, tear, gold ingot over recollite shard, gap, recollite shard.
  - Armour 2, durability multiplier 20, enchantability 18, repaired with recollite shards. It has its own worn
    texture: a gold band with recollite side stones and a forget-me-not stone at the brow.
- **What wearing it does:**
  - *Seeing.* Every second it reveals every living faded within 8 blocks, and the Unremembered within 10 (its body is
    larger), as a raised lens would: glowing, and they take more damage. The lens still reaches further (12 and 16).
  - *Ward.* The Unremembered's forgetting pulse passes over the wearer.
  - *Keeping.* A hollow does not forget within 8 blocks of a wearer: `HollowForgetting.forget` treats a wearer like a
    recollite lantern. It does not stop the boss feeding (only a lantern does), so a fight is still fought by a
    lantern.
- **Advancements.** *The Hollow Remembers* (challenge): every non-spectator within 32 blocks when the Unremembered
  dies. *Nothing Unseen*: the circlet reveals something for you.
- **Guide.** The Unremembered page gains a paragraph on the tear and the circlet.

## Stage 3 rules

- **Scaling.** When the effigy calls it, the survival and adventure players within 32 blocks of the spot take part
  (at least one). Its health is `unrememberedHealth × (1 + unrememberedHealthPerPlayer × (players − 1))`: 160, 240,
  320 … with the defaults. It is set once at the call, at full health, and saved with the entity. Players who come
  later do not raise it.
- **Shared reward.** Every survival or adventure player who hurts it (a blow, an arrow, a posy) is recorded as a
  fighter (saved with the entity). Creative players are not recorded. When a player brings it down, it drops one
  Lethe tear per fighter still alive within 32 blocks, and at least one.
- **Server options** (`mnemolith-server.toml`, section `unremembered`, synced to clients):

  | Key | Default | Range | What |
  | --- | --- | --- | --- |
  | `unrememberedEnabled` | true | | Whether an effigy can call it (refusal line `mnemolith.effigy.disabled`). One already risen stays. |
  | `unrememberedHealth` | 160 | 40–1024 | Health with one player. |
  | `unrememberedHealthPerPlayer` | 0.5 | 0–2 | Extra share per further player; 0 turns scaling off. |
  | `unrememberedCallForgotten` | 3 | 1–32 | Forgotten imprints a chunk needs for a call. The refusal line names the number. |

## QA

- `/mnemolith unrememberedqa` checks every rule above on a flat test world painted with `fillbiome`.
- Live test `unremembered_called_and_seen`: two real players. One uses the effigy on a prepared hollow
  (`gameMode.useItemOn`), and both players are on the boss bar.
- `/mnemolith unremembered2qa` checks stage 2: items, recipe, head slot, repair, the tear only from a player kill,
  and revealing near but not far.
- Live test `circlet_wards_and_keeps`: two real players. The pulse darkens only the bare-headed one, and a hollow
  keeps its memory beside the wearer but still forgets 20 blocks away.
- `/mnemolith unremembered3qa` checks stage 3: the four options (defaults, ranges, RU/EN strings), the health
  formula, scaling at full health, fighters (creative not counted) and at least one tear.
- Live test `unremembered_scales_and_shares`: two real players. A call with both near gives two-player health, both
  strike it with real attacks, and the kill leaves two tears.

## By hand

- Fight it in survival on a dedicated server with two clients: the boss bar shows on both, it is readable, and it
  goes away for a player who walks off past 32 blocks.
- Check the pulse's darkness on screen and the size and translucency of its body; the circlet on the player model
  (first person and F5).
- The faded voice at half pitch: does it still read as the faded, louder and deeper?
