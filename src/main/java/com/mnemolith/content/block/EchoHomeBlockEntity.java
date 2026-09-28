package com.mnemolith.content.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mnemolith.content.ModBlockEntities;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoRole;
import com.mnemolith.echo.StoredEcho;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Holds sleeping echo snapshots (O3). Soft cap keeps the list small. */
public class EchoHomeBlockEntity extends BlockEntity {
    public static final int CAP = 8;

    private final List<StoredEcho> housed = new ArrayList<>();

    public EchoHomeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ECHO_HOME.get(), pos, state);
    }

    public int size() {
        return this.housed.size();
    }

    public boolean isEmpty() {
        return this.housed.isEmpty();
    }

    public boolean isFull() {
        return this.housed.size() >= CAP;
    }

    public List<StoredEcho> housed() {
        return List.copyOf(this.housed);
    }

    public boolean offer(StoredEcho echo) {
        if (this.isFull()) {
            return false;
        }
        this.housed.add(echo);
        this.setChanged();
        return true;
    }

    public Optional<StoredEcho> takeFirst() {
        if (this.housed.isEmpty()) {
            return Optional.empty();
        }
        StoredEcho echo = this.housed.remove(0);
        this.setChanged();
        return Optional.of(echo);
    }

    public void assignRole(int index, EchoRole role) {
        if (index < 0 || index >= this.housed.size()) {
            return;
        }
        this.housed.set(index, this.housed.get(index).withRole(role));
        this.setChanged();
    }

    public Component statusLine() {
        if (this.housed.isEmpty()) {
            return Component.translatable("mnemolith.echo.home.empty");
        }
        StoredEcho first = this.housed.get(0);
        Component name = first.customName().orElse(Component.translatable("entity.mnemolith.echo.named", first.ownerName()));
        if (this.housed.size() == 1) {
            return Component.translatable("mnemolith.echo.home.one", name);
        }
        return Component.translatable("mnemolith.echo.home.many", name, this.housed.size());
    }


    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!(this.level instanceof ServerLevel level) || this.housed.isEmpty()) {
            return;
        }
        while (!this.housed.isEmpty()) {
            StoredEcho first = this.housed.get(0);
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(first.owner());
            if (owner == null || !EchoLife.wake(owner, this, pos.above())) {
                this.housed.remove(0);
                this.setChanged();
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("housed", StoredEcho.LIST_CODEC, this.housed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.housed.clear();
        input.read("housed", StoredEcho.LIST_CODEC).ifPresent(this.housed::addAll);
    }
}
