package com.mnemolith.client.render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.mnemolith.Mnemolith;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * Triangles of the chair scan. Loaded once from {@code models/entity/pleading_chair.mesh}.
 * The file is the GLB reduced to a triangle list: position, Minecraft UV, normal. No vertex colors.
 */
public final class ChairMesh {
    public static final Identifier MESH = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "models/entity/pleading_chair.mesh");
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/pleading_chair.png");
    /** {@code PCH2} little-endian. */
    private static final int MAGIC = 0x32484350;

    private static ChairMesh loaded;

    public final float[] position;
    public final float[] uv;
    public final float[] normal;
    public final int vertices;

    private ChairMesh(float[] position, float[] uv, float[] normal) {
        this.position = position;
        this.uv = uv;
        this.normal = normal;
        this.vertices = uv.length / 2;
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
                if (magic != MAGIC) {
                    throw new IOException("bad chair mesh");
                }
                buffer.getFloat();
                int count = buffer.getInt();
                float[] position = new float[count * 3];
                float[] uv = new float[count * 2];
                float[] normal = new float[count * 3];
                for (int i = 0; i < count; i++) {
                    position[i * 3] = buffer.getFloat();
                    position[i * 3 + 1] = buffer.getFloat();
                    position[i * 3 + 2] = buffer.getFloat();
                    uv[i * 2] = buffer.getFloat();
                    uv[i * 2 + 1] = buffer.getFloat();
                    normal[i * 3] = buffer.getFloat();
                    normal[i * 3 + 1] = buffer.getFloat();
                    normal[i * 3 + 2] = buffer.getFloat();
                }
                return new ChairMesh(position, uv, normal);
            }
        } catch (RuntimeException | IOException ex) {
            Mnemolith.LOGGER.error("Mnemolith pleading chair mesh failed to load", ex);
            return new ChairMesh(new float[0], new float[0], new float[0]);
        }
    }
}
