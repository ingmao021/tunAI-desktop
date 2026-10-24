package com.tunai.desktop.viewer;

import de.javagl.jgltf.model.AccessorByteData;
import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorDatas;
import de.javagl.jgltf.model.AccessorFloatData;
import de.javagl.jgltf.model.AccessorIntData;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.AccessorShortData;
import de.javagl.jgltf.model.ElementType;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.ImageModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.TextureModel;
import de.javagl.jgltf.model.io.GltfModelReader;
import de.javagl.jgltf.model.v2.MaterialModelV2;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Lee un GLB con la forma de Stable Fast 3D (§3): 1 malla, 1 material y 1 textura de color base
 * con UV, sin extensiones obligatorias. Devuelve los datos ya convertidos al sistema de JavaFX
 * (Y hacia abajo), centrados en el origen y escalados a {@link #TARGET_SIZE}.
 */
public final class GlbModel {

    private static final Logger LOG = Logger.getLogger(GlbModel.class.getName());
    private static final int MODE_TRIANGLES = 4;
    /** Tamaño de la mayor dimensión del modelo en unidades de la escena. */
    public static final float TARGET_SIZE = 200f;

    private final float[] points;
    private final float[] normals;
    private final float[] texCoords;
    private final int[] indices;
    private final byte[] textureBytes;
    private final String textureMimeType;

    private GlbModel(float[] points, float[] normals, float[] texCoords, int[] indices,
                     byte[] textureBytes, String textureMimeType) {
        this.points = points;
        this.normals = normals;
        this.texCoords = texCoords;
        this.indices = indices;
        this.textureBytes = textureBytes;
        this.textureMimeType = textureMimeType;
    }

    /** Comprueba que el GLB tiene la forma de SF3D sin construir nada de JavaFX. */
    public static void validate(byte[] glb) throws UnsupportedModelException {
        load(glb);
    }

    public static GlbModel load(byte[] glb) throws UnsupportedModelException {
        GltfModel model;
        try {
            model = new GltfModelReader().readWithoutReferences(new ByteArrayInputStream(glb));
        } catch (Exception e) {
            throw new UnsupportedModelException("GLB ilegible", e);
        }
        if (model == null) {
            throw new UnsupportedModelException("GLB ilegible");
        }

        List<String> required = model.getExtensionsModel() != null
                ? model.getExtensionsModel().getExtensionsRequired() : null;
        if (required != null && !required.isEmpty()) {
            throw new UnsupportedModelException("Extensiones obligatorias no soportadas: " + required);
        }
        List<MeshModel> meshes = model.getMeshModels();
        if (meshes.size() != 1) {
            throw new UnsupportedModelException("Se esperaba 1 malla y hay " + meshes.size());
        }
        List<MeshPrimitiveModel> primitives = meshes.get(0).getMeshPrimitiveModels();
        if (primitives.size() != 1) {
            throw new UnsupportedModelException("Se esperaba 1 primitiva y hay " + primitives.size());
        }
        if (model.getMaterialModels().size() != 1) {
            throw new UnsupportedModelException("Se esperaba 1 material y hay " + model.getMaterialModels().size());
        }
        MeshPrimitiveModel primitive = primitives.get(0);
        if (primitive.getMode() != MODE_TRIANGLES) {
            throw new UnsupportedModelException("La primitiva no es de triángulos (modo " + primitive.getMode() + ")");
        }

        MaterialModel material = primitive.getMaterialModel();
        if (!(material instanceof MaterialModelV2 pbr) || pbr.getBaseColorTexture() == null) {
            throw new UnsupportedModelException("El material no tiene textura de color base");
        }
        Integer texcoord = pbr.getBaseColorTexcoord();
        if (texcoord != null && texcoord != 0) {
            throw new UnsupportedModelException("La textura usa TEXCOORD_" + texcoord);
        }
        TextureModel texture = pbr.getBaseColorTexture();
        ImageModel image = texture.getImageModel();
        ByteBuffer imageData = image != null ? image.getImageData() : null;
        if (imageData == null || !imageData.hasRemaining()) {
            throw new UnsupportedModelException("La textura no está incrustada en el GLB");
        }
        if (model.getTextureModels().size() > 1) {
            LOG.info("El GLB trae " + model.getTextureModels().size()
                    + " texturas; solo se usa la de color base");
        }

        Map<String, AccessorModel> attributes = primitive.getAttributes();
        float[] positions = floats(attributes.get("POSITION"), ElementType.VEC3, "POSITION");
        float[] uvs = floats(attributes.get("TEXCOORD_0"), ElementType.VEC2, "TEXCOORD_0");
        int vertexCount = positions.length / 3;
        if (uvs.length / 2 != vertexCount) {
            throw new UnsupportedModelException("TEXCOORD_0 no coincide con POSITION");
        }
        float[] normalData = attributes.containsKey("NORMAL")
                ? floats(attributes.get("NORMAL"), ElementType.VEC3, "NORMAL") : null;
        if (normalData != null && normalData.length != positions.length) {
            throw new UnsupportedModelException("NORMAL no coincide con POSITION");
        }
        int[] indices = indices(primitive.getIndices(), vertexCount);
        if (indices.length == 0 || indices.length % 3 != 0) {
            throw new UnsupportedModelException("Índices de triángulos inválidos");
        }

        float[] transform = nodeTransform(model, meshes.get(0));
        applyTransform(positions, normalData, transform);
        if (normalData == null) {
            normalData = computeNormals(positions, indices);
        }
        toJavaFxAxes(positions, normalData);
        normalize(positions);

        byte[] textureBytes = new byte[imageData.remaining()];
        imageData.duplicate().get(textureBytes);
        return new GlbModel(positions, normalData, uvs, indices, textureBytes, image.getMimeType());
    }

    private static float[] floats(AccessorModel accessor, ElementType type, String name) throws UnsupportedModelException {
        if (accessor == null) {
            throw new UnsupportedModelException("Falta el atributo " + name);
        }
        if (accessor.getElementType() != type) {
            throw new UnsupportedModelException(name + " no es " + type);
        }
        AccessorData data = accessor.getAccessorData();
        if (!(data instanceof AccessorFloatData floatData)) {
            throw new UnsupportedModelException(name + " no es de tipo float");
        }
        int components = type.getNumComponents();
        float[] out = new float[floatData.getNumElements() * components];
        for (int e = 0; e < floatData.getNumElements(); e++) {
            for (int c = 0; c < components; c++) {
                out[e * components + c] = floatData.get(e, c);
            }
        }
        return out;
    }

    private static int[] indices(AccessorModel accessor, int vertexCount) throws UnsupportedModelException {
        if (accessor == null) {
            int[] seq = new int[vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                seq[i] = i;
            }
            return seq;
        }
        AccessorData data = AccessorDatas.create(accessor);
        int count = data.getNumElements();
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int v;
            if (data instanceof AccessorByteData b) {
                v = b.getInt(i);
            } else if (data instanceof AccessorShortData s) {
                v = s.getInt(i);
            } else if (data instanceof AccessorIntData n) {
                v = n.get(i);
            } else {
                throw new UnsupportedModelException("Tipo de índices no soportado");
            }
            if (v < 0 || v >= vertexCount) {
                throw new UnsupportedModelException("Índice fuera de rango: " + v);
            }
            out[i] = v;
        }
        return out;
    }

    /** Transformación global del nodo que usa la malla (matriz 4×4 por columnas), o nula. */
    private static float[] nodeTransform(GltfModel model, MeshModel mesh) {
        for (NodeModel node : model.getNodeModels()) {
            if (node.getMeshModels().contains(mesh)) {
                return node.computeGlobalTransform(null);
            }
        }
        return null;
    }

    private static void applyTransform(float[] positions, float[] normals, float[] m) {
        if (m == null) {
            return;
        }
        for (int i = 0; i < positions.length; i += 3) {
            float x = positions[i];
            float y = positions[i + 1];
            float z = positions[i + 2];
            positions[i] = m[0] * x + m[4] * y + m[8] * z + m[12];
            positions[i + 1] = m[1] * x + m[5] * y + m[9] * z + m[13];
            positions[i + 2] = m[2] * x + m[6] * y + m[10] * z + m[14];
            if (normals != null) {
                // Sin escalas no uniformes en SF3D: basta la parte de rotación, renormalizada.
                float nx = normals[i];
                float ny = normals[i + 1];
                float nz = normals[i + 2];
                normals[i] = m[0] * nx + m[4] * ny + m[8] * nz;
                normals[i + 1] = m[1] * nx + m[5] * ny + m[9] * nz;
                normals[i + 2] = m[2] * nx + m[6] * ny + m[10] * nz;
                normalizeVector(normals, i);
            }
        }
    }

    /** glTF tiene Y hacia arriba y JavaFX hacia abajo: rotación de 180° sobre X (§3, punto 1). */
    private static void toJavaFxAxes(float[] positions, float[] normals) {
        for (int i = 0; i < positions.length; i += 3) {
            positions[i + 1] = -positions[i + 1];
            positions[i + 2] = -positions[i + 2];
            normals[i + 1] = -normals[i + 1];
            normals[i + 2] = -normals[i + 2];
        }
    }

    /** Centra el modelo en el origen y escala su mayor dimensión a {@link #TARGET_SIZE}. */
    private static void normalize(float[] positions) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < positions.length; i++) {
            min[i % 3] = Math.min(min[i % 3], positions[i]);
            max[i % 3] = Math.max(max[i % 3], positions[i]);
        }
        float size = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
        float scale = size > 0 ? TARGET_SIZE / size : 1f;
        for (int i = 0; i < positions.length; i++) {
            float center = (min[i % 3] + max[i % 3]) / 2f;
            positions[i] = (positions[i] - center) * scale;
        }
    }

    /** Normales suavizadas por vértice, si el GLB no las trae. */
    static float[] computeNormals(float[] p, int[] indices) {
        float[] n = new float[p.length];
        for (int t = 0; t < indices.length; t += 3) {
            int a = indices[t] * 3;
            int b = indices[t + 1] * 3;
            int c = indices[t + 2] * 3;
            float ux = p[b] - p[a], uy = p[b + 1] - p[a + 1], uz = p[b + 2] - p[a + 2];
            float vx = p[c] - p[a], vy = p[c + 1] - p[a + 1], vz = p[c + 2] - p[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            for (int v : new int[]{a, b, c}) {
                n[v] += nx;
                n[v + 1] += ny;
                n[v + 2] += nz;
            }
        }
        for (int i = 0; i < n.length; i += 3) {
            normalizeVector(n, i);
        }
        return n;
    }

    private static void normalizeVector(float[] v, int i) {
        float len = (float) Math.sqrt(v[i] * v[i] + v[i + 1] * v[i + 1] + v[i + 2] * v[i + 2]);
        if (len > 1e-12f) {
            v[i] /= len;
            v[i + 1] /= len;
            v[i + 2] /= len;
        }
    }

    public float[] points() {
        return points;
    }

    public float[] normals() {
        return normals;
    }

    public float[] texCoords() {
        return texCoords;
    }

    public int[] indices() {
        return indices;
    }

    public int vertexCount() {
        return points.length / 3;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    public byte[] textureBytes() {
        return textureBytes;
    }

    public String textureMimeType() {
        return textureMimeType;
    }
}
