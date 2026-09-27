package com.mnemolith.client.render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.mnemolith.Mnemolith;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/** Baked triangles of the turquoise chair. Loaded once from {@code models/entity/pleading_chair.mesh}. */
public final class ChairMesh {
    public static final Identifier MESH = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "models/entity/pleading_chair.mesh");
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/pleading_chair.png");

    private static ChairMesh loaded;

    public final float[] position;
    public final float[] normal;
    public final int[] color;
    public final int vertices;

    private ChairMesh(float[] position, float[] normal, int[] color) {
        this.position = position;
        this.normal = normal;
        this.color = color;
        this.vertices = color.length;
    }

    public static ChairMesh get() {
        ChairMesh mesh = loaded;
        if (mesh == null) {
            mesh = read();
            loaded = mesh;
        }
        return mesh;
    }

    private static ChairMesh read() {
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(MESH).orElseThrow();
            try (InputStream in = resource.open()) {
                byte[] bytes = in.readAllBytes();
                ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                int magic = buffer.getInt();
                if (magic != 0x31484350) {
                    throw new IOException("bad chair mesh");
                }
                buffer.getFloat();
                int count = buffer.getInt();
                float[] position = new float[count * 3];
                float[] normal = new float[count * 3];
                int[] color = new int[count];
                for (int i = 0; i < count; i++) {
                    position[i * 3] = buffer.getFloat();
                    position[i * 3 + 1] = buffer.getFloat();
                    position[i * 3 + 2] = buffer.getFloat();
                    int r = buffer.get() & 0xFF;
                    int g = buffer.get() & 0xFF;
                    int b = buffer.get() & 0xFF;
                    int a = buffer.get() & 0xFF;
                    color[i] = net.minecraft.util.ARGB.color(a, r, g, b);
                    normal[i * 3] = buffer.getFloat();
                    normal[i * 3 + 1] = buffer.getFloat();
                    normal[i * 3 + 2] = buffer.getFloat();
                }
                return new ChairMesh(position, normal, color);
            }
        } catch (RuntimeException | IOException ex) {
            Mnemolith.LOGGER.error("Mnemolith pleading chair mesh failed to load", ex);
            return new ChairMesh(new float[0], new float[0], new int[0]);
        }
    }
}
