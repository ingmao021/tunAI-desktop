package com.tunai.desktop.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Exportar GLB (§5.5): el GLB original con la textura de color base reemplazada por la editada.
 * Reescribe los chunks JSON y BIN directamente: el resto del modelo queda byte a byte igual.
 */
public final class GlbTextureReplacer {

    private static final int MAGIC = 0x46546C67; // "glTF"
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GlbTextureReplacer() {
    }

    public static byte[] replaceBaseColorTexture(byte[] glb, byte[] png) throws IOException {
        ByteBuffer in = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        if (glb.length < 20 || in.getInt(0) != MAGIC || in.getInt(4) != 2) {
            throw new IOException("No es un GLB 2.0");
        }
        int jsonLength = in.getInt(12);
        if (in.getInt(16) != CHUNK_JSON) {
            throw new IOException("Falta el chunk JSON");
        }
        ObjectNode json = (ObjectNode) MAPPER.readTree(new String(glb, 20, jsonLength, StandardCharsets.UTF_8));
        int binStart = 20 + jsonLength;
        if (binStart + 8 > glb.length || in.getInt(binStart + 4) != CHUNK_BIN) {
            throw new IOException("Falta el chunk BIN");
        }
        int binLength = in.getInt(binStart);
        byte[] bin = Arrays.copyOfRange(glb, binStart + 8, binStart + 8 + binLength);

        int imageIndex = baseColorImage(json);
        ObjectNode image = (ObjectNode) json.path("images").path(imageIndex);
        int targetView = image.path("bufferView").asInt(-1);
        if (targetView < 0) {
            throw new IOException("La textura no está incrustada");
        }

        ArrayNode views = (ArrayNode) json.path("bufferViews");
        ByteArrayOutputStream newBin = new ByteArrayOutputStream(bin.length + png.length);
        for (int v = 0; v < views.size(); v++) {
            ObjectNode view = (ObjectNode) views.get(v);
            if (view.path("buffer").asInt(0) != 0) {
                continue;
            }
            byte[] data;
            if (v == targetView) {
                data = png;
            } else {
                int offset = view.path("byteOffset").asInt(0);
                data = Arrays.copyOfRange(bin, offset, offset + view.path("byteLength").asInt());
            }
            pad(newBin, 4, (byte) 0);
            view.put("byteOffset", newBin.size());
            view.put("byteLength", data.length);
            newBin.writeBytes(data);
        }
        pad(newBin, 4, (byte) 0);
        image.put("mimeType", "image/png");
        ((ObjectNode) json.path("buffers").path(0)).put("byteLength", newBin.size());

        ByteArrayOutputStream jsonOut = new ByteArrayOutputStream();
        jsonOut.writeBytes(MAPPER.writeValueAsBytes(json));
        pad(jsonOut, 4, (byte) ' ');

        byte[] jsonBytes = jsonOut.toByteArray();
        byte[] binBytes = newBin.toByteArray();
        int total = 12 + 8 + jsonBytes.length + 8 + binBytes.length;
        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(MAGIC).putInt(2).putInt(total);
        out.putInt(jsonBytes.length).putInt(CHUNK_JSON).put(jsonBytes);
        out.putInt(binBytes.length).putInt(CHUNK_BIN).put(binBytes);
        return out.array();
    }

    private static int baseColorImage(JsonNode json) throws IOException {
        JsonNode texture = json.path("materials").path(0).path("pbrMetallicRoughness").path("baseColorTexture");
        if (texture.isMissingNode()) {
            throw new IOException("El material no tiene textura de color base");
        }
        int source = json.path("textures").path(texture.path("index").asInt()).path("source").asInt(-1);
        if (source < 0) {
            throw new IOException("La textura no tiene imagen");
        }
        return source;
    }

    private static void pad(ByteArrayOutputStream out, int alignment, byte fill) {
        while (out.size() % alignment != 0) {
            out.write(fill);
        }
    }
}
