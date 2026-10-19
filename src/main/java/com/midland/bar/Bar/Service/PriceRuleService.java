package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Model.PriceRule;
import com.midland.bar.Bar.Repository.BarSalesRepository;
import com.midland.bar.Bar.Repository.PriceRuleRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Happy hours and offers (PriceRule): which rules are on at a moment, what a
 * line costs under the best of them, and keeping the list. Lines are priced
 * once, when sold; nothing is repriced later.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceRuleService {

    private final PriceRuleRepository ruleRepository;
    private final BarSalesRepository barSalesRepository;
    private final com.midland.bar.Bar.Repository.BarServiceRepository barServiceRepository;

    /** A line's price: per unit charged, the total, the menu price, the offer's name and free units. */
    public record Priced(int unitPrice, int lineTotal, int listPrice, String ruleName, int freeUnits) {}

    /** The cheapest price any rule running at [at] gives [qty] of [service] on [billUid]. */
    public Priced price(BarServiceEntity service, int qty, LocalDateTime at, String billUid) {
        int list = service.getPrice() == null ? 0 : service.getPrice();
        Priced best = new Priced(list, list * qty, list, null, 0);
        String branch = service.getBranchUid();
        for (PriceRule r : com.midland.bar.Utils.TxMemo.get("priceRules:" + branch, () -> ruleRepository.enabledInBranch(branch))) {
            if (!applies(r, service) || !runs(r, at))
                continue;
            Priced p = switch (r.getKind()) {
                case PriceRule.PERCENT -> {
                    int pct = Math.max(0, Math.min(100, r.getPercent() == null ? 0 : r.getPercent()));
                    int unit = Math.round(list * (100 - pct) / 100f);
                    yield new Priced(unit, unit * qty, list, r.getName(), 0);
                }
                case PriceRule.PRICE -> {
                    int unit = r.getPrice() == null ? list : Math.max(0, Math.min(list, r.getPrice()));
                    yield new Priced(unit, unit * qty, list, r.getName(), 0);
                }
                case PriceRule.BUY_GET -> buyGet(r, service, qty, list, billUid);
                default -> null;
            };
            if (p != null && p.lineTotal() < best.lineTotal())
                best = p;
        }
        return best;
    }

    /**
     * Buy N get M: counted over the bill, so a round bought one at a time still
     * earns its free one. The free units owed now are those the bill has earned
     * with this line, less those already given.
     */
    private Priced buyGet(PriceRule r, BarServiceEntity service, int qty, int list, String billUid) {
        int buy = r.getBuyQty() == null ? 0 : r.getBuyQty();
        int free = r.getFreeQty() == null ? 0 : r.getFreeQty();
        if (buy < 1 || free < 1)
            return null;
        long[] before = {0, 0};
        if (billUid != null)
            for (Object[] row : barSalesRepository.unitsAndFreeOnBill(billUid, service.getUid()))
                before = new long[]{((Number) row[0]).longValue(), ((Number) row[1]).longValue()};
        long total = before[0] + qty;
        // Buy 5 get 2: the 6th and 7th are free. Once per bill gives that one
        // time; otherwise it starts again after every 7 (the 13th and 14th...).
        long earned = freeEarned(total, buy, free, r.once());
        int owed = (int) Math.max(0, Math.min(qty, earned - before[1]));
        if (owed == 0)
            return null;
        return new Priced(list, list * (qty - owed), list, r.getName(), owed);
    }

    /** Free units [total] bought on one bill earn under buy [buy] get [free]. */
    static long freeEarned(long total, int buy, int free, boolean once) {
        if (once)
            return Math.min(free, Math.max(0, total - buy));
        return (total / (buy + free)) * free + Math.min(free, Math.max(0, total % (buy + free) - buy));
    }

    private static boolean applies(PriceRule r, BarServiceEntity s) {
        if (r.getServiceUid() != null)
            return r.getServiceUid().equals(s.getUid());
        return r.getCategory() == null || r.getCategory().equalsIgnoreCase(s.getCategory());
    }

    /** On that weekday between its times; past midnight counts as the day it started. */
    static boolean runs(PriceRule r, LocalDateTime at) {
        LocalTime t = at.toLocalTime(), from = r.getStartTime(), to = r.getEndTime();
        String days = r.getDays() == null ? "1234567" : r.getDays();
        if (!from.isAfter(to))
            return days.indexOf('0' + at.getDayOfWeek().getValue()) >= 0 && !t.isBefore(from) && t.isBefore(to);
        if (!t.isBefore(from))
            return days.indexOf('0' + at.getDayOfWeek().getValue()) >= 0;
        return t.isBefore(to) && days.indexOf('0' + at.minusDays(1).getDayOfWeek().getValue()) >= 0;
    }

    /**
     * "Jaribu ofa": what [qty] of a product costs right now, and for every
     * offer of the branch why it does or does not apply - so a CEO can see
     * at once that an offer's hours, days or product are not what they meant.
     */
    public Response<Map<String, Object>> preview(String serviceUid, int qty) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        BarServiceEntity s = barServiceRepository.findById(serviceUid).filter(x -> branchUID.equals(x.getBranchUid())).orElse(null);
        if (s == null)
            return new Response<>("Product not found");
        int q = Math.max(1, qty);
        LocalDateTime now = LocalDateTime.now();
        Priced p = price(s, q, now, null);
        List<Map<String, Object>> rules = new java.util.ArrayList<>();
        for (PriceRule r : ruleRepository.findInBranch(branchUID)) {
            String why;
            if (!Boolean.TRUE.equals(r.getEnabled()))
                why = "OFF";
            else if (!applies(r, s))
                why = r.getServiceUid() != null ? "OTHER_PRODUCT" : "OTHER_CATEGORY";
            else if (!runs(r, now))
                why = "NOT_NOW";
            else if (PriceRule.BUY_GET.equals(r.getKind()) && q <= (r.getBuyQty() == null ? 0 : r.getBuyQty()))
                why = "NEED_MORE";
            else
                why = "APPLIES";
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("name", r.getName());
            m.put("kind", r.getKind());
            m.put("why", why);
            m.put("startTime", r.getStartTime());
            m.put("endTime", r.getEndTime());
            m.put("days", r.getDays());
            rules.add(m);
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("serviceName", s.getServiceName());
        out.put("category", s.getCategory());
        out.put("quantity", q);
        out.put("listPrice", p.listPrice());
        out.put("lineTotal", p.lineTotal());
        out.put("freeUnits", p.freeUnits());
        out.put("offer", p.ruleName());
        out.put("serverTime", now);
        out.put("rules", rules);
        return new Response<>(out);
    }

    // ------------------------------------------------------------ the list

    public ResponseList<PriceRule> list() {
        return new ResponseList<>(ruleRepository.findInBranch(LoggerUser.getBranchUIDOrMain()));
    }

    /** The rules running now - shown at the till so the cashier knows. */
    public ResponseList<PriceRule> runningNow() {
        LocalDateTime now = LocalDateTime.now();
        return new ResponseList<>(ruleRepository.enabledInBranch(LoggerUser.getBranchUIDOrMain()).stream()
                .filter(r -> runs(r, now)).toList());
    }

    @Transactional
    public Response<PriceRule> save(String uid, Map<String, Object> b) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        PriceRule r = uid == null ? new PriceRule() : ruleRepository.findInBranch(uid, branchUID).orElse(null);
        if (r == null)
            return new Response<>("Offer not found");
        String name = str(b.get("name"));
        if (name == null || name.length() < 2)
            return new Response<>("Name the offer (e.g. Happy hour)");
        String kind = str(b.get("kind"));
        if (!List.of(PriceRule.PERCENT, PriceRule.PRICE, PriceRule.BUY_GET).contains(kind == null ? "" : kind.toUpperCase()))
            return new Response<>("Choose percent off, a set price, or buy-and-get-free");
        kind = kind.toUpperCase();
        try {
            r.setStartTime(LocalTime.parse(str(b.get("startTime"))));
            r.setEndTime(LocalTime.parse(str(b.get("endTime"))));
        } catch (Exception e) {
            return new Response<>("Enter the times as HH:mm");
        }
        if (r.getStartTime().equals(r.getEndTime()))
            return new Response<>("The start and end times are the same");
        String days = str(b.get("days"));
        days = days == null ? "" : days.replaceAll("[^1-7]", "");
        if (days.isEmpty())
            return new Response<>("Choose at least one day");
        r.setName(name.length() > 80 ? name.substring(0, 80) : name);
        r.setKind(kind);
        r.setDays(days.chars().distinct().sorted().collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString());
        r.setServiceUid(str(b.get("serviceUid")));
        String category = str(b.get("category"));
        r.setCategory(r.getServiceUid() != null || category == null ? null : category.toUpperCase());
        r.setPercent(num(b.get("percent")));
        r.setPrice(num(b.get("price")));
        r.setBuyQty(num(b.get("buyQty")));
        r.setFreeQty(num(b.get("freeQty")));
        switch (kind) {
            case PriceRule.PERCENT -> {
                if (r.getPercent() == null || r.getPercent() < 1 || r.getPercent() > 100)
                    return new Response<>("Percent off is between 1 and 100");
            }
            case PriceRule.PRICE -> {
                if (r.getServiceUid() == null)
                    return new Response<>("A set price is for one product - choose it");
                if (r.getPrice() == null || r.getPrice() < 0)
                    return new Response<>("Enter the offer price");
            }
            default -> {
                if (r.getServiceUid() == null)
                    return new Response<>("Buy-and-get-free is for one product - choose it");
                if (r.getBuyQty() == null || r.getBuyQty() < 1 || r.getFreeQty() == null || r.getFreeQty() < 1)
                    return new Response<>("Enter how many to buy and how many free");
            }
        }
        r.setEnabled(b.get("enabled") == null || Boolean.parseBoolean(String.valueOf(b.get("enabled"))));
        r.setOncePerBill(b.get("oncePerBill") == null || Boolean.parseBoolean(String.valueOf(b.get("oncePerBill"))));
        if (uid != null)
            r.update();
        log.info("{} saved offer {} ({})", LoggerUser.getEmail(), r.getName(), kind);
        com.midland.bar.Utils.TxMemo.forget("priceRules:");
        return new Response<>(ruleRepository.save(r));
    }

    @Transactional
    public Response<Boolean> delete(String uid) {
        PriceRule r = ruleRepository.findInBranch(uid, LoggerUser.getBranchUIDOrMain()).orElse(null);
        if (r == null)
            return new Response<>("Offer not found");
        r.delete();
        com.midland.bar.Utils.TxMemo.forget("priceRules:");
        ruleRepository.save(r);
        return new Response<>(Boolean.TRUE);
    }

    private static String str(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static Integer num(Object v) {
        String s = str(v);
        if (s == null)
            return null;
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
