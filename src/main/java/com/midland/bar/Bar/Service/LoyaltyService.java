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
        String branch = LoggerUser.getBranchUIDOrMain();
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
        String number = SmsService.normalise(phone);
        if (number == null)
            throw new BusinessException("Enter a valid phone number, e.g. 0712 345 678");
        DebtCustomer c = customerRepository.findByPhone(number, LoggerUser.getBranchUIDOrMain())
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
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uid", c.getUid());
        m.put("name", c.getName());
        m.put("offerLeft", offerLeft(c));
        int limit = c.getCreditLimit() == null ? 0 : c.getCreditLimit();
        m.put("creditLeft", Math.max(0, limit - entryRepository.balanceOf(c.getUid())));
        LoyaltyRule rule = rule();
        m.put("loyalty", progress(c.getUid(), rule));
        return m;
    }

    /** Visits counted so far in the current round, out of how many. */
    public Map<String, Object> progress(String customerUid, LoyaltyRule rule) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", Boolean.TRUE.equals(rule.getEnabled()));
        m.put("visits", rule.getUid() == null ? 0 : visitRepository.counting(customerUid, windowStart(rule)).size());
        m.put("needed", rule.getBillsNeeded());
        m.put("rewardLeft", rewardLeft(customerUid));
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
        return new Response<>(card(c));
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
}
