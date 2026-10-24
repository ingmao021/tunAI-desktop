package com.tunai.desktop.viewer;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GlbTextureReplacerTest {

    @Test
    void replacesTextureAndKeepsGeometry() throws Exception {
        byte[] original = GlbModelTest.resource("BoxTextured.glb");
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0x1565C0);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);

        byte[] exported = GlbTextureReplacer.replaceBaseColorTexture(original, png.toByteArray());

        GlbModel before = GlbModel.load(original);
        GlbModel after = GlbModel.load(exported);
        assertArrayEquals(png.toByteArray(), after.textureBytes());
        assertEquals("image/png", after.textureMimeType());
        assertArrayEquals(before.points(), after.points());
        assertArrayEquals(before.texCoords(), after.texCoords());
        assertArrayEquals(before.indices(), after.indices());
        assertEquals(0, exported.length % 4);
    }
}
