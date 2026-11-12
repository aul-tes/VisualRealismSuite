package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * 3D Visual Realism Suite (Minggu 11) - memperluas 3D Viewer dari Minggu 10.
 *
 * Kontrol:
 *   B  Phong <-> Blinn-Phong            (Indikator 1)
 *   F  Flat  <-> Smooth Shading         (Indikator 2)
 *   Z  Z-Buffer Depth Test ON/OFF       (Indikator 3)
 *   C  Back-Face Culling ON/OFF         (Indikator 3)
 *   T  Texture ON/OFF                   (Indikator 4)
 *   N  Nearest <-> Bilinear+Mipmap      (Indikator 4)
 *   S  Shadow Mapping ON/OFF            (Indikator 5)
 *   [ ]  Kecilkan / besarkan Shadow Bias (R = reset)   (Indikator 5)
 *   M  Ganti model (Sphere <-> House)
 *   W  Wireframe ON/OFF
 *   L  Jeda/lanjut gerak lampu     O  Putar objek ON/OFF     V  VSync ON/OFF
 *   P  Simpan screenshot ke folder ./screenshots
 *   Mouse drag = orbit kamera, scroll = zoom, ESC = keluar.
 */
public class Main {

    private record ModelPair(Mesh flat, Mesh smooth) {
        Mesh get(boolean useSmooth) { return useSmooth ? smooth : flat; }
    }

    // ---------------------------------------------------------------- window & state
    private long window;
    private final int initialWidth = 1280, initialHeight = 720;

    private boolean blinnPhong = true;
    private boolean smoothShading = true;
    private boolean depthTest = true;
    private boolean backFaceCulling = true;
    private boolean textureOn = true;
    private boolean mipmapBilinear = true;
    private boolean shadowOn = true;
    private boolean wireframe = false;
    private boolean vsync = false;
    private boolean lightPaused = false;
    private boolean objectSpin = false;
    private boolean screenshotRequested = false;
    private int modelIndex = 0; // 0 = Sphere, 1 = House
    private float biasMul = 1.0f;

    // kamera orbit
    private float yaw = 0.6f, pitch = 0.5f, distance = 9.0f;
    private boolean dragging = false;
    private double lastX, lastY;

    private float lightAngle = 0.8f;
    private float objectAngle = 0f;

    // FPS
    private int frames = 0;
    private double fpsTimer = 0;
    private double fps = 0;

    // resources
    private ShaderProgram phongShader, depthShader;
    private ModelPair[] models;
    private final String[] modelNames = {"Sphere", "House"};
    private Mesh floorMesh;
    private Texture2D texture;
    private ShadowMapFBO shadowFBO;

    // matrik
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f lightSpace = new Matrix4f();
    private final Matrix4f lightView = new Matrix4f();
    private final Matrix4f lightProj = new Matrix4f().setOrtho(-8f, 8f, -8f, 8f, 1f, 22f);
    private final Matrix4f modelMat = new Matrix4f();
    private final Matrix4f identity = new Matrix4f();
    private final Vector3f lightPos = new Vector3f();
    private final Vector3f eye = new Vector3f();
    private final Vector3f target = new Vector3f(0, 1.0f, 0);
    private final Vector3f up = new Vector3f(0, 1, 0);
    private final Vector3f origin = new Vector3f(0, 0, 0);

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    // ---------------------------------------------------------------- init
    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Gagal menginisialisasi GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(initialWidth, initialHeight, "3D Visual Realism Suite", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Gagal membuat window GLFW (butuh OpenGL 3.3+)");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(vsync ? 1 : 0);
        GL.createCapabilities();

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> onKey(key, action));
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) dragging = (action == GLFW_PRESS);
        });
        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (dragging) {
                yaw -= (float) (x - lastX) * 0.005f;
                pitch += (float) (y - lastY) * 0.005f;
                pitch = Math.max(0.05f, Math.min(1.5f, pitch));
            }
            lastX = x;
            lastY = y;
        });
        glfwSetScrollCallback(window, (w, xoff, yoff) ->
                distance = Math.max(3f, Math.min(25f, distance - (float) yoff * 0.6f)));

        // Shaders
        phongShader = new ShaderProgram("/shaders/phong.vert", "/shaders/phong.frag");
        depthShader = new ShaderProgram("/shaders/depth.vert", "/shaders/depth.frag");

        // Mesh 3D bermateri: Normal Vertex + UV (Flat dan Smooth versi)
        MeshData sphere = MeshFactory.sphere(32, 48, 1.0f);
        MeshData house = MeshFactory.house();
        models = new ModelPair[]{
                new ModelPair(MeshFactory.buildFlat(sphere), MeshFactory.buildSmooth(sphere)),
                new ModelPair(MeshFactory.buildFlat(house), MeshFactory.buildSmooth(house))
        };
        floorMesh = MeshFactory.buildFlat(MeshFactory.floor(6f, 6f));

        // Texture (cari "texture.png" di working dir / resources; jika tidak ada -> papan catur prosedural)
        texture = new Texture2D("texture.png");
        texture.setMipmapBilinear(mipmapBilinear);

        // Shadow map FBO
        shadowFBO = new ShadowMapFBO();

        printHelp();
        fpsTimer = glfwGetTime();
    }

    private void printHelp() {
        System.out.println("""
                ===== 3D Visual Realism Suite =====
                B: Phong/Blinn-Phong   F: Flat/Smooth   Z: Depth Test   C: Back-Face Culling
                T: Texture ON/OFF      N: Nearest/Bilinear+Mipmap       S: Shadow ON/OFF
                [ ]: Shadow Bias -/+   R: reset bias     M: ganti model  W: wireframe
                L: jeda lampu   O: putar objek   V: VSync   P: screenshot   ESC: keluar
                Mouse drag: orbit | Scroll: zoom
                """);
    }

    // ---------------------------------------------------------------- input
    private void onKey(int key, int action) {
        if (action != GLFW_PRESS) return;
        switch (key) {
            case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true);
            case GLFW_KEY_B -> blinnPhong = !blinnPhong;
            case GLFW_KEY_F -> smoothShading = !smoothShading;
            case GLFW_KEY_Z -> depthTest = !depthTest;
            case GLFW_KEY_C -> backFaceCulling = !backFaceCulling;
            case GLFW_KEY_T -> textureOn = !textureOn;
            case GLFW_KEY_N -> {
                mipmapBilinear = !mipmapBilinear;
                texture.setMipmapBilinear(mipmapBilinear);
            }
            case GLFW_KEY_S -> shadowOn = !shadowOn;
            case GLFW_KEY_LEFT_BRACKET -> biasMul = (biasMul <= 0.05f) ? 0f : biasMul / 2f;
            case GLFW_KEY_RIGHT_BRACKET -> biasMul = (biasMul == 0f) ? 0.125f : Math.min(biasMul * 2f, 64f);
            case GLFW_KEY_R -> biasMul = 1.0f;
            case GLFW_KEY_M -> modelIndex = (modelIndex + 1) % models.length;
            case GLFW_KEY_W -> wireframe = !wireframe;
            case GLFW_KEY_L -> lightPaused = !lightPaused;
            case GLFW_KEY_O -> objectSpin = !objectSpin;
            case GLFW_KEY_V -> {
                vsync = !vsync;
                glfwSwapInterval(vsync ? 1 : 0);
            }
            case GLFW_KEY_P -> screenshotRequested = true;
            default -> { }
        }
    }

    // ---------------------------------------------------------------- main loop
    private void loop() {
        double previous = glfwGetTime();
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) (now - previous);
            previous = now;

            if (!lightPaused) lightAngle += dt * 0.6f;
            if (objectSpin) objectAngle += dt * 0.7f;

            renderFrame();

            // FPS
            frames++;
            if (now - fpsTimer >= 0.5) {
                fps = frames / (now - fpsTimer);
                frames = 0;
                fpsTimer = now;
                glfwSetWindowTitle(window, buildTitle());
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void renderFrame() {
        int[] fbw = new int[1], fbh = new int[1];
        glfwGetFramebufferSize(window, fbw, fbh);
        int w = Math.max(1, fbw[0]), h = Math.max(1, fbh[0]);

        // --- posisi lampu & matriks cahaya
        lightPos.set(6f * (float) Math.cos(lightAngle), 7f, 6f * (float) Math.sin(lightAngle));
        lightView.identity().lookAt(lightPos, origin, up);
        lightProj.mul(lightView, lightSpace);

        // ===================== PASS 1: Depth Pass (sudut pandang cahaya) =====================
        if (shadowOn) {
            shadowFBO.bindForWriting();
            HSRManager.setupDepthTest(true);       // depth test wajib aktif untuk menyusun shadow map
            HSRManager.setupBackFaceCulling(false);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

            depthShader.use();
            depthShader.setMat4("lightSpaceMatrix", lightSpace);
            drawScene(depthShader, true);

            shadowFBO.unbind(w, h);
        } else {
            glViewport(0, 0, w, h);
        }

        // ===================== PASS 2: Lighting Pass (sudut pandang kamera) =====================
        glClearColor(0.07f, 0.08f, 0.11f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        HSRManager.setupDepthTest(depthTest);
        HSRManager.setupBackFaceCulling(backFaceCulling);
        glPolygonMode(GL_FRONT_AND_BACK, wireframe ? GL_LINE : GL_FILL);

        eye.set(
                target.x + distance * (float) (Math.cos(pitch) * Math.sin(yaw)),
                target.y + distance * (float) Math.sin(pitch),
                target.z + distance * (float) (Math.cos(pitch) * Math.cos(yaw)));
        view.identity().lookAt(eye, target, up);
        // zNear tidak terlalu dekat ke 0.0 untuk mengurangi Z-Fighting
        projection.identity().perspective((float) Math.toRadians(45.0), (float) w / h, 0.1f, 100f);

        phongShader.use();
        phongShader.setMat4("view", view);
        phongShader.setMat4("projection", projection);
        phongShader.setMat4("lightSpaceMatrix", lightSpace);
        phongShader.setVec3("lightPos", lightPos.x, lightPos.y, lightPos.z);
        phongShader.setVec3("viewPos", eye.x, eye.y, eye.z);
        phongShader.setVec3("lightColor", 1.0f, 1.0f, 1.0f);
        phongShader.setBool("useTexture", textureOn);
        phongShader.setBool("useBlinnPhong", blinnPhong);
        phongShader.setBool("useShadow", shadowOn);
        phongShader.setFloat("biasSlope", 0.01f * biasMul);
        phongShader.setFloat("biasMin", 0.001f * biasMul);
        phongShader.setInt("textureSampler", 0);
        phongShader.setInt("shadowMap", 1);

        shadowFBO.bindDepthTexture(1);  // unit 1 = Shadow Map
        texture.bind(0);                // unit 0 = tekstur objek (aktif terakhir = unit 0)

        drawScene(phongShader, false);

        if (screenshotRequested) {
            screenshotRequested = false;
            String name = buildScreenshotName();
            String path = Screenshot.save(w, h, name);
            System.out.printf("[Screenshot] %s | FPS = %.1f%n", path, fps);
        }
    }

    /** Menggambar objek utama lalu lantai. Objek digambar lebih dulu agar efek Z-Buffer OFF terlihat jelas. */
    private void drawScene(ShaderProgram sp, boolean depthOnly) {
        // Objek utama
        modelMat.identity();
        if (modelIndex == 0) {
            modelMat.translate(0f, 1.0f, 0f);
        } else {
            modelMat.scale(1.3f);
        }
        modelMat.rotateY(objectAngle);
        sp.setMat4("model", modelMat);
        if (!depthOnly) sp.setVec3("objectColor", 0.90f, 0.45f, 0.20f);
        models[modelIndex].get(smoothShading).draw();

        // Lantai (ground plane) penerima bayangan
        sp.setMat4("model", identity);
        if (!depthOnly) sp.setVec3("objectColor", 0.75f, 0.75f, 0.78f);
        floorMesh.draw();
    }

    // ---------------------------------------------------------------- helpers
    private String buildTitle() {
        return String.format(
                "3D Visual Realism Suite | FPS %.0f | %s | %s | Z:%s | Cull:%s | Tex:%s | Shadow:%s (bias x%s) | %s%s",
                fps,
                blinnPhong ? "Blinn-Phong" : "Phong",
                smoothShading ? "Smooth" : "Flat",
                depthTest ? "ON" : "OFF",
                backFaceCulling ? "ON" : "OFF",
                !textureOn ? "OFF" : (mipmapBilinear ? "Bilinear+Mipmap" : "Nearest"),
                shadowOn ? "ON" : "OFF",
                biasMul,
                modelNames[modelIndex],
                vsync ? " | VSync" : "");
    }

    private String buildScreenshotName() {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        return String.format("%s_%s_Z%s_C%s_%s_shadow%s_bias%s_%s_%s.png",
                blinnPhong ? "blinn" : "phong",
                smoothShading ? "smooth" : "flat",
                depthTest ? "on" : "off",
                backFaceCulling ? "on" : "off",
                !textureOn ? "notex" : (mipmapBilinear ? "mipmap" : "nearest"),
                shadowOn ? "on" : "off",
                biasMul,
                modelNames[modelIndex].toLowerCase(),
                time);
    }

    private void cleanup() {
        for (ModelPair m : models) {
            m.flat().cleanup();
            m.smooth().cleanup();
        }
        floorMesh.cleanup();
        texture.cleanup();
        shadowFBO.cleanup();
        phongShader.cleanup();
        depthShader.cleanup();
        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }
}
