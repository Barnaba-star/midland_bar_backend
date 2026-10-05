package com.midland.bar.Bar.Live;

import com.midland.bar.Config.Security.LoggerUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.regex.Pattern;

/**
 * After a request that changes orders or bills has finished (its
 * transaction committed), nudge the branch's open screens. One place
 * instead of a call in every service - a refused request nudges too,
 * which only costs the screens one extra fetch.
 */
@Component
@RequiredArgsConstructor
public class LivePublishInterceptor implements HandlerInterceptor {

    private record Rule(Pattern path, String topic) {}

    private static final List<Rule> RULES = List.of(
            // Written, sent, received, rejected: the supervisor's queue and the staff member's bill.
            new Rule(Pattern.compile("^/bar/staffOrders/(send/[^/]+|offline|[^/]+/(receive|reject|review))$"), "orders"),
            new Rule(Pattern.compile("^/bar/(payBill|addSaleItems|saveOpenSale|saveBarSales|removeBillLine|staffLoss"
                    + "|deleteEmptyBill/[^/]+|deleteBarSales/[^/]+|bills/[^/]+/paymentNote"
                    + "|staffSell/openBill|staffSell/handover/.+)$"), "bills")
    );

    private final LiveEvents liveEvents;
    private final com.midland.bar.Bar.Repository.StaffOrderRepository staffOrderRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || ex != null || response.getStatus() >= 400)
            return;
        String path = request.getRequestURI();
        for (Rule r : RULES) {
            if (r.path().matcher(path).matches()) {
                String branch = LoggerUser.getBranchUIDOrMain();
                if ("orders".equals(r.topic())) {
                    publishPending(branch);
                    // Receiving or rejecting an order changes the bill as well.
                    liveEvents.publish(branch, "bills");
                } else {
                    liveEvents.publish(branch, r.topic());
                }
                return;
            }
        }
    }

    /** The queue goes with the nudge: the supervisor's screen shows it without fetching. */
    private void publishPending(String branch) {
        try {
            liveEvents.publishPending(branch, objectMapper.writeValueAsString(staffOrderRepository.findPending(branch)));
        } catch (Exception e) {
            liveEvents.publish(branch, "orders");
        }
    }
}
