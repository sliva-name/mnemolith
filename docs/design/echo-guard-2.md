# Echo guard, stage 2: bows, shields, escort

Status: implemented on `feature/echo-guard-2`. Builds on [echo-guard.md](echo-guard.md).

## Problem

A stage-1 guard can only walk up to a mob and hit it. It can't cover a field from its post, it soaks every hit, and it
only guards a fixed spot. Players asked for an archer at the wall, a guard that holds a shield, and a bodyguard that
walks with them.

## Design

**Ranged guard.** A guard that has a **bow or crossbow and arrows** in its inventory shoots instead of walking up:

- It shoots a foe at more than 3.5 blocks, up to its leash (post radius + 3, at most 16 blocks). It still uses its
  melee weapon if it has one and the foe is within reach. Without a melee weapon it shoots at any distance.
- **No infinite ammo.** Each shot takes one arrow item (normal, tipped or spectral) from the echo's inventory. Infinity
  is ignored. Fired arrows can be picked up like a player's. Each shot costs the bow one durability point.
- One shot every `echoGuardShotTicks = 35` ticks (a crossbow takes 1.4× as long), at velocity 1.6 (a skeleton's).
  Base arrow damage is `2 × echoGuardArrowDamageScale (1.0)`, so about 4 per hit. That is weaker per second than the
  melee guard (about 6) and much weaker than a golem; the trade is distance and spent arrows. Volatile and hushed
  multiply the base damage by 1.5 and spend a charge, as in melee.
- About 10 ticks before a shot the echo draws the bow (a visible pull, and the shield drops).
- **Line of fire.** Before every shot the guard checks the straight line from its eyes to the target: blocks must not
  be in the way (`hasLineOfSight`), and no protected entity's box (grown by 0.4) may touch the line. Protected means
  anything `canFight` refuses: the owner, players, villagers, animals, pets, armor stands, other echoes, creepers.
  When the line is blocked it does not shoot. A guard with a melee weapon walks in to melee instead; a pure archer
  waits and drops the target after 2 s of blocked line.
- **Friendly-fire guard.** An arrow fired by an echo never hurts a protected entity: a `ProjectileImpactEvent` cancels
  the impact, and the arrow flies through. Arrows also lose Flame fire when they are fired (no burning, no TNT).
- **Lesson.** A bow hit by the recording player on a hostile mob now counts for the guard lesson like a melee hit.

**Shield.** A guard with a shield puts it in its off hand and **raises it while it has a foe**. Blocking uses the
vanilla shield (`BLOCKS_ATTACKS`): frontal hits and arrows are blocked fully after the 5-tick raise delay, the shield
loses durability, and the usual sounds play. It lowers the shield right before each swing or bow draw, so there is a
gap of a few ticks in every attack cycle. An axe hit that disables the shield keeps it down for
`echoGuardShieldCooldown = 100` ticks. `echoGuardShields = true` turns this on or off.

**Guard me (escort).** A second button next to «Охрана» (☺, tooltip «Охрана: со мной») starts the guard in escort
mode (`Mode.ESCORT`):

- The post is the owner's position, updated every tick while the owner is online, in the same dimension and within
  24 blocks. The echo stays within 3 blocks of them (it re-paths when they move).
- The post radius is `echoGuardEscortRadius = 5`, with the same +3 leash, and mobs after the owner come first.
- When the owner is away (offline, another dimension or farther than 24 blocks) the echo holds the last spot and shows
  «Охрана: ждёт хозяина». It never teleports.
- Status: «Охрана: рядом с вами · N».

**Safety, unchanged.** The same `canFight` rule applies, and the line-of-fire check plus the impact guard are on top
of it. Arrows never hit the owner, players, villagers, pets or passive mobs.

**Not in this stage.** Patrol between two posts, multishot and piercing (ignored), armor use, potions.

## Verification

- `guardqa` grows checks for: bow shot with ammo spent, no shot without arrows, line of fire blocked by a villager,
  arrow passing through a villager, shield block, escort post following the owner, and escort waiting.
- A live GameTest: an archer guard kills a zombie across a villager-free line, and a villager in the line is never hit.
- In game (headless client): an archer at the post, the shield blocking a skeleton, escort following the player, plus
  the three unchecked stage-1 rows (leash run-out, the "no weapon" stop live, the English guide page).
