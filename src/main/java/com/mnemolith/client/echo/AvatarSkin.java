package com.mnemolith.client.echo;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

/** Resolves the owner's skin for an echo or shell, the same way the vanilla mannequin does. */
final class AvatarSkin {
    private @Nullable CompletableFuture<Optional<PlayerSkin>> lookup;
    private PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();

    void request(ResolvableProfile profile) {
        if (this.lookup != null) {
            this.lookup.cancel(false);
        }
        GameProfile partial = profile.partialProfile();
        if (partial != null && partial.id() != null) {
            this.skin = DefaultPlayerSkin.get(partial);
        }
        this.lookup = Minecraft.getInstance().playerSkinRenderCache().lookup(profile).thenApply(info -> info.map(PlayerSkinRenderCache.RenderInfo::playerSkin));
    }

    void tick() {
        if (this.lookup != null && this.lookup.isDone()) {
            try {
                this.lookup.get().ifPresent(found -> this.skin = found);
            } catch (Exception e) {
                Mnemolith.LOGGER.debug("Mnemolith echo skin lookup failed", e);
            }
            this.lookup = null;
        }
    }

    PlayerSkin skin() {
        return this.skin;
    }
}
