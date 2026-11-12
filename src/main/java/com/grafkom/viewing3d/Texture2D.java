package com.grafkom.viewing3d;

import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBImage.*;

/**
 * Texture Mapping (Indikator 4): memuat PNG/JPG memakai STBImage ke GPU Memory.
 * Jika file tidak ditemukan, dibuat tekstur papan catur prosedural sebagai cadangan
 * sehingga program tetap berjalan (pita merah di v>0.9 membantu mendeteksi arah V yang terbalik).
 *
 * Mode filtering:
 *   false -> Nearest Neighbor (GL_NEAREST)
 *   true  -> Bilinear + Mipmap (GL_LINEAR_MIPMAP_LINEAR / GL_LINEAR)
 */
public class Texture2D {
    private final int textureID;
    private boolean mipmapBilinear = true;

    /** @param filePath path file di disk (mis. "texture.png") atau resource classpath (mis. "/textures/texture.png"). */
    public Texture2D(String filePath) {
        textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);

        // Wrap Parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        boolean loaded = false;
        ByteBuffer fileBytes = readFile(filePath);
        if (fileBytes != null) {
            stbi_set_flip_vertically_on_load(true); // v=0 di bawah, sesuai konvensi UV OpenGL
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);

                ByteBuffer data = stbi_load_from_memory(fileBytes, width, height, channels, 4);
                if (data != null) {
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0), 0,
                            GL_RGBA, GL_UNSIGNED_BYTE, data);
                    stbi_image_free(data);
                    loaded = true;
                    System.out.println("[Texture2D] Tekstur dimuat: " + filePath
                            + " (" + width.get(0) + "x" + height.get(0) + ")");
                } else {
                    System.err.println("Failed to load texture: " + filePath + " -> " + stbi_failure_reason());
                }
            }
        } else {
            System.err.println("[Texture2D] File tidak ditemukan: " + filePath + " -> memakai tekstur papan catur prosedural.");
        }

        if (!loaded) uploadProceduralChecker();
        glGenerateMipmap(GL_TEXTURE_2D);
        applyFilter();
    }

    private static ByteBuffer readFile(String path) {
        byte[] bytes = null;
        try { // 1) coba dari disk (working directory)
            Path p = Path.of(path);
            if (Files.exists(p)) bytes = Files.readAllBytes(p);
        } catch (IOException | RuntimeException ignored) { }
        if (bytes == null) { // 2) coba dari classpath (src/main/resources)
            try (InputStream in = Texture2D.class.getResourceAsStream(path.startsWith("/") ? path : "/" + path)) {
                if (in != null) bytes = in.readAllBytes();
            } catch (IOException | RuntimeException ignored) { }
        }
        if (bytes == null) return null;
        ByteBuffer buf = BufferUtils.createByteBuffer(bytes.length);
        buf.put(bytes).flip();
        return buf;
    }

    private void uploadProceduralChecker() {
        final int size = 512, cell = 16;
        ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++) {       // baris 0 = v 0 (bawah)
            for (int x = 0; x < size; x++) {
                boolean odd = ((x / cell) + (y / cell)) % 2 == 0;
                int r = odd ? 235 : 45, g = odd ? 235 : 90, b = odd ? 235 : 165;
                if (y > size * 0.9) { r = odd ? 240 : 170; g = odd ? 70 : 30; b = odd ? 60 : 30; } // pita merah (v > 0.9)
                pixels.put((byte) r).put((byte) g).put((byte) b).put((byte) 255);
            }
        }
        pixels.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    }

    private void applyFilter() {
        if (mipmapBilinear) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        } else {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        }
    }

    public void setMipmapBilinear(boolean enable) {
        mipmapBilinear = enable;
        glBindTexture(GL_TEXTURE_2D, textureID);
        applyFilter();
    }

    public boolean isMipmapBilinear() { return mipmapBilinear; }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureID);
    }

    public void cleanup() { glDeleteTextures(textureID); }
}
