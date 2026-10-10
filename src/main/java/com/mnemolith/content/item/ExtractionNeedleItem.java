package com.mnemolith.content.item;

import java.util.Optional;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintWriter;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.worldgen.hollows.HollowFlickers;

public class ExtractionNeedleItem extends Item {
    private final boolean reinforced;
    private final boolean twin;

    public ExtractionNeedleItem(Properties properties) {
        this(properties, false, false);
    }

    public ExtractionNeedleItem(Properties properties, boolean reinforced, boolean twin) {
        super(properties);
        this.reinforced = reinforced;
        this.twin = twin;
    }

    public boolean reinforced() {
        return this.reinforced;
    }

    public boolean twin() {
        return this.twin;
    }

    /** Whether this needle catches flickers without a lens in the other hand (the recollite needle). */
    public boolean catchesUnaided() {
        return false;
    }

    private boolean canCatch(Player player) {
        return this.catchesUnaided() || ChronicleLensItem.isHeld(player);
    }

    /** Right-click in the air: with a lens in the other hand (or a recollite needle), catch a memory flicker in reach. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!this.canCatch(player)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack needle = player.getItemInHand(hand);
        if (onCooldown(serverPlayer, needle)) {
            return InteractionResult.FAIL;
        }
        InteractionResult caught = catchFlicker(server, serverPlayer, needle);
        if (caught != null) {
            return caught;
        }
        serverPlayer.sendSystemMessage(Component.translatable("mnemolith.message.flicker_none"));
        return InteractionResult.FAIL;
    }

    private static boolean onCooldown(ServerPlayer player, ItemStack needle) {
        int cooldown = CommonConfig.EXTRACTION_COOLDOWN_TICKS.get();
        if (cooldown > 0 && player.getCooldowns().isOnCooldown(needle)) {
            player.sendSystemMessage(Component.translatable("mnemolith.message.extract_cooldown"));
            return true;
        }
        return false;
    }

    /**
     * Tries to catch a flicker ({@link HollowFlickers#tryCatch}). Null when no flicker was in reach, so the caller
     * carries on with what it would otherwise do; a result when the attempt was spent on a flicker.
     */
    private InteractionResult catchFlicker(ServerLevel level, ServerPlayer player, ItemStack needle) {
        com.mnemolith.content.InventorySpace.clearRefused();
        HollowFlickers.CatchResult result = HollowFlickers.tryCatch(level, player);
        switch (result.kind()) {
            case NONE:
                return null;
            case FAINT:
                player.sendSystemMessage(Component.translatable("mnemolith.message.flicker_faint"));
                return InteractionResult.FAIL;
            case GONE:
                if (!com.mnemolith.content.InventorySpace.consumeRefused()) {
                    player.sendSystemMessage(Component.translatable("mnemolith.message.flicker_gone"));
                }
                return InteractionResult.FAIL;
            default:
                break;
        }
        level.playSound(null, player.blockPosition(), ModSounds.FLICKER_CATCH.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        player.sendSystemMessage(Component.translatable("mnemolith.message.flicker_caught",
                Component.translatable(java.util.Objects.requireNonNull(result.tag()).translationKey())));
        int cooldown = CommonConfig.EXTRACTION_COOLDOWN_TICKS.get();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(needle, cooldown);
        }
        if (!this.reinforced) {
            int cost = CommonConfig.EXTRACTION_DURABILITY_COST.get();
            if (cost > 0) {
                needle.hurtAndBreak(cost, level, player, item -> {});
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide() || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        int cooldown = CommonConfig.EXTRACTION_COOLDOWN_TICKS.get();
        if (onCooldown(player, context.getItemInHand())) {
            return InteractionResult.FAIL;
        }
        // A flicker is usually standing on the block the player clicks: catch it first, extract only when none is near.
        if (this.canCatch(player)) {
            InteractionResult caught = this.catchFlicker(level, player, context.getItemInHand());
            if (caught != null) {
                return caught;
            }
        }
        int pulls = this.twin ? 2 : 1;
        int extracted = 0;
        Imprint last = null;
        for (int i = 0; i < pulls; i++) {
            com.mnemolith.content.InventorySpace.clearRefused();
            Optional<Imprint> result;
            var be = level.getBlockEntity(context.getClickedPos());
            if (be instanceof com.mnemolith.content.block.ArchiveVaultBlockEntity vault) {
                result = com.mnemolith.vault.ArchiveVaults.extract(level, context.getClickedPos(), vault, player);
            } else if (be instanceof com.mnemolith.content.block.PlayerMemorialBlockEntity memorial) {
                result = extractMemorial(level, context.getClickedPos(), memorial, player);
            } else {
                result = ImprintWriter.extract(level, context.getClickedPos(), player);
            }
            if (result.isEmpty()) {
                if (extracted == 0 && !com.mnemolith.content.InventorySpace.consumeRefused()) {
                    player.sendSystemMessage(Component.translatable("mnemolith.message.extract_empty"));
                }
                break;
            }
            extracted++;
            last = result.get();
        }
        if (extracted == 0) {
            return InteractionResult.FAIL;
        }
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(context.getItemInHand(), cooldown);
        }
        if (!this.reinforced) {
            int cost = CommonConfig.EXTRACTION_DURABILITY_COST.get();
            if (cost > 0) {
                context.getItemInHand().hurtAndBreak(cost, level, player, item -> {});
            }
        }
        if (last != null) {
            if (extracted > 1) {
                player.sendSystemMessage(Component.translatable(
                        "mnemolith.message.extracted_multi",
                        extracted,
                        Component.translatable(last.tag().translationKey()),
                        last.intensity()));
            } else {
                player.sendSystemMessage(Component.translatable(
                        "mnemolith.message.extracted",
                        Component.translatable(last.tag().translationKey()),
                        last.intensity()));
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static Optional<Imprint> extractMemorial(
            ServerLevel level,
            net.minecraft.core.BlockPos pos,
            com.mnemolith.content.block.PlayerMemorialBlockEntity memorial,
            ServerPlayer player) {
        Optional<Imprint> peek = memorial.deathImprint();
        if (peek.isEmpty()) {
            return Optional.empty();
        }
        if (!com.mnemolith.content.InventorySpace.fits(player.getInventory(), com.mnemolith.data.ImprintSlips.of(peek.get()))) {
            com.mnemolith.content.InventorySpace.refuse(player);
            return Optional.empty();
        }
        Optional<Imprint> taken = memorial.takeDeathImprint();
        if (taken.isEmpty()) {
            return Optional.empty();
        }
        ImprintWriter.giveSlip(level, pos, player, taken.get());
        return taken;
    }
}
