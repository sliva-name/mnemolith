package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.*;
import java.util.List;
import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.composition.ComposeResult;
import com.mnemolith.content.composition.CompositionFormula;
import com.mnemolith.content.item.CatalogFragmentItem;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.OpenCatalogPayload;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Writes, bands, extraction, formulas, and the two compose failures. */
public final class QaLoop {
    private QaLoop() {}

    static boolean writes(ServerLevel level, FakePlayer player, int chunkX, int chunkZ) {
        boolean path = walk(level, player, column(level, chunkX, chunkZ));
        boolean build = writeTag(level, column(level, chunkX + 1, chunkZ), ImprintTag.BUILD);
        boolean death = writeTag(level, column(level, chunkX + 2, chunkZ), ImprintTag.DEATH);
        boolean explosion = writeTag(level, column(level, chunkX + 3, chunkZ), ImprintTag.EXPLOSION);
        boolean fall = writeTag(level, column(level, chunkX + 4, chunkZ), ImprintTag.FALL);
        boolean silence = writeTag(level, column(level, chunkX + 5, chunkZ), ImprintTag.SILENCE);
        return path && build && death && explosion && fall && silence;
    }

    static boolean bands(ServerLevel level, BlockPos pos) {
        int saturated = CommonConfig.SATURATED_THRESHOLD.get();
        int overloaded = CommonConfig.OVERLOADED_THRESHOLD.get();
        int fracture = CommonConfig.FRACTURE_THRESHOLD.get();
        boolean defaults = saturated == 20
                && overloaded == 50
                && fracture == 80
                && CommonConfig.PRESSURE_SOFT_CAP.get() == 100
                && Math.abs(CommonConfig.RECOLLECTION_STORM_THRESHOLD.get() - 1.0D) < 0.001D;
        boolean edges = MemoryPressure.band(saturated - 1) == PressureBand.CALM
                && MemoryPressure.band(saturated) == PressureBand.SATURATED
                && MemoryPressure.band(overloaded - 1) == PressureBand.SATURATED
                && MemoryPressure.band(overloaded) == PressureBand.OVERLOADED
                && MemoryPressure.band(fracture - 1) == PressureBand.OVERLOADED
                && MemoryPressure.band(fracture) == PressureBand.FRACTURE;
        ChunkPos chunk = ChunkPos.containing(pos);
        tickColumn(level, pos);
        try {
            clear(level, pos);
            discardReplicants(level, pos);
            int calm = ImprintWriter.spike(level, pos, saturated - 1);
            boolean calmLive = calm == saturated - 1 && MemoryPressure.band(calm) == PressureBand.CALM;
            int saturatedLive = ImprintWriter.spike(level, pos, 1);
            boolean saturatedOk = saturatedLive == saturated && MemoryPressure.band(saturatedLive) == PressureBand.SATURATED;
            clear(level, pos);
            discardReplicants(level, pos);
            int fractured = ImprintWriter.spike(level, pos, fracture);
            ChunkMemory memory = memory(level, pos);
            int replicants = replicantCount(level, pos);
            boolean fractureLive = fractured >= fracture
                    && memory != null
                    && memory.fractured()
                    && MemoryPressure.band(fractured) == PressureBand.FRACTURE
                    && replicants >= 1;
            boolean ok = defaults && edges && calmLive && saturatedOk && fractureLive;
            if (!ok) {
                Mnemolith.LOGGER.info(
                        "Mnemolith qa bands detail defaults={} edges={} calm={} saturated={} fractureLive={} pressure={} fractured={} replicants={} ticking={}",
                        defaults,
                        edges,
                        calmLive,
                        saturatedOk,
                        fractureLive,
                        fractured,
                        memory != null && memory.fractured(),
                        replicants,
                        level.isPositionEntityTicking(pos));
            }
            discardReplicants(level, pos);
            return ok;
        } finally {
            releaseColumn(level, chunk);
        }
    }

    static boolean extract(ServerLevel level, FakePlayer player, BlockPos pos) {
        reset(player);
        clear(level, pos);
        if (!ImprintWriter.tryWrite(level, pos, ImprintTag.FALL, null, false)) {
            return false;
        }
        if (ImprintWriter.extract(level, pos, player).isEmpty()) {
            return false;
        }
        BlockPos again = pos.offset(3, 0, 0);
        if (!ImprintWriter.tryWrite(level, again, ImprintTag.FALL, null, false) || ImprintWriter.extract(level, again, player).isEmpty() || !oneStack(player, ImprintTag.FALL, 2)) {
            return false;
        }
        OpenCatalogPayload payload = CatalogFragmentItem.payloadFor(player);
        boolean noted = holdsTag(player, ImprintTag.FALL)
                && payload.tags() == (1 << ImprintTag.FALL.ordinal())
                && payload.formulas() == 0;
        if (!noted || !ImprintWriter.tryWrite(level, pos, ImprintTag.DEATH, null, false)) {
            return false;
        }
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int slot = 0; slot < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
        }
        boolean refused = ImprintWriter.extract(level, pos, player).isEmpty();
        inventory.setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
        boolean extracted = refused && !ImprintWriter.extract(level, pos, player).isEmpty() && holdsTag(player, ImprintTag.DEATH);
        return extracted && legacySlipsStack(level, player);
    }

    /** Two falls extracted from different blocks are one stack. Two older slips of one tag, saved with different places, join too. */
    private static boolean oneStack(FakePlayer player, ImprintTag tag, int count) {
        int stacks = 0;
        int total = 0;
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int slot = 0; slot < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; slot++) {
            net.minecraft.world.item.ItemStack stack = inventory.getItem(slot);
            com.mnemolith.data.ImprintCast cast = stack.get(com.mnemolith.data.ModDataComponents.IMPRINT_CAST.get());
            if (cast != null && cast.tag() == tag && stack.is(com.mnemolith.content.ModItems.IMPRINT_SLIP.get())) {
                stacks++;
                total += stack.getCount();
            }
        }
        return stacks == 1 && total == count;
    }

    private static boolean legacySlipsStack(ServerLevel level, FakePlayer player) {
        player.getInventory().clearContent();
        net.minecraft.world.item.ItemStack left = new net.minecraft.world.item.ItemStack(com.mnemolith.content.ModItems.IMPRINT_SLIP.get());
        net.minecraft.world.item.ItemStack right = new net.minecraft.world.item.ItemStack(com.mnemolith.content.ModItems.IMPRINT_SLIP.get());
        left.set(com.mnemolith.data.ModDataComponents.IMPRINT_CAST.get(),
                new com.mnemolith.data.ImprintCast(ImprintTag.DEATH, 3, new BlockPos(1, 2, 3), java.util.Optional.empty(), 1, 5L));
        right.set(com.mnemolith.data.ModDataComponents.IMPRINT_CAST.get(),
                new com.mnemolith.data.ImprintCast(ImprintTag.DEATH, 3, new BlockPos(4, 5, 6), java.util.Optional.empty(), 9, 8L));
        player.getInventory().setItem(0, left);
        player.getInventory().setItem(1, right);
        left.getItem().inventoryTick(left, level, player, null);
        right.getItem().inventoryTick(right, level, player, null);
        return oneStack(player, ImprintTag.DEATH, 2);
    }

    static boolean formulas(ServerLevel level, FakePlayer player, BlockPos pos) {
        reset(player);
        clear(level, pos);
        boolean all = true;
        for (CompositionFormula formula : CompositionFormula.values()) {
            List<ImprintTag> tags = formula.tags();
            if (tags.size() != 2) {
                return false;
            }
            ComposeResult result = compose(level, pos, player, tags.get(0), tags.get(1));
            all &= result.success() && result.formulaOrdinal() == formula.ordinal();
            all &= player.getData(ModAttachments.DISCOVERY.get()).hasFormula(formula.ordinal());
            all &= tookShard(player, formula.product());
        }
        OpenCatalogPayload payload = CatalogFragmentItem.payloadFor(player);
        return all && Integer.bitCount(payload.formulas() & ((1 << CompositionFormula.values().length) - 1)) == CompositionFormula.values().length;
    }

    /** The compose put a strength-4 shard of {@code tag} in the inventory, and that shard is taken back out. */
    private static boolean tookShard(FakePlayer player, ImprintTag tag) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
            if (cast != null && stack.is(ModItems.RESIDUAL_SHARD.get()) && cast.tag() == tag && cast.intensity() == CompositionFormula.SHARD_STRENGTH) {
                inventory.setItem(slot, ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }

    static boolean quietFail(ServerLevel level, FakePlayer player, BlockPos pos) {
        reset(player);
        clear(level, pos);
        discardReplicants(level, pos);
        int before = replicantCount(level, pos);
        ComposeResult result = compose(level, pos, player, ImprintTag.BUILD, ImprintTag.BUILD);
        ChunkMemory memory = memory(level, pos);
        int pressure = memory == null ? -1 : memory.cachedPressure();
        int spike = CommonConfig.FAILURE_PRESSURE_SPIKE.get();
        return !result.success()
                && spike > 0
                && spike < CommonConfig.SATURATED_THRESHOLD.get()
                && pressure == spike
                && MemoryPressure.band(pressure) == PressureBand.CALM
                && replicantCount(level, pos) == before;
    }

    static boolean loudFail(ServerLevel level, FakePlayer player, BlockPos pos) {
        reset(player);
        ChunkPos chunk = ChunkPos.containing(pos);
        tickColumn(level, pos);
        try {
            clear(level, pos);
            discardReplicants(level, pos);
            ImprintWriter.tryWrite(level, pos, ImprintTag.DEATH, null, false);
            ImprintWriter.tryWrite(level, pos, ImprintTag.EXPLOSION, null, false);
            ChunkMemory before = memory(level, pos);
            if (before == null || MemoryPressure.band(before.cachedPressure()) != PressureBand.OVERLOADED) {
                Mnemolith.LOGGER.info(
                        "Mnemolith qa loudFail detail overloaded=false pressure={}",
                        before == null ? -1 : before.cachedPressure());
                return false;
            }
            int spawned = replicantCount(level, pos);
            ComposeResult result = compose(level, pos, player, ImprintTag.BUILD, ImprintTag.BUILD);
            ChunkMemory after = memory(level, pos);
            PressureBand band = after == null ? PressureBand.CALM : MemoryPressure.band(after.cachedPressure());
            boolean loud = band == PressureBand.OVERLOADED || band == PressureBand.FRACTURE;
            int replicants = replicantCount(level, pos);
            boolean ok = !result.success() && loud && replicants == spawned + 1;
            if (!ok) {
                Mnemolith.LOGGER.info(
                        "Mnemolith qa loudFail detail success={} band={} before={} after={} ticking={}",
                        result.success(),
                        band,
                        spawned,
                        replicants,
                        level.isPositionEntityTicking(pos));
            }
            discardReplicants(level, pos);
            return ok;
        } finally {
            releaseColumn(level, chunk);
        }
    }
}
