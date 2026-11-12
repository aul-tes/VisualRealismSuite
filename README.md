# 3D Visual Realism Suite (Grafika Komputer - Minggu 11)

Java 17+ | LWJGL 3.3.4 | JOML 1.10.5 | OpenGL 3.3 core (GLSL 330)

## Cara menjalankan
**IntelliJ IDEA / Eclipse:** Open -> pilih `pom.xml` -> tunggu Maven mengunduh library -> jalankan `com.grafkom.viewing3d.Main`.
**Terminal:** `mvn compile exec:java`
macOS: tambahkan VM option `-XstartOnFirstThread`.

Tekstur: letakkan file `texture.png` di folder project (working directory) atau di `src/main/resources/`.
Jika tidak ada, program memakai tekstur papan catur prosedural.

## Kontrol
| Tombol | Fungsi | Indikator |
|---|---|---|
| B | Phong <-> Blinn-Phong | 1 |
| F | Flat <-> Smooth Shading | 2 |
| Z | Z-Buffer Depth Test ON/OFF | 3 |
| C | Back-Face Culling ON/OFF | 3 |
| T | Texture ON/OFF | 4 |
| N | Nearest <-> Bilinear + Mipmap | 4 |
| S | Shadow Mapping ON/OFF | 5 |
| [ / ] | Perkecil / perbesar Shadow Bias (bias 0 = Shadow Acne) | 5 |
| R | Reset bias | 5 |
| M | Ganti model Sphere <-> House | - |
| W | Wireframe | - |
| L / O / V | Jeda lampu / putar objek / VSync | - |
| P | Simpan screenshot ke `./screenshots` (nama file memuat status fitur) | - |
| Mouse drag / scroll | Orbit / zoom kamera | - |

FPS dan status semua fitur tampil di judul window.

## Struktur
- `Main` - window GLFW, input, render loop 2-pass (Depth Pass lalu Lighting Pass)
- `ShaderProgram` - compile/link GLSL + uniform
- `Mesh`, `MeshData`, `MeshFactory` - geometri Sphere/House/Floor, normal vertex (averaging) dan UV, versi Flat & Smooth
- `HSRManager` - Depth Test & Back-Face Culling
- `Texture2D` - STBImage loader, UV, Nearest vs Bilinear+Mipmap
- `ShadowMapFBO` - FBO Depth Texture 2048x2048
- `Screenshot` - simpan framebuffer ke PNG
- `resources/shaders` - `phong.vert/frag` (Blinn-Phong per-pixel + shadow PCF), `depth.vert/frag`
