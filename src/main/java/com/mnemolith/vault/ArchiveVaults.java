package com.mnemolith.vault;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.ArchiveVaultBlock;
import com.mnemolith.content.block.ArchiveVaultBlockEntity;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Archive vaults: pressure as a resource you bank, move and spend, with a price.
 * <ul>
 *   <li><b>Bank</b>: a drawing vault pulls the loudest imprint out of its chunk or the eight around it every
 *       {@code vaultDrawSeconds}, up to {@code vaultCapacity}. The chunks it drains go quiet.</li>
 *   <li><b>The price</b>: a share ({@code vaultBleed}) of what it holds bleeds into its own chunk's pressure, so a full
 *       vault makes its chunk loud (archivists come for it, residues condense there). If that chunk fractures, the vault
 *       ruptures and spills half of what it holds; an explosion spills everything.</li>
 *   <li><b>Move</b>: broken with any tool it keeps its imprints on the item. Carried, it leaks one imprint where you
 *       stand every {@code vaultLeakSeconds}.</li>
 *   <li><b>Spend</b>: the needle takes the loudest one as a slip; sneak-use discharges everything into the chunk it
 *       stands in (feed a reel, call residues or a storm on purpose); an idle vault feeds grafted echoes standing next
 *       to it, one matching imprint per cycle.</li>
 * </ul>
 * A mute stone in the vault's chunk keeps it from drawing there and swallows whatever it spills or discharges.
 */
public final class ArchiveVaults {
    private ArchiveVaults() {}

    /** Blocks from the vault within which grafted echoes are fed. */
    public static final double FEED_RANGE = 4.0D;
    /** Blocks within which an archivist with free hands goes for a filled vault. */
    public static final double RAID_RANGE = 10.0D;

    public enum Draw { DREW, FULL, NOTHING, DISABLED }

    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    public static boolean enabled() {
        return CommonConfig.VAULTS_ENABLED.get();
    }

    public static int capacity() {
        return CommonConfig.VAULT_CAPACITY.get();
    }

    // ---- tracking (for archivists) ----

    public static void track(ServerLevel level, BlockPos pos) {
        LOADED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    public static void untrack(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    public static void clearAll() {
        LOADED.clear();
    }

    // ---- load ----

    /** Pressure {@code imprints} bleed into the vault's chunk. */
    public static int loadOf(List<Imprint> imprints) {
        int sum = 0;
        for (Imprint imprint : imprints) {
            sum += imprint.pressureContribution();
        }
        return (int) Math.ceil(sum * CommonConfig.VAULT_BLEED.get());
    }

    /** Recounts the bleed of every vault in the chunk of {@code pos} and rescores it (a rupture waits for the vault's own tick). */
    public static void refreshLoad(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        int load = 0;
        if (enabled()) {
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                if (entity instanceof ArchiveVaultBlockEntity vault && !vault.isRemoved()) {
                    load += loadOf(vault.stored());
                }
            }
        }
        ChunkMemory memory = load > 0 ? LoadedChunkMemory.getOrCreate(chunk) : LoadedChunkMemory.existing(chunk);
        if (memory == null || memory.vaultLoad() == load) {
            return;
        }
        memory.setVaultLoad(load);
        chunk.markUnsaved();
        MemoryPressure.recompute(chunk, memory);
    }

    /** Current bleed of the vault's chunk (every vault in it). */
    public static int chunkLoad(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? 0 : memory.vaultLoad();
    }

    // ---- the block entity tick ----

    public static void tick(ServerLevel level, BlockPos pos, ArchiveVaultBlockEntity vault) {
        if (vault.consumeRefresh()) {
            // First tick after loading or placing: count its bleed and let archivists find it (onLoad also tracks it).
            track(level, pos);
            refreshLoad(level, pos);
        }
        if (!enabled() || vault.advance() < CommonConfig.VAULT_DRAW_SECONDS.get() * 20) {
            return;
        }
        vault.resetTimer();
        if (vault.drawing()) {
            draw(level, pos, vault);
        } else {
            feed(level, pos, vault);
        }
        if (!vault.isEmpty() && fractured(level, pos)) {
            rupture(level, pos, vault);
        }
    }

    private static boolean fractured(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory != null && MemoryPressure.band(memory.cachedPressure()) == PressureBand.FRACTURE;
    }

    // ---- bank ----

    /** One draw: the loudest imprint in the loaded, unmuted 3x3 chunks around the vault moves into it. */
    public static Draw draw(ServerLevel level, BlockPos pos, ArchiveVaultBlockEntity vault) {
        if (!enabled()) {
            return Draw.DISABLED;
        }
        if (vault.count() >= capacity()) {
            return Draw.FULL;
        }
        LevelChunk bestChunk = null;
        ChunkMemory bestMemory = null;
        Imprint best = null;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                ChunkMemory memory = LoadedChunkMemory.existing(chunk);
                if (memory == null || memory.hasMuteStone()) {
                    continue;
                }
                for (int i = 0; i < memory.imprintCount(); i++) {
                    Imprint imprint = memory.imprintAt(i);
                    if (best == null || imprint.pressureContribution() > best.pressureContribution()) {
                        best = imprint;
                        bestChunk = chunk;
                        bestMemory = memory;
                    }
                }
            }
        }
        if (best == null || !bestMemory.removeImprint(best)) {
            return Draw.NOTHING;
        }
        bestMemory.setArchival(true);
        MemoryPressure.recompute(bestChunk, bestMemory);
        vault.add(best);
        BlockPos from = best.origin();
        level.sendParticles(ModParticles.IMPRINT_EXTRACT.get(), from.getX() + 0.5D, from.getY() + 0.8D, from.getZ() + 0.5D, 6, 0.2D, 0.3D, 0.2D, 0.02D);
        level.sendParticles(ModParticles.IMPRINT_SHIMMER.get(), pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D, 6, 0.25D, 0.1D, 0.25D, 0.01D);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 0.7F);
        refreshLoad(level, pos);
        Mnemolith.LOGGER.info("Mnemolith vault drew tag={} intensity={} from {} into {} held={}", best.tag().getSerializedName(), best.intensity(),
                from.toShortString(), pos.toShortString(), vault.count());
        return Draw.DREW;
    }

    public static void toggle(ServerLevel level, BlockPos pos, BlockState state, ArchiveVaultBlockEntity vault, ServerPlayer player) {
        boolean drawing = !state.getValue(ArchiveVaultBlock.DRAWING);
        level.setBlock(pos, state.setValue(ArchiveVaultBlock.DRAWING, drawing), 3);
        vault.resetTimer();
        level.playSound(null, pos, drawing ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.BLOCKS, 0.8F, drawing ? 0.9F : 1.3F);
        player.sendSystemMessage(status(level, pos, vault, drawing), true);
    }

    /** "Archive vault · drawing · 5/12 · load 18 · death 2, fire 3". */
    public static Component status(ServerLevel level, BlockPos pos, ArchiveVaultBlockEntity vault, boolean drawing) {
        MutableComponent line = Component.translatable(drawing ? "mnemolith.vault.status.drawing" : "mnemolith.vault.status.idle",
                vault.count(), capacity(), chunkLoad(level, pos));
        Map<com.mnemolith.imprint.ImprintTag, Integer> counts = new EnumMap<>(com.mnemolith.imprint.ImprintTag.class);
        for (Imprint imprint : vault.stored()) {
            counts.merge(imprint.tag(), 1, Integer::sum);
        }
        counts.forEach((tag, count) -> line.append(" · ").append(Component.translatable(tag.translationKey())).append(" " + count));
        return line;
    }

    // ---- spend ----

    /** The needle on a vault: the loudest stored imprint becomes a slip in the player's hands. */
    public static Optional<Imprint> extract(ServerLevel level, BlockPos pos, ArchiveVaultBlockEntity vault, @Nullable ServerPlayer player) {
        Optional<Imprint> taken = vault.takeLoudest();
        taken.ifPresent(imprint -> {
            ImprintWriter.giveSlip(level, pos, player, imprint);
            refreshLoad(level, pos);
            Mnemolith.LOGGER.info("Mnemolith vault extract tag={} at {} held={}", imprint.tag().getSerializedName(), pos.toShortString(), vault.count());
        });
        return taken;
    }

    /** Sneak-use: every stored imprint is written into the vault's chunk. Returns how many; -1 when a mute stone refuses. */
    public static int discharge(ServerLevel level, BlockPos pos, ArchiveVaultBlockEntity vault, @Nullable ServerPlayer player) {
        if (vault.isEmpty()) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("mnemolith.vault.empty"), true);
            }
            return 0;
        }
        if (LoadedChunkMemory.isMuted(level, pos)) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("mnemolith.vault.discharge_muted"), true);
            }
            return -1;
        }
        List<Imprint> all = vault.takeAll();
        int written = 0;
        for (Imprint imprint : all) {
            if (ImprintWriter.restore(level, pos.above(), imprint)) {
                written++;
            }
        }
        refreshLoad(level, pos);
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.sendParticles(ModParticles.PRESSURE_WARN.get(), pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D, 16, 0.6D, 0.4D, 0.6D, 0.03D);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("mnemolith.vault.discharged", written), true);
        }
        Mnemolith.LOGGER.info("Mnemolith vault discharge count={} at {}", written, pos.toShortString());
        return written;
    }

    /** An idle vault feeds one grafted echo within 4 blocks: a stored imprint of its temper becomes half a slip of charges. */
    public static boolean feed(ServerLevel level, BlockPos pos, ArchiveVaultBlockEntity vault) {
        if (vault.isEmpty() || !EchoGrafts.enabled()) {
            return false;
        }
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, new AABB(pos).inflate(FEED_RANGE), e -> e.isAlive() && e.graftTemper() != null)) {
            Temper temper = echo.graftTemper();
            Imprint match = vault.stored().stream().filter(imprint -> Temper.of(imprint.tag()) == temper)
                    .max(Comparator.comparingInt(Imprint::pressureContribution)).orElse(null);
            if (match != null && EchoGrafts.topUp(echo, Math.max(1, EchoGrafts.slipCharge(temper) / 2))) {
                vault.remove(match);
                refreshLoad(level, pos);
                level.sendParticles(EchoGrafts.particle(temper), echo.getX(), echo.getY() + 1.0D, echo.getZ(), 10, 0.3D, 0.5D, 0.3D, 0.02D);
                Mnemolith.LOGGER.info("Mnemolith vault fed tag={} echo={} at {}", match.tag().getSerializedName(), echo.getUUID(), pos.toShortString());
                return true;
            }
        }
        return false;
    }

    // ---- risk ----

    /** The vault's chunk fractured: half of what it holds (the loudest) spills back into it. */
    public static int rupture(ServerLevel level, BlockPos pos, ArchiveVaultBlockEntity vault) {
        int spilled = spill(level, pos, vault, (vault.count() + 1) / 2, "rupture");
        for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= 32.0D * 32.0D)) {
            player.sendOverlayMessage(Component.translatable("mnemolith.vault.ruptured"));
        }
        return spilled;
    }

    /** Writes the {@code count} loudest stored imprints into the vault's chunk (a muted chunk swallows them: lost). */
    public static int spill(ServerLevel level, BlockPos pos, ArchiveVaultBlockEntity vault, int count, String why) {
        int written = 0;
        int taken = 0;
        for (int i = 0; i < count; i++) {
            Optional<Imprint> loudest = vault.takeLoudest();
            if (loudest.isEmpty()) {
                break;
            }
            taken++;
            if (ImprintWriter.restore(level, pos.above(), loudest.get())) {
                written++;
            }
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.2F, 0.4F);
        level.sendParticles(ModParticles.PRESSURE_WARN.get(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 20, 0.7D, 0.5D, 0.7D, 0.04D);
        Mnemolith.LOGGER.info("Mnemolith vault spill why={} taken={} written={} left={} at {}", why, taken, written, vault.count(), pos.toShortString());
        refreshLoad(level, pos);
        return written;
    }

    /** Archivists: the nearest tracked vault holding something within {@link #RAID_RANGE} of {@code from}, or null. */
    public static @Nullable BlockPos raidTarget(ServerLevel level, BlockPos from) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set == null || set.isEmpty() || !enabled()) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = RAID_RANGE * RAID_RANGE;
        for (BlockPos pos : set) {
            double distance = pos.distSqr(from);
            if (distance <= bestDistance && level.getBlockEntity(pos) instanceof ArchiveVaultBlockEntity vault && !vault.isEmpty()) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }

    /** An archivist took the loudest stored imprint: returned as the slip it will carry (and drop on death). */
    public static ItemStack raid(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ArchiveVaultBlockEntity vault)) {
            return ItemStack.EMPTY;
        }
        Optional<Imprint> taken = vault.takeLoudest();
        if (taken.isEmpty()) {
            return ItemStack.EMPTY;
        }
        refreshLoad(level, pos);
        Mnemolith.LOGGER.info("Mnemolith vault raided tag={} at {} held={}", taken.get().tag().getSerializedName(), pos.toShortString(), vault.count());
        return ImprintSlips.of(taken.get());
    }

    /** Player tick: a filled vault carried in the inventory leaks one of its imprints where the player stands. */
    public static void carryTick(ServerLevel level, ServerPlayer player) {
        int seconds = CommonConfig.VAULT_LEAK_SECONDS.get();
        if (seconds <= 0 || !enabled() || player.isSpectator() || (player.tickCount + player.getId()) % (seconds * 20) != 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (leak(level, player, stack)) {
                return;
            }
        }
    }

    /** Leaks one imprint of a carried vault item at the player. Public for the QA. */
    public static boolean leak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!stack.is(ModItems.ARCHIVE_VAULT.get())) {
            return false;
        }
        VaultContents contents = stack.get(ModDataComponents.VAULT_CONTENTS.get());
        if (contents == null || contents.isEmpty()) {
            return false;
        }
        List<Imprint> left = new ArrayList<>(contents.imprints());
        Imprint leaked = left.remove(level.getRandom().nextInt(left.size()));
        if (!ImprintWriter.restore(level, player.blockPosition(), leaked)) {
            return false;
        }
        if (left.isEmpty()) {
            stack.remove(ModDataComponents.VAULT_CONTENTS.get());
        } else {
            stack.set(ModDataComponents.VAULT_CONTENTS.get(), new VaultContents(left));
        }
        player.sendOverlayMessage(Component.translatable("mnemolith.vault.leaked", Component.translatable(leaked.tag().translationKey())));
        Mnemolith.LOGGER.info("Mnemolith vault leaked tag={} carrier={} at {}", leaked.tag().getSerializedName(), player.getGameProfile().name(), player.blockPosition().toShortString());
        return true;
    }
}
