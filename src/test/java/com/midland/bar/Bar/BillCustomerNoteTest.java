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

    @Autowired com.midland.bar.Bar.Service.LoyaltyService loyaltyService;

    private static final String PIN = "4826";

    private Map<String, Object> customer(String phone, int limit, int offer) {
        DebtCustomerDTO c = new DebtCustomerDTO();
        c.setName("Mteja Test");
        c.setPhone(phone);
        c.setCreditLimit(limit);
        c.setMonthlyOffer(offer);
        Map<String, Object> saved = debtService.save(null, c, true).getData();
        loyaltyService.register(null, phone, PIN);
        saved.put("phone", phone);
        return saved;
    }

    private PaymentNoteDTO note(String method, Map<String, Object> c, String pin) {
        PaymentNoteDTO dto = new PaymentNoteDTO();
        dto.setMethod(method);
        dto.setPhone((String) c.get("phone"));
        dto.setPin(pin);
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
    void anOfferBillNeedsTheCustomersPinAndIsTakenAtHandover() {
        Map<String, Object> c = customer("0711000031", 0, (int) total + 5000);

        BusinessException wrong = assertThrows(BusinessException.class,
                () -> billPaymentService.paymentNote(bill.getUid(), note("ofa", c, "9371")));
        assertEquals(com.midland.bar.Bar.Service.LoyaltyService.WRONG_PIN, wrong.getMessage());

        SalesOpened noted = billPaymentService.paymentNote(bill.getUid(), note("ofa", c, PIN)).getData();
        assertEquals("ofa", noted.getPaymentNoteMethod());
        assertEquals("MTEJA TEST", noted.getPaymentNotePayer());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) ((Map<String, Object>) staffSellService.handover(waiter.getStaffCode()).getData().get("open")).get("byMethod");
        assertTrue(rows.stream().anyMatch(r -> "ofa".equals(r.get("method")) && ((Number) r.get("amount")).longValue() == total));

        receive("ofa");
        assertEquals("PAID", salesOpenedRepository.findById(bill.getUid()).orElseThrow().getPaymentStatus());
        Map<String, Object> after = debtService.ledger((String) c.get("uid")).getData();
        assertEquals(total, ((Number) after.get("offerUsed")).longValue());
    }

    @Test
    void ofaAtTheTillNeedsThePinOnTheBill() {
        Map<String, Object> c = customer("0711000034", 0, (int) total + 5000);
        PayBillDTO pay = new PayBillDTO();
        pay.setSalesOpenedUID(bill.getUid());
        PayBillDTO.Part part = new PayBillDTO.Part();
        part.setMethod("ofa");
        part.setAmount((int) total);
        part.setCustomerUid((String) c.get("uid"));
        pay.setPayments(List.of(part));
        assertNull(billPaymentService.payBill(pay).getData(), "no PIN on the bill, no offer");

        assertNotNull(loyaltyService.attach(bill.getUid(), "0711000034", PIN).getData());
        assertNotNull(billPaymentService.payBill(pay).getData());
    }

    @Test
    void enoughBigBillsEarnAnOfferThatIsSpentAndTheCountStartsAgain() {
        com.midland.bar.Bar.Model.LoyaltyRule rule = new com.midland.bar.Bar.Model.LoyaltyRule();
        rule.setEnabled(true);
        rule.setMinBill((int) Math.max(1000, total));
        rule.setBillsNeeded(2);
        rule.setPeriodDays(30);
        rule.setRewardAmount((int) total);
        assertNotNull(loyaltyService.saveRule(rule).getData());
        if (total < 1000)
            return; // the test item is too cheap for the minimum - nothing more to see
        loyaltyService.register("Mzee Loyal", "0711000035", PIN);

        for (int i = 0; i < 2; i++) {
            SalesOpened b = i == 0 ? bill : newBill();
            assertNotNull(loyaltyService.attach(b.getUid(), "0711000035", PIN).getData());
            assertNotNull(billPaymentService.payBill(cash(b)).getData());
        }
        Map<String, Object> card = loyaltyService.card(loyaltyService.verify("0711000035", PIN));
        assertEquals(total, ((Number) card.get("offerLeft")).longValue(), "two big bills earned the reward");
        @SuppressWarnings("unchecked")
        Map<String, Object> progress = (Map<String, Object>) card.get("loyalty");
        assertEquals(0, ((Number) progress.get("visits")).intValue(), "the count starts again");

        SalesOpened third = newBill();
        assertNotNull(loyaltyService.attach(third.getUid(), "0711000035", PIN).getData());
        PayBillDTO pay = new PayBillDTO();
        pay.setSalesOpenedUID(third.getUid());
        PayBillDTO.Part part = new PayBillDTO.Part();
        part.setMethod("ofa");
        part.setAmount((int) total);
        part.setCustomerUid((String) card.get("uid"));
        pay.setPayments(List.of(part));
        assertNotNull(billPaymentService.payBill(pay).getData());
        assertEquals(0, loyaltyService.rewardLeft((String) card.get("uid")), "the reward is spent");
    }

    private SalesOpened newBill() {
        StaffBillDTO b = new StaffBillDTO();
        b.setStaffCode(waiter.getStaffCode());
        SalesOpened opened = staffSellService.openBill(b).getData();
        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(opened.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(1);
        sale.setItems(List.of(item));
        assertNotNull(barService.addItemsToBill(sale).getData());
        return opened;
    }

    private PayBillDTO cash(SalesOpened b) {
        PayBillDTO pay = new PayBillDTO();
        pay.setSalesOpenedUID(b.getUid());
        PayBillDTO.Part part = new PayBillDTO.Part();
        part.setMethod("cash");
        part.setAmount((int) barSalesRepository.billTotal(b.getUid()));
        pay.setPayments(List.of(part));
        return pay;
    }

    @Test
    void aCreditBillGoesOnTheCustomersAccountAndCashClearsTheNote() {
        Map<String, Object> small = customer("0711000032", (int) total - 1, 0);
        assertThrows(BusinessException.class,
                () -> billPaymentService.paymentNote(bill.getUid(), note("deni", small, PIN)),
                "a bill over what is left on credit is refused");

        Map<String, Object> c = customer("0711000033", (int) total + 1000, 0);
        assertNotNull(billPaymentService.paymentNote(bill.getUid(), note("deni", c, PIN)).getData());

        PaymentNoteDTO cash = new PaymentNoteDTO();
        cash.setMethod("cash");
        assertNull(billPaymentService.paymentNote(bill.getUid(), cash).getData().getPaymentNoteMethod(), "cash is the default: no note");

        assertNotNull(billPaymentService.paymentNote(bill.getUid(), note("deni", c, PIN)).getData());
        receive("deni");
        assertEquals(total, debtEntryRepository.balanceOf((String) c.get("uid")));
    }
}
