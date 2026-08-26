package com.example.thumbnailer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class StatusController {

    private final InstanceInfo instanceInfo;

    StatusController(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }

    /**
     * Readiness and liveness endpoint.
     *
     * <p>It checks the one thing this application cannot work without — a JPEG writer in the
     * runtime — rather than returning 200 unconditionally. A health check that cannot fail tells
     * you nothing.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean jpegWriterAvailable = ImageIO.getImageWritersByFormatName("jpg").hasNext();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", jpegWriterAvailable ? "UP" : "DOWN");
        body.put("jpegWriterAvailable", jpegWriterAvailable);
        body.put("servedBy", instanceInfo.hostname());

        return ResponseEntity
                .status(jpegWriterAvailable ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return instanceInfo.snapshot();
    }
}
