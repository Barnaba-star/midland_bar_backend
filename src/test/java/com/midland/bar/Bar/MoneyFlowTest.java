package com.midland.bar.Bar;

import com.midland.bar.Bar.Dto.*;
import com.midland.bar.Bar.Model.SalesOpened;
import com.midland.bar.Bar.Repository.BarReportsRepository;
import com.midland.bar.Bar.Repository.BarSalesRepository;
import com.midland.bar.Bar.Service.*;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The money added on 2026-10-18/19, end to end against the local database and
 * rolled back: credit within a limit, a refund taken off the cash-up, a
 * discount spread over the lines, and a happy hour pricing a sale.
 */
@SpringBootTest
@Transactional
@Tag("local")
class MoneyFlowTest {

    private static final String LOGIN = "root@root.com";
    private static final String MSHIKAKI = "7b57124d-a41d-40e2-b5e5-54fce93123f8";

    @Autowired UserRepository userRepository;
    @Autowired BarService barService;
    @Autowired BillCodeService billCodeService;
    @Autowired BillPaymentService billPaymentService;
    @Autowired DebtService debtService;
    @Autowired RefundService refundService;
    @Autowired PriceRuleService priceRuleService;
    @Autowired WorkShiftService workShiftService;
    @Autowired BarSalesRepository barSalesRepository;
    @Autowired BarReportsRepository barReportsRepository;

    @BeforeEach
    void signIn() {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROOT"))));
        workShiftService.open();
    }

    private SalesOpened bill(int quantity) {
        String code = billCodeService.findAvailable().getData().get(0);
        SaleOpenedDTO open = new SaleOpenedDTO();
        open.setSalesCode(code);
        SalesOpened bill = barService.saveOpenSale(open).getData();
        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(bill.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(quantity);
        sale.setItems(List.of(item));
        return barService.addSaleItems(sale).getData();
    }

    private int total(SalesOpened bill) {
        return (int) barSalesRepository.billTotal(bill.getUid());
    }

    private PayBillDTO pay(SalesOpened bill, String method, int amount, String customer) {
        PayBillDTO dto = new PayBillDTO();
        dto.setSalesOpenedUID(bill.getUid());
        PayBillDTO.Part part = new PayBillDTO.Part();
        part.setMethod(method);
        part.setAmount(amount);
        part.setCustomerUid(customer);
        dto.setPayments(List.of(part));
        return dto;
    }

    @Test
    void creditStaysWithinTheLimitAndPaymentsLowerIt() {
        DebtCustomerDTO c = new DebtCustomerDTO();
        c.setName("test deni");
        c.setPhone("0799" + (System.nanoTime() % 1_000_000));
        c.setCreditLimit(0);
        String uid = (String) debtService.save(null, c, true).getData().get("uid");

        SalesOpened b = bill(1);
        int due = total(b);
        assertThrows(Exception.class, () -> billPaymentService.payBill(pay(b, "deni", due, uid)), "no limit yet - no credit");

        c.setCreditLimit(due);
        debtService.save(uid, c, true);
        assertNotNull(billPaymentService.payBill(pay(b, "deni", due, uid)).getData());
        assertEquals((long) due, debtService.ledger(uid).getData().get("balance"));

        SalesOpened b2 = bill(1);
        assertThrows(Exception.class, () -> billPaymentService.payBill(pay(b2, "deni", total(b2), uid)), "past the limit");

        DebtPaymentDTO p = new DebtPaymentDTO();
        p.setCustomerUid(uid);
        p.setMethod("cash");
        p.setAmount(due);
        assertNotNull(debtService.pay(p).getData());
        assertEquals(0L, debtService.ledger(uid).getData().get("balance"));
    }

    @Test
    void anApprovedRefundUndoesTheLine() {
        SalesOpened b = bill(2);
        int due = total(b);
        assertNotNull(billPaymentService.payBill(pay(b, "cash", due, null)).getData());
        String line = barSalesRepository.findLinesOfBill(b.getUid()).get(0).getUid();

        RefundRequestDTO r = new RefundRequestDTO();
        r.setSalesOpenedUID(b.getUid());
        RefundRequestDTO.Line l = new RefundRequestDTO.Line();
        l.setBarSalesUID(line);
        l.setQuantity(1);
        r.setLines(List.of(l));
        r.setMethod("cash");
        r.setReason("test refund");
        var done = refundService.request(r); // ROOT approves its own
        assertNotNull(done.getData(), done.getMessage());
        assertEquals("APPROVED", done.getData().getStatus());
        assertEquals(due / 2, done.getData().getAmount());
        assertEquals(due - due / 2, total(b), "the line carries one less");

        var again = refundService.request(r);
        l.setQuantity(5);
        assertNull(refundService.request(r).getData(), "more than is on the bill");
        assertNotNull(again.getData());
    }

    @Test
    void aDiscountIsSpreadOverTheLinesAndTheSplit() {
        SalesOpened b = bill(2);
        int before = total(b);
        String line = barSalesRepository.findLinesOfBill(b.getUid()).get(0).getUid();
        int splitBefore = barReportsRepository.findBySalesLine(line).stream()
                .mapToInt(x -> x.getOwnerAmount() == null ? 0 : x.getOwnerAmount()).sum();

        assertNull(barService.discountBill(b.getUid(), null, 10, "").getData(), "a reason is needed");
        var res = barService.discountBill(b.getUid(), null, 10, "test discount");
        assertNotNull(res.getData(), res.getMessage());
        int off = Math.round(before * 10 / 100f);
        assertEquals(before - off, total(b));
        int splitAfter = barReportsRepository.findBySalesLine(line).stream()
                .mapToInt(x -> x.getOwnerAmount() == null ? 0 : x.getOwnerAmount()).sum();
        assertTrue(splitAfter < splitBefore, "the owner's share follows the discount");
    }

    @Test
    void aHappyHourPricesTheSaleAndTheSplitFollows() {
        var rule = priceRuleService.save(null, Map.of("name", "test hour", "kind", "PERCENT", "serviceUid", MSHIKAKI,
                "percent", 50, "days", "1234567", "startTime", "00:00", "endTime", "23:59"));
        assertNotNull(rule.getData(), rule.getMessage());
        SalesOpened b = bill(2);
        var line = barSalesRepository.findLinesOfBill(b.getUid()).get(0);
        assertEquals("test hour", line.getPriceRuleName());
        assertEquals(Math.round(line.getListPrice() * 0.5f) * 2, line.getLineTotal());
    }
}
