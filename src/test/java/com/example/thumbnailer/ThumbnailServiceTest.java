package com.example.thumbnailer;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

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

    @Test
    void rejectsMorePixelsThanWouldFitInMemory() {
        // 36 megapixels: a small file, and around 144 MB once decoded.
        IOException thrown = assertThrows(IOException.class, () -> service.read(pngHeader(6000, 6000)));

        assertTrue(thrown.getMessage().contains("megapixel"),
                "should be refused for its pixel count, not for being unreadable: "
                        + thrown.getMessage());
    }

    @Test
    void rejectsASideLongerThanTheLimit() {
        // Only 2 megapixels, so the pixel budget alone would let this through.
        IOException thrown = assertThrows(IOException.class, () -> service.read(pngHeader(200_000, 10)));

        assertTrue(thrown.getMessage().contains("side"),
                "should be refused for its width: " + thrown.getMessage());
    }

    @Test
    void acceptsAnImageInsideTheLimits() throws IOException {
        BufferedImage read = service.read(service.toJpeg(image(1600, 1200)));

        assertEquals(1600, read.getWidth());
        assertEquals(1200, read.getHeight());
    }

    /**
     * A PNG carrying only its header. The size guard reads the dimensions from IHDR and refuses
     * the image before any pixel data is needed, so a test can claim a size that would be absurd
     * to allocate — and these bytes only decode if the guard fails to fire.
     */
    private static byte[] pngHeader(int width, int height) {
        byte[] chunk = ByteBuffer.allocate(17)
                .put("IHDR".getBytes(StandardCharsets.US_ASCII))
                .putInt(width)
                .putInt(height)
                .put((byte) 8)  // bit depth
                .put((byte) 2)  // colour type: truecolour
                .put((byte) 0)  // compression
                .put((byte) 0)  // filter
                .put((byte) 0)  // interlace
                .array();

        CRC32 crc = new CRC32();
        crc.update(chunk);

        return ByteBuffer.allocate(8 + 4 + chunk.length + 4)
                .put(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
                .putInt(chunk.length - "IHDR".length())  // the length counts the data, not the type
                .put(chunk)
                .putInt((int) crc.getValue())
                .array();
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
