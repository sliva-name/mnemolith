# Echo guard, stage 3: mobs fight back, armor

Status: implemented on `feature/guard-3`. Builds on [echo-guard.md](echo-guard.md) and [echo-guard-2.md](echo-guard-2.md).

## Problem

After stage 2 a guard was close to invulnerable. Three things caused it:

- **Reach and shove.** The guard struck from 2.6 blocks, and on top of the vanilla knockback it shoved the mob
  another 0.3 blocks. A zombie hits from about 1.4 blocks, so it was pushed out again before it ever got in reach. A
  diagnostic GameTest (a husk with AI against a guard with a wooden sword) showed this: the husk targeted the guard
  the whole fight but never landed a hit. It got pushed so far that it left the leash, and the guard dropped it and
  walked back.
- **Snipers.** A skeleton shooting from outside the leash got no answer at all. The guard had no target, so its
  shield stayed down, and a grave guard paid a charge for every arrow.
- **Axes.** An axe never knocked an echo's shield down. Vanilla only does that in `Player.blockUsingItem`, and an
  echo is not a player.

Echoes also had armor slots that nothing filled on its own, and worn armor never lost durability. Vanilla wears only
a player's or a mob's armor, and an echo is neither.

## Design

**Noticing (unchanged, now explained).** Hostile mobs already have the stage 3 `EchoHuntGoal`. It is a vanilla
`NearestAttackableTargetGoal` with a random interval of 10 that needs line of sight. It matches an echo only while
`attractsMobs()` is true, and a guard on duty counts as working. A second copy of the goal above the player target
picks grave decoys, so a **grave guard is chosen first**. A hushed echo is never picked, nor are idle echoes, scouts or
replays. Creepers, neutral mobs and memory mobs never hunt echoes. No new scan was added.

**Fair melee.**

- The guard's reach is `echoGuardReach` (2.0 blocks, center to center, from 1.5 to 3.0), close to a zombie's own.
- The extra shove is gone; only the hit's own knockback (0.4, like a player's) is left.
- In the same diagnostic the husk now lands hits (about 3 damage at a time), and the guard still wins with a wooden
  sword.

**Provocation.**

- A hostile mob that a guard hits or shoots turns on that guard (`echoGuardProvokes`, on by default).
- Vanilla's hurt-by goal already does this for most mobs. The explicit hook (`LivingDamageEvent.Post` → `provoke`)
  also covers mobs without that goal, and it skips hushed guards, neutral mobs and anything `canFight` refuses.
- It runs only when a hit happens. Nothing scans.

**Under fire.**

- When a valid foe hurts the guard, or hits its shield, from **outside the leash** (up to 24 blocks away), the guard
  remembers it as a threat for 3 seconds.
- While the threat lasts, the guard stays on post, faces it and keeps the shield up. An archer guard shoots back if
  the line of fire is clear and the threat is within 16 blocks.
- The shield's block counts as being attacked again, so a skeleton that keeps shooting keeps the shield turned to it.
- Once the shooter stops, dies or goes out of range, the shield comes down. Blocked arrows cost a grave guard no
  charges.

**Axe disables the shield.** `EchoEntity.blockUsingItem` does what `Player.blockUsingItem` does: an attacker whose
weapon has `disable_blocking_for_seconds` (any axe, a warden) knocks the shield down. The guard keeps it down for the
same time, which is 100 ticks for an axe.

**Armor.**

- Every 2 seconds an echo puts on the best armor from its own main inventory, one piece per slot
  (`echoArmorAutoEquip`). It's a swap: the replaced piece goes back into that inventory slot.
- "Best" means armor + toughness / 2 for that slot, from the item's attribute modifiers.
- Pieces with no armor value (an elytra, a carved pumpkin, a head) are never put on. A worn piece with Curse of
  Binding never comes off.
- Worn armor protects through vanilla attributes. It also wears like a player's when the echo is hit
  (`echoArmorWear`): `hurtArmor` and `hurtHelmet` call `doHurtEquipment`.
- The echo model draws the armor with the vanilla humanoid armor layer.
- Armor drops where the echo dies, like the rest of its inventory (existing `dropEquipment`).
- All echoes do this, not only guards.

**Not in this stage.** Patrol between two posts (it needs a way to place the second post far from the echo screen,
which only opens within 8 blocks), potions, and multi-echo squads.

## Balance

| Setting | Default | Range |
| --- | --- | --- |
| `echoGuardReach` | 2.0 | 1.5–3.0 |
| `echoGuardProvokes` | true | |
| `echoArmorAutoEquip` | true | |
| `echoArmorWear` | true | |

The guard is now as exposed as a player who stands their ground:

- A husk lands hits on it, and a vindicator with an axe killed an iron-armored guard in about 3 seconds. That is
  vanilla vindicator strength.
- A skeleton out of reach is met with a raised shield instead of free hits.

## Verification

- `guardqa` grows 5 checks (24 in total):
  - `provoke`: a zombie with no AI that targets a villager turns to the guard after the guard's hit.
  - `underFire`: a skeleton 14 blocks out hurts the guard. The guard stays on post, faces it and raises the shield,
    then lowers it 3 s later.
  - `armorEquip`: picks the best piece, keeps a bound piece, skips the elytra and the pumpkin, and is stable on a
    second pass.
  - `armorProtects`: 11 armor turns 10 damage into 7.2, and every worn piece wears.
  - `armorDrops`: the armor lies on the ground after death.
- New live GameTests:
  - `guard_husk_fights_back`: a husk notices the guard before being hit, lands at least one hit, and the guard wins.
  - `guard_axe_disables_shield`: a blocked axe hit causes no damage and keeps the shield down for 100 ticks; then it
    goes back up.
- In game: `/mnemolith echodemo armored` (iron set auto-equipped, rendered), a husk against the armored guard, a
  vindicator with an axe (shield disabled 100 ticks, the guard dies, everything drops), and a skeleton against a grave
  guard (shield turned to the shooter, arrows blocked, charges paid only for arrows that got through).
