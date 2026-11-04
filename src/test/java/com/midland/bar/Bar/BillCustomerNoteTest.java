package com.midland.bar.Bar;

import com.midland.bar.Bar.Dto.*;
import com.midland.bar.Bar.Model.*;
import com.midland.bar.Bar.Repository.BarSalesRepository;
import com.midland.bar.Bar.Repository.DebtEntryRepository;
import com.midland.bar.Bar.Repository.SalesOpenedRepository;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Bar.Service.BillPaymentService;
import com.midland.bar.Bar.Service.DebtService;
import com.midland.bar.Bar.Service.StaffSellService;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Utils.Exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A waiter notes a bill as ofa / deni for a Madeni customer, proved by the
 * customer's code; the cashier takes it at handover like any other method.
 * Local database, rolled back.
 */
@SpringBootTest
@org.junit.jupiter.api.Tag("local")
@Transactional
class BillCustomerNoteTest {

    private static final String LOGIN = "root@root.com";
    private static final String MSHIKAKI = "7b57124d-a41d-40e2-b5e5-54fce93123f8";

    @Autowired UserRepository userRepository;
    @Autowired BarService barService;
    @Autowired StaffSellService staffSellService;
    @Autowired BillPaymentService billPaymentService;
    @Autowired DebtService debtService;
    @Autowired BarSalesRepository barSalesRepository;
    @Autowired SalesOpenedRepository salesOpenedRepository;
    @Autowired DebtEntryRepository debtEntryRepository;
    @Autowired com.midland.bar.Bar.Service.WorkShiftService workShiftService;

    private BarStaff waiter;
    private SalesOpened bill;
    private long total;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        workShiftService.open();
        BarStaffDTO s = new BarStaffDTO();
        s.setFirstName("Neema");
        s.setLastName("Ofa");
        s.setPhoneNumber("0700000031");
        s.setBarCategory(StaffCategory.values()[0].name());
        s.setStaffCode("");
        waiter = barService.saveBarStaff(s).getData();
        StaffBillDTO b = new StaffBillDTO();
        b.setStaffCode(waiter.getStaffCode());
        bill = staffSellService.openBill(b).getData();
        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(bill.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(1);
        sale.setItems(List.of(item));
        assertNotNull(barService.addItemsToBill(sale).getData());
        total = barSalesRepository.billTotal(bill.getUid());
    }

    private Map<String, Object> customer(String phone, int limit, int offer) {
        DebtCustomerDTO c = new DebtCustomerDTO();
        c.setName("Mteja Test");
        c.setPhone(phone);
        c.setCreditLimit(limit);
        c.setMonthlyOffer(offer);
        Map<String, Object> saved = debtService.save(null, c, true).getData();
        assertNotNull(saved.get("customerCode"), "every new customer gets a code");
        return saved;
    }

    private PaymentNoteDTO note(String method, Map<String, Object> c, String code) {
        PaymentNoteDTO dto = new PaymentNoteDTO();
        dto.setMethod(method);
        dto.setCustomerUid((String) c.get("uid"));
        dto.setCustomerCode(code);
        return dto;
    }

    private void receive(String method) {
        HandoverReceiveDTO dto = new HandoverReceiveDTO();
        dto.setStaffCode(waiter.getStaffCode());
        dto.setMethod(method);
        HandoverReceiveDTO.Bill b = new HandoverReceiveDTO.Bill();
        b.setUid(bill.getUid());
        b.setAmount(total);
        dto.setBills(List.of(b));
        dto.setHandedAmount(0L); // nothing handed in - no shortage for ofa / deni
        assertNotNull(staffSellService.receiveHandover(dto).getData());
    }

    @Test
    void anOfferBillNeedsTheCustomersCodeAndIsTakenAtHandover() {
        Map<String, Object> c = customer("0711000031", 0, (int) total + 5000);
        String code = (String) c.get("customerCode");

        BusinessException wrong = assertThrows(BusinessException.class,
                () -> billPaymentService.paymentNote(bill.getUid(), note("ofa", c, code.equals("0000") ? "1111" : "0000")));
        assertEquals("WRONG_CUSTOMER_CODE", wrong.getMessage());

        SalesOpened noted = billPaymentService.paymentNote(bill.getUid(), note("ofa", c, code)).getData();
        assertEquals("ofa", noted.getPaymentNoteMethod());
        assertEquals("MTEJA TEST", noted.getPaymentNotePayer());
        assertTrue(debtService.noteCustomers("ofa").getData().stream().anyMatch(r -> c.get("uid").equals(r.get("uid"))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) ((Map<String, Object>) staffSellService.handover(waiter.getStaffCode()).getData().get("open")).get("byMethod");
        assertTrue(rows.stream().anyMatch(r -> "ofa".equals(r.get("method")) && ((Number) r.get("amount")).longValue() == total));

        receive("ofa");
        assertEquals("PAID", salesOpenedRepository.findById(bill.getUid()).orElseThrow().getPaymentStatus());
        Map<String, Object> after = debtService.ledger((String) c.get("uid")).getData();
        assertEquals(total, ((Number) after.get("offerUsed")).longValue());
    }

    @Test
    void aCreditBillGoesOnTheCustomersAccountAndCashClearsTheNote() {
        Map<String, Object> small = customer("0711000032", (int) total - 1, 0);
        assertThrows(BusinessException.class,
                () -> billPaymentService.paymentNote(bill.getUid(), note("deni", small, (String) small.get("customerCode"))),
                "a bill over what is left on credit is refused");

        Map<String, Object> c = customer("0711000033", (int) total + 1000, 0);
        assertNotNull(billPaymentService.paymentNote(bill.getUid(), note("deni", c, (String) c.get("customerCode"))).getData());

        PaymentNoteDTO cash = new PaymentNoteDTO();
        cash.setMethod("cash");
        assertNull(billPaymentService.paymentNote(bill.getUid(), cash).getData().getPaymentNoteMethod(), "cash is the default: no note");

        assertNotNull(billPaymentService.paymentNote(bill.getUid(), note("deni", c, (String) c.get("customerCode"))).getData());
        receive("deni");
        assertEquals(total, debtEntryRepository.balanceOf((String) c.get("uid")));
    }
}
