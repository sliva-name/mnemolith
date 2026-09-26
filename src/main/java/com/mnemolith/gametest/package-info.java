/**
 * NeoForge GameTest harness. Registered only when game tests are enabled (the {@code runGameTestServer} task, a dev
 * run, or {@code -Dneoforge.enableGameTest=true}); a production server never sees these tests.
 * <ul>
 * <li>{@link com.mnemolith.gametest.SuiteTests}: every {@code /mnemolith ...qa} suite as one required test, reusing
 * the same code the commands run.</li>
 * <li>{@link com.mnemolith.gametest.ResidueLiveTests}: residual echoes against real server players in the player
 * list, ticked like a connected client, so formation, lashes and lens reading run through the natural paths.</li>
 * </ul>
 */
package com.mnemolith.gametest;
