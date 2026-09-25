package com.mnemolith.echo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * What a recording teaches, worked out once when the recording closes. This is the small summary that travels to the
 * client (tooltip, echo screen, ghost preview); the frames stay on the server.
 * <ul>
 * <li>{@code mining}: block types the player broke, most broken first (present when at least 2 blocks were broken);</li>
 * <li>{@code blueprint}: blocks the player placed that were still standing when the recording closed, relative to an
 * anchor (the lowest, first-placed block), with the facing the player had while placing.</li>
 * </ul>
 */
public record EchoLesson(int frames, int breaks, int places, int uses, List<MineTarget> mining, Optional<Blueprint> blueprint) {
    public static final int MAX_MINE_TARGETS = 8;
    public static final int MAX_BLUEPRINT = EchoRecording.MAX_ACTIONS;
    public static final EchoLesson NONE = new EchoLesson(0, 0, 0, 0, List.of(), Optional.empty());

    public record MineTarget(Block block, int count) {
        public static final Codec<MineTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(MineTarget::block),
                Codec.INT.optionalFieldOf("count", 1).forGetter(MineTarget::count))
                .apply(instance, MineTarget::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, MineTarget> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.BLOCK), MineTarget::block,
                ByteBufCodecs.VAR_INT, MineTarget::count,
                MineTarget::new);
    }

    /** One placed block, relative to the anchor. */
    public record Entry(BlockPos offset, BlockState state) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("o").forGetter(Entry::offset),
                BlockState.CODEC.fieldOf("s").forGetter(Entry::state))
                .apply(instance, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::offset,
                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), Entry::state,
                Entry::new);

        public Item item() {
            return itemFor(this.state);
        }
    }

    /** Placed blocks in placing order, bottom layer first once rotated (see {@link #placed(BlockPos, Rotation)}). */
    public record Blueprint(Direction facing, List<Entry> entries) {
        public static final Codec<Blueprint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Direction.CODEC.optionalFieldOf("facing", Direction.SOUTH).forGetter(Blueprint::facing),
                Entry.CODEC.listOf(0, MAX_BLUEPRINT).fieldOf("entries").forGetter(Blueprint::entries))
                .apply(instance, Blueprint::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Blueprint> STREAM_CODEC = StreamCodec.composite(
                Direction.STREAM_CODEC, Blueprint::facing,
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_BLUEPRINT)), Blueprint::entries,
                Blueprint::new);

        public Blueprint {
            entries = List.copyOf(entries);
            if (facing.getAxis().isVertical()) {
                facing = Direction.SOUTH;
            }
        }

        public int size() {
            return this.entries.size();
        }

        /** Rotation that turns the recorded facing into {@code now}. */
        public Rotation rotationTo(Direction now) {
            if (now.getAxis().isVertical()) {
                return Rotation.NONE;
            }
            int steps = Math.floorMod(now.get2DDataValue() - this.facing.get2DDataValue(), 4);
            return switch (steps) {
                case 1 -> Rotation.CLOCKWISE_90;
                case 2 -> Rotation.CLOCKWISE_180;
                case 3 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
        }

        /**
         * World positions and rotated states, sorted bottom-up (then in placing order). Upper door halves and bed heads
         * are left out: placing the lower half or the foot creates them.
         */
        public List<Entry> placed(BlockPos anchor, Rotation rotation) {
            List<Entry> out = new ArrayList<>(this.entries.size());
            for (Entry entry : this.entries) {
                BlockState state = entry.state();
                if (isSecondaryPart(state)) {
                    continue;
                }
                out.add(new Entry(anchor.offset(entry.offset().rotate(rotation)), state.rotate(rotation)));
            }
            // Stable sort keeps the placing order inside one layer.
            out.sort((a, b) -> Integer.compare(a.offset().getY(), b.offset().getY()));
            return out;
        }

        /** Item counts needed for the whole blueprint. */
        public Map<Item, Integer> materials() {
            Map<Item, Integer> counts = new LinkedHashMap<>();
            for (Entry entry : this.entries) {
                if (!isSecondaryPart(entry.state())) {
                    counts.merge(entry.item(), 1, Integer::sum);
                }
            }
            return counts;
        }
    }

    public static final Codec<EchoLesson> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("frames", 0).forGetter(EchoLesson::frames),
            Codec.INT.optionalFieldOf("breaks", 0).forGetter(EchoLesson::breaks),
            Codec.INT.optionalFieldOf("places", 0).forGetter(EchoLesson::places),
            Codec.INT.optionalFieldOf("uses", 0).forGetter(EchoLesson::uses),
            MineTarget.CODEC.listOf(0, MAX_MINE_TARGETS).optionalFieldOf("mining", List.of()).forGetter(EchoLesson::mining),
            Blueprint.CODEC.optionalFieldOf("blueprint").forGetter(EchoLesson::blueprint))
            .apply(instance, EchoLesson::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoLesson> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EchoLesson::frames,
            ByteBufCodecs.VAR_INT, EchoLesson::breaks,
            ByteBufCodecs.VAR_INT, EchoLesson::places,
            ByteBufCodecs.VAR_INT, EchoLesson::uses,
            MineTarget.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MINE_TARGETS)), EchoLesson::mining,
            ByteBufCodecs.optional(Blueprint.STREAM_CODEC), EchoLesson::blueprint,
            EchoLesson::new);

    public EchoLesson {
        mining = List.copyOf(mining);
    }

    public boolean teachesMining() {
        return !this.mining.isEmpty();
    }

    public boolean teachesBuilding() {
        return this.blueprint.isPresent() && !this.blueprint.get().entries().isEmpty();
    }

    public int seconds() {
        return (this.frames + 19) / 20;
    }

    /** Name of the most broken block, with "+N" when more kinds were broken. */
    public Component miningName() {
        if (this.mining.isEmpty()) {
            return Component.empty();
        }
        Component name = this.mining.get(0).block().getName();
        if (this.mining.size() > 1) {
            return Component.translatable("mnemolith.lesson.more", name, this.mining.size() - 1);
        }
        return name;
    }

    /** Tooltip / screen lines, one per lesson. */
    public List<Component> describe() {
        List<Component> lines = new ArrayList<>(2);
        if (this.teachesMining()) {
            lines.add(Component.translatable("mnemolith.lesson.mining", this.miningName()));
        }
        if (this.teachesBuilding()) {
            lines.add(Component.translatable("mnemolith.lesson.building", this.blueprint.get().size()));
        }
        if (lines.isEmpty()) {
            lines.add(Component.translatable("mnemolith.lesson.replay_only"));
        }
        return lines;
    }

    /** Shorter lines for the narrow job panel of the echo screen. */
    public List<Component> describeShort() {
        List<Component> lines = new ArrayList<>(2);
        if (this.teachesMining()) {
            lines.add(Component.translatable("mnemolith.gui.echo.lesson.mining", this.miningName()));
        }
        if (this.teachesBuilding()) {
            lines.add(Component.translatable("mnemolith.gui.echo.lesson.building", this.blueprint.get().size()));
        }
        if (lines.isEmpty()) {
            lines.add(Component.translatable("mnemolith.gui.echo.lesson.replay_only"));
        }
        return lines;
    }

    public static Item itemFor(BlockState state) {
        Item item = state.getBlock().asItem();
        return item == null ? Items.AIR : item;
    }

    /** The upper door half and the bed head appear by themselves when the other half is placed. */
    public static boolean isSecondaryPart(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        return state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }
}
