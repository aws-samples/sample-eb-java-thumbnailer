package com.example.thumbnailer;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThumbnailServiceTest {

    private final ThumbnailService service = new ThumbnailService();

    @Test
    void scalesToTheRequestedWidthAndKeepsTheAspectRatio() {
        BufferedImage scaled = service.scale(image(1600, 1200), 480);

        assertEquals(480, scaled.getWidth());
        assertEquals(360, scaled.getHeight());
    }

    @Test
    void neverUpscalesBeyondTheSourceWidth() {
        BufferedImage scaled = service.scale(image(120, 90), 480);

        assertEquals(120, scaled.getWidth());
        assertEquals(90, scaled.getHeight());
    }

    @Test
    void keepsAtLeastOnePixelOfHeightForVeryWideImages() {
        BufferedImage scaled = service.scale(image(4000, 5), 96);

        assertEquals(96, scaled.getWidth());
        assertTrue(scaled.getHeight() >= 1, "height should never round down to zero");
    }

    @Test
    void generatesThumbnailsInDescendingSize() throws IOException {
        byte[] jpeg = service.toJpeg(image(1600, 1200));

        ThumbnailService.Result result = service.generate("test.jpg", jpeg);

        assertEquals(1600, result.sourceWidth());
        assertEquals(1200, result.sourceHeight());
        assertEquals(jpeg.length, result.sourceBytes());

        List<ThumbnailService.Thumbnail> thumbnails = result.thumbnails();
        assertEquals(3, thumbnails.size());
        for (int i = 1; i < thumbnails.size(); i++) {
            assertTrue(thumbnails.get(i).width() < thumbnails.get(i - 1).width(),
                    "thumbnails should get smaller, largest first");
        }
        assertTrue(thumbnails.getFirst().dataUri().startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void rejectsSomethingThatIsNotAnImage() {
        byte[] notAnImage = "this is not an image".getBytes();

        assertThrows(IOException.class, () -> service.read(notAnImage));
    }

    private BufferedImage image(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setPaint(new Color(0x2E7D8F));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }
}
