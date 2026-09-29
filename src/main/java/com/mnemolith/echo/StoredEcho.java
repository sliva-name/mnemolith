package com.mnemolith.echo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.echo.graft.EchoGraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Snapshot of an echo body housed on an echo home pedestal (O3). No live entity, so no chunk tick cost.
 */
public record StoredEcho(
        UUID echo,
        UUID owner,
        String ownerName,
        Optional<Component> customName,
        EchoRole role,
        List<SlotStack> inventory,
        Optional<EchoRecording> recording,
        EchoLesson lesson,
        FarmLesson farm,
        LumberLesson lumber,
        CareLesson care,
        float health,
        double bonusHealth,
        Optional<EchoGraft> graft,
        boolean scarred,
        long generation) {

    public static final Codec<StoredEcho> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("echo").forGetter(StoredEcho::echo),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(StoredEcho::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(StoredEcho::ownerName),
            ComponentSerialization.CODEC.optionalFieldOf("custom_name").forGetter(StoredEcho::customName),
            Codec.STRING.xmap(EchoRole::byName, role -> role.name().toLowerCase(java.util.Locale.ROOT))
                    .optionalFieldOf("role", EchoRole.NONE).forGetter(StoredEcho::role),
            SlotStack.LIST_CODEC.optionalFieldOf("inventory", List.of()).forGetter(StoredEcho::inventory),
            EchoRecording.CODEC.optionalFieldOf("recording").forGetter(StoredEcho::recording),
            EchoLesson.CODEC.optionalFieldOf("lesson", EchoLesson.NONE).forGetter(StoredEcho::lesson),
            FarmLesson.CODEC.optionalFieldOf("farm", FarmLesson.NONE).forGetter(StoredEcho::farm),
            LumberLesson.CODEC.optionalFieldOf("lumber", LumberLesson.NONE).forGetter(StoredEcho::lumber),
            CareLesson.CODEC.optionalFieldOf("care", CareLesson.NONE).forGetter(StoredEcho::care),
            Codec.FLOAT.optionalFieldOf("health", 20.0F).forGetter(StoredEcho::health),
            Codec.DOUBLE.optionalFieldOf("bonus_health", 0.0D).forGetter(StoredEcho::bonusHealth),
            EchoGraft.CODEC.optionalFieldOf("graft").forGetter(StoredEcho::graft),
            Codec.BOOL.optionalFieldOf("scarred", false).forGetter(StoredEcho::scarred),
            Codec.LONG.optionalFieldOf("generation", 0L).forGetter(StoredEcho::generation))
            .apply(instance, StoredEcho::new));

    public static final Codec<List<StoredEcho>> LIST_CODEC = CODEC.listOf();

    public StoredEcho withRole(EchoRole role) {
        return new StoredEcho(echo, owner, ownerName, customName, role, inventory, recording, lesson, farm, lumber, care,
                health, bonusHealth, graft, scarred, generation);
    }

    public static StoredEcho capture(com.mnemolith.entity.echo.EchoEntity body) {
        UUID owner = body.ownerId();
        if (owner == null) {
            throw new IllegalStateException("echo has no owner");
        }
        Optional<Component> name = body.hasCustomName() ? Optional.ofNullable(body.getCustomName()) : Optional.empty();
        return new StoredEcho(
                body.getUUID(),
                owner,
                body.ownerName(),
                name,
                body.role(),
                SlotStack.snapshot(body.inventory()),
                Optional.ofNullable(body.recording()),
                body.job().lesson(),
                body.job().farmLesson(),
                body.job().lumberLesson(),
                body.job().careLesson(),
                body.getHealth(),
                body.bonusHealth(),
                Optional.ofNullable(body.graft()),
                body.scarred(),
                body.generation());
    }
}
