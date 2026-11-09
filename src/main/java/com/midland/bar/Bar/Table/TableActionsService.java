package com.midland.bar.Bar.Table;

import com.midland.bar.Bar.Alert.OwnerAlertService;
import com.midland.bar.Bar.Live.LiveEvents;
import com.midland.bar.Bar.Service.LoyaltyService;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Exceptions.BusinessException;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * What a customer does from a table's QR window besides reading the menu
 * (owner, 2026-11-09): call the waiter, ask for the bill, write to the
 * manager, check their ofa / visits. And the bar's side: the open calls and
 * the feedback list. Customer calls are limited per table so a prank can't
 * flood the bar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableActionsService {

    /** Calls older than this are not "open" any more - the night is over. */
    private static final int CALL_HOURS = 6;
    /** One call of a kind per table per this many seconds. */
    private static final int CALL_REST_SECONDS = 60;
    /** At most this many messages from one table per 10 minutes. */
    private static final int FEEDBACK_PER_10_MIN = 5;
    public static final String TOPIC_CALLS = "calls";

    private final BarTableRepository tableRepository;
    private final TableCallRepository callRepository;
    private final CustomerFeedbackRepository feedbackRepository;
    private final LiveEvents liveEvents;
    private final OwnerAlertService ownerAlertService;
    private final LoyaltyService loyaltyService;

    private Optional<BarTable> table(String token) {
        String key = token == null ? "" : token.trim().toUpperCase();
        return key.length() == 10 ? tableRepository.findByToken(key) : Optional.empty();
    }

    // ------------------------------------------------------------ customer side

    public Response<Map<String, Object>> call(String token, String rawKind) {
        BarTable t = table(token).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        String kind = TableCall.BILL.equalsIgnoreCase(rawKind) ? TableCall.BILL : TableCall.WAITER;
        LocalDateTime now = LocalDateTime.now();
        Optional<TableCall> open = callRepository.findOpenFor(t.getUid(), kind, now.minusHours(CALL_HOURS));
        if (open.isPresent()) {
            // Already waiting: a second tap within a minute is refused, later it just stays open.
            if (open.get().getCalledAt().isAfter(now.minusSeconds(CALL_REST_SECONDS)))
                return new Response<>("TOO_SOON");
            return new Response<>(Map.of("kind", kind, "waiting", true));
        }
        TableCall c = new TableCall();
        c.setBranchUid(t.getBranchUid());
        c.setTableUid(t.getUid());
        c.setTableName(t.getName());
        c.setKind(kind);
        c.setCalledAt(now);
        callRepository.save(c);
        liveEvents.publish(t.getBranchUid(), TOPIC_CALLS);
        log.info("{} called ({}) in branch {}", t.getName(), kind, t.getBranchUid());
        return new Response<>(Map.of("kind", kind, "waiting", true));
    }

    public Response<Map<String, Object>> feedback(String token, Map<String, Object> body) {
        BarTable t = table(token).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        String kind = String.valueOf(body.getOrDefault("kind", "ADVICE")).toUpperCase();
        if (!CustomerFeedback.KINDS.contains(kind))
            kind = "ADVICE";
        String message = body.get("message") == null ? "" : body.get("message").toString().trim();
        if (message.length() < 2)
            return new Response<>("Write your message");
        if (message.length() > 1000)
            message = message.substring(0, 1000);
        Integer rating = null;
        if (body.get("rating") instanceof Number n && n.intValue() >= 1 && n.intValue() <= 5)
            rating = n.intValue();
        LocalDateTime now = LocalDateTime.now();
        if (feedbackRepository.countFromTableSince(t.getUid(), now.minusMinutes(10)) >= FEEDBACK_PER_10_MIN)
            return new Response<>("TOO_MANY");
        CustomerFeedback f = new CustomerFeedback();
        f.setBranchUid(t.getBranchUid());
        f.setTableUid(t.getUid());
        f.setTableName(t.getName());
        f.setKind(kind);
        f.setMessage(message);
        f.setRating(rating);
        f.setCustomerName(trim(body.get("name"), 60));
        f.setCustomerPhone(trim(body.get("phone"), 20));
        f.setSentAt(now);
        feedbackRepository.save(f);
        ownerAlertService.customerFeedback(t.getBranchUid(), t.getName(), kind, rating, message);
        return new Response<>(Map.of("sent", true));
    }

    /** Phone + PIN (the bar's Ofa za Wateja): what they have, as plain lines for the window. */
    public Response<Map<String, Object>> card(String token, Map<String, String> body) {
        BarTable t = table(token).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        try {
            var c = loyaltyService.verify(body.get("phone"), body.get("pin"), t.getBranchUid());
            Map<String, Object> card = loyaltyService.card(c, t.getBranchUid());
            List<Map<String, String>> lines = new ArrayList<>();
            long offer = card.get("offerLeft") instanceof Number n ? n.longValue() : 0;
            lines.add(line("Ofa iliyobaki", "Tsh " + String.format("%,d", offer)));
            if (card.get("loyalty") instanceof Map<?, ?> l && Boolean.TRUE.equals(l.get("enabled"))) {
                lines.add(line("Ziara mwezi huu", l.get("visits") + " kati ya " + l.get("needed")));
                if (l.get("waiting") instanceof Number w && w.longValue() > 0)
                    lines.add(line("Bill zinazosubiri kulipwa", String.valueOf(w)));
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", card.get("name"));
            out.put("lines", lines);
            return new Response<>(out);
        } catch (BusinessException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            return new Response<>(m.startsWith("Too many") ? "TOO_MANY_ATTEMPTS" : "WRONG_PHONE_OR_PIN");
        }
    }

    // --------------------------------------------------------------- bar side

    public ResponseList<TableCall> openCalls() {
        return new ResponseList<>(callRepository.findOpen(LoggerUser.getBranchUIDOrMain(), LocalDateTime.now().minusHours(CALL_HOURS)));
    }

    public Response<TableCall> done(String uid) {
        TableCall c = callRepository.findInBranch(uid, LoggerUser.getBranchUIDOrMain()).orElse(null);
        if (c == null)
            return new Response<>("Call Not Found");
        if (c.getDoneAt() == null) {
            c.setDoneAt(LocalDateTime.now());
            c.setDoneBy(LoggerUser.getEmail());
            callRepository.save(c);
            liveEvents.publish(c.getBranchUid(), TOPIC_CALLS);
        }
        return new Response<>(c);
    }

    public ResponseList<CustomerFeedback> feedbackList(String filter) {
        LocalDateTime[] range = com.midland.bar.Bar.Service.ReportRange.of(filter);
        return new ResponseList<>(feedbackRepository.findIn(LoggerUser.getBranchUIDOrMain(), range[0], range[1]));
    }

    public Response<Long> unreadFeedback() {
        return new Response<>(feedbackRepository.countUnread(LoggerUser.getBranchUIDOrMain()));
    }

    public Response<CustomerFeedback> markRead(String uid) {
        CustomerFeedback f = feedbackRepository.findInBranch(uid, LoggerUser.getBranchUIDOrMain()).orElse(null);
        if (f == null)
            return new Response<>("Feedback Not Found");
        if (f.getReadAt() == null) {
            f.setReadAt(LocalDateTime.now());
            f.setReadBy(LoggerUser.getEmail());
            feedbackRepository.save(f);
        }
        return new Response<>(f);
    }

    private static Map<String, String> line(String label, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        return m;
    }

    private static String trim(Object v, int max) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
