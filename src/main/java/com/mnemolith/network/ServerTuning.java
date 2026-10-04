package com.mnemolith.network;

import java.util.List;
import java.util.Optional;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.composition.CompositionRecipe;
import com.mnemolith.content.composition.CompositionRecipes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Sends {@link ServerTuningPayload} on join and {@code /reload} (and when the common config file changes), and
 * holds what the client received. Client UI reads the getters here; each falls back to the local value until the
 * server has spoken, and the client clears it again on logout.
 */
public final class ServerTuning {
    private static volatile ServerTuningPayload received;

    private ServerTuning() {}

    public static ServerTuningPayload current() {
        return new ServerTuningPayload(CommonConfig.ECHO_POSSESS_RANGE.get(), CommonConfig.ECHO_MINE_MAX_RADIUS.get(), CompositionRecipes.all());
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerTuningPayload payload = current();
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != CommonConfig.SPEC) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                ServerTuningPayload payload = current();
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            });
        }
    }

    /** Client side: called on the client thread by the payload handler. */
    public static void accept(ServerTuningPayload payload) {
        received = payload;
        Mnemolith.LOGGER.debug("Mnemolith server tuning: possessRange={} mineMaxRadius={} formulas={}",
                payload.possessRange(), payload.mineMaxRadius(), payload.formulas().size());
    }

    /** Client side: forget the last server's values on logout. */
    public static void clear() {
        received = null;
    }

    public static int possessRange() {
        ServerTuningPayload payload = received;
        return payload != null ? payload.possessRange() : CommonConfig.ECHO_POSSESS_RANGE.get();
    }

    public static int mineMaxRadius() {
        ServerTuningPayload payload = received;
        return payload != null ? payload.mineMaxRadius() : CommonConfig.ECHO_MINE_MAX_RADIUS.get();
    }

    /** The server's drum formulas in server order, so discovery bits and formula ordinals line up. */
    public static List<CompositionRecipe> formulas() {
        ServerTuningPayload payload = received;
        return payload != null && !payload.formulas().isEmpty() ? payload.formulas() : CompositionRecipes.all();
    }

    public static Optional<CompositionRecipe> formula(int index) {
        List<CompositionRecipe> formulas = formulas();
        return index < 0 || index >= formulas.size() ? Optional.empty() : Optional.of(formulas.get(index));
    }
}
