package com.mnemolith.echo.relay;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Echo relay. A relay thread ties two of your echoes into a pair that shares one memory. Everything the link does is
 * something only bodies made of memory can do; it moves no items (chests and echo jobs already do that):
 * <ul>
 *   <li><b>Aura conduit</b>: each end always stands inside its partner's hush and kindle, at any distance (the partner
 *       pays the charges), so one hushed echo can quiet a miner at the far end of the base.</li>
 *   <li><b>Residue relay</b>: an echo drinks a residue of its <i>partner's</i> temper and passes the charges along the
 *       thread, so a pair covers two tempers.</li>
 *   <li><b>Hop</b>: while you possess one end, sneak and press the return key to jump into the other end, wherever it
 *       stands (the body you leave stays behind as an echo; your real body keeps waiting at the shell).</li>
 *   <li><b>Mirror</b>: while you possess one end, the other end repeats every block you break or place, at the same
 *       offset from its own feet, with its own tools and blocks (a live recording relayed).</li>
 * </ul>
 * The risk: a fracture under either end makes the link noisy (none of the above works through it), and when one end
 * dies the other takes the shock: it is hurt, a death memory is written under it, and the thread snaps.
 */
public final class EchoRelays {
    private EchoRelays() {}

    /** Blocks from the mirroring echo's feet to the block it repeats. */
    public static final double MIRROR_REACH = 6.0D;
    /** Damage the surviving end takes when its partner dies. */
    public static final float SHOCK_DAMAGE = 4.0F;
    /** Mirror actions handled per server tick, whatever the queue holds. */
    public static final int MIRROR_PER_TICK = 8;

    public enum HopResult { HOPPED, DISABLED, NOT_POSSESSING, UNLINKED, GONE, NOISY, COOLDOWN }

    public enum MirrorResult { DONE, NO_PARTNER, NOISY, BUSY, FAR, NOTHING, NO_ITEM, REFUSED }

    /** One block the possessing player broke or placed, waiting for the end of the tick to be mirrored. */
    private record Pending(UUID player, BlockPos target, @Nullable BlockState placed) {}

    private static final Deque<Pending> PENDING = new ArrayDeque<>();
    private static final Map<UUID, Long> LAST_HOP = new HashMap<>();
    private static @Nullable MirrorResult lastMirror;

    public static boolean enabled() {
        return CommonConfig.RELAY_ENABLED.get() && CommonConfig.ECHOES_ENABLED.get();
    }

    // ---- tying and cutting ----

    /**
     * Right-click with a relay thread on your own echo. The first echo is remembered on the thread; the second, within
     * {@code relayLinkRange} of the first, ties the link and uses up one thread. Sneaking cuts the echo's link.
     */
    public static boolean useThread(ServerPlayer player, EchoEntity echo, ItemStack thread) {
        if (!enabled() || !(player.level() instanceof ServerLevel level)) {
            player.sendSystemMessage(Component.translatable("mnemolith.relay.disabled"), true);
            return false;
        }
        if (player.isShiftKeyDown()) {
            if (echo.relay() == null) {
                player.sendSystemMessage(Component.translatable("mnemolith.relay.not_linked"), true);
                return false;
            }
            unlink(level, echo);
            thread.remove(ModDataComponents.RELAY_FIRST.get());
            level.playSound(null, echo.blockPosition(), SoundEvents.LEAD_UNTIED, SoundSource.PLAYERS, 0.8F, 0.8F);
            player.sendSystemMessage(Component.translatable("mnemolith.relay.cut"), true);
            return true;
        }
        UUID firstId = thread.get(ModDataComponents.RELAY_FIRST.get());
        EchoEntity first = firstId == null ? null : level.getEntity(firstId) instanceof EchoEntity e && e.isAlive() && e.isOwnedBy(player) ? e : null;
        if (first == null || first == echo) {
            thread.set(ModDataComponents.RELAY_FIRST.get(), echo.getUUID());
            level.playSound(null, echo.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.8F, 1.2F);
            player.sendSystemMessage(Component.translatable("mnemolith.relay.first"), true);
            return true;
        }
        int range = CommonConfig.RELAY_LINK_RANGE.get();
        if (first.distanceToSqr(echo) > (double) range * range) {
            player.sendSystemMessage(Component.translatable("mnemolith.relay.too_far", range), true);
            return false;
        }
        link(level, first, echo);
        thread.remove(ModDataComponents.RELAY_FIRST.get());
        thread.consume(1, player);
        player.sendSystemMessage(Component.translatable("mnemolith.relay.linked"), true);
        return true;
    }

    /** Ties {@code a} and {@code b}; a link either had before is cut first. Returns the shared id. */
    public static UUID link(ServerLevel level, EchoEntity a, EchoEntity b) {
        unlink(level, a);
        unlink(level, b);
        UUID id = UUID.randomUUID();
        a.setRelay(id);
        b.setRelay(id);
        level.playSound(null, b.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
        thread(level, a.position(), b.position(), false);
        Mnemolith.LOGGER.info("Mnemolith relay linked owner={} a={} b={}", a.ownerName(), a.getUUID(), b.getUUID());
        return id;
    }

    /** Cuts the echo's link at both ends (the far end only if it is loaded; an unloaded end finds no partner later). */
    public static void unlink(ServerLevel level, EchoEntity echo) {
        EchoEntity partner = partner(level, echo);
        echo.setRelay(null);
        if (partner != null) {
            partner.setRelay(null);
        }
    }

    // ---- finding the other end ----

    /** The loaded, living other end of {@code echo}'s link, or null. */
    public static @Nullable EchoEntity partner(ServerLevel level, EchoEntity echo) {
        UUID relay = echo.relay();
        UUID owner = echo.ownerId();
        return relay == null || owner == null ? null : find(level, owner, relay, echo);
    }

    /** The owner's loaded echo carrying {@code relay}, other than {@code except}. Walks the owner's few registry entries. */
    public static @Nullable EchoEntity find(ServerLevel level, UUID owner, UUID relay, @Nullable Entity except) {
        for (EchoRegistry.Entry entry : EchoRegistry.get(level.getServer()).entries(owner)) {
            if (level.getEntity(entry.echo()) instanceof EchoEntity echo && echo != except && echo.isAlive() && relay.equals(echo.relay())) {
                return echo;
            }
        }
        return null;
    }

    /** The relay id of the body {@code player} possesses, or null. */
    public static @Nullable UUID possessedRelay(ServerPlayer player) {
        PossessionState.Data data = EchoPossession.state(player).data();
        return data == null ? null : data.body().relay().orElse(null);
    }

    /** The other end of the body {@code player} possesses, or null. */
    public static @Nullable EchoEntity partnerOfPossessed(ServerPlayer player) {
        UUID relay = possessedRelay(player);
        return relay == null ? null : find(player.level(), player.getUUID(), relay, null);
    }

    // ---- noise ----

    /** A fracture under {@code pos}: the link through it is noise. */
    public static boolean noisy(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory != null && MemoryPressure.band(memory.cachedPressure()) == PressureBand.FRACTURE;
    }

    /** Whether the link between {@code a} and {@code b} carries anything right now. */
    public static boolean carries(ServerLevel level, Entity a, Entity b) {
        return enabled() && !noisy(level, a.blockPosition()) && !noisy(level, b.blockPosition());
    }

    // ---- what the link carries ----

    /** The partner whose {@code temper} aura reaches {@code echo} through the link (it pays the charges), or null. */
    public static @Nullable EchoEntity auraPartner(ServerLevel level, EchoEntity echo, Temper temper) {
        EchoEntity partner = partner(level, echo);
        return partner != null && partner.graftTemper() == temper && carries(level, echo, partner) ? partner : null;
    }

    /** A residue of {@code temper} next to {@code echo}: the partner it drinks for, when that partner carries the temper. */
    public static @Nullable EchoEntity drinkFor(ServerLevel level, EchoEntity echo, Temper temper) {
        return auraPartner(level, echo, temper);
    }

    /** Every 40 ticks for a linked echo: a faint thread of motes toward a partner within 48 blocks (drawn by one end). */
    public static void tick(ServerLevel level, EchoEntity echo) {
        EchoEntity partner = partner(level, echo);
        if (partner == null || echo.getUUID().compareTo(partner.getUUID()) > 0 || echo.distanceToSqr(partner) > 48.0D * 48.0D) {
            return;
        }
        thread(level, echo.position(), partner.position(), !carries(level, echo, partner));
    }

    private static void thread(ServerLevel level, Vec3 from, Vec3 to, boolean noisy) {
        int steps = (int) Math.min(12, Math.max(3, from.distanceTo(to) / 2.0D));
        for (int i = 1; i < steps; i++) {
            Vec3 at = from.lerp(to, i / (double) steps).add(0.0D, 1.2D, 0.0D);
            level.sendParticles(noisy ? ModParticles.PRESSURE_WARN.get() : ModParticles.IMPRINT_SHIMMER.get(), at.x, at.y, at.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    // ---- the shock ----

    /** One end died: the other is hurt, a death memory is written under it, and the thread snaps. */
    public static void onBodyDied(ServerLevel level, EchoEntity echo) {
        UUID relay = echo.relay();
        UUID owner = echo.ownerId();
        echo.setRelay(null);
        if (relay != null && owner != null) {
            shock(level, owner, relay);
        }
    }

    /** A possessed end died (the player is being pulled back). */
    public static void onPossessedBodyDied(ServerLevel level, @Nullable UUID owner, @Nullable UUID relay) {
        if (owner != null && relay != null) {
            shock(level, owner, relay);
        }
    }

    private static void shock(ServerLevel level, UUID owner, UUID relay) {
        EchoEntity partner = find(level, owner, relay, null);
        if (partner == null) {
            return;
        }
        partner.setRelay(null);
        if (!enabled()) {
            return;
        }
        BlockPos pos = partner.blockPosition();
        ImprintWriter.write(level, pos, List.of(ImprintTag.DEATH), owner, false);
        partner.hurtServer(level, level.damageSources().magic(), SHOCK_DAMAGE);
        level.playSound(null, pos, SoundEvents.LEAD_BREAK, SoundSource.NEUTRAL, 1.0F, 0.5F);
        level.sendParticles(ModParticles.PRESSURE_WARN.get(), partner.getX(), partner.getY() + 1.0D, partner.getZ(), 12, 0.3D, 0.6D, 0.3D, 0.02D);
        if (level.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable("mnemolith.relay.shock"));
        }
        Mnemolith.LOGGER.info("Mnemolith relay shock owner={} survivor={} at {}", partner.ownerName(), partner.getUUID(), pos.toShortString());
    }

    // ---- hop ----

    /** Sneak + return key while possessing a linked echo: jump into the other end. */
    public static HopResult hop(ServerPlayer player) {
        if (!enabled()) {
            return HopResult.DISABLED;
        }
        if (!EchoPossession.isPossessing(player)) {
            return HopResult.NOT_POSSESSING;
        }
        if (possessedRelay(player) == null) {
            return HopResult.UNLINKED;
        }
        EchoEntity partner = partnerOfPossessed(player);
        if (partner == null) {
            return HopResult.GONE;
        }
        if (!carries(player.level(), player, partner)) {
            return HopResult.NOISY;
        }
        long now = player.level().getGameTime();
        Long last = LAST_HOP.get(player.getUUID());
        if (last != null && now - last < CommonConfig.RELAY_HOP_COOLDOWN_SECONDS.get() * 20L && now >= last) {
            return HopResult.COOLDOWN;
        }
        if (!EchoPossession.hop(player, partner)) {
            return HopResult.GONE;
        }
        LAST_HOP.put(player.getUUID(), now);
        return HopResult.HOPPED;
    }

    /** The return key: sneaking with a linked body hops (and never returns, even when the hop fails); otherwise return. */
    public static void returnKey(ServerPlayer player) {
        if (player.isShiftKeyDown() && possessedRelay(player) != null && enabled()) {
            HopResult result = hop(player);
            if (result != HopResult.HOPPED) {
                player.sendSystemMessage(Component.translatable("mnemolith.relay.hop." + result.name().toLowerCase(java.util.Locale.ROOT)), true);
            }
            return;
        }
        EchoPossession.unpossess(player, EchoPossession.Reason.KEY);
    }

    public static void forget(UUID player) {
        LAST_HOP.remove(player);
    }

    // ---- mirror ----

    /** A possessing player broke a block: queued, mirrored at the end of the server tick. */
    public static void onPlayerBreak(ServerPlayer player, BlockPos pos) {
        queue(player, pos, null);
    }

    /** A possessing player placed a block: queued, mirrored at the end of the server tick. */
    public static void onPlayerPlace(ServerPlayer player, BlockPos pos, BlockState placed) {
        queue(player, pos, placed);
    }

    private static void queue(ServerPlayer player, BlockPos pos, @Nullable BlockState placed) {
        if (!enabled() || !CommonConfig.RELAY_MIRROR.get() || player instanceof net.neoforged.neoforge.common.util.FakePlayer
                || !EchoPossession.isPossessing(player) || possessedRelay(player) == null || PENDING.size() >= 64) {
            return;
        }
        PENDING.add(new Pending(player.getUUID(), pos.immutable(), placed));
    }

    /** Server tick (post): mirrors what was queued this tick, at most {@link #MIRROR_PER_TICK}. */
    public static void serverTick(MinecraftServer server) {
        for (int i = 0; i < MIRROR_PER_TICK && !PENDING.isEmpty(); i++) {
            Pending pending = PENDING.poll();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.player());
            if (player != null) {
                lastMirror = mirror(player, pending.target(), pending.placed());
            }
        }
    }

    /** Repeats one broken ({@code placed} null) or placed block through the other end. Public for the QA. */
    public static MirrorResult mirror(ServerPlayer player, BlockPos target, @Nullable BlockState placed) {
        EchoEntity partner = partnerOfPossessed(player);
        if (partner == null) {
            return MirrorResult.NO_PARTNER;
        }
        ServerLevel level = player.level();
        if (!carries(level, player, partner)) {
            return MirrorResult.NOISY;
        }
        if (partner.isReplaying() || partner.job().isWorking()) {
            return MirrorResult.BUSY;
        }
        BlockPos offset = target.subtract(player.blockPosition());
        BlockPos dest = partner.blockPosition().offset(offset);
        if (Vec3.atCenterOf(dest).distanceToSqr(partner.getEyePosition()) > MIRROR_REACH * MIRROR_REACH || !level.isLoaded(dest)) {
            return MirrorResult.FAR;
        }
        MirrorResult result;
        if (placed == null) {
            BlockState state = level.getBlockState(dest);
            if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, dest) < 0.0F || !state.getFluidState().isEmpty()) {
                return MirrorResult.NOTHING;
            }
            EchoHands.JobBreak broke = EchoHands.breakForJob(level, partner, dest, bestTool(partner.inventory(), state));
            result = broke.outcome() == EchoHands.Outcome.DONE ? MirrorResult.DONE : MirrorResult.REFUSED;
        } else {
            if (!level.getBlockState(dest).canBeReplaced()) {
                return MirrorResult.NOTHING;
            }
            EchoHands.Outcome outcome = EchoHands.placeForJob(level, partner, dest, placed);
            result = outcome == EchoHands.Outcome.DONE ? MirrorResult.DONE : outcome == EchoHands.Outcome.SKIPPED_NO_ITEM ? MirrorResult.NO_ITEM : MirrorResult.REFUSED;
        }
        Mnemolith.LOGGER.debug("Mnemolith relay mirror {} {} at {} -> {}", player.getGameProfile().name(), placed == null ? "break" : "place", dest.toShortString(), result);
        return result;
    }

    /** The main-inventory slot that breaks {@code state} fastest, or -1 for the bare hand. */
    private static int bestTool(EchoInventory inventory, BlockState state) {
        int best = -1;
        float bestSpeed = 1.0F;
        for (int slot = 0; slot < EchoInventory.MAIN; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                float speed = stack.getDestroySpeed(state);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    best = slot;
                }
            }
        }
        return best;
    }

    /** The last mirror handled by the server tick (the QA and the game tests read it). */
    public static @Nullable MirrorResult lastMirror() {
        return lastMirror;
    }

    public static void clearPending() {
        PENDING.clear();
        lastMirror = null;
    }

    // ---- status ----

    /** "Relay: linked · 12 m", "Relay: linked · far", or "Relay: noisy" for the owner's right-click. */
    public static Component statusLine(ServerLevel level, EchoEntity echo) {
        EchoEntity partner = partner(level, echo);
        if (partner == null) {
            return Component.translatable("mnemolith.relay.status.far");
        }
        if (!carries(level, echo, partner)) {
            return Component.translatable("mnemolith.relay.status.noisy");
        }
        return Component.translatable("mnemolith.relay.status.linked", Math.round(echo.distanceTo(partner)));
    }
}
