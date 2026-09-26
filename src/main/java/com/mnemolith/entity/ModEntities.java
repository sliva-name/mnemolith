package com.mnemolith.entity;

import com.mnemolith.Mnemolith;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoShell;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.entity.mob.EchoStrider;
import com.mnemolith.entity.mob.MomentReplicant;

import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The three memory mobs, plus the echo body, the shell left behind while a player possesses it, and residual echoes. The Scar stays unregistered. */
public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(Mnemolith.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<EchoStrider>> ECHO_STRIDER = ENTITY_TYPES.registerEntityType(
            "echo_strider",
            EchoStrider::new,
            MobCategory.MONSTER,
            builder -> builder.sized(0.9F, 1.4F).eyeHeight(1.1F).clientTrackingRange(8).notInPeaceful());
    public static final DeferredHolder<EntityType<?>, EntityType<Archivist>> ARCHIVIST = ENTITY_TYPES.registerEntityType(
            "archivist",
            Archivist::new,
            MobCategory.MONSTER,
            builder -> builder.sized(0.6F, 1.1F).eyeHeight(0.9F).clientTrackingRange(8).notInPeaceful());
    public static final DeferredHolder<EntityType<?>, EntityType<MomentReplicant>> MOMENT_REPLICANT = ENTITY_TYPES.registerEntityType(
            "moment_replicant",
            MomentReplicant::new,
            MobCategory.MONSTER,
            builder -> builder.sized(0.6F, 1.8F).eyeHeight(1.6F).clientTrackingRange(8).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<EchoEntity>> ECHO = ENTITY_TYPES.registerEntityType(
            "echo",
            EchoEntity::create,
            MobCategory.MISC,
            builder -> builder.sized(0.6F, 1.8F).eyeHeight(1.62F).vehicleAttachment(Avatar.DEFAULT_VEHICLE_ATTACHMENT).clientTrackingRange(10).updateInterval(2).noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<EchoShell>> ECHO_SHELL = ENTITY_TYPES.registerEntityType(
            "echo_shell",
            EchoShell::create,
            MobCategory.MISC,
            builder -> builder.sized(0.6F, 1.8F).eyeHeight(1.62F).vehicleAttachment(Avatar.DEFAULT_VEHICLE_ATTACHMENT).clientTrackingRange(10).updateInterval(2).noLootTable());

    public static final DeferredHolder<EntityType<?>, EntityType<com.mnemolith.entity.echo.ResidueEntity>> RESIDUE = ENTITY_TYPES.registerEntityType(
            "residue",
            com.mnemolith.entity.echo.ResidueEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.6F, 1.6F).eyeHeight(1.3F).clientTrackingRange(10).noLootTable().fireImmune());

    public static final DeferredHolder<EntityType<?>, EntityType<com.mnemolith.entity.echo.ScarEntity>> SCAR = ENTITY_TYPES.registerEntityType(
            "scar",
            com.mnemolith.entity.echo.ScarEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(1.2F, 3.2F).eyeHeight(2.6F).clientTrackingRange(10).noLootTable().fireImmune());

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
