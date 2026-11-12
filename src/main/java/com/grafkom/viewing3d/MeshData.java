package com.grafkom.viewing3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Data geometri mentah (posisi + UV + indeks segitiga CCW), belum memiliki normal.
 * Normal dihitung oleh MeshFactory sesuai mode Flat atau Smooth.
 */
public class MeshData {
    final List<float[]> vertices = new ArrayList<>(); // {x, y, z, u, v}
    final List<Integer> indices = new ArrayList<>();

    public int addVertex(float x, float y, float z, float u, float v) {
        vertices.add(new float[]{x, y, z, u, v});
        return vertices.size() - 1;
    }

    public void addTriangle(int a, int b, int c) {
        indices.add(a);
        indices.add(b);
        indices.add(c);
    }

    /** Quad dengan urutan titik CCW jika dilihat dari luar; dipecah menjadi 2 segitiga. */
    public void addQuad(float[] p0, float[] p1, float[] p2, float[] p3) {
        int a = addVertex(p0[0], p0[1], p0[2], p0[3], p0[4]);
        int b = addVertex(p1[0], p1[1], p1[2], p1[3], p1[4]);
        int c = addVertex(p2[0], p2[1], p2[2], p2[3], p2[4]);
        int d = addVertex(p3[0], p3[1], p3[2], p3[3], p3[4]);
        addTriangle(a, b, c);
        addTriangle(a, c, d);
    }

    /** Segitiga tunggal dengan urutan titik CCW jika dilihat dari luar. */
    public void addTri(float[] p0, float[] p1, float[] p2) {
        int a = addVertex(p0[0], p0[1], p0[2], p0[3], p0[4]);
        int b = addVertex(p1[0], p1[1], p1[2], p1[3], p1[4]);
        int c = addVertex(p2[0], p2[1], p2[2], p2[3], p2[4]);
        addTriangle(a, b, c);
    }

    public int[] indexArray() {
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }
}
