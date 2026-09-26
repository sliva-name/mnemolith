package com.mnemolith.content.block;

import com.mnemolith.content.ModBlockEntities;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.vault.ArchiveVaults;
import com.mnemolith.vault.VaultContents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * An archive vault's stored imprints and timer. Every rule lives in {@link ArchiveVaults}; this only keeps state,
 * saves it ({@code vault_imprints}) and moves it to and from the item ({@link ModDataComponents#VAULT_CONTENTS}).
 */
public class ArchiveVaultBlockEntity extends BlockEntity {
    private final List<Imprint> stored = new ArrayList<>();
    private int timer;
    /** The chunk's vault load is refreshed on the first tick after loading or placing (not inside chunk loading). */
    private boolean needsRefresh = true;

    public ArchiveVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCHIVE_VAULT.get(), pos, state);
    }

    public List<Imprint> stored() {
        return List.copyOf(this.stored);
    }

    public int count() {
        return this.stored.size();
    }

    public boolean isEmpty() {
        return this.stored.isEmpty();
    }

    public void add(Imprint imprint) {
        this.stored.add(imprint);
        this.setChanged();
    }

    /** Removes and returns the loudest stored imprint. */
    public Optional<Imprint> takeLoudest() {
        Optional<Imprint> loudest = this.stored.stream().max(Comparator.comparingInt(Imprint::pressureContribution));
        loudest.ifPresent(imprint -> {
            this.stored.remove(imprint);
            this.setChanged();
        });
        return loudest;
    }

    public boolean remove(Imprint imprint) {
        boolean removed = this.stored.remove(imprint);
        if (removed) {
            this.setChanged();
        }
        return removed;
    }

    public List<Imprint> takeAll() {
        List<Imprint> all = List.copyOf(this.stored);
        this.stored.clear();
        this.setChanged();
        return all;
    }

    public void replace(List<Imprint> imprints) {
        this.stored.clear();
        this.stored.addAll(imprints);
        this.setChanged();
    }

    public boolean drawing() {
        return this.getBlockState().hasProperty(ArchiveVaultBlock.DRAWING) && this.getBlockState().getValue(ArchiveVaultBlock.DRAWING);
    }

    /** Ticks since the last draw or feed. */
    public int advance() {
        return ++this.timer;
    }

    public void resetTimer() {
        this.timer = 0;
    }

    public boolean consumeRefresh() {
        boolean refresh = this.needsRefresh;
        this.needsRefresh = false;
        return refresh;
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, ArchiveVaultBlockEntity vault) {
        if (level instanceof ServerLevel server) {
            ArchiveVaults.tick(server, pos, vault);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel server) {
            ArchiveVaults.track(server, this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level instanceof ServerLevel server) {
            ArchiveVaults.untrack(server, this.worldPosition);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("vault_imprints", VaultContents.CODEC, new VaultContents(this.stored));
        output.putInt("vault_timer", this.timer);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.stored.clear();
        input.read("vault_imprints", VaultContents.CODEC).ifPresent(contents -> this.stored.addAll(contents.imprints()));
        this.timer = input.getIntOr("vault_timer", 0);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        VaultContents contents = components.get(ModDataComponents.VAULT_CONTENTS.get());
        if (contents != null) {
            this.stored.clear();
            this.stored.addAll(contents.imprints());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!this.stored.isEmpty()) {
            components.set(ModDataComponents.VAULT_CONTENTS.get(), new VaultContents(this.stored));
        }
    }
}
