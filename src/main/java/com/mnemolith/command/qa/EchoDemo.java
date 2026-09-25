package com.mnemolith.command.qa;

import java.util.List;

import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.FarmLesson;
import com.mnemolith.echo.job.EchoJob;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.network.EchoNetwork;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /mnemolith echodemo <scene>} (operators): builds a small stage 3 scene south of the player with the player's
 * own echo, for manual checks and screenshots. {@code farm}: a wheat field, a chest and a farming echo;
 * {@code overload}: the same in an overloaded chunk; {@code door}, {@code gate}, {@code ladder}: a long wall with that
 * passage and an echo told to return to a point behind it.
 */
public final class EchoDemo {
    private EchoDemo() {}

    public static int run(CommandContext<CommandSourceStack> context, String scene) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        BlockPos p = player.blockPosition();
        BlockPos center = p.offset(0, 0, 6);
        flatten(level, center, 9, 7);
        EchoEntity echo = switch (scene) {
            case "farm", "overload" -> farm(level, player, center, scene.equals("overload"));
            default -> passage(level, player, center, scene);
        };
        if (echo == null) {
            context.getSource().sendFailure(Component.literal("echodemo: no echo"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("echodemo " + scene + ": echo " + echo.getId() + " at " + echo.blockPosition().toShortString()), false);
        return 1;
    }

    private static EchoEntity farm(ServerLevel level, ServerPlayer player, BlockPos center, boolean overload) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = 0; dz <= 3; dz++) {
                BlockPos soil = center.offset(dx, -1, dz);
                level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 3);
                level.setBlock(soil.above(), dz <= 1 ? ((CropBlock) Blocks.WHEAT).getStateForAge(7) : Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.setBlock(center.offset(0, -1, 4), Blocks.WATER.defaultBlockState(), 3);
        BlockPos chest = center.offset(4, 0, -2);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        FarmLesson lesson = new FarmLesson(List.of(Blocks.WHEAT), 2, 4, 4);
        EchoEntity echo = spawn(level, player, center.offset(0, 0, -2), lesson);
        if (echo == null) {
            return null;
        }
        echo.inventory().insert(new ItemStack(Items.WHEAT_SEEDS, 12));
        echo.inventory().insert(new ItemStack(Items.IRON_HOE));
        echo.job().setRadius(5);
        echo.job().setChest(chest);
        if (overload) {
            ImprintWriter.spike(level, echo.blockPosition(), 55);
        }
        echo.job().startFarming(echo);
        return echo;
    }

    private static EchoEntity passage(ServerLevel level, ServerPlayer player, BlockPos center, String scene) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        int height = scene.equals("ladder") ? 4 : 3;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = 0; dy < height; dy++) {
                level.setBlock(center.offset(dx, dy, 0), stone, 3);
            }
        }
        switch (scene) {
            case "door" -> {
                BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
                level.setBlock(center, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2 | 16);
                level.setBlock(center.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2 | 16);
            }
            case "gate" -> {
                level.setBlock(center, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH), 2 | 16);
                level.setBlock(center.above(), Blocks.AIR.defaultBlockState(), 3);
            }
            default -> {
                for (int dy = 0; dy < height; dy++) {
                    level.setBlock(center.offset(0, dy, -1), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH), 2 | 16);
                    level.setBlock(center.offset(0, dy, 1), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH), 2 | 16);
                }
            }
        }
        EchoEntity echo = spawn(level, player, center.offset(0, 0, -4), FarmLesson.NONE);
        if (echo == null) {
            return null;
        }
        echo.job().setWorkAnchor(center.offset(0, 0, 6));
        EchoNetwork.applyCommand(player, echo.getId(), EchoJob.Order.RETURN, false);
        return echo;
    }

    private static EchoEntity spawn(ServerLevel level, ServerPlayer player, BlockPos at, FarmLesson farm) {
        Vec3 origin = Vec3.atBottomCenterOf(at);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, origin, origin.x, origin.y, origin.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        EchoRecording recording = new EchoRecording(player.getUUID(), player.getGameProfile().name(), level.dimension(), origin, buffer.array(), List.of());
        EchoEntity echo = EchoLife.spawn(level, player, recording, EchoLesson.NONE, farm);
        if (echo != null) {
            echo.stopReplay();
            echo.snapTo(origin, 180.0F, 0.0F);
        }
        return echo;
    }

    private static void flatten(ServerLevel level, BlockPos center, int radius, int height) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.setBlock(center.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int dy = 0; dy <= height; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
}
