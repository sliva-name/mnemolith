package com.mnemolith.echo;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One world action inside a recording. {@code tick} is the frame index it fires on.
 * {@code block} is the block that was there when it was recorded (break and use check it again on replay).
 * {@code item} is the item that was placed (place only). The hit offset is relative to the block origin.
 */
public record EchoAction(int tick, Kind kind, BlockPos pos, Direction face, float hitX, float hitY, float hitZ, boolean offhand,
        Optional<Block> block, Optional<Item> item) {

    public enum Kind implements StringRepresentable {
        BREAK("break"),
        PLACE("place"),
        USE("use");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        public static final StreamCodec<io.netty.buffer.ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[Math.floorMod(i, values().length)], Kind::ordinal);
        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final Codec<EchoAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("tick").forGetter(EchoAction::tick),
            Kind.CODEC.fieldOf("kind").forGetter(EchoAction::kind),
            BlockPos.CODEC.fieldOf("pos").forGetter(EchoAction::pos),
            Direction.CODEC.fieldOf("face").forGetter(EchoAction::face),
            Codec.FLOAT.fieldOf("hx").forGetter(EchoAction::hitX),
            Codec.FLOAT.fieldOf("hy").forGetter(EchoAction::hitY),
            Codec.FLOAT.fieldOf("hz").forGetter(EchoAction::hitZ),
            Codec.BOOL.optionalFieldOf("offhand", false).forGetter(EchoAction::offhand),
            BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("block").forGetter(EchoAction::block),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item").forGetter(EchoAction::item))
            .apply(instance, EchoAction::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Block>> BLOCK_STREAM = ByteBufCodecs.optional(ByteBufCodecs.registry(Registries.BLOCK));
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Item>> ITEM_STREAM = ByteBufCodecs.optional(ByteBufCodecs.registry(Registries.ITEM));

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoAction> STREAM_CODEC = StreamCodec.of(
            (buf, action) -> {
                buf.writeVarInt(action.tick);
                Kind.STREAM_CODEC.encode(buf, action.kind);
                buf.writeBlockPos(action.pos);
                buf.writeEnum(action.face);
                buf.writeFloat(action.hitX);
                buf.writeFloat(action.hitY);
                buf.writeFloat(action.hitZ);
                buf.writeBoolean(action.offhand);
                BLOCK_STREAM.encode(buf, action.block);
                ITEM_STREAM.encode(buf, action.item);
            },
            buf -> new EchoAction(
                    buf.readVarInt(),
                    Kind.STREAM_CODEC.decode(buf),
                    buf.readBlockPos(),
                    buf.readEnum(Direction.class),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    BLOCK_STREAM.decode(buf),
                    ITEM_STREAM.decode(buf)));

    public static EchoAction of(int tick, Kind kind, BlockHitResult hit, boolean offhand, Block block, Item item) {
        BlockPos pos = hit.getBlockPos();
        Vec3 location = hit.getLocation();
        return new EchoAction(tick, kind, pos, hit.getDirection(),
                (float) (location.x - pos.getX()), (float) (location.y - pos.getY()), (float) (location.z - pos.getZ()),
                offhand, Optional.ofNullable(block), Optional.ofNullable(item));
    }

    public BlockHitResult hit() {
        Vec3 location = new Vec3(this.pos.getX() + this.hitX, this.pos.getY() + this.hitY, this.pos.getZ() + this.hitZ);
        return new BlockHitResult(location, this.face, this.pos, false);
    }

    public InteractionHand hand() {
        return this.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
