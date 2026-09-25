package com.mnemolith.echo.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

/**
 * What an echo is doing, or why it stopped. Saved with the echo and synced to clients as a ready-made component.
 * {@code detail} carries a block id, a tool kind, or a "id=count;id=count" list of missing items; {@code a}/{@code b}
 * carry counters.
 */
public record JobStatus(Kind kind, String detail, int a, int b) {
    public enum Kind implements StringRepresentable {
        IDLE(false),
        REPLAY(false),
        MINING(false),
        DEPOSIT(false),
        BUILDING(false),
        FETCH(false),
        DONE(false),
        WAIT_MISSING(false),
        NO_TOOL(true),
        TOOL_BROKE(true),
        NOTHING_LEFT(true),
        UNREACHABLE(true),
        UNLOADED(true),
        INVENTORY_FULL(true),
        CHEST_UNAVAILABLE(true),
        CHEST_FULL(true),
        BLOCKED(true),
        NO_SUPPORT(true),
        NO_LESSON(true),
        NO_BLUEPRINT(true);

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final boolean stop;

        Kind(boolean stop) {
            this.stop = stop;
        }

        public boolean isStop() {
            return this.stop;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static final JobStatus IDLE = new JobStatus(Kind.IDLE, "", 0, 0);
    public static final JobStatus REPLAY = new JobStatus(Kind.REPLAY, "", 0, 0);

    public static final Codec<JobStatus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Kind.CODEC.optionalFieldOf("kind", Kind.IDLE).forGetter(JobStatus::kind),
            Codec.STRING.optionalFieldOf("detail", "").forGetter(JobStatus::detail),
            Codec.INT.optionalFieldOf("a", 0).forGetter(JobStatus::a),
            Codec.INT.optionalFieldOf("b", 0).forGetter(JobStatus::b))
            .apply(instance, JobStatus::new));

    public static JobStatus of(Kind kind) {
        return new JobStatus(kind, "", 0, 0);
    }

    public static JobStatus of(Kind kind, String detail) {
        return new JobStatus(kind, detail, 0, 0);
    }

    public static JobStatus of(Kind kind, int a, int b) {
        return new JobStatus(kind, "", a, b);
    }

    public static String missingDetail(Map<Item, Integer> missing) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Item, Integer> entry : missing.entrySet()) {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(BuiltInRegistries.ITEM.getKey(entry.getKey())).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    /** The line shown above the echo in the lens view and on the echo screen. */
    public Component component() {
        return switch (this.kind) {
            case IDLE -> Component.translatable("mnemolith.job.idle");
            case REPLAY -> Component.translatable("mnemolith.job.replay");
            case MINING -> Component.translatable("mnemolith.job.mining", blockName(this.detail), this.a);
            case DEPOSIT -> Component.translatable("mnemolith.job.deposit", this.a);
            case BUILDING -> Component.translatable("mnemolith.job.building", this.a, this.b);
            case FETCH -> Component.translatable("mnemolith.job.fetch", this.a, this.b);
            case DONE -> Component.translatable("mnemolith.job.done", this.a, this.b);
            case WAIT_MISSING -> Component.translatable("mnemolith.job.missing", missingList(this.detail));
            case NO_TOOL -> stop(Component.translatable("mnemolith.job.reason.no_tool." + (this.detail.isEmpty() ? "tool" : this.detail)));
            case BLOCKED -> stop(Component.translatable("mnemolith.job.reason.blocked", this.a));
            default -> stop(Component.translatable("mnemolith.job.reason." + this.kind.getSerializedName()));
        };
    }

    private static Component stop(Component reason) {
        return Component.translatable("mnemolith.job.stop", reason);
    }

    private static Component blockName(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) {
            return Component.literal(id);
        }
        return BuiltInRegistries.BLOCK.getOptional(key).map(block -> (Component) block.getName()).orElse(Component.literal(id));
    }

    /** "5× Stone, 3× Oak Planks, …" from the detail string; at most four kinds are named. */
    public static Component missingList(String detail) {
        List<Component> parts = new ArrayList<>();
        int shown = 0;
        int more = 0;
        for (String part : detail.split(";")) {
            int eq = part.lastIndexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (shown >= 4) {
                more++;
                continue;
            }
            Identifier key = Identifier.tryParse(part.substring(0, eq));
            Component name = key == null ? Component.literal(part) : BuiltInRegistries.ITEM.getOptional(key).map(item -> (Component) item.getName(new net.minecraft.world.item.ItemStack(item))).orElse(Component.literal(part));
            parts.add(Component.translatable("mnemolith.job.count", part.substring(eq + 1), name));
            shown++;
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i));
        }
        if (more > 0) {
            out.append(Component.translatable("mnemolith.job.and_more", more));
        }
        return out;
    }
}
