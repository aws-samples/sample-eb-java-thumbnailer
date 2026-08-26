package com.example.thumbnailer;

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
 * <p>Deliberately not linked from the UI: it is a tool for demonstrating autoscaling, not a
 * feature of the application. Saturating every core can also make health checks time out, so
 * keep the bursts short.
 */
@RestController
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
