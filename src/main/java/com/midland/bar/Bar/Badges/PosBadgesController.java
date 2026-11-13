package com.midland.bar.Bar.Badges;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Responses.Response;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POS tiles: how many things wait for someone on each (owner, 2026-11-13) -
 * the CEO, manager or accountant sees at a glance where to look.
 *
 * sales    - "Nimelipa" from tables to check, refunds to approve, table orders to take
 * debts    - Jack Point winners waiting for approval
 * store    - supplier orders not yet received
 * feedback - customer messages not yet read
 *
 * Plain counts, one small query each; the screens behind the tiles stay the
 * place where they are dealt with.
 */
@Slf4j
@RestController
@RequestMapping("/bar")
@RequiredArgsConstructor
public class PosBadgesController {

    private final EntityManager em;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/posBadges")
    public Response<Map<String, Long>> badges() {
        String branch = LoggerUser.getBranchUIDOrMain();
        LocalDateTime day = LocalDateTime.now().minusHours(24);
        Map<String, Long> out = new LinkedHashMap<>();
        long paidCalls = count("SELECT COUNT(c) FROM TableCall c WHERE c.branchUid = :b AND c.isActive = true AND c.kind = 'PAID' AND c.doneAt IS NULL AND c.calledAt >= :since", branch, day);
        long refunds = count("SELECT COUNT(r) FROM BillRefund r WHERE r.branchUid = :b AND r.isActive = true AND r.status = 'PENDING'", branch, null);
        long tableOrders = count("SELECT COUNT(o) FROM TableOrder o WHERE o.branchUid = :b AND o.isActive = true AND o.status = 'PENDING' AND o.placedAt >= :since", branch, day);
        out.put("sales", paidCalls + refunds + tableOrders);
        out.put("debts", count("SELECT COUNT(a) FROM JackPointAward a WHERE a.branchUid = :b AND a.isActive = true AND a.status = 'QUALIFIED'", branch, null));
        out.put("store", count("SELECT COUNT(p) FROM PurchaseOrder p WHERE p.branchUid = :b AND p.isActive = true AND p.status IN ('OPEN', 'PARTIAL')", branch, null));
        out.put("feedback", count("SELECT COUNT(f) FROM CustomerFeedback f WHERE f.branchUid = :b AND f.isActive = true AND f.readAt IS NULL", branch, null));
        return new Response<>(out);
    }

    private long count(String jpql, String branch, LocalDateTime since) {
        try {
            var q = em.createQuery(jpql, Long.class).setParameter("b", branch);
            if (since != null) q.setParameter("since", since);
            Long n = q.getSingleResult();
            return n == null ? 0 : n;
        } catch (RuntimeException e) {
            // One count failing must not blank the others.
            log.warn("POS badge count failed: {}", e.getMessage());
            return 0;
        }
    }
}
