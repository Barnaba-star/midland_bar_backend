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
 * the next poll. Screens that may see every order (RECEIVE_ORDERS) also get
 * the supervisor's queue itself, so it shows without another round trip.
 * Memory only: one backend.
 */
@Component
@Slf4j
public class LiveEvents {

    /** A screen stays connected this long, then reconnects by itself. */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private record Sub(SseEmitter emitter, boolean seesOrders) {}

    private final Map<String, Set<Sub>> byBranch = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String branchUID, boolean seesOrders) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Sub sub = new Sub(emitter, seesOrders);
        Set<Sub> set = byBranch.computeIfAbsent(branchUID, k -> new CopyOnWriteArraySet<>());
        set.add(sub);
        Runnable drop = () -> set.remove(sub);
        emitter.onCompletion(drop);
        emitter.onTimeout(drop);
        emitter.onError(e -> drop.run());
        send(set, sub, "change", "hello");
        return emitter;
    }

    /** Tell the branch's open screens that a topic changed. */
    public void publish(String branchUID, String topic) {
        Set<Sub> set = byBranch.get(branchUID);
        if (set == null)
            return;
        set.forEach(s -> send(set, s, "change", topic));
    }

    /** The supervisor's queue (JSON) to screens that may see it; "orders changed" to the rest. */
    public void publishPending(String branchUID, String pendingJson) {
        Set<Sub> set = byBranch.get(branchUID);
        log.info("live: orders changed in {} - {} screen(s) connected", branchUID, set == null ? 0 : set.size());
        if (set == null)
            return;
        set.forEach(s -> {
            if (s.seesOrders())
                send(set, s, "pending", pendingJson);
            send(set, s, "change", "orders");
        });
    }

    /** Proxies drop a silent connection: a comment every 20s keeps it open. */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        byBranch.values().forEach(set -> set.forEach(s -> {
            try {
                s.emitter().send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                set.remove(s);
            }
        }));
    }

    private static void send(Set<Sub> set, Sub s, String event, String data) {
        try {
            s.emitter().send(SseEmitter.event().name(event).data(data, MediaType.TEXT_PLAIN));
        } catch (IOException | IllegalStateException ex) {
            set.remove(s);
        }
    }
}
