package com.mnemolith.compat.wthit;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.block.ArchiveVaultBlock;
import com.mnemolith.content.block.ArchiveVaultBlockEntity;
import com.mnemolith.content.block.PressureSensorBlock;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * WTHIT tooltips: chunk pressure, vault imprint count, residue temper/strength.
 * Jade has no NeoForge 26.2 build yet; WTHIT neo-20.0.0 is the working overlay.
 */
public final class MnemolithWthitPlugin implements IWailaPlugin {
    private static final Identifier PRESSURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "pressure");
    private static final Identifier VAULT = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "vault");
    private static final Identifier RESIDUE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "residue");

    @Override
    public void register(IRegistrar registrar) {
        registrar.addConfig(PRESSURE, true);
        registrar.addConfig(VAULT, true);
        registrar.addConfig(RESIDUE, true);

        // Sync pressure whenever the looked-at block has a block entity.
        registrar.addBlockData(PressureData.INSTANCE, BlockEntity.class);
        registrar.addComponent(PressureBody.INSTANCE, TooltipPosition.BODY, net.minecraft.world.level.block.Block.class);

        registrar.addBlockData(VaultData.INSTANCE, ArchiveVaultBlockEntity.class);
        registrar.addComponent(VaultBody.INSTANCE, TooltipPosition.BODY, ArchiveVaultBlock.class);

        registrar.addComponent(ResidueBody.INSTANCE, TooltipPosition.BODY, ResidueEntity.class);
    }

    private enum PressureData implements IDataProvider<BlockEntity> {
        INSTANCE;

        @Override
        public void appendData(IDataWriter writer, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
            if (!(accessor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            LevelChunk chunk = level.getChunkAt(accessor.getTarget().getBlockPos());
            ChunkMemory memory = LoadedChunkMemory.existing(chunk);
            int pressure = memory == null ? 0 : memory.cachedPressure();
            PressureBand band = MemoryPressure.band(pressure);
            CompoundTag tag = writer.raw();
            tag.putInt("mnemolith_pressure", pressure);
            tag.putInt("mnemolith_band", band.ordinal());
        }
    }

    private enum PressureBody implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            if (!config.getBoolean(PRESSURE)) {
                return;
            }
            int pressure = -1;
            int bandOrd = 0;
            CompoundTag data = accessor.getData().raw();
            if (data.contains("mnemolith_pressure")) {
                pressure = data.getIntOr("mnemolith_pressure", 0);
                bandOrd = data.getIntOr("mnemolith_band", 0);
            } else if (accessor.getBlock() instanceof PressureSensorBlock
                    && accessor.getBlockState().hasProperty(BlockStateProperties.POWER)) {
                int power = accessor.getBlockState().getValue(BlockStateProperties.POWER);
                bandOrd = switch (power) {
                    case 0 -> 0;
                    case 5 -> 1;
                    case 10 -> 2;
                    default -> power >= 15 ? 3 : 1;
                };
                pressure = power; // sensor shows band via power; raw score unavailable client-side
                tooltip.addLine(Component.translatable(
                        "mnemolith.wthit.pressure.band",
                        Component.translatable(PressureBand.byOrdinal(bandOrd).translationKey())));
                return;
            } else if (accessor.getLevel() instanceof ServerLevel level) {
                ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(accessor.getPosition()));
                if (memory != null) {
                    pressure = memory.cachedPressure();
                    bandOrd = MemoryPressure.band(pressure).ordinal();
                }
            }
            if (pressure < 0) {
                return;
            }
            tooltip.addLine(Component.translatable(
                    "mnemolith.wthit.pressure",
                    pressure,
                    Component.translatable(PressureBand.byOrdinal(bandOrd).translationKey())));
        }
    }

    private enum VaultData implements IDataProvider<ArchiveVaultBlockEntity> {
        INSTANCE;

        @Override
        public void appendData(IDataWriter writer, IServerAccessor<ArchiveVaultBlockEntity> accessor, IPluginConfig config) {
            writer.raw().putInt("mnemolith_vault_count", accessor.getTarget().count());
            writer.raw().putBoolean("mnemolith_vault_drawing", accessor.getTarget().drawing());
        }
    }

    private enum VaultBody implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            if (!config.getBoolean(VAULT)) {
                return;
            }
            CompoundTag data = accessor.getData().raw();
            if (!data.contains("mnemolith_vault_count")) {
                return;
            }
            int count = data.getIntOr("mnemolith_vault_count", 0);
            tooltip.addLine(Component.translatable("mnemolith.wthit.vault", count));
            if (data.getBooleanOr("mnemolith_vault_drawing", false)) {
                tooltip.addLine(Component.translatable("mnemolith.wthit.vault.drawing"));
            }
        }
    }

    private enum ResidueBody implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            if (!config.getBoolean(RESIDUE)) {
                return;
            }
            if (!(accessor.getEntity() instanceof ResidueEntity residue)) {
                return;
            }
            tooltip.addLine(Component.translatable(
                    "mnemolith.wthit.residue",
                    Component.translatable(residue.temper().key()),
                    residue.strength()));
        }
    }
}
