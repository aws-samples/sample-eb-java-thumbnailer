package com.example.thumbnailer;

import org.springframework.stereotype.Component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Source images so the application is usable without finding a file to upload.
 *
 * <p>They are drawn at startup rather than committed to the repository. That keeps this a
 * text-only project with no binary assets and no image licensing to account for.
 */
@Component
public class SampleImages {

    private static final int WIDTH = 1600;
    private static final int HEIGHT = 1200;

    /** Width of the copy the picker displays, so the page does not download three full-size images. */
    private static final int PREVIEW_WIDTH = 480;

    public record Sample(String id, String name, int width, int height, int bytes) {}

    private final Map<String, byte[]> jpegById = new LinkedHashMap<>();
    private final Map<String, byte[]> previewById = new LinkedHashMap<>();
    private final List<Sample> samples = new ArrayList<>();

    SampleImages(ThumbnailService thumbnailService) throws IOException {
        add(thumbnailService, "gradient", "Gradient", drawGradient());
        add(thumbnailService, "rings", "Rings", drawRings());
        add(thumbnailService, "mesh", "Mesh", drawMesh());
    }

    public List<Sample> samples() {
        return List.copyOf(samples);
    }

    /** Full-size JPEG bytes, the input to a resize. Null if the id is unknown. */
    public byte[] jpeg(String id) {
        return jpegById.get(id);
    }

    /** Reduced JPEG bytes for the picker. Null if the id is unknown. */
    public byte[] preview(String id) {
        return previewById.get(id);
    }

    private void add(ThumbnailService thumbnailService, String id, String name, BufferedImage image)
            throws IOException {
        byte[] jpeg = thumbnailService.toJpeg(image);
        jpegById.put(id, jpeg);
        previewById.put(id, thumbnailService.toJpeg(thumbnailService.scale(image, PREVIEW_WIDTH)));
        samples.add(new Sample(id, name, image.getWidth(), image.getHeight(), jpeg.length));
    }

    private BufferedImage drawGradient() {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        graphics.setPaint(new LinearGradientPaint(new Point(0, 0), new Point(WIDTH, HEIGHT),
                new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(0x1B2A4A), new Color(0x2E7D8F), new Color(0xF2C57C)}));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        for (int i = 0; i < 6; i++) {
            graphics.setColor(new Color(255, 255, 255, 18 + i * 6));
            int diameter = 300 + i * 180;
            graphics.fillOval(WIDTH / 2 - diameter / 2 + i * 40, HEIGHT / 2 - diameter / 2 - i * 30,
                    diameter, diameter);
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage drawRings() {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        graphics.setColor(new Color(0x10141C));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setStroke(new BasicStroke(14f));
        for (int diameter = 1900; diameter > 0; diameter -= 34) {
            graphics.setColor(Color.getHSBColor((diameter % 720) / 720f, 0.55f, 0.95f));
            graphics.drawOval(WIDTH / 2 - diameter / 2, HEIGHT / 2 - diameter / 2, diameter, diameter);
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage drawMesh() {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        graphics.setColor(new Color(0xFAF7F2));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        for (int x = 0; x < WIDTH; x += 56) {
            graphics.setColor(new Color(46, 125, 143, 40 + (x / 56 % 5) * 28));
            graphics.fillRect(x, 0, 28, HEIGHT);
        }
        for (int y = 0; y < HEIGHT; y += 56) {
            graphics.setColor(new Color(214, 106, 74, 40 + (y / 56 % 5) * 28));
            graphics.fillRect(0, y, WIDTH, 28);
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage canvas() {
        return new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    }

    private Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return graphics;
    }
}
