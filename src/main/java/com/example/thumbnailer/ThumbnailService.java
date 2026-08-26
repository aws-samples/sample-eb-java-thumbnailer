package com.example.thumbnailer;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Resizes an image into a set of thumbnails. Everything happens in memory: nothing is written to
 * disk and nothing is stored between requests, so any replica can serve any request.
 */
@Service
public class ThumbnailService {

    /** Widths to produce, largest first. Heights follow from the source aspect ratio. */
    private static final List<Size> SIZES =
            List.of(new Size("large", 480), new Size("medium", 240), new Size("small", 96));

    public record Size(String label, int width) {}

    public record Thumbnail(String label, int width, int height, int bytes, String dataUri) {}

    public record Result(String sourceName, int sourceWidth, int sourceHeight, int sourceBytes,
                         long elapsedMs, List<Thumbnail> thumbnails) {}

    public Result generate(String sourceName, byte[] imageBytes) throws IOException {
        long startedAt = System.nanoTime();
        BufferedImage source = read(imageBytes);

        List<Thumbnail> thumbnails = new ArrayList<>();
        for (Size size : SIZES) {
            BufferedImage scaled = scale(source, size.width());
            byte[] jpeg = toJpeg(scaled);
            thumbnails.add(new Thumbnail(size.label(), scaled.getWidth(), scaled.getHeight(),
                    jpeg.length, "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)));
        }

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new Result(sourceName, source.getWidth(), source.getHeight(), imageBytes.length,
                elapsedMs, thumbnails);
    }

    public BufferedImage read(byte[] imageBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new IOException("That file could not be read as an image. Try a JPEG, PNG or GIF.");
        }
        return image;
    }

    public byte[] toJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", out)) {
            throw new IOException("No JPEG writer is available in this runtime");
        }
        return out.toByteArray();
    }

    /**
     * Halves the image repeatedly before the final step. That costs more CPU than one big resize
     * and produces visibly better thumbnails, which is the trade a real thumbnailer makes.
     */
    BufferedImage scale(BufferedImage source, int requestedWidth) {
        int targetWidth = Math.min(requestedWidth, source.getWidth());
        int targetHeight = Math.max(1,
                Math.round(source.getHeight() * (targetWidth / (float) source.getWidth())));

        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();
        while (width / 2 > targetWidth) {
            width = width / 2;
            height = Math.max(1, height / 2);
            current = draw(current, width, height);
        }
        return draw(current, targetWidth, targetHeight);
    }

    private BufferedImage draw(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // JPEG has no alpha channel, so flatten anything transparent onto white.
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }
}
