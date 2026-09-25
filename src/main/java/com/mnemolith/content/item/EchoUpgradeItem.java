package com.mnemolith.content.item;

import com.mnemolith.Mnemolith;
import com.mnemolith.echo.EchoProgress;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.particle.ModParticles;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * Stage 3 echo upgrade slip. Use: absorbs it (one is consumed) and raises that upgrade by one level, up to the
 * configured maximum. The level lives on the player, so it works for echoes made later too.
 */
public class EchoUpgradeItem extends Item {
    private final EchoProgress.Kind kind;

    public EchoUpgradeItem(EchoProgress.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public EchoProgress.Kind kind() {
        return this.kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        return absorb(serverPlayer, this.kind) ? consume(stack, player) : InteractionResult.FAIL;
    }

    private static InteractionResult consume(ItemStack stack, Player player) {
        stack.consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Raises {@code kind} by one for {@code player}. False (with a message) at the maximum. Public for QA. */
    public static boolean absorb(ServerPlayer player, EchoProgress.Kind kind) {
        EchoProgress progress = EchoProgress.of(player);
        int current = progress.level(kind);
        int max = EchoProgress.maxLevel(kind);
        if (current >= max) {
            player.sendSystemMessage(Component.translatable("mnemolith.upgrade.maxed"), true);
            return false;
        }
        EchoProgress next = progress.with(kind, current + 1);
        player.setData(ModAttachments.ECHO_PROGRESS.get(), next);
        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        MemoryFx.mob(level, ModParticles.COMPOSE_SUCCESS.get(), player.getX(), player.getY() + 1.0D, player.getZ(), 14);
        Component detail = switch (kind) {
            case CHORUS -> Component.translatable("mnemolith.upgrade.chorus.done", EchoProgress.echoLimit(player));
            case LONG_TAKE -> Component.translatable("mnemolith.upgrade.long_take.done", EchoProgress.recordFrames(player) / 20);
            case STURDY -> Component.translatable("mnemolith.upgrade.sturdy.done", Math.round(com.mnemolith.config.CommonConfig.ECHO_MAX_HEALTH.get() + EchoProgress.bonusHealth(player)));
        };
        player.sendSystemMessage(detail, true);
        if (kind == EchoProgress.Kind.STURDY) {
            for (ServerLevel each : level.getServer().getAllLevels()) {
                for (EchoEntity echo : each.getEntities(EntityTypeTest.forClass(EchoEntity.class), echo -> echo.isOwnedBy(player))) {
                    echo.applyBonusHealth(EchoProgress.bonusHealth(player));
                }
            }
        }
        Mnemolith.LOGGER.info("Mnemolith echo upgrade player={} kind={} level={}", player.getGameProfile().name(), kind, current + 1);
        return true;
    }
}
