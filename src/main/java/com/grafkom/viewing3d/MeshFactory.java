package com.grafkom.viewing3d;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Pembuat model 3D (Sphere, House, Floor) beserta perhitungan Vektor Normal Vertex dan UV.
 *
 *  - buildFlat   : Flat Shading   -> satu normal per face (vertex diduplikasi per segitiga).
 *  - buildSmooth : Smooth Shading -> N_vertex = (sum N_face_i) / ||sum N_face_i||
 *                  (Vertex Normal Averaging; vertex dengan posisi sama digabung/"welded").
 */
public final class MeshFactory {
    private MeshFactory() {}

    private record PosKey(int x, int y, int z) {
        static PosKey of(float[] v) {
            return new PosKey(Math.round(v[0] * 10000f), Math.round(v[1] * 10000f), Math.round(v[2] * 10000f));
        }
    }

    // ------------------------------------------------------------------ normal builders

    private static Vector3f faceNormal(float[] a, float[] b, float[] c) {
        Vector3f ab = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
        Vector3f ac = new Vector3f(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
        Vector3f n = ab.cross(ac);
        if (n.lengthSquared() < 1e-12f) return null; // segitiga degenerate (mis. di kutub bola)
        return n.normalize();
    }

    public static Mesh buildFlat(MeshData d) {
        int[] idx = d.indexArray();
        float[] out = new float[(idx.length / 3) * 3 * 8];
        int[] outIdx = new int[(idx.length / 3) * 3];
        int o = 0, vi = 0;
        for (int t = 0; t < idx.length; t += 3) {
            float[] a = d.vertices.get(idx[t]);
            float[] b = d.vertices.get(idx[t + 1]);
            float[] c = d.vertices.get(idx[t + 2]);
            Vector3f n = faceNormal(a, b, c);
            if (n == null) continue;
            for (float[] v : new float[][]{a, b, c}) {
                out[o++] = v[0]; out[o++] = v[1]; out[o++] = v[2];
                out[o++] = n.x;  out[o++] = n.y;  out[o++] = n.z;
                out[o++] = v[3]; out[o++] = v[4];
                outIdx[vi] = vi;
                vi++;
            }
        }
        return new Mesh(java.util.Arrays.copyOf(out, o), java.util.Arrays.copyOf(outIdx, vi));
    }

    public static Mesh buildSmooth(MeshData d) {
        int[] idx = d.indexArray();
        Map<PosKey, Vector3f> accum = new HashMap<>();

        // 1) jumlahkan normal setiap face ke semua vertex yang berbagi posisi yang sama
        for (int t = 0; t < idx.length; t += 3) {
            float[] a = d.vertices.get(idx[t]);
            float[] b = d.vertices.get(idx[t + 1]);
            float[] c = d.vertices.get(idx[t + 2]);
            Vector3f n = faceNormal(a, b, c);
            if (n == null) continue;
            for (float[] v : new float[][]{a, b, c}) {
                accum.computeIfAbsent(PosKey.of(v), k -> new Vector3f()).add(n);
            }
        }

        // 2) normalisasi hasil penjumlahan -> N_vertex
        float[] out = new float[d.vertices.size() * 8];
        int o = 0;
        for (float[] v : d.vertices) {
            Vector3f n = accum.getOrDefault(PosKey.of(v), new Vector3f(0, 1, 0));
            if (n.lengthSquared() < 1e-12f) n = new Vector3f(0, 1, 0);
            else n = new Vector3f(n).normalize();
            out[o++] = v[0]; out[o++] = v[1]; out[o++] = v[2];
            out[o++] = n.x;  out[o++] = n.y;  out[o++] = n.z;
            out[o++] = v[3]; out[o++] = v[4];
        }
        return new Mesh(out, idx);
    }

    // ------------------------------------------------------------------ model geometry

    /** UV Sphere. u searah putaran sehingga tekstur tidak terbalik jika dilihat dari luar; v=1 di kutub atas. */
    public static MeshData sphere(int stacks, int slices, float radius) {
        MeshData d = new MeshData();
        for (int i = 0; i <= stacks; i++) {
            double theta = Math.PI * i / stacks;
            for (int j = 0; j <= slices; j++) {
                double phi = 2.0 * Math.PI * j / slices;
                float x = (float) (Math.sin(theta) * Math.cos(phi));
                float y = (float) Math.cos(theta);
                float z = (float) (Math.sin(theta) * Math.sin(phi));
                d.addVertex(radius * x, radius * y, radius * z, 1f - (float) j / slices, 1f - (float) i / stacks);
            }
        }
        int stride = slices + 1;
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int a = i * stride + j;
                int b = (i + 1) * stride + j;
                int c = (i + 1) * stride + j + 1;
                int e = i * stride + j + 1;
                d.addTriangle(a, e, c); // CCW dilihat dari luar
                d.addTriangle(a, c, b);
            }
        }
        return d;
    }

    private static float[] p(float x, float y, float z, float u, float v) { return new float[]{x, y, z, u, v}; }

    /** Mesh Rumah 3D: balok (alas 2x2, tinggi 1.5) + atap pelana (tinggi puncak 2.5). Semua face CCW dari luar. */
    public static MeshData house() {
        MeshData d = new MeshData();
        float h = 1.5f, top = 2.5f;
        // Dinding
        d.addQuad(p(-1, 0, 1, 0, 0), p(1, 0, 1, 1, 0), p(1, h, 1, 1, 1), p(-1, h, 1, 0, 1));      // depan (+z)
        d.addQuad(p(1, 0, -1, 0, 0), p(-1, 0, -1, 1, 0), p(-1, h, -1, 1, 1), p(1, h, -1, 0, 1));  // belakang (-z)
        d.addQuad(p(1, 0, 1, 0, 0), p(1, 0, -1, 1, 0), p(1, h, -1, 1, 1), p(1, h, 1, 0, 1));      // kanan (+x)
        d.addQuad(p(-1, 0, -1, 0, 0), p(-1, 0, 1, 1, 0), p(-1, h, 1, 1, 1), p(-1, h, -1, 0, 1));  // kiri (-x)
        d.addQuad(p(-1, 0, -1, 0, 0), p(1, 0, -1, 1, 0), p(1, 0, 1, 1, 1), p(-1, 0, 1, 0, 1));    // alas (-y)
        // Atap
        d.addQuad(p(1, h, 1, 0, 0), p(1, h, -1, 1, 0), p(0, top, -1, 1, 1), p(0, top, 1, 0, 1));  // lereng kanan
        d.addQuad(p(-1, h, -1, 0, 0), p(-1, h, 1, 1, 0), p(0, top, 1, 1, 1), p(0, top, -1, 0, 1)); // lereng kiri
        // Gable (segitiga depan & belakang)
        d.addTri(p(-1, h, 1, 0, 0), p(1, h, 1, 1, 0), p(0, top, 1, 0.5f, 1));
        d.addTri(p(1, h, -1, 0, 0), p(-1, h, -1, 1, 0), p(0, top, -1, 0.5f, 1));
        return d;
    }

    /** Lantai (ground plane) di y = 0, normal +y, UV diulang sebanyak 'tiles' kali. */
    public static MeshData floor(float halfSize, float tiles) {
        MeshData d = new MeshData();
        d.addQuad(p(-halfSize, 0, -halfSize, 0, 0), p(-halfSize, 0, halfSize, 0, tiles),
                  p(halfSize, 0, halfSize, tiles, tiles), p(halfSize, 0, -halfSize, tiles, 0));
        return d;
    }
}
