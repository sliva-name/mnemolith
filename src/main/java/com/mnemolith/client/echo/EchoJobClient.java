package com.mnemolith.client.echo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.gui.GuiArt;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.network.EchoGhostPayload;
import com.mnemolith.network.EchoJobPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client side of echo jobs: the "link a chest" and "place the blueprint" modes started from the echo screen, and the
 * light pink ghost of a blueprint (while placing, and while your echo builds it). The server decides everything;
 * this only aims, previews and sends {@link EchoJobPayload}s.
 */
public final class EchoJobClient {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "echo_job_hints");
    /** Ghost of a blueprint being built. */
    private static final int GHOST = 0x55FFB6DD;
    /** Ghost while choosing where to place (a little stronger so it reads against the world). */
    private static final int PREVIEW = 0x80FFB6DD;
    private static final int PREVIEW_ANCHOR = 0xA0FFD6EC;
    private static final int PINK = 0xFFFFC6E6;
    private static final int CHIP = 0xB0200A18;
    private static final int MODE_TICKS = 20 * 60;

    private enum Mode {
        NONE,
        LINK,
        PLACE
    }

    private record Ghost(BlockPos anchor, Rotation rotation, EchoLesson.Blueprint blueprint, List<EchoLesson.Entry> placed) {
        static Ghost of(BlockPos anchor, Rotation rotation, EchoLesson.Blueprint blueprint) {
            return new Ghost(anchor, rotation, blueprint, blueprint.placed(anchor, rotation));
        }
    }

    private static final Map<Integer, Ghost> BUILDING = new HashMap<>();
    private static Mode mode = Mode.NONE;
    private static int echoId = -1;
    private static EchoLesson.@Nullable Blueprint blueprint;
    private static @Nullable Ghost preview;
    private static int modeTicks;
    private static @Nullable Component flash;
    private static int flashTicks;
    private static boolean sneakWasDown;

    private EchoJobClient() {}

    // ---- entry points (echo screen) ----

    public static void beginLink(int id) {
        mode = Mode.LINK;
        echoId = id;
        modeTicks = MODE_TICKS;
        sneakWasDown = true;
    }

    public static void beginPlacement(int id, EchoLesson.Blueprint print) {
        mode = Mode.PLACE;
        echoId = id;
        blueprint = print;
        preview = null;
        modeTicks = MODE_TICKS;
        sneakWasDown = true;
    }

    public static boolean placing() {
        return mode == Mode.PLACE;
    }

    public static void acceptGhost(EchoGhostPayload payload) {
        if (payload.blueprint().isEmpty()) {
            BUILDING.remove(payload.entityId());
        } else {
            BUILDING.put(payload.entityId(), Ghost.of(payload.anchor(), payload.rotation(), payload.blueprint().get()));
        }
    }

    private static void cancel(boolean announce) {
        mode = Mode.NONE;
        echoId = -1;
        blueprint = null;
        preview = null;
        if (announce) {
            flash(Component.translatable("mnemolith.hint.echo_cancelled"));
        }
    }

    private static void flash(Component text) {
        flash = text;
        flashTicks = 40;
    }

    // ---- events ----

    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (flashTicks > 0 && --flashTicks == 0) {
            flash = null;
        }
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            BUILDING.clear();
            cancel(false);
            return;
        }
        BUILDING.keySet().removeIf(id -> level.getEntity(id) == null);
        if (mode == Mode.NONE) {
            return;
        }
        if (--modeTicks <= 0 || level.getEntity(echoId) == null) {
            cancel(true);
            return;
        }
        boolean sneak = minecraft.options.keyShift.isDown();
        if (sneak && !sneakWasDown && minecraft.gui.screen() == null) {
            cancel(true);
            return;
        }
        sneakWasDown = sneak;
        if (mode == Mode.PLACE && blueprint != null) {
            net.minecraft.core.Direction facing = player.getDirection();
            Rotation rotation = blueprint.rotationTo(facing);
            preview = aimAnchor(minecraft, level).map(anchor -> Ghost.of(awayFromViewer(anchor, rotation, facing, blueprint), rotation, blueprint)).orElse(null);
        }
    }

    /**
     * The anchor is the first-placed corner, so a blueprint may reach back toward the viewer. Slide it along the
     * view direction so that its nearest row starts at the aimed block and the whole build lies in front of you.
     */
    private static BlockPos awayFromViewer(BlockPos aimed, Rotation rotation, net.minecraft.core.Direction facing, EchoLesson.Blueprint blueprint) {
        int nearest = 0;
        for (EchoLesson.Entry entry : blueprint.placed(aimed, rotation)) {
            BlockPos d = entry.offset().subtract(aimed);
            nearest = Math.min(nearest, d.getX() * facing.getStepX() + d.getZ() * facing.getStepZ());
        }
        return aimed.relative(facing, -nearest);
    }

    /** Where the blueprint anchor goes: the block in front of the face you look at (or the looked-at block if it can be replaced). */
    private static java.util.Optional<BlockPos> aimAnchor(Minecraft minecraft, ClientLevel level) {
        HitResult hit = minecraft.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return java.util.Optional.empty();
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        return java.util.Optional.of(state.canBeReplaced() ? pos : pos.relative(blockHit.getDirection()));
    }

    /** Right-click while linking or placing does that instead of the normal use (so a chest does not open). */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (mode == Mode.NONE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isAttack() || event.isPickBlock()) {
            if (event.isAttack()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
            return;
        }
        if (!event.isUseItem()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (mode == Mode.LINK) {
            if (minecraft.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
                ClientPacketDistributor.sendToServer(new EchoJobPayload(echoId, EchoJobPayload.Action.LINK_CHEST, blockHit.getBlockPos(), 0));
                cancel(false);
            }
        } else if (mode == Mode.PLACE && preview != null) {
            ClientPacketDistributor.sendToServer(new EchoJobPayload(echoId, EchoJobPayload.Action.PLACE_BLUEPRINT, preview.anchor(), preview.rotation().ordinal()));
            BUILDING.put(echoId, preview);
            cancel(false);
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BUILDING.clear();
        cancel(false);
    }

    // ---- ghost rendering ----

    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || (BUILDING.isEmpty() && preview == null)) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Ghost shown = preview;
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
            for (Ghost ghost : BUILDING.values()) {
                for (EchoLesson.Entry entry : ghost.placed()) {
                    BlockState now = level.getBlockState(entry.offset());
                    if (now.getBlock() != entry.state().getBlock()) {
                        cube(pose, buffer, entry.offset(), GHOST, 0.03F);
                    }
                }
            }
            if (shown != null) {
                for (EchoLesson.Entry entry : shown.placed()) {
                    cube(pose, buffer, entry.offset(), entry.offset().equals(shown.anchor()) ? PREVIEW_ANCHOR : PREVIEW, 0.02F);
                }
            }
        });
        poseStack.popPose();
    }

    private static void cube(PoseStack.Pose pose, VertexConsumer buffer, BlockPos pos, int color, float inset) {
        float x0 = pos.getX() + inset;
        float y0 = pos.getY() + inset;
        float z0 = pos.getZ() + inset;
        float x1 = pos.getX() + 1.0F - inset;
        float y1 = pos.getY() + 1.0F - inset;
        float z1 = pos.getZ() + 1.0F - inset;
        // Faces get slightly different shades so the shape reads without lighting.
        int top = color;
        int side = shade(color, 0.88F);
        int bottom = shade(color, 0.72F);
        quad(pose, buffer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, top);
        quad(pose, buffer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, bottom);
        quad(pose, buffer, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, side);
        quad(pose, buffer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, side);
        quad(pose, buffer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, shade(color, 0.8F));
        quad(pose, buffer, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, shade(color, 0.8F));
    }

    private static int shade(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz, int color) {
        buffer.addVertex(pose, ax, ay, az).setColor(color);
        buffer.addVertex(pose, bx, by, bz).setColor(color);
        buffer.addVertex(pose, cx, cy, cz).setColor(color);
        buffer.addVertex(pose, dx, dy, dz).setColor(color);
    }

    // ---- HUD ----

    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, EchoJobClient::renderHud);
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null || minecraft.player == null) {
            return;
        }
        Component line = switch (mode) {
            case LINK -> Component.translatable("mnemolith.hint.echo_link");
            case PLACE -> Component.translatable("mnemolith.hint.echo_place", blueprint == null ? 0 : blueprint.size());
            default -> flash;
        };
        if (line == null) {
            return;
        }
        Font font = minecraft.font;
        int width = font.width(line);
        int x = graphics.guiWidth() / 2 - width / 2;
        int y = graphics.guiHeight() / 2 + 18;
        graphics.fill(x - 4, y - 2, x + width + 4, y + 10, CHIP);
        GuiArt.label(graphics, font, line, x, y, PINK);
    }
}
