package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL30.*;

/**
 * Shadow Mapping (Indikator 5): Framebuffer Object yang menampung Shadow Map 2D Depth Texture.
 * Pass 1 menulis depth dari sudut pandang cahaya ke texture ini; Pass 2 membacanya di Fragment Shader.
 */
public class ShadowMapFBO {
    private final int fboID;
    private final int depthTextureID;
    private final int SHADOW_WIDTH = 2048, SHADOW_HEIGHT = 2048;

    public ShadowMapFBO() {
        fboID = glGenFramebuffers();
        depthTextureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTextureID);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, SHADOW_WIDTH, SHADOW_HEIGHT, 0,
                GL_DEPTH_COMPONENT, GL_FLOAT, (java.nio.ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        float[] borderColor = {1.0f, 1.0f, 1.0f, 1.0f}; // di luar shadow map = jarak maksimum (tidak berbayang)
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor);

        glBindFramebuffer(GL_FRAMEBUFFER, fboID);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTextureID, 0);
        glDrawBuffer(GL_NONE); // tidak ada color attachment
        glReadBuffer(GL_NONE);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Shadow Map FBO tidak lengkap (incomplete).");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bindForWriting() {
        glViewport(0, 0, SHADOW_WIDTH, SHADOW_HEIGHT);
        glBindFramebuffer(GL_FRAMEBUFFER, fboID);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    public void unbind(int screenWidth, int screenHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, screenWidth, screenHeight);
    }

    /** Mengikat depth map ke texture unit tertentu untuk dibaca di Pass 2. */
    public void bindDepthTexture(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, depthTextureID);
    }

    public int getDepthTextureID() { return depthTextureID; }

    public void cleanup() {
        glDeleteFramebuffers(fboID);
        glDeleteTextures(depthTextureID);
    }
}
