package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Dto.DebtCustomerDTO;
import com.midland.bar.Bar.Dto.DebtPaymentDTO;
import com.midland.bar.Bar.Dto.DebtWriteOffDTO;
import com.midland.bar.Bar.Model.DebtCustomer;
import com.midland.bar.Bar.Model.DebtEntry;
import com.midland.bar.Bar.Model.SalesOpened;
import com.midland.bar.Bar.Repository.DebtCustomerRepository;
import com.midland.bar.Bar.Repository.DebtEntryRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Notification.Sms.Service.SmsService;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Utils.Exceptions.BusinessException;
import com.midland.bar.Utils.Offline.OfflineContext;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Customers' debts (owner, 2026-10-18). A bill put on credit is paid with
 * the method "deni" for the customer: the bill closes and its code is free,
 * the sale counts as it always did, and the amount goes on the customer's
 * account. The bar carries it - no staff member is charged. Money the
 * customer brings later is a PAYMENT, taken in a shift and counted in that
 * cashier's cash-up by its method. A CEO may write off what will never come.
 *
 * The customer is texted when a bill goes on their account and when they
 * pay, and reminded weekly while they owe (DebtReminderJob).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DebtService {

    /** The payment method a bill part goes on credit by. Not a way money comes in, so not in BillPaymentService.METHODS. */
    public static final String METHOD = "deni";

    public static final String SMS_CHARGE = "DEBT_CHARGE";
    public static final String SMS_PAYMENT = "DEBT_PAYMENT";
    public static final String SMS_REMINDER = "DEBT_REMINDER";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DebtCustomerRepository customerRepository;
    private final DebtEntryRepository entryRepository;
    private final WorkShiftService workShiftService;
    private final SmsService smsService;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    /** Every credit customer in the branch with what they owe and since when, most owed first. */
    public ResponseList<Map<String, Object>> customers() {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        Map<String, Long> balances = balances(branchUID);
        Map<String, LocalDateTime> since = owedSince(branchUID, balances);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DebtCustomer c : customerRepository.findInBranch(branchUID))
            rows.add(row(c, balances.getOrDefault(c.getUid(), 0L), since.get(c.getUid())));
        rows.sort(Comparator.comparingLong((Map<String, Object> r) -> -(Long) r.get("balance"))
                .thenComparing(r -> String.valueOf(r.get("name"))));
        return new ResponseList<>(rows);
    }

    /** One customer's account: who they are, what they owe, and every line, newest first. */
    public Response<Map<String, Object>> ledger(String customerUid) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        Optional<DebtCustomer> customer = customerRepository.findInBranch(customerUid, branchUID);
        if (customer.isEmpty())
            return new Response<>("Customer not found");
        long balance = entryRepository.balanceOf(customerUid);
        Map<String, Object> out = row(customer.get(), balance,
                owedSince(branchUID, Map.of(customerUid, balance)).get(customerUid));
        out.put("entries", entryRepository.ledger(customerUid, branchUID));
        return new Response<>(out);
    }

    /**
     * Registers a customer, or corrects their name, phone and note. Only a
     * CEO or manager (canSetLimit) may set the limit; a customer a cashier
     * registers waits at 0 - no credit - until one of them does.
     */
    @Transactional
    public Response<Map<String, Object>> save(String uid, DebtCustomerDTO dto, boolean canSetLimit) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        String name = dto.getName() == null ? "" : dto.getName().trim().replaceAll("\\s+", " ").toUpperCase();
        if (name.length() < 2)
            return new Response<>("Enter the customer's name");
        if (name.length() > 80)
            return new Response<>("The name is too long");
        String phone = SmsService.normalise(dto.getPhone());
        if (phone == null)
            return new Response<>("Enter a valid phone number, e.g. 0712 345 678");
        String note = dto.getNote() == null ? null : dto.getNote().trim();
        if (note != null && note.length() > 200)
            return new Response<>("The note is too long");

        DebtCustomer customer;
        if (uid == null) {
            customer = new DebtCustomer();
            customer.setRegisteredBy(LoggerUser.getEmail());
            customer.setRegisteredAt(LocalDateTime.now());
        } else {
            customer = customerRepository.findInBranch(uid, branchUID).orElse(null);
            if (customer == null)
                return new Response<>("Customer not found");
        }
        Optional<DebtCustomer> samePhone = customerRepository.findByPhone(phone, branchUID);
        if (samePhone.isPresent() && !samePhone.get().getUid().equals(customer.getUid()))
            return new Response<>("This phone number is already registered to " + samePhone.get().getName());

        customer.setName(name);
        customer.setPhone(phone);
        customer.setNote(note == null || note.isEmpty() ? null : note);
        if (dto.getCreditLimit() != null && canSetLimit) {
            if (dto.getCreditLimit() < 0 || dto.getCreditLimit() > 100_000_000)
                return new Response<>("The limit must be between 0 and 100,000,000");
            if (!dto.getCreditLimit().equals(customer.getCreditLimit())) {
                customer.setCreditLimit(dto.getCreditLimit());
                customer.setLimitSetBy(LoggerUser.getEmail());
                customer.setLimitSetAt(LocalDateTime.now());
            }
        }
        if (uid != null)
            customer.update();
        DebtCustomer saved = customerRepository.save(customer);
        log.info("{} {} credit customer {} (limit {})", LoggerUser.getEmail(), uid == null ? "registered" : "updated", saved.getUid(), saved.getCreditLimit());
        long balance = entryRepository.balanceOf(saved.getUid());
        return new Response<>(row(saved, balance, null));
    }

    /**
     * Puts part of a bill on a customer's account, as payBill takes it. Within
     * the limit or not at all; never offline - the limit is only known here.
     */
    @Transactional
    public DebtEntry charge(SalesOpened bill, String customerUid, int amount, LocalDateTime at) {
        if (OfflineContext.isReplay())
            throw new BusinessException("A bill goes on credit only with internet - take it again online");
        String branchUID = LoggerUser.getBranchUIDOrMain();
        if (customerUid == null || customerUid.isBlank())
            throw new BusinessException("Choose the customer the bill goes on credit to");
        DebtCustomer customer = customerRepository.findForUpdate(customerUid, branchUID)
                .orElseThrow(() -> new BusinessException("Customer not found"));
        int limit = customer.getCreditLimit() == null ? 0 : customer.getCreditLimit();
        if (limit <= 0)
            throw new BusinessException(customer.getName() + " has no credit limit yet - a CEO or manager sets it in Madeni");
        long owed = entryRepository.balanceOf(customerUid);
        if (owed + amount > limit)
            throw new BusinessException(customer.getName() + " owes " + tzs(owed) + " of a " + tzs(limit)
                    + " limit - at most " + tzs(Math.max(0, limit - owed)) + " more can go on credit");

        DebtEntry entry = new DebtEntry();
        entry.setCustomerUid(customerUid);
        entry.setKind(DebtEntry.CHARGE);
        entry.setAmount(amount);
        entry.setBillUid(bill.getUid());
        entry.setBillCode(bill.getSalesCode());
        entry.setEntryBy(LoggerUser.getEmail());
        entry.setEntryByName(displayName(LoggerUser.getEmail()));
        entry.setEntryAt(at);
        DebtEntry saved = entryRepository.save(entry);
        long total = owed + amount;
        textAfterCommit(SMS_CHARGE, customer, String.format(
                "%s: Deni la Tsh %s limeandikwa kwako (bili %s). Jumla unadaiwa Tsh %s. Asante.",
                branchName(branchUID), tzs(amount), bill.getSalesCode(), tzs(total)));
        log.info("{} put {} of bill {} on credit for customer {} (now owes {})", LoggerUser.getEmail(), amount, bill.getSalesCode(), customerUid, total);
        return saved;
    }

    /** The customer's name, for the bill payment it was charged by. */
    public String nameOf(String customerUid) {
        return customerRepository.findInBranch(customerUid, LoggerUser.getBranchUIDOrMain())
                .map(DebtCustomer::getName).orElse(null);
    }

    /**
     * Money a customer brings in. Taken in the cashier's open shift, by one of
     * the bill methods, and no more than they owe; it is in their cash-up.
     */
    @Transactional
    public Response<Map<String, Object>> pay(DebtPaymentDTO dto) {
        workShiftService.requireOpen();
        String branchUID = LoggerUser.getBranchUIDOrMain();
        DebtCustomer customer = dto.getCustomerUid() == null ? null
                : customerRepository.findForUpdate(dto.getCustomerUid(), branchUID).orElse(null);
        if (customer == null)
            return new Response<>("Customer not found");
        String method = dto.getMethod() == null ? "" : dto.getMethod().trim().toLowerCase();
        if (!BillPaymentService.METHODS.contains(method))
            return new Response<>("Unknown payment method: " + dto.getMethod());
        int amount = dto.getAmount() == null ? 0 : dto.getAmount();
        if (amount <= 0)
            return new Response<>("Enter the amount");
        long owed = entryRepository.balanceOf(customer.getUid());
        if (owed <= 0)
            return new Response<>(customer.getName() + " owes nothing");
        if (amount > owed)
            return new Response<>(customer.getName() + " owes only " + tzs(owed));
        Integer tendered = null;
        Integer change = null;
        if ("cash".equals(method) && dto.getTendered() != null) {
            if (dto.getTendered() < amount)
                return new Response<>("Cash handed over (" + dto.getTendered() + ") is less than the amount (" + amount + ")");
            tendered = dto.getTendered();
            change = tendered - amount;
        }
        String note = dto.getNote() == null ? "" : dto.getNote().trim();
        if (note.length() > 100)
            return new Response<>("The reference is too long");

        DebtEntry entry = new DebtEntry();
        entry.setCustomerUid(customer.getUid());
        entry.setKind(DebtEntry.PAYMENT);
        entry.setAmount(amount);
        entry.setMethod(method);
        entry.setTendered(tendered);
        entry.setChangeGiven(change);
        entry.setNote(note.isEmpty() ? null : note.toUpperCase());
        entry.setEntryBy(LoggerUser.getEmail());
        entry.setEntryByName(displayName(LoggerUser.getEmail()));
        entry.setEntryAt(LocalDateTime.now());
        entryRepository.save(entry);
        long left = owed - amount;
        textAfterCommit(SMS_PAYMENT, customer, left > 0
                ? String.format("%s: Tumepokea Tsh %s. Bado unadaiwa Tsh %s. Asante.", branchName(branchUID), tzs(amount), tzs(left))
                : String.format("%s: Tumepokea Tsh %s. Deni lako limelipwa lote. Asante sana.", branchName(branchUID), tzs(amount)));
        log.info("{} took {} {} from credit customer {} (still owes {})", LoggerUser.getEmail(), amount, method, customer.getUid(), left);
        return ledger(customer.getUid());
    }

    /** A CEO giving up on what a customer owes (or part of it). No money moves; the reason is kept. */
    @Transactional
    public Response<Map<String, Object>> writeOff(DebtWriteOffDTO dto) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        DebtCustomer customer = dto.getCustomerUid() == null ? null
                : customerRepository.findForUpdate(dto.getCustomerUid(), branchUID).orElse(null);
        if (customer == null)
            return new Response<>("Customer not found");
        int amount = dto.getAmount() == null ? 0 : dto.getAmount();
        if (amount <= 0)
            return new Response<>("Enter the amount");
        long owed = entryRepository.balanceOf(customer.getUid());
        if (amount > owed)
            return new Response<>(customer.getName() + " owes only " + tzs(owed));
        String reason = dto.getReason() == null ? "" : dto.getReason().trim();
        if (reason.length() < 3)
            return new Response<>("Write why the debt is written off");
        if (reason.length() > 300)
            return new Response<>("The reason is too long");

        DebtEntry entry = new DebtEntry();
        entry.setCustomerUid(customer.getUid());
        entry.setKind(DebtEntry.WRITE_OFF);
        entry.setAmount(amount);
        entry.setNote(reason);
        entry.setEntryBy(LoggerUser.getEmail());
        entry.setEntryByName(displayName(LoggerUser.getEmail()));
        entry.setEntryAt(LocalDateTime.now());
        entryRepository.save(entry);
        log.info("{} wrote off {} of credit customer {}: {}", LoggerUser.getEmail(), amount, customer.getUid(), reason);
        return ledger(customer.getUid());
    }

    /**
     * The branch's debt in a period: what went on credit, came in and was
     * written off, every line, and what is owed now in total.
     */
    public Response<Map<String, Object>> report(String filter) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        LocalDateTime[] range = ReportRange.of(filter);
        Map<String, String> names = new HashMap<>();
        customerRepository.findInBranch(branchUID).forEach(c -> names.put(c.getUid(), c.getName()));
        long charged = 0, paid = 0, writtenOff = 0;
        List<Map<String, Object>> lines = new ArrayList<>();
        for (DebtEntry e : entryRepository.between(branchUID, range[0], range[1])) {
            switch (e.getKind()) {
                case DebtEntry.CHARGE -> charged += e.getAmount();
                case DebtEntry.PAYMENT -> paid += e.getAmount();
                default -> writtenOff += e.getAmount();
            }
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("uid", e.getUid());
            l.put("customerUid", e.getCustomerUid());
            l.put("customerName", names.get(e.getCustomerUid()));
            l.put("kind", e.getKind());
            l.put("amount", e.getAmount());
            l.put("billCode", e.getBillCode());
            l.put("method", e.getMethod());
            l.put("note", e.getNote());
            l.put("by", e.getEntryByName() != null ? e.getEntryByName() : e.getEntryBy());
            l.put("at", e.getEntryAt());
            lines.add(l);
        }
        long owedNow = balances(branchUID).values().stream().mapToLong(Long::longValue).filter(b -> b > 0).sum();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("charged", charged);
        out.put("paid", paid);
        out.put("writtenOff", writtenOff);
        out.put("owedNow", owedNow);
        out.put("lines", lines);
        return new Response<>(out);
    }

    /** Monday's text to everyone who still owes, every branch. Called by DebtReminderJob, outside any request. */
    public int sendReminders() {
        int sent = 0;
        Map<String, Map<String, Long>> balancesByBranch = new HashMap<>();
        Map<String, Map<String, LocalDateTime>> sinceByBranch = new HashMap<>();
        for (DebtCustomer c : customerRepository.findAllActive()) {
            String branchUID = c.getBranchUid();
            Map<String, Long> balances = balancesByBranch.computeIfAbsent(branchUID, this::balances);
            long owed = balances.getOrDefault(c.getUid(), 0L);
            if (owed <= 0)
                continue;
            LocalDateTime since = sinceByBranch.computeIfAbsent(branchUID, b -> owedSince(b, balances)).get(c.getUid());
            String body = String.format("%s: Kumbusho - unadaiwa Tsh %s%s. Karibu ulipe. Asante.",
                    branchName(branchUID), tzs(owed), since == null ? "" : " tangu " + since.format(DAY));
            smsService.send(SMS_REMINDER, c.getUid(), c.getPhone(), body, true);
            sent++;
        }
        return sent;
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, Long> balances(String branchUID) {
        Map<String, Long> out = new HashMap<>();
        for (Object[] r : entryRepository.balances(branchUID))
            out.put((String) r[0], ((Number) r[1]).longValue());
        return out;
    }

    /**
     * Since when each balance has been owed: payments clear the oldest charges
     * first, so it is the oldest charge still needed to make up the balance.
     */
    private Map<String, LocalDateTime> owedSince(String branchUID, Map<String, Long> balances) {
        Map<String, Long> left = new HashMap<>();
        balances.forEach((uid, b) -> { if (b > 0) left.put(uid, b); });
        Map<String, LocalDateTime> out = new HashMap<>();
        if (left.isEmpty())
            return out;
        for (DebtEntry e : entryRepository.chargesNewestFirst(branchUID)) {
            Long rest = left.get(e.getCustomerUid());
            if (rest == null || rest <= 0)
                continue;
            out.put(e.getCustomerUid(), e.getEntryAt());
            left.put(e.getCustomerUid(), rest - e.getAmount());
        }
        return out;
    }

    private Map<String, Object> row(DebtCustomer c, long balance, LocalDateTime since) {
        Map<String, Object> r = new LinkedHashMap<>();
        int limit = c.getCreditLimit() == null ? 0 : c.getCreditLimit();
        r.put("uid", c.getUid());
        r.put("name", c.getName());
        r.put("phone", c.getPhone());
        r.put("note", c.getNote());
        r.put("creditLimit", limit);
        r.put("balance", balance);
        r.put("available", Math.max(0, limit - balance));
        r.put("owedSince", since);
        r.put("daysOwed", since == null ? null : java.time.temporal.ChronoUnit.DAYS.between(since.toLocalDate(), LocalDate.now()));
        r.put("registeredAt", c.getRegisteredAt());
        return r;
    }

    /** Texts the customer once the money is on their account for good - a rolled-back bill sends nothing. */
    private void textAfterCommit(String template, DebtCustomer customer, String body) {
        String uid = customer.getUid();
        String phone = customer.getPhone();
        Runnable send = () -> smsService.send(template, uid, phone, body, true);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private String branchName(String branchUID) {
        return branchRepository.findById(branchUID)
                .map(b -> b.getBranchName())
                .filter(n -> n != null && !n.isBlank())
                .orElse("Baronix");
    }

    private String displayName(String login) {
        if (login == null || login.isBlank())
            return login;
        String name = userRepository.findFullNameByLogin(login);
        return name == null || name.isBlank() ? login : name;
    }

    private static String tzs(long amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }
}
