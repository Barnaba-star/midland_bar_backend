package com.midland.bar.Bar.Live;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Live nudges to open screens in a branch (Staff Sell, Supervisor, Sales):
 * "orders" or "bills" changed, so fetch again now instead of waiting for
 * the next poll. Only the topic is sent, never data - each screen re-reads
 * through its usual, permission-checked calls. Memory only: one backend.
 */
@Component
@Slf4j
public class LiveEvents {

    /** A screen stays connected this long, then reconnects by itself. */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, Set<SseEmitter>> byBranch = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String branchUID) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> set = byBranch.computeIfAbsent(branchUID, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        Runnable drop = () -> set.remove(emitter);
        emitter.onCompletion(drop);
        emitter.onTimeout(drop);
        emitter.onError(e -> drop.run());
        send(set, emitter, "hello");
        return emitter;
    }

    /** Tell the branch's open screens that a topic changed. */
    public void publish(String branchUID, String topic) {
        Set<SseEmitter> set = byBranch.get(branchUID);
        if (set == null)
            return;
        for (SseEmitter e : set)
            send(set, e, topic);
    }

    /** Proxies drop a silent connection: a comment every 20s keeps it open. */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        byBranch.values().forEach(set -> set.forEach(e -> {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                set.remove(e);
            }
        }));
    }

    private static void send(Set<SseEmitter> set, SseEmitter e, String topic) {
        try {
            e.send(SseEmitter.event().name("change").data(topic, MediaType.TEXT_PLAIN));
        } catch (IOException | IllegalStateException ex) {
            set.remove(e);
        }
    }
}
