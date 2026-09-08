package com.example.thumbnailer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Burns CPU on demand, so scaling and observability have something to react to.
 *
 * <p>Off unless {@code LOAD_ENDPOINT_ENABLED=true} is set: without it this bean is never created
 * and {@code /api/load} returns 404. Beanstalk publishes this application through an
 * internet-facing load balancer with nothing authenticating in front of it, and an open load
 * generator on a public endpoint is a denial-of-service tool: anyone who finds it can saturate
 * the CPU and drive scaling. Turn it on for a scaling demo, then turn it off.
 *
 * <p>Deliberately not linked from the UI: it is a tool for demonstrating autoscaling, not a
 * feature of the application. Saturating every core can also make health checks time out, so
 * keep the bursts short.
 */
@RestController
@ConditionalOnProperty(name = "thumbnailer.load-endpoint.enabled", havingValue = "true")
public class LoadController {

    private static final int MAX_SECONDS = 30;

    /** Written to only so the JIT cannot discard the work loop. */
    private static volatile double sink;

    private final InstanceInfo instanceInfo;

    LoadController(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }

    @GetMapping("/api/load")
    public Map<String, Object> load(@RequestParam(defaultValue = "10") int seconds)
            throws InterruptedException {
        int duration = Math.clamp(seconds, 1, MAX_SECONDS);
        int threads = Runtime.getRuntime().availableProcessors();
        long deadline = System.nanoTime() + duration * 1_000_000_000L;

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                double accumulator = 0;
                while (System.nanoTime() < deadline) {
                    for (int j = 0; j < 10_000; j++) {
                        accumulator += Math.sqrt(j + (accumulator % 7) + 1);
                    }
                }
                sink = accumulator;
            }, "load-" + i);
            worker.start();
            workers.add(worker);
        }
        for (Thread worker : workers) {
            worker.join();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("seconds", duration);
        body.put("threads", threads);
        body.put("servedBy", instanceInfo.hostname());
        body.put("maxSeconds", MAX_SECONDS);
        return body;
    }
}
