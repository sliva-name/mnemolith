package com.mnemolith.entity;

import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.entity.mob.EchoStrider;
import com.mnemolith.entity.mob.MomentReplicant;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/** Attribute suppliers for the memory mobs. Damage is overwritten from config when one spawns. */
public final class ModEntityAttributes {
    private ModEntityAttributes() {}

    public static void onCreate(EntityAttributeCreationEvent event) {
        event.put(ModEntities.ECHO_STRIDER.get(), EchoStrider.createAttributes().build());
        event.put(ModEntities.ARCHIVIST.get(), Archivist.createAttributes().build());
        event.put(ModEntities.MOMENT_REPLICANT.get(), MomentReplicant.createAttributes().build());
        event.put(ModEntities.ECHO.get(), com.mnemolith.entity.echo.EchoEntity.createAttributes().build());
        event.put(ModEntities.ECHO_SHELL.get(), com.mnemolith.entity.echo.EchoShell.createAttributes().build());
        event.put(ModEntities.RESIDUE.get(), com.mnemolith.entity.echo.ResidueEntity.createAttributes().build());
        event.put(ModEntities.SCAR.get(), com.mnemolith.entity.echo.ScarEntity.createAttributes().build());
    }
}
