package com.mnemolith.echo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A finished self-recording. Frames are packed 19 bytes each: float dx, dy, dz from {@code origin},
 * short yaw, pitch and head yaw (360 degrees over 65536), then a flag byte. Actions carry the block edits.
 * Stored on the filled echo recording item and inside the echo entity.
 */
public record EchoRecording(UUID owner, String ownerName, ResourceKey<Level> dimension, Vec3 origin, byte[] frames, List<EchoAction> actions, long networkKey) {
    public static final int FRAME_BYTES = 19;
    public static final int FLAG_SNEAK = 1;
    public static final int FLAG_SPRINT = 2;
    public static final int FLAG_SWING = 4;
    public static final int FLAG_GROUND = 8;
    public static final int FLAG_SWING_OFFHAND = 16;
    /** 60 seconds at 20 ticks, the config ceiling. Anything longer is refused when decoding. */
    public static final int MAX_FRAMES = 1200;
    public static final int MAX_ACTIONS = 600;

    private static final Codec<byte[]> BYTES = Codec.BYTE_BUFFER.xmap(buffer -> {
        ByteBuffer copy = buffer.duplicate();
        byte[] out = new byte[copy.remaining()];
        copy.get(out);
        return out;
    }, ByteBuffer::wrap);

    public static final Codec<EchoRecording> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(EchoRecording::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(EchoRecording::ownerName),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(EchoRecording::dimension),
            Vec3.CODEC.fieldOf("origin").forGetter(EchoRecording::origin),
            BYTES.fieldOf("frames").forGetter(EchoRecording::frames),
            EchoAction.CODEC.listOf(0, MAX_ACTIONS).optionalFieldOf("actions", List.of()).forGetter(EchoRecording::actions))
            .apply(instance, EchoRecording::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoRecording> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, EchoRecording::owner,
            ByteBufCodecs.stringUtf8(64), EchoRecording::ownerName,
            ResourceKey.streamCodec(Registries.DIMENSION), EchoRecording::dimension,
            Vec3.STREAM_CODEC, EchoRecording::origin,
            ByteBufCodecs.byteArray(MAX_FRAMES * FRAME_BYTES), EchoRecording::frames,
            EchoAction.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ACTIONS)), EchoRecording::actions,
            EchoRecording::new);

    /**
     * What goes over the network inside an item: the header and a content key, never the frames. The encoding side
     * remembers the full recording under that key, so a stack that comes back from a creative-mode client (which only
     * ever saw the header) is restored in full on the server. A client that never had the frames gets an empty stub;
     * it reads the {@link EchoLesson} component instead.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoRecording> NETWORK_CODEC = StreamCodec.of(
            (buf, recording) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, recording.owner());
                ByteBufCodecs.stringUtf8(64).encode(buf, recording.ownerName());
                ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, recording.dimension());
                Vec3.STREAM_CODEC.encode(buf, recording.origin());
                // A stub (a client copy without frames) sends back the key of the recording it stands for.
                long key = recording.length() > 0 ? recording.contentKey() : recording.networkKey();
                buf.writeLong(key);
                if (recording.length() > 0) {
                    RecordingCache.remember(key, recording);
                }
            },
            buf -> {
                UUID owner = UUIDUtil.STREAM_CODEC.decode(buf);
                String name = ByteBufCodecs.stringUtf8(64).decode(buf);
                ResourceKey<Level> dimension = ResourceKey.streamCodec(Registries.DIMENSION).decode(buf);
                Vec3 origin = Vec3.STREAM_CODEC.decode(buf);
                long key = buf.readLong();
                EchoRecording full = RecordingCache.find(key);
                if (full != null && full.owner().equals(owner) && full.dimension().equals(dimension)) {
                    return full;
                }
                return new EchoRecording(owner, name, dimension, origin, new byte[0], List.of(), key);
            });

    /** A recording as it is saved and recorded. {@code networkKey} is only set on client-side stubs. */
    public EchoRecording(UUID owner, String ownerName, ResourceKey<Level> dimension, Vec3 origin, byte[] frames, List<EchoAction> actions) {
        this(owner, ownerName, dimension, origin, frames, actions, 0L);
    }

    public EchoRecording {
        actions = List.copyOf(actions);
        if (frames.length % FRAME_BYTES != 0 || frames.length > MAX_FRAMES * FRAME_BYTES) {
            frames = Arrays.copyOf(frames, Math.min(MAX_FRAMES, frames.length / FRAME_BYTES) * FRAME_BYTES);
        }
    }

    public int length() {
        return this.frames.length / FRAME_BYTES;
    }

    public Frame frame(int index) {
        int clamped = Math.max(0, Math.min(this.length() - 1, index));
        ByteBuffer buffer = ByteBuffer.wrap(this.frames, clamped * FRAME_BYTES, FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        double x = this.origin.x + buffer.getFloat();
        double y = this.origin.y + buffer.getFloat();
        double z = this.origin.z + buffer.getFloat();
        float yRot = unpackAngle(buffer.getShort());
        float xRot = unpackAngle(buffer.getShort());
        float headRot = unpackAngle(buffer.getShort());
        int flags = buffer.get() & 0xFF;
        return new Frame(x, y, z, yRot, xRot, headRot, flags);
    }

    public static void writeFrame(ByteBuffer out, Vec3 origin, double x, double y, double z, float yRot, float xRot, float headRot, int flags) {
        out.putFloat((float) (x - origin.x));
        out.putFloat((float) (y - origin.y));
        out.putFloat((float) (z - origin.z));
        out.putShort(packAngle(yRot));
        out.putShort(packAngle(xRot));
        out.putShort(packAngle(headRot));
        out.put((byte) flags);
    }

    static short packAngle(float degrees) {
        float wrapped = degrees % 360.0F;
        return (short) Math.round(wrapped * 65536.0F / 360.0F);
    }

    static float unpackAngle(short packed) {
        return packed * 360.0F / 65536.0F;
    }

    /** 64-bit FNV-1a over the owner, origin, frames, and actions. Used as the network cache key. */
    public long contentKey() {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, this.owner.getMostSignificantBits());
        hash = mix(hash, this.owner.getLeastSignificantBits());
        hash = mix(hash, Double.doubleToLongBits(this.origin.x));
        hash = mix(hash, Double.doubleToLongBits(this.origin.y));
        hash = mix(hash, Double.doubleToLongBits(this.origin.z));
        for (byte b : this.frames) {
            hash ^= b & 0xFF;
            hash *= 0x100000001b3L;
        }
        hash = mix(hash, this.actions.hashCode());
        return mix(hash, this.frames.length);
    }

    private static long mix(long hash, long value) {
        for (int i = 0; i < 8; i++) {
            hash ^= (value >>> (i * 8)) & 0xFF;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /** Recently sent full recordings by content key. Bounded; shared by the logical sides of one JVM. */
    static final class RecordingCache {
        private static final int SIZE = 512;
        private static final java.util.Map<Long, EchoRecording> CACHE = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<Long, EchoRecording> eldest) {
                return this.size() > SIZE;
            }
        });

        private RecordingCache() {}

        static void remember(long key, EchoRecording recording) {
            CACHE.put(key, recording);
        }

        static @org.jspecify.annotations.Nullable EchoRecording find(long key) {
            return CACHE.get(key);
        }
    }

    public int seconds() {
        return (this.length() + 19) / 20;
    }

    public long countActions(EchoAction.Kind kind) {
        return this.actions.stream().filter(action -> action.kind() == kind).count();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EchoRecording that
                && this.owner.equals(that.owner)
                && this.ownerName.equals(that.ownerName)
                && this.dimension.equals(that.dimension)
                && this.origin.equals(that.origin)
                && Arrays.equals(this.frames, that.frames)
                && this.actions.equals(that.actions)
                && this.networkKey == that.networkKey;
    }

    @Override
    public int hashCode() {
        int hash = this.owner.hashCode();
        hash = hash * 31 + this.dimension.hashCode();
        hash = hash * 31 + this.origin.hashCode();
        hash = hash * 31 + Arrays.hashCode(this.frames);
        hash = hash * 31 + this.actions.hashCode();
        return hash * 31 + Long.hashCode(this.networkKey);
    }

    @Override
    public String toString() {
        return "EchoRecording[owner=" + this.ownerName + ", frames=" + this.length() + ", actions=" + this.actions.size() + "]";
    }

    public record Frame(double x, double y, double z, float yRot, float xRot, float headRot, int flags) {
        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        public Vec3 position() {
            return new Vec3(this.x, this.y, this.z);
        }
    }
}
