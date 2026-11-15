package com.tunai.desktop.paint;

import java.util.HashMap;
import java.util.Map;

/**
 * Descarta la pintura que la máscara de color marcó fuera de la carrocería, como los rines cromados.
 *
 * <p>La máscara solo mira el color: un rin plateado o cromado se parece a una carrocería plateada. Pero
 * geométricamente es otra pieza: el neumático oscuro (que no es pintura) lo separa del cuerpo. Se unen
 * los triángulos pintados que comparten vértices (soldados por posición, porque las costuras UV los
 * duplican) y solo se conservan las islas grandes; las pequeñas, desconectadas del cuerpo, no son
 * carrocería (rines, interior, espejos sueltos, antenas).</p>
 */
public final class PaintIslandFilter {

    /** Una isla se conserva si su área 3D es al menos esta fracción de la mayor. */
    static final double MIN_ISLAND_SHARE = 0.10;
    /** Si descartar islas quitara más de esta fracción del área pintada, el filtro se ignora. */
    static final double MAX_REMOVED_SHARE = 0.60;
    /** Promedio de máscara desde el que un triángulo cuenta como pintado. */
    static final double PAINTED = 0.5;
    /** Promedio de máscara desde el que un triángulo toca la pintura (borde de una isla). */
    static final double TOUCHED = 0.02;
    /** Los vértices a menos de 1/200 de unidad se consideran el mismo (el modelo mide 200 de lado). */
    private static final double WELD_SCALE = 200.0;
    private static final int COORD_OFFSET = 1 << 20;
    private static final long COORD_MASK = (1L << 21) - 1;

    private PaintIslandFilter() {
    }

    /**
     * @param mask      máscara de pintura por píxel de la textura
     * @param points    posiciones por vértice (x, y, z)
     * @param texCoords coordenadas UV por vértice
     * @param indices   tres índices por triángulo
     * @return los píxeles de la textura que pertenecen a islas descartadas y deben quedar sin pintar, o
     *         {@code null} si no hay nada que descartar o el filtro no es fiable para este modelo
     */
    public static boolean[] droppedPixels(float[] mask, int width, int height,
                                          float[] points, float[] texCoords, int[] indices) {
        int triangleCount = indices.length / 3;
        int[] node = weld(points);
        int[] parent = new int[points.length / 3];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        double[] share = new double[triangleCount];
        for (int t = 0; t < triangleCount; t++) {
            share[t] = averageMask(mask, width, height, texCoords, indices[t * 3], indices[t * 3 + 1], indices[t * 3 + 2]);
            if (share[t] > PAINTED) {
                union(parent, node[indices[t * 3]], node[indices[t * 3 + 1]]);
                union(parent, node[indices[t * 3]], node[indices[t * 3 + 2]]);
            }
        }

        double[] area = new double[parent.length];
        for (int t = 0; t < triangleCount; t++) {
            if (share[t] > PAINTED) {
                area[find(parent, node[indices[t * 3]])] += triangleArea(points, indices, t);
            }
        }
        double total = 0;
        double biggest = 0;
        for (double a : area) {
            total += a;
            biggest = Math.max(biggest, a);
        }
        if (biggest <= 0) {
            return null;
        }
        double removed = 0;
        for (double a : area) {
            if (a > 0 && a < MIN_ISLAND_SHARE * biggest) {
                removed += a;
            }
        }
        if (removed <= 0 || removed > MAX_REMOVED_SHARE * total) {
            return null;
        }

        int[] keep = new int[indices.length];
        int[] drop = new int[indices.length];
        int keepCount = 0;
        int dropCount = 0;
        for (int t = 0; t < triangleCount; t++) {
            if (share[t] <= TOUCHED) {
                continue;
            }
            boolean touchesKept = false;
            boolean touchesDropped = false;
            for (int k = 0; k < 3; k++) {
                double islandArea = area[find(parent, node[indices[t * 3 + k]])];
                if (islandArea <= 0) {
                    continue; // vértice que no pertenece a ninguna isla pintada
                }
                if (islandArea >= MIN_ISLAND_SHARE * biggest) {
                    touchesKept = true;
                } else {
                    touchesDropped = true;
                }
            }
            // Un triángulo a medio pintar (borde) sigue la suerte de la isla que toca; si toca dos, gana la conservada.
            if (touchesKept) {
                System.arraycopy(indices, t * 3, keep, keepCount, 3);
                keepCount += 3;
            } else if (touchesDropped) {
                System.arraycopy(indices, t * 3, drop, dropCount, 3);
                dropCount += 3;
            }
        }
        boolean[] dropped = UvCoverage.compute(texCoords, java.util.Arrays.copyOf(drop, dropCount), width, height);
        boolean[] kept = UvCoverage.compute(texCoords, java.util.Arrays.copyOf(keep, keepCount), width, height);
        for (int i = 0; i < dropped.length; i++) {
            // El relleno de UV deja un píxel de margen: en el borde común manda lo conservado.
            dropped[i] = dropped[i] && !kept[i];
        }
        return dropped;
    }

    /** Vértice → identificador de posición única. */
    private static int[] weld(float[] points) {
        int vertexCount = points.length / 3;
        Map<Long, Integer> ids = new HashMap<>(vertexCount * 2);
        int[] node = new int[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            long key = (quantize(points[v * 3]) << 42) | (quantize(points[v * 3 + 1]) << 21) | quantize(points[v * 3 + 2]);
            Integer id = ids.get(key);
            if (id == null) {
                id = ids.size();
                ids.put(key, id);
            }
            node[v] = id;
        }
        return node;
    }

    private static long quantize(float v) {
        return (Math.round(v * WELD_SCALE) + COORD_OFFSET) & COORD_MASK;
    }

    /** Media de la máscara en el centroide del triángulo y en los puntos medios hacia cada vértice. */
    private static double averageMask(float[] mask, int w, int h, float[] uv, int a, int b, int c) {
        double cu = (uv[a * 2] + uv[b * 2] + uv[c * 2]) / 3;
        double cv = (uv[a * 2 + 1] + uv[b * 2 + 1] + uv[c * 2 + 1]) / 3;
        double sum = at(mask, w, h, cu, cv);
        for (int vertex : new int[]{a, b, c}) {
            sum += at(mask, w, h, (uv[vertex * 2] + cu) / 2, (uv[vertex * 2 + 1] + cv) / 2);
        }
        return sum / 4;
    }

    private static double at(float[] mask, int w, int h, double u, double v) {
        double uu = u - Math.floor(u);
        double vv = v - Math.floor(v);
        int x = Math.min(w - 1, (int) (uu * w));
        int y = Math.min(h - 1, (int) (vv * h));
        return mask[y * w + x];
    }

    private static double triangleArea(float[] p, int[] indices, int t) {
        int a = indices[t * 3] * 3;
        int b = indices[t * 3 + 1] * 3;
        int c = indices[t * 3 + 2] * 3;
        double ux = p[b] - p[a];
        double uy = p[b + 1] - p[a + 1];
        double uz = p[b + 2] - p[a + 2];
        double vx = p[c] - p[a];
        double vy = p[c + 1] - p[a + 1];
        double vz = p[c + 2] - p[a + 2];
        double cx = uy * vz - uz * vy;
        double cy = uz * vx - ux * vz;
        double cz = ux * vy - uy * vx;
        return 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    private static int find(int[] parent, int a) {
        int x = a;
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[rb] = ra;
        }
    }
}
