package com.mnemolith.entity;

import com.mnemolith.Mnemolith;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoShell;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.entity.mob.EchoStrider;
import com.mnemolith.entity.mob.FractureStalker;
import com.mnemolith.entity.mob.KinWitness;
import com.mnemolith.entity.mob.LedgerMite;
import com.mnemolith.entity.mob.MomentReplicant;

import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The three memory mobs, plus the echo body, the shell left behind while a player possesses it, residual echoes, and the Scar. */
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
    public static final DeferredHolder<EntityType<?>, EntityType<LedgerMite>> LEDGER_MITE = ENTITY_TYPES.registerEntityType(
            "ledger_mite",
            LedgerMite::new,
            MobCategory.CREATURE,
            builder -> builder.sized(0.7F, 0.4F).eyeHeight(0.2F).clientTrackingRange(8));
    public static final DeferredHolder<EntityType<?>, EntityType<KinWitness>> KIN_WITNESS = ENTITY_TYPES.registerEntityType(
            "kin_witness",
            KinWitness::new,
            MobCategory.CREATURE,
            builder -> builder.sized(0.7F, 2.2F).eyeHeight(1.9F).clientTrackingRange(8));
    public static final DeferredHolder<EntityType<?>, EntityType<FractureStalker>> FRACTURE_STALKER = ENTITY_TYPES.registerEntityType(
            "fracture_stalker",
            FractureStalker::new,
            MobCategory.MONSTER,
            builder -> builder.sized(1.1F, 0.9F).eyeHeight(0.7F).clientTrackingRange(8).notInPeaceful());
    public static final DeferredHolder<EntityType<?>, EntityType<com.mnemolith.entity.armory.MemoryBolt>> MEMORY_BOLT = ENTITY_TYPES.registerEntityType(
            "memory_bolt",
            com.mnemolith.entity.armory.MemoryBolt::new,
            MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(10).noLootTable());

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

    public static final DeferredHolder<EntityType<?>, EntityType<PleadingChair>> PLEADING_CHAIR = ENTITY_TYPES.registerEntityType(
            "pleading_chair",
            PleadingChair::new,
            MobCategory.MISC,
            builder -> builder.sized(0.8F, 1.2F).eyeHeight(0.9F).clientTrackingRange(10).updateInterval(3).fireImmune().noLootTable());

    public static final DeferredHolder<EntityType<?>, EntityType<com.mnemolith.entity.echo.ScarEntity>> SCAR = ENTITY_TYPES.registerEntityType(
            "scar",
            com.mnemolith.entity.echo.ScarEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(1.2F, 3.2F).eyeHeight(2.6F).clientTrackingRange(10).noLootTable().fireImmune());

    /** Silence twin of the Scar: born from void pressure in a mute that lasted too long (P3 / B4). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.mnemolith.entity.echo.ScarEntity>> SILENCE_MIRROR = ENTITY_TYPES.registerEntityType(
            "silence_mirror",
            com.mnemolith.entity.echo.ScarEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(1.2F, 3.2F).eyeHeight(2.6F).clientTrackingRange(10).noLootTable().fireImmune());

    /** Archive Guardian: deterministic shrine boss at an archive shrine (B4). Never storm dice. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.mnemolith.entity.echo.ScarEntity>> ARCHIVE_GUARDIAN = ENTITY_TYPES.registerEntityType(
            "archive_guardian",
            com.mnemolith.entity.echo.ScarEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(1.2F, 3.2F).eyeHeight(2.6F).clientTrackingRange(10).noLootTable().fireImmune());

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
