package com.midland.bar.Bar.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.midland.bar.Bar.Dto.PayBillDTO;
import com.midland.bar.Bar.Model.BillPayment;
import com.midland.bar.Bar.Model.SalesOpened;
import com.midland.bar.Bar.Projection.BarProjection;
import com.midland.bar.Bar.Repository.BarSalesRepository;
import com.midland.bar.Bar.Repository.BillPaymentRepository;
import com.midland.bar.Bar.Repository.SalesOpenedRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Notification.Service.NotificationService;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Utils.Exceptions.BusinessException;
import com.midland.bar.Utils.Responses.Response;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/** Paying bills, and the receipt that follows. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillPaymentService {

    /** The ways a bill can be paid. Stored lower-case, as the till has always sent them. */
    public static final Set<String> METHODS = Set.of("cash", "mpesa", "tigopesa", "airtelmoney", "halopesa", "bank");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SalesOpenedRepository salesOpenedRepository;
    private final BarSalesRepository barSalesRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final NotificationService notificationService;

    /**
     * Settles a bill in one or more payments. The amount due is worked out
     * here from the bill's lines - not taken from the screen - and the
     * payments must add up to it exactly. Paying frees the bill's code.
     */
    @Transactional
    public Response<SalesOpened> payBill(PayBillDTO dto) {
        String branchUID = Optional.ofNullable(LoggerUser.getBranchUID()).orElse("MAIN_OFFICE");
        SalesOpened bill = salesOpenedRepository.findForUpdate(dto.getSalesOpenedUID(), branchUID).orElse(null);
        if (bill == null)
            return new Response<>("Open Sale Not Found");
        if ("PAID".equals(bill.getPaymentStatus()))
            return new Response<>("Bill " + bill.getSalesCode() + " is already paid");

        int due = (int) barSalesRepository.billTotal(bill.getUid());
        if (due <= 0)
            return new Response<>("Bill " + bill.getSalesCode() + " has nothing on it to pay");

        int paid = 0;
        List<BillPayment> rows = new ArrayList<>();
        List<Map<String, Object>> breakdown = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String by = LoggerUser.getEmail();
        for (PayBillDTO.Part part : dto.getPayments()) {
            String method = part.getMethod() == null ? "" : part.getMethod().trim().toLowerCase();
            if (!METHODS.contains(method))
                return new Response<>("Unknown payment method: " + part.getMethod());
            int amount = part.getAmount();
            Integer tendered = null;
            Integer change = null;
            if ("cash".equals(method) && part.getTendered() != null) {
                if (part.getTendered() < amount)
                    return new Response<>("Cash handed over (" + part.getTendered() + ") is less than the cash amount (" + amount + ")");
                tendered = part.getTendered();
                change = tendered - amount;
            }
            BillPayment row = new BillPayment();
            row.setSalesOpened(bill);
            row.setMethod(method);
            row.setAmount(amount);
            row.setTendered(tendered);
            row.setChangeGiven(change);
            row.setReceivedBy(by);
            row.setReceivedAt(now);
            rows.add(row);
            breakdown.add(Map.of("method", method, "amount", amount));
            paid += amount;
        }
        if (paid != due)
            return new Response<>("Payments add up to " + paid + " but the bill is " + due);

        billPaymentRepository.saveAll(rows);
        bill.setBill(due);
        bill.setPaidAmount(paid);
        bill.setPaymentStatus("PAID");
        bill.setPaymentMethod(rows.size() == 1 ? rows.get(0).getMethod() : "split");
        bill.setPaidBy(by);
        bill.setPaidAt(now);
        try {
            bill.setPaymentBreakdown(JSON.writeValueAsString(breakdown));
        } catch (Exception e) {
            throw new BusinessException("Could not record the payment breakdown");
        }
        bill.update();
        SalesOpened saved = salesOpenedRepository.save(bill);

        try {
            notificationService.notifySaleCompleted(saved);
        } catch (Exception notifyError) {
            log.warn("Failed to send sale-completed notification: {}", notifyError.getMessage());
        }
        log.info("{} took payment of {} for bill {} ({} part(s))", by, paid, bill.getSalesCode(), rows.size());
        return new Response<>(saved);
    }

    /** Everything a printed receipt shows, for a paid bill (or a pro-forma of an open one). */
    public Response<Map<String, Object>> receipt(String billUid) {
        String branchUID = Optional.ofNullable(LoggerUser.getBranchUID()).orElse("MAIN_OFFICE");
        SalesOpened bill = salesOpenedRepository.findById(billUid)
                .filter(b -> branchUID.equals(b.getBranchUid()))
                .orElse(null);
        if (bill == null)
            return new Response<>("Open Sale Not Found");

        List<Map<String, Object>> lines = new ArrayList<>();
        for (BarProjection line : barSalesRepository.findBarSalesList(LoggerUser.getBranchUID(), bill.getUid())) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("name", line.getServiceName());
            l.put("quantity", line.getQuantity());
            l.put("unitPrice", line.getPrice());
            l.put("lineTotal", line.getLineTotal());
            lines.add(l);
        }
        List<Map<String, Object>> payments = new ArrayList<>();
        for (BillPayment p : billPaymentRepository.findByBill(bill.getUid(), branchUID)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("method", p.getMethod());
            m.put("amount", p.getAmount());
            m.put("tendered", p.getTendered());
            m.put("change", p.getChangeGiven());
            payments.add(m);
        }

        String branchName = null;
        try {
            User user = LoggerUser.getUser();
            branchName = user.getBranch() == null ? null : user.getBranch().getBranchName();
        } catch (Exception ignored) {
            // A receipt without the branch name is still a receipt.
        }

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("uid", bill.getUid());
        receipt.put("code", bill.getSalesCode());
        receipt.put("branchName", branchName);
        receipt.put("status", bill.getPaymentStatus());
        receipt.put("total", barSalesRepository.billTotal(bill.getUid()));
        receipt.put("paidBy", bill.getPaidBy());
        receipt.put("paidAt", bill.getPaidAt());
        receipt.put("lines", lines);
        receipt.put("payments", payments);
        return new Response<>(receipt);
    }
}
