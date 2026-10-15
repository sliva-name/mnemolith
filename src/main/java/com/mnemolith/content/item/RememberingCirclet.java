package com.mnemolith.content.item;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.entity.boss.Unremembered;
import com.mnemolith.entity.mob.Faded;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The remembering circlet (Unremembered stage 2), worn on the head: every second it reveals the faded and the
 * Unremembered within {@link #REVEAL_RANGE} blocks, as a raised lens would; the Unremembered's forgetting pulse passes
 * over the wearer; and a hollow does not forget within {@link #KEEP_RANGE} blocks of the wearer (like a lantern). See
 * {@code docs/design/unremembered.md}.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class RememberingCirclet {
    public static final double REVEAL_RANGE = 8.0D;
    public static final double KEEP_RANGE = 8.0D;
    public static final int PERIOD = 20;
    public static final Identifier ADVANCEMENT = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "remembering_circlet");

    private RememberingCirclet() {}

    public static boolean worn(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.REMEMBERING_CIRCLET.get());
    }

    /** Whether a non-spectator wearing the circlet stands within {@link #KEEP_RANGE} of {@code pos}. */
    public static boolean wornNear(ServerLevel level, BlockPos pos) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && worn(player) && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= KEEP_RANGE * KEEP_RANGE) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PERIOD != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && worn(player)) {
                reveal(player.level(), player);
            }
        }
    }

    /** Reveals every faded and Unremembered within {@link #REVEAL_RANGE} of {@code player}. Returns how many. Public for QA. */
    public static int reveal(ServerLevel level, ServerPlayer player) {
        int n = 0;
        AABB box = player.getBoundingBox().inflate(REVEAL_RANGE);
        for (Faded faded : level.getEntitiesOfClass(Faded.class, box)) {
            if (faded.isAlive() && faded.distanceToSqr(player) <= REVEAL_RANGE * REVEAL_RANGE) {
                faded.reveal(player);
                n++;
            }
        }
        for (Unremembered boss : level.getEntitiesOfClass(Unremembered.class, box.inflate(2.0D))) {
            if (boss.isAlive() && boss.distanceToSqr(player) <= (REVEAL_RANGE + 2.0D) * (REVEAL_RANGE + 2.0D)) {
                boss.reveal();
                n++;
            }
        }
        if (n > 0) {
            var advancement = level.getServer().getAdvancements().get(ADVANCEMENT);
            if (advancement != null) {
                player.getAdvancements().award(advancement, "revealed");
            }
        }
        return n;
    }
}
