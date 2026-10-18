package com.midland.bar.Bar.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.midland.bar.Bar.Dto.RefundRequestDTO;
import com.midland.bar.Bar.Model.*;
import com.midland.bar.Bar.Repository.*;
import com.midland.bar.Config.Security.AuthChecker;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Exceptions.BusinessException;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Refunds on paid bills (owner, 2026-10-18) - see BillRefund. A cashier asks;
 * a CEO or manager (REFUND_SALES) approves or rejects, and their own requests
 * go straight through. Whoever asks gives the money back, so they need their
 * shift open and the refund comes off their cash-up.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BillRefundRepository refundRepository;
    private final SalesOpenedRepository salesOpenedRepository;
    private final BarSalesRepository barSalesRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final DebtEntryRepository debtEntryRepository;
    private final WorkShiftService workShiftService;
    private final BarService barService;
    private final AuthChecker authChecker;

    @Transactional
    public Response<BillRefund> request(RefundRequestDTO dto) {
        workShiftService.requireOpen();
        String branchUID = LoggerUser.getBranchUIDOrMain();
        SalesOpened bill = dto == null || dto.getSalesOpenedUID() == null ? null
                : salesOpenedRepository.findForUpdate(dto.getSalesOpenedUID(), branchUID).orElse(null);
        if (bill == null)
            return new Response<>("Bill not found");
        if (!"PAID".equals(bill.getPaymentStatus()))
            return new Response<>("Bill " + bill.getSalesCode() + " is not paid - take the item off the bill instead");
        String reason = dto.getReason() == null ? "" : dto.getReason().trim();
        if (reason.length() < 3)
            return new Response<>("Write why the money goes back");
        if (reason.length() > 300)
            return new Response<>("The reason is too long");
        String method = dto.getMethod() == null ? "" : dto.getMethod().trim().toLowerCase();
        boolean credit = DebtService.METHOD.equals(method);
        if (!BillPaymentService.METHODS.contains(method) && !credit)
            return new Response<>("Unknown payment method: " + dto.getMethod());
        if (dto.getLines() == null || dto.getLines().isEmpty())
            return new Response<>("Choose what is given back");

        List<Map<String, Object>> lines = new ArrayList<>();
        int amount = 0;
        Set<String> seen = new HashSet<>();
        for (RefundRequestDTO.Line l : dto.getLines()) {
            if (l.getBarSalesUID() == null || !seen.add(l.getBarSalesUID()))
                return new Response<>("Choose each item once");
            BarSales line = barSalesRepository.findById(l.getBarSalesUID())
                    .filter(x -> x.getSalesOpened() != null && bill.getUid().equals(x.getSalesOpened().getUid()))
                    .orElse(null);
            if (line == null)
                return new Response<>("That item is not on bill " + bill.getSalesCode());
            int lineQty = line.getQuantity() == null || line.getQuantity() < 1 ? 1 : line.getQuantity();
            int qty = l.getQuantity() == null ? lineQty : l.getQuantity();
            if (qty < 1 || qty > lineQty)
                return new Response<>("Between 1 and " + lineQty + " can go back");
            int unit = line.getUnitPrice() != null ? line.getUnitPrice()
                    : (line.getLineTotal() != null ? line.getLineTotal() / lineQty : 0);
            int lineAmount = qty == lineQty && line.getLineTotal() != null ? line.getLineTotal() : unit * qty;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("barSalesUID", line.getUid());
            row.put("serviceName", line.getBarServiceEntity() == null ? null : line.getBarServiceEntity().getServiceName());
            row.put("quantity", qty);
            row.put("unitPrice", unit);
            row.put("amount", lineAmount);
            lines.add(row);
            amount += lineAmount;
        }

        // Never more back than came in, counting refunds already asked for.
        int paid = bill.getPaidAmount() == null ? 0 : bill.getPaidAmount();
        int already = refundRepository.forBill(bill.getUid()).stream().mapToInt(BillRefund::getAmount).sum();
        if (amount + already > paid)
            return new Response<>("Only " + Math.max(0, paid - already) + " of bill " + bill.getSalesCode() + " can still go back");

        BillRefund refund = new BillRefund();
        refund.setBillUid(bill.getUid());
        refund.setSalesCode(bill.getSalesCode());
        try {
            refund.setLinesJson(JSON.writeValueAsString(lines));
        } catch (Exception e) {
            throw new BusinessException("Could not record the items");
        }
        refund.setAmount(amount);
        refund.setMethod(method);
        if (credit) {
            String customer = billPaymentRepository.findByBill(bill.getUid(), branchUID).stream()
                    .filter(p -> DebtService.METHOD.equals(p.getMethod()) && p.getCustomerUid() != null)
                    .map(BillPayment::getCustomerUid).findFirst().orElse(null);
            if (customer == null)
                return new Response<>("Bill " + bill.getSalesCode() + " did not go on credit - give the money back another way");
            refund.setCustomerUid(customer);
        }
        refund.setReason(reason);
        refund.setRequestedBy(LoggerUser.getEmail());
        refund.setRequestedByName(CashUpService.nameOf(LoggerUser.getUser()));
        refund.setRequestedAt(LocalDateTime.now());
        refundRepository.save(refund);
        log.info("{} asked to refund {} {} on bill {}: {}", refund.getRequestedBy(), amount, method, bill.getSalesCode(), reason);

        // A CEO or manager asking needs nobody's approval.
        if (canApprove())
            return decide(refund.getUid(), true, null);
        return new Response<>(refund);
    }

    /** Approve (the items come off, the money goes back) or reject. */
    @Transactional
    public Response<BillRefund> decide(String uid, boolean approve, String note) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        BillRefund refund = refundRepository.findForUpdate(uid, branchUID).orElse(null);
        if (refund == null)
            return new Response<>("Refund not found");
        if (!BillRefund.PENDING.equals(refund.getStatus()))
            return new Response<>("This refund was already " + refund.getStatus().toLowerCase());
        String n = note == null ? "" : note.trim();
        if (!approve && n.length() < 3)
            return new Response<>("Write why it is rejected");

        refund.setDecidedBy(LoggerUser.getEmail());
        refund.setDecidedByName(CashUpService.nameOf(LoggerUser.getUser()));
        refund.setDecidedAt(LocalDateTime.now());
        refund.setDecisionNote(n.isEmpty() ? null : (n.length() > 300 ? n.substring(0, 300) : n));
        if (!approve) {
            refund.setStatus(BillRefund.REJECTED);
            log.info("{} rejected refund {} on bill {}", refund.getDecidedBy(), uid, refund.getSalesCode());
            return new Response<>(refundRepository.save(refund));
        }

        List<Map<String, Object>> lines;
        try {
            lines = JSON.readValue(refund.getLinesJson(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException("Could not read the refund's items");
        }
        for (Map<String, Object> l : lines) {
            Response<SalesOpened> done = barService.takeOffForRefund(String.valueOf(l.get("barSalesUID")),
                    ((Number) l.get("quantity")).intValue(), "Refund: " + refund.getReason());
            // An item changed since it was asked for: nothing of this refund happens.
            if (done.getData() == null)
                throw new BusinessException(done.getMessage());
        }
        if (refund.getCustomerUid() != null) {
            long owes = debtEntryRepository.balanceOf(refund.getCustomerUid());
            if (refund.getAmount() > owes)
                throw new BusinessException("The customer owes only " + owes + " - give the rest back another way");
            DebtEntry entry = new DebtEntry();
            entry.setCustomerUid(refund.getCustomerUid());
            entry.setKind(DebtEntry.REFUND);
            entry.setAmount(refund.getAmount());
            entry.setBillUid(refund.getBillUid());
            entry.setBillCode(refund.getSalesCode());
            entry.setNote(refund.getReason());
            entry.setEntryBy(refund.getDecidedBy());
            entry.setEntryByName(refund.getDecidedByName());
            entry.setEntryAt(refund.getDecidedAt());
            debtEntryRepository.save(entry);
        }
        refund.setStatus(BillRefund.APPROVED);
        log.info("{} approved refund {} of {} {} on bill {}", refund.getDecidedBy(), uid, refund.getAmount(), refund.getMethod(), refund.getSalesCode());
        return new Response<>(refundRepository.save(refund));
    }

    public ResponseList<BillRefund> pending() {
        return new ResponseList<>(refundRepository.pending(LoggerUser.getBranchUIDOrMain()));
    }

    /** A period's refunds; a cashier sees only their own. */
    public ResponseList<BillRefund> list(String filter) {
        LocalDateTime[] range = ReportRange.of(filter);
        List<BillRefund> all = refundRepository.between(LoggerUser.getBranchUIDOrMain(), range[0], range[1]);
        if (canApprove() || CashUpService.seesAll())
            return new ResponseList<>(all);
        String me = LoggerUser.getEmail();
        return new ResponseList<>(all.stream().filter(r -> me.equals(r.getRequestedBy())).toList());
    }

    private boolean canApprove() {
        return Boolean.TRUE.equals(authChecker.hasPermissionOrRoot("REFUND_SALES"));
    }
}
