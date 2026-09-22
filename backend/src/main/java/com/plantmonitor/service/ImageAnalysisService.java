package com.plantmonitor.service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Heurística de cor pra estimar saúde da planta (verde vs. amarelo/marrom),
 * porte 1:1 de services/imageAnalysis.js da versão Node.js. Não é ML — é só
 * matemática em pixels, isolada aqui para poder ser trocada por um modelo de
 * verdade no futuro sem tocar em quem chama.
 */
public class ImageAnalysisService {

    public record Result(Integer healthScore, String healthLabel) {}

    public static Result analyzePhoto(String filepath) throws IOException {
        BufferedImage original = ImageIO.read(new File(filepath));
        if (original == null) {
            throw new IOException("Formato de imagem não suportado ou arquivo corrompido");
        }
        BufferedImage image = resizeToFit(original, 120, 120);

        int width = image.getWidth();
        int height = image.getHeight();
        int greenish = 0;
        int yellowBrown = 0;
        int other = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                double[] hsl = rgbToHsl(r, g, b);
                double h = hsl[0], s = hsl[1], l = hsl[2];

                if (s < 0.12 || l < 0.08 || l > 0.95) {
                    other++;
                    continue;
                }
                if (h >= 70 && h <= 160) {
                    greenish++;
                } else if (h >= 30 && h < 70) {
                    yellowBrown++;
                } else {
                    other++;
                }
            }
        }

        int vegetationPixels = greenish + yellowBrown;
        if (vegetationPixels == 0) {
            return new Result(null, "sem_folhagem_detectada");
        }

        int healthScore = (int) Math.round((greenish * 100.0) / vegetationPixels);
        String healthLabel = "saudavel";
        if (healthScore < 50) healthLabel = "critico";
        else if (healthScore < 75) healthLabel = "atencao";

        return new Result(healthScore, healthLabel);
    }

    private static BufferedImage resizeToFit(BufferedImage src, int maxWidth, int maxHeight) {
        double scale = Math.min((double) maxWidth / src.getWidth(), (double) maxHeight / src.getHeight());
        int newWidth = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int newHeight = Math.max(1, (int) Math.round(src.getHeight() * scale));

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return resized;
    }

    private static double[] rgbToHsl(int r8, int g8, int b8) {
        double r = r8 / 255.0, g = g8 / 255.0, b = b8 / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2.0;
        double h = 0, s = 0;

        if (max != min) {
            double d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
        }
        return new double[]{h, s, l};
    }
}
