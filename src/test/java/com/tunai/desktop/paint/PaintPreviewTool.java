package com.tunai.desktop.paint;

import com.tunai.desktop.viewer.GlbModel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Diagnóstico de la máscara de pintura con un GLB real. Uso:
 * <pre>PaintPreviewTool &lt;modelo.glb&gt; &lt;carpeta-salida&gt; [#RRGGBB]</pre>
 * Escribe la textura, la cobertura UV, la máscara y la textura recoloreada.
 */
public final class PaintPreviewTool {

    public static void main(String[] args) throws Exception {
        GlbModel model = GlbModel.load(Files.readAllBytes(Path.of(args[0])));
        Path out = Files.createDirectories(Path.of(args[1]));
        int target = Integer.parseInt((args.length > 2 ? args[2] : "#C62828").substring(1), 16);

        BufferedImage tex = ImageIO.read(new ByteArrayInputStream(model.textureBytes()));
        int w = tex.getWidth();
        int h = tex.getHeight();
        int[] px = tex.getRGB(0, 0, w, h, null, 0, w);

        long start = System.nanoTime();
        boolean[] coverage = UvCoverage.compute(model.texCoords(), model.indices(), model.normals(), w, h);
        PaintMaskStrategy strategy = new BodyPaintMaskStrategy();
        float[] mask = strategy.computeMask(px, w, h, coverage);
        int[] recolored = new Recolorer(px, mask).apply(target, 0.85);
        long ms = (System.nanoTime() - start) / 1_000_000;

        int covered = 0;
        double maskSum = 0;
        int[] maskImg = new int[px.length];
        int[] covImg = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            covered += coverage[i] ? 1 : 0;
            maskSum += mask[i];
            int g = Math.round(mask[i] * 255);
            maskImg[i] = 0xFF000000 | g << 16 | g << 8 | g;
            covImg[i] = coverage[i] ? px[i] : 0xFFFF00FF;
        }
        System.out.printf("Textura %dx%d, cobertura %.1f%%, máscara %.1f%% de lo cubierto, %d ms%n",
                w, h, 100.0 * covered / px.length, 100.0 * maskSum / Math.max(1, covered), ms);

        write(out.resolve("1-textura.png"), px, w, h);
        write(out.resolve("2-cobertura.png"), covImg, w, h);
        write(out.resolve("3-mascara.png"), maskImg, w, h);
        write(out.resolve("4-recoloreada.png"), recolored, w, h);
    }

    private static void write(Path file, int[] px, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, px, 0, w);
        ImageIO.write(img, "png", file.toFile());
    }
}
