package com.example.thumbnailer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Facts about the replica handling a request.
 *
 * <p>Elastic Beanstalk can run several replicas of this application behind a single endpoint.
 * Reporting the hostname and version in every response makes that visible: during a rolling
 * update you can watch the version change while the application keeps serving traffic.
 */
@Component
public class InstanceInfo {

    private final String version;
    private final boolean loadEndpointEnabled;
    private final String hostname = resolveHostname();
    private final Instant startedAt = Instant.now();
    private final AtomicLong thumbnailsGenerated = new AtomicLong();

    InstanceInfo(@Value("${app.version:dev}") String version,
                 @Value("${thumbnailer.load-endpoint.enabled:false}") boolean loadEndpointEnabled) {
        this.version = version;
        this.loadEndpointEnabled = loadEndpointEnabled;
    }

    public String version() {
        return version;
    }

    public String hostname() {
        return hostname;
    }

    public void recordThumbnails(int count) {
        thumbnailsGenerated.addAndGet(count);
    }

    /** Everything served by {@code GET /info}. */
    public Map<String, Object> snapshot() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("application", "thumbnailer");
        info.put("version", version);
        info.put("servedBy", hostname);
        info.put("startedAt", startedAt.toString());
        info.put("uptimeSeconds", Duration.between(startedAt, Instant.now()).toSeconds());
        info.put("port", System.getenv().getOrDefault("PORT", "8080"));
        info.put("region", resolveRegion());
        info.put("thumbnailsGenerated", thumbnailsGenerated.get());
        // So you can tell whether the load generator is exposed without having to probe for it.
        info.put("loadEndpointEnabled", loadEndpointEnabled);
        info.put("processors", runtime.availableProcessors());
        info.put("heapUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        info.put("heapMaxMb", runtime.maxMemory() / (1024 * 1024));
        return info;
    }

    /**
     * Elastic Beanstalk injects PORT but not AWS_REGION. An application that calls AWS services
     * should have the Region set explicitly as an environment property, so this reports whether
     * that was done rather than guessing a default.
     */
    private static String resolveRegion() {
        String region = System.getenv("AWS_REGION");
        if (region == null || region.isBlank()) {
            region = System.getenv("AWS_DEFAULT_REGION");
        }
        return (region == null || region.isBlank()) ? "not set" : region;
    }

    private static String resolveHostname() {
        String fromEnv = System.getenv("HOSTNAME");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
