package com.example.thumbnailer;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ThumbnailController {

    private final ThumbnailService thumbnailService;
    private final SampleImages sampleImages;
    private final InstanceInfo instanceInfo;

    ThumbnailController(ThumbnailService thumbnailService, SampleImages sampleImages,
                        InstanceInfo instanceInfo) {
        this.thumbnailService = thumbnailService;
        this.sampleImages = sampleImages;
        this.instanceInfo = instanceInfo;
    }

    /** Which replica and which version handled the resize, alongside the thumbnails themselves. */
    public record Response(String servedBy, String version, String sourceName, int sourceWidth,
                           int sourceHeight, int sourceBytes, long elapsedMs,
                           List<ThumbnailService.Thumbnail> thumbnails) {}

    @GetMapping("/samples")
    public List<SampleImages.Sample> samples() {
        return sampleImages.samples();
    }

    @GetMapping(value = "/samples/{id}/preview", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> preview(@PathVariable String id) {
        byte[] jpeg = sampleImages.preview(id);
        return jpeg == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(jpeg);
    }

    @PostMapping("/samples/{id}/thumbnails")
    public Response fromSample(@PathVariable String id) throws IOException {
        byte[] jpeg = sampleImages.jpeg(id);
        if (jpeg == null) {
            throw new IllegalArgumentException("Unknown sample: " + id);
        }
        return respond(id + ".jpg", jpeg);
    }

    @PostMapping(value = "/thumbnails", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response fromUpload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded");
        }
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        return respond(name, file.getBytes());
    }

    private Response respond(String sourceName, byte[] imageBytes) throws IOException {
        ThumbnailService.Result result = thumbnailService.generate(sourceName, imageBytes);
        instanceInfo.recordThumbnails(result.thumbnails().size());
        return new Response(instanceInfo.hostname(), instanceInfo.version(), result.sourceName(),
                result.sourceWidth(), result.sourceHeight(), result.sourceBytes(),
                result.elapsedMs(), result.thumbnails());
    }
}
