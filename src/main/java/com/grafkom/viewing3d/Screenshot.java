package com.grafkom.viewing3d;

import org.lwjgl.BufferUtils;

import java.io.File;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBImageWrite.*;

/** Menyimpan isi framebuffer ke file PNG (tekan P) untuk bahan laporan. */
public final class Screenshot {
    private Screenshot() {}

    public static String save(int width, int height, String fileName) {
        File dir = new File("screenshots");
        if (!dir.exists()) dir.mkdirs();
        String path = new File(dir, fileName).getPath();

        ByteBuffer buf = BufferUtils.createByteBuffer(width * height * 3);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, buf);
        stbi_flip_vertically_on_write(true);
        stbi_write_png(path, width, height, 3, buf, width * 3);
        return path;
    }
}
