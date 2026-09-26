# Game test packs

Passed to the game test server with `--packs` (see `build.gradle`, run `gameTestServer`). Test-only: never part of the mod jar.

- `mnemolith_test_world` overrides `minecraft:flat`, the preset `GameTestServer` always uses, with a deep superflat:
  bedrock, 60 stone, 3 dirt, grass (surface at y=0), plains, and the `mnemolith:chronicle_observatory` structure set.
  The vanilla flat world is 4 blocks deep, too shallow for the QA suites (the vein and pocket sit 4 to 8 blocks under the
  surface, the mining and safety checks dig into stone), and has no observatory placement for the `locate` check.
  Every column is identical, so the suites never land in an ocean or on a cliff.
