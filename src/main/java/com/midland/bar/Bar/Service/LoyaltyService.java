package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Model.*;
import com.midland.bar.Bar.Repository.*;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Notification.Sms.Service.SmsService;
import com.midland.bar.Utils.Exceptions.BusinessException;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Customer loyalty (owner, 2026-11-04). A customer is known by their phone
 * number and a PIN they type themselves - the waiter never sees it. A bill of
 * at least the branch's minimum, paid with the customer named on it, is one
 * visit; enough visits within the period earn a reward that is spent as ofa
 * (DebtService.useOffer) until it is used up or expires. Then the count
 * starts again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoyaltyService {

    public static final String WRONG_PIN = "WRONG_CUSTOMER_PIN";
    public static final String PIN_NOT_SET = "CUSTOMER_PIN_NOT_SET";
    public static final String NOT_REGISTERED = "CUSTOMER_NOT_REGISTERED";
    private static final int MAX_TRIES = 5;
    private static final int LOCK_MINUTES = 15;

    private final LoyaltyRuleRepository ruleRepository;
    private final LoyaltyVisitRepository visitRepository;
    private final LoyaltyRewardRepository rewardRepository;
    private final DebtCustomerRepository customerRepository;
    private final DebtEntryRepository entryRepository;
    private final OfferUseRepository offerUseRepository;
    private final SalesOpenedRepository salesOpenedRepository;
    private final BarSalesRepository barSalesRepository;
    private final BCryptPasswordEncoder encoder;

    // ---------------------------------------------------------------- rule

    /**
     * The branch's rule. A branch without its own follows the main office's
     * (a ROOT/DIRECTOR login has no branch, so what they set is saved there);
     * a fresh, switched-off one when neither was saved.
     */
    public LoyaltyRule rule() {
        return rule(LoggerUser.getBranchUIDOrMain());
    }

    /** The same for a named branch - the customers' window has no login. */
    public LoyaltyRule rule(String branch) {
        return ruleRepository.findInBranch(branch)
                .or(() -> "MAIN_OFFICE".equals(branch) ? Optional.empty() : ruleRepository.findInBranch("MAIN_OFFICE"))
                .orElseGet(LoyaltyRule::new);
    }

    /**
     * Should this bill ask "does the customer have a code?" - worked out here
     * from the bill's lines (a screen's copy of the total can be stale).
     */
    public Response<Map<String, Object>> askFor(String billUid) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        SalesOpened bill = salesOpenedRepository.findById(billUid).filter(b -> branchUID.equals(b.getBranchUid())).orElse(null);
        if (bill == null)
            return new Response<>("Open Sale Not Found");
        LoyaltyRule rule = rule();
        long total = barSalesRepository.billTotal(bill.getUid());
        boolean on = rule.getUid() != null && Boolean.TRUE.equals(rule.getEnabled());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ask", on && total >= rule.getMinBill() && bill.getLoyaltyCustomerUid() == null);
        m.put("enabled", on);
        m.put("minBill", rule.getMinBill());
        m.put("total", total);
        m.put("customerName", bill.getLoyaltyCustomerName());
        m.put("customerUid", bill.getLoyaltyCustomerUid());
        m.put("offerLeft", bill.getLoyaltyCustomerUid() == null ? 0
                : customerRepository.findById(bill.getLoyaltyCustomerUid()).map(this::offerLeft).orElse(0L));
        // The customer's offer comes off this bill by itself; they pay the rest.
        long off = autoOffer(bill, total);
        m.put("offerNow", off);
        m.put("toPay", total - off);
        if (bill.getLoyaltyCustomerUid() != null && on)
            m.put("loyalty", progress(bill.getLoyaltyCustomerUid(), rule));
        return new Response<>(m);
    }

    public Response<Map<String, Object>> ruleView() {
        return new Response<>(ruleMap(rule()));
    }

    @Transactional
    public Response<Map<String, Object>> saveRule(LoyaltyRule in) {
        if (in.getMinBill() == null || in.getMinBill() < 1000 || in.getMinBill() > 100_000_000)
            return new Response<>("The bill amount must be between 1,000 and 100,000,000");
        if (in.getBillsNeeded() == null || in.getBillsNeeded() < 1 || in.getBillsNeeded() > 100)
            return new Response<>("The number of bills must be between 1 and 100");
        if (in.getPeriodDays() == null || in.getPeriodDays() < 1 || in.getPeriodDays() > 366)
            return new Response<>("The period must be between 1 and 366 days");
        if (in.getRewardAmount() == null || in.getRewardAmount() < 1 || in.getRewardAmount() > 100_000_000)
            return new Response<>("The reward must be between 1 and 100,000,000");
        LoyaltyRule rule = ruleRepository.findInBranch(LoggerUser.getBranchUIDOrMain()).orElseGet(LoyaltyRule::new);
        rule.setEnabled(Boolean.TRUE.equals(in.getEnabled()));
        rule.setMinBill(in.getMinBill());
        rule.setBillsNeeded(in.getBillsNeeded());
        rule.setPeriodDays(in.getPeriodDays());
        rule.setRewardAmount(in.getRewardAmount());
        rule.setSetBy(LoggerUser.getEmail());
        rule.setSetAt(LocalDateTime.now());
        if (rule.getUid() != null)
            rule.update();
        LoyaltyRule saved = ruleRepository.save(rule);
        log.info("{} set the loyalty rule: on={} min {} x{} in {} days -> {}", LoggerUser.getEmail(), saved.getEnabled(),
                saved.getMinBill(), saved.getBillsNeeded(), saved.getPeriodDays(), saved.getRewardAmount());
        return new Response<>(ruleMap(saved));
    }

    private static Map<String, Object> ruleMap(LoyaltyRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", Boolean.TRUE.equals(r.getEnabled()));
        m.put("minBill", r.getMinBill());
        m.put("billsNeeded", r.getBillsNeeded());
        m.put("periodDays", r.getPeriodDays());
        m.put("rewardAmount", r.getRewardAmount());
        m.put("setAt", r.getSetAt());
        return m;
    }

    // ---------------------------------------------------------------- who

    /**
     * The customer behind a phone number, proved by their PIN. Five wrong
     * PINs lock the number for 15 minutes. Refuses with NOT_REGISTERED (no
     * such number) or PIN_NOT_SET (registered in Madeni, no PIN yet) so the
     * screen can offer to set one.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public DebtCustomer verify(String phone, String pin) {
        return verify(phone, pin, LoggerUser.getBranchUIDOrMain());
    }

    /** The same in a named branch (the customers' window: no login, the table says the branch). Same 5-try lock. */
    @Transactional(noRollbackFor = BusinessException.class)
    public DebtCustomer verify(String phone, String pin, String branchUID) {
        String number = SmsService.normalise(phone);
        if (number == null)
            throw new BusinessException("Enter a valid phone number, e.g. 0712 345 678");
        DebtCustomer c = customerRepository.findByPhone(number, branchUID)
                .orElseThrow(() -> new BusinessException(NOT_REGISTERED));
        if (c.getPinHash() == null)
            throw new BusinessException(PIN_NOT_SET);
        LocalDateTime now = LocalDateTime.now();
        if (c.getPinLockedUntil() != null && c.getPinLockedUntil().isAfter(now))
            throw new BusinessException("Too many wrong PINs - try again after " + LOCK_MINUTES + " minutes");
        if (pin == null || !encoder.matches(pin.trim(), c.getPinHash())) {
            int tries = (c.getPinTries() == null ? 0 : c.getPinTries()) + 1;
            c.setPinTries(tries >= MAX_TRIES ? 0 : tries);
            if (tries >= MAX_TRIES)
                c.setPinLockedUntil(now.plusMinutes(LOCK_MINUTES));
            customerRepository.save(c);
            throw new BusinessException(WRONG_PIN);
        }
        if (c.getPinTries() != null && c.getPinTries() > 0) {
            c.setPinTries(0);
            customerRepository.save(c);
        }
        return c;
    }

    /**
     * A customer sets their PIN at the till: a new customer (name + phone)
     * is registered with no credit; one registered in Madeni without a PIN
     * gets theirs. A number that already has a PIN is refused - a manager
     * resets it in Madeni.
     */
    @Transactional
    public DebtCustomer register(String name, String phone, String pin) {
        String number = SmsService.normalise(phone);
        if (number == null)
            throw new BusinessException("Enter a valid phone number, e.g. 0712 345 678");
        String problem = StaffPinRules.problem(pin == null ? null : pin.trim());
        if (problem != null)
            throw new BusinessException(problem);
        String branchUID = LoggerUser.getBranchUIDOrMain();
        DebtCustomer c = customerRepository.findByPhone(number, branchUID).orElse(null);
        if (c != null && c.getPinHash() != null)
            throw new BusinessException("This number already has a PIN - a manager can reset it in Madeni");
        if (c == null) {
            String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ").toUpperCase();
            if (clean.length() < 2)
                throw new BusinessException("Enter the customer's name");
            if (clean.length() > 80)
                throw new BusinessException("The name is too long");
            c = new DebtCustomer();
            c.setName(clean);
            c.setPhone(number);
            c.setCreditLimit(0);
            c.setRegisteredBy(LoggerUser.getEmail());
            c.setRegisteredAt(LocalDateTime.now());
        } else {
            c.update();
        }
        c.setPinHash(encoder.encode(pin.trim()));
        c.setPinTries(0);
        c.setPinLockedUntil(null);
        DebtCustomer saved = customerRepository.save(c);
        log.info("{} set the PIN of customer {}", LoggerUser.getEmail(), saved.getUid());
        return saved;
    }

    /** A manager clears a forgotten PIN; the customer sets a new one next time. */
    @Transactional
    public Response<Map<String, Object>> resetPin(String customerUid) {
        DebtCustomer c = customerRepository.findInBranch(customerUid, LoggerUser.getBranchUIDOrMain()).orElse(null);
        if (c == null)
            return new Response<>("Customer not found");
        c.setPinHash(null);
        c.setPinTries(0);
        c.setPinLockedUntil(null);
        c.update();
        customerRepository.save(c);
        log.info("{} reset the PIN of customer {}", LoggerUser.getEmail(), customerUid);
        return new Response<>(Map.of("uid", customerUid, "hasPin", false));
    }

    /** What the screens show once a customer is known: name, offer, credit and loyalty progress. No phone, no PIN. */
    public Map<String, Object> card(DebtCustomer c) {
        return card(c, LoggerUser.getBranchUIDOrMain());
    }

    /** The same for a named branch. */
    public Map<String, Object> card(DebtCustomer c, String branchUID) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uid", c.getUid());
        m.put("name", c.getName());
        m.put("offerLeft", offerLeft(c));
        int limit = c.getCreditLimit() == null ? 0 : c.getCreditLimit();
        m.put("creditLeft", Math.max(0, limit - entryRepository.balanceOf(c.getUid())));
        LoyaltyRule rule = rule(branchUID);
        m.put("loyalty", progress(c.getUid(), rule, branchUID));
        return m;
    }

    /** Visits counted so far in the current round, out of how many. */
    public Map<String, Object> progress(String customerUid, LoyaltyRule rule) {
        return progress(customerUid, rule, LoggerUser.getBranchUIDOrMain());
    }

    public Map<String, Object> progress(String customerUid, LoyaltyRule rule, String branchUID) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", Boolean.TRUE.equals(rule.getEnabled()));
        m.put("visits", rule.getUid() == null ? 0 : visitRepository.counting(customerUid, windowStart(rule)).size());
        m.put("needed", rule.getBillsNeeded());
        m.put("rewardLeft", rewardLeft(customerUid));
        // Their other unpaid bills big enough to count: they count once paid,
        // so the screen can say "2 paid, 1 waiting for payment".
        long waiting = 0;
        if (rule.getUid() != null) {
            List<SalesOpened> open = salesOpenedRepository.findOpenBillsByLoyaltyCustomer(customerUid, branchUID);
            if (!open.isEmpty()) {
                Map<String, Long> totals = new java.util.HashMap<>();
                for (Object[] r : barSalesRepository.billTotals(open.stream().map(SalesOpened::getUid).toList()))
                    totals.put((String) r[0], ((Number) r[1]).longValue());
                waiting = open.stream().filter(b -> totals.getOrDefault(b.getUid(), 0L) >= rule.getMinBill()).count();
            }
        }
        m.put("waiting", waiting);
        return m;
    }

    // ---------------------------------------------------------------- bills

    /** The customer gave their phone + PIN on a bill: it counts for them when paid. */
    @Transactional(noRollbackFor = BusinessException.class)
    public Response<Map<String, Object>> attach(String billUid, String phone, String pin) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        SalesOpened bill = salesOpenedRepository.findById(billUid).filter(b -> branchUID.equals(b.getBranchUid())).orElse(null);
        if (bill == null)
            return new Response<>("Open Sale Not Found");
        com.midland.bar.Config.Security.StaffSession.requireOwnBill(bill);
        if (!"PENDING".equals(bill.getPaymentStatus()))
            return new Response<>("Bill " + bill.getSalesCode() + " is already paid");
        DebtCustomer c = verify(phone, pin);
        bill.setLoyaltyCustomerUid(c.getUid());
        bill.setLoyaltyCustomerName(c.getName());
        salesOpenedRepository.save(bill);
        Map<String, Object> card = card(c);
        long total = barSalesRepository.billTotal(bill.getUid());
        long off = autoOffer(bill, total);
        card.put("offerNow", off);
        card.put("toPay", total - off);
        return new Response<>(card);
    }

    /** Called when a bill is paid in full: one visit when it is big enough, and a reward when enough visits are in. */
    @Transactional
    public void recordVisit(SalesOpened bill, int total, LocalDateTime at) {
        String customerUid = bill.getLoyaltyCustomerUid();
        if (customerUid == null)
            return;
        LoyaltyRule rule = rule();
        if (rule.getUid() == null || !Boolean.TRUE.equals(rule.getEnabled()) || total < rule.getMinBill())
            return;
        if (visitRepository.countForBill(bill.getUid()) > 0)
            return;
        LoyaltyVisit v = new LoyaltyVisit();
        v.setCustomerUid(customerUid);
        v.setBillUid(bill.getUid());
        v.setBillCode(bill.getSalesCode());
        v.setAmount(total);
        v.setVisitAt(at);
        visitRepository.save(v);

        List<LoyaltyVisit> counting = visitRepository.counting(customerUid, windowStart(rule));
        if (counting.size() < rule.getBillsNeeded())
            return;
        LoyaltyReward reward = new LoyaltyReward();
        reward.setCustomerUid(customerUid);
        reward.setCustomerName(bill.getLoyaltyCustomerName());
        reward.setAmount(rule.getRewardAmount());
        reward.setVisits(counting.size());
        reward.setGrantedAt(at);
        // Earned this month, usable to the month's end (owner, 2026-11-05: calendar month).
        reward.setExpiresAt(at.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay());
        LoyaltyReward saved = rewardRepository.save(reward);
        counting.forEach(x -> x.setRewardUid(saved.getUid()));
        visitRepository.saveAll(counting);
        log.info("Customer {} earned a loyalty reward of {} after {} bill(s)", customerUid, saved.getAmount(), counting.size());
    }

    /** Bills count within the calendar month: from the 1st, everyone starts again. */
    private static LocalDateTime windowStart(LoyaltyRule rule) {
        return java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
    }

    // ---------------------------------------------------------------- ofa

    /**
     * The offer that comes off each bill by itself (owner, 2026-11-05): a bill
     * whose customer typed their PIN and has offer left gets min(left, total)
     * taken off; they pay the rest. Shared out bill by bill, so one customer's
     * offer is never counted twice across their bills. Bills noted "ofa" as a
     * whole are left out (already all offer).
     */
    public Map<String, Long> autoOffers(List<SalesOpened> bills, Map<String, Long> totals) {
        Map<String, Long> out = new HashMap<>();
        Map<String, Long> left = new HashMap<>();
        for (SalesOpened b : bills) {
            String c = b.getLoyaltyCustomerUid();
            if (c == null || "ofa".equals(b.getPaymentNoteMethod()) || "deni".equals(b.getPaymentNoteMethod()))
                continue;
            long total = totals.getOrDefault(b.getUid(), 0L);
            if (total <= 0)
                continue;
            long l = left.computeIfAbsent(c, k -> customerRepository.findById(k).map(this::offerLeft).orElse(0L));
            long off = Math.min(l, total);
            if (off > 0) {
                out.put(b.getUid(), off);
                left.put(c, l - off);
            }
        }
        return out;
    }

    /** The offer that comes off one bill by itself (see autoOffers). */
    public long autoOffer(SalesOpened bill, long total) {
        return autoOffers(List.of(bill), Map.of(bill.getUid(), total)).getOrDefault(bill.getUid(), 0L);
    }

    /** Live rewards with what is left of each, soonest to expire first. */
    public LinkedHashMap<LoyaltyReward, Long> liveRewards(String customerUid) {
        List<LoyaltyReward> live = rewardRepository.live(customerUid, LocalDateTime.now());
        Map<String, Long> used = new HashMap<>();
        if (!live.isEmpty())
            for (Object[] r : offerUseRepository.usedOfRewards(live.stream().map(LoyaltyReward::getUid).toList()))
                used.put((String) r[0], ((Number) r[1]).longValue());
        LinkedHashMap<LoyaltyReward, Long> out = new LinkedHashMap<>();
        for (LoyaltyReward r : live) {
            long left = r.getAmount() - used.getOrDefault(r.getUid(), 0L);
            if (left > 0)
                out.put(r, left);
        }
        return out;
    }

    public long rewardLeft(String customerUid) {
        return liveRewards(customerUid).values().stream().mapToLong(Long::longValue).sum();
    }

    /** Monthly offer left this month plus earned rewards left. */
    public long offerLeft(DebtCustomer c) {
        int monthly = c.getMonthlyOffer() == null ? 0 : c.getMonthlyOffer();
        long monthlyLeft = Math.max(0, monthly - offerUseRepository.usedSince(c.getUid(),
                java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        return monthlyLeft + rewardLeft(c.getUid());
    }

    /**
     * Customers who earned an offer this round and still hold it: name, what
     * they earned, what is left to spend and when it expires - Madeni > Ofa.
     */
    public Response<Map<String, Object>> winners() {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        List<LoyaltyReward> live = rewardRepository.liveInBranch(branchUID, LocalDateTime.now());
        Map<String, Long> used = new HashMap<>();
        if (!live.isEmpty())
            for (Object[] r : offerUseRepository.usedOfRewards(live.stream().map(LoyaltyReward::getUid).toList()))
                used.put((String) r[0], ((Number) r[1]).longValue());
        Map<String, Map<String, Object>> byCustomer = new LinkedHashMap<>();
        for (LoyaltyReward r : live) {
            Map<String, Object> m = byCustomer.computeIfAbsent(r.getCustomerUid(), k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("customerUid", k);
                x.put("name", r.getCustomerName());
                x.put("earned", 0L);
                x.put("used", 0L);
                x.put("grantedAt", r.getGrantedAt());
                x.put("expiresAt", r.getExpiresAt());
                return x;
            });
            long u = Math.min(r.getAmount(), used.getOrDefault(r.getUid(), 0L));
            m.put("earned", (Long) m.get("earned") + r.getAmount());
            m.put("used", (Long) m.get("used") + u);
            if (r.getGrantedAt() != null && (m.get("grantedAt") == null || r.getGrantedAt().isAfter((LocalDateTime) m.get("grantedAt"))))
                m.put("grantedAt", r.getGrantedAt());
        }
        List<Map<String, Object>> rows = new ArrayList<>(byCustomer.values());
        for (Map<String, Object> m : rows) {
            m.put("left", Math.max(0, (Long) m.get("earned") - (Long) m.get("used")));
            customerRepository.findById((String) m.get("customerUid")).ifPresent(c -> m.put("phone", c.getPhone()));
        }
        // Still to spend first, biggest first; spent ones after.
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (Long) m.get("left")).reversed());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("customers", rows);
        out.put("withOffer", rows.stream().filter(m -> (Long) m.get("left") > 0).count());
        out.put("totalLeft", rows.stream().mapToLong(m -> (Long) m.get("left")).sum());
        return new Response<>(out);
    }

    /**
     * Reports > Ofa za wateja: in a period, the big bills each customer paid,
     * the offers they earned and spent, and what they hold now.
     */
    public Response<Map<String, Object>> report(String filter) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        LocalDateTime[] range = ReportRange.of(filter);
        List<LoyaltyVisit> visits = visitRepository.between(branchUID, range[0], range[1]);
        List<LoyaltyReward> rewards = rewardRepository.grantedBetween(branchUID, range[0], range[1]);
        List<OfferUse> uses = offerUseRepository.between(branchUID, range[0], range[1]).stream()
                .filter(u -> u.getRewardUid() != null).toList();

        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        java.util.function.BiFunction<String, String, Map<String, Object>> row = (uid, name) -> rows.computeIfAbsent(uid, k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("customerUid", k);
            m.put("name", name);
            m.put("bills", 0L);
            m.put("billsAmount", 0L);
            m.put("rewards", 0L);
            m.put("earned", 0L);
            m.put("used", 0L);
            return m;
        });
        for (LoyaltyVisit v : visits) {
            Map<String, Object> m = row.apply(v.getCustomerUid(), null);
            m.put("bills", (Long) m.get("bills") + 1);
            m.put("billsAmount", (Long) m.get("billsAmount") + (v.getAmount() == null ? 0 : v.getAmount()));
        }
        for (LoyaltyReward r : rewards) {
            Map<String, Object> m = row.apply(r.getCustomerUid(), r.getCustomerName());
            m.put("rewards", (Long) m.get("rewards") + 1);
            m.put("earned", (Long) m.get("earned") + r.getAmount());
            if (m.get("name") == null)
                m.put("name", r.getCustomerName());
        }
        for (OfferUse u : uses) {
            Map<String, Object> m = row.apply(u.getCustomerUid(), u.getCustomerName());
            m.put("used", (Long) m.get("used") + u.getAmount());
            if (m.get("name") == null)
                m.put("name", u.getCustomerName());
        }
        long leftNow = 0;
        for (Map<String, Object> m : rows.values()) {
            long left = rewardLeft((String) m.get("customerUid"));
            m.put("leftNow", left);
            leftNow += left;
            customerRepository.findById((String) m.get("customerUid")).ifPresent(c -> {
                m.put("phone", c.getPhone());
                if (m.get("name") == null)
                    m.put("name", c.getName());
            });
        }
        List<Map<String, Object>> list = new ArrayList<>(rows.values());
        list.sort(Comparator.comparing((Map<String, Object> m) -> (Long) m.get("earned"))
                .thenComparing(m -> (Long) m.get("bills")).reversed());

        List<Map<String, Object>> spent = new ArrayList<>();
        for (OfferUse u : uses) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("usedAt", u.getUsedAt());
            m.put("customerName", u.getCustomerName());
            m.put("billCode", u.getBillCode());
            m.put("usedBy", u.getUsedByName() != null ? u.getUsedByName() : u.getUsedBy());
            m.put("amount", u.getAmount());
            spent.add(m);
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("customers", list.size());
        totals.put("qualified", rewards.stream().map(LoyaltyReward::getCustomerUid).distinct().count());
        totals.put("bills", visits.size());
        totals.put("billsAmount", visits.stream().mapToLong(v -> v.getAmount() == null ? 0 : v.getAmount()).sum());
        totals.put("earned", rewards.stream().mapToLong(LoyaltyReward::getAmount).sum());
        totals.put("used", uses.stream().mapToLong(OfferUse::getAmount).sum());
        totals.put("leftNow", leftNow);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rule", ruleMap(rule()));
        out.put("totals", totals);
        out.put("customers", list);
        out.put("spent", spent);
        return new Response<>(out);
    }
}
