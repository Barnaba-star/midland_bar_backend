package com.midland.bar.Bar.Table;

import com.midland.bar.Bar.Model.SalesOpened;
import com.midland.bar.Bar.Repository.SalesOpenedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A bill's own QR (owner, 2026-11-10): printed on the bill or shown on the
 * waiter's phone, it opens the customers' window for that bill only.
 */
@Service
@RequiredArgsConstructor
public class BillQrService {

    /** Table tokens are 10 long; a bill's is 16, so the window tells them apart. */
    public static final int TOKEN_LENGTH = 16;
    /** A paid bill's window stays readable this long. */
    public static final int PAID_HOURS = 6;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SalesOpenedRepository salesOpenedRepository;

    /** The bill's token, made and kept the first time it is asked for. */
    public String tokenOf(SalesOpened bill) {
        if (bill.getCustomerToken() == null) {
            String t;
            do {
                StringBuilder b = new StringBuilder(TOKEN_LENGTH);
                for (int i = 0; i < TOKEN_LENGTH; i++)
                    b.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
                t = b.toString();
            } while (salesOpenedRepository.findByCustomerToken(t).isPresent());
            bill.setCustomerToken(t);
            salesOpenedRepository.save(bill);
        }
        return bill.getCustomerToken();
    }

    /** For the waiter: their unpaid bill's token, to show or print (a code + PIN waiter gets their own only). */
    public com.midland.bar.Utils.Responses.Response<java.util.Map<String, Object>> qrFor(String billUid) {
        SalesOpened bill = salesOpenedRepository.findById(billUid)
                .filter(b -> com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain().equals(b.getBranchUid()))
                .orElse(null);
        if (bill == null)
            return new com.midland.bar.Utils.Responses.Response<>("Open Sale Not Found");
        com.midland.bar.Config.Security.StaffSession.requireOwnBill(bill);
        if (!"PENDING".equals(bill.getPaymentStatus()))
            return new com.midland.bar.Utils.Responses.Response<>("Bill " + bill.getSalesCode() + " is already paid");
        if (bill.getStaffUid() == null)
            return new com.midland.bar.Utils.Responses.Response<>("Only a waiter's bill has a QR");
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("token", tokenOf(bill));
        out.put("code", bill.getSalesCode());
        out.put("staffName", bill.getStaffName());
        return new com.midland.bar.Utils.Responses.Response<>(out);
    }

    /** The bill behind a token, while its window is open: unpaid, or paid in the last few hours. */
    public Optional<SalesOpened> billFor(String token) {
        String key = token == null ? "" : token.trim().toUpperCase();
        if (key.length() != TOKEN_LENGTH)
            return Optional.empty();
        LocalDateTime paidSince = LocalDateTime.now().minusHours(PAID_HOURS);
        return salesOpenedRepository.findByCustomerToken(key).filter(b -> "PENDING".equals(b.getPaymentStatus())
                || ("PAID".equals(b.getPaymentStatus()) && b.getPaidAt() != null && b.getPaidAt().isAfter(paidSince)));
    }

    /** The bill read as a table of its own, for the window's code (never saved). */
    public static BarTable asTable(SalesOpened bill, String token) {
        BarTable t = new BarTable();
        t.setUid(BILL_PREFIX + bill.getUid());
        String name = (bill.getTableName() != null ? bill.getTableName() + " · " : "Bill ") + bill.getSalesCode();
        t.setName(name.length() > 40 ? name.substring(0, 40) : name);
        t.setNumber(0);
        t.setToken(token.trim().toUpperCase());
        t.setBranchUid(bill.getBranchUid());
        return t;
    }

    /** Uids of tables made from bills start so. */
    public static final String BILL_PREFIX = "B:";

    public static boolean isBill(BarTable t) {
        return t != null && t.getUid() != null && t.getUid().startsWith(BILL_PREFIX);
    }

    public static String billUid(BarTable t) {
        return isBill(t) ? t.getUid().substring(BILL_PREFIX.length()) : null;
    }
}
