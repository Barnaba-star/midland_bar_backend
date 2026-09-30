package com.midland.bar.Bar;

import com.midland.bar.Bar.Dto.*;
import com.midland.bar.Bar.Model.*;
import com.midland.bar.Bar.Repository.BarSalesRepository;
import com.midland.bar.Bar.Repository.BarServiceRepository;
import com.midland.bar.Bar.Repository.SalesOpenedRepository;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Bar.Service.BillPaymentService;
import com.midland.bar.Bar.Service.StaffOrderService;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Staff orders against the local database, rolled back afterwards: what a
 * staff member writes waits for the supervisor, and only a receive puts it on
 * the bill and takes it out of the store.
 */
@SpringBootTest
@Transactional
class StaffOrderFlowTest {

    private static final String LOGIN = "root@root.com";
    private static final String MSHIKAKI = "7b57124d-a41d-40e2-b5e5-54fce93123f8";

    @Autowired UserRepository userRepository;
    @Autowired BarService barService;
    @Autowired StaffSellService staffSellService;
    @Autowired StaffOrderService staffOrderService;
    @Autowired BillPaymentService billPaymentService;
    @Autowired SalesOpenedRepository salesOpenedRepository;
    @Autowired BarServiceRepository barServiceRepository;
    @Autowired BarSalesRepository barSalesRepository;

    private BarStaff staff;
    private SalesOpened bill;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));

        BarStaffDTO s = new BarStaffDTO();
        s.setFirstName("Maiko");
        s.setLastName("Test");
        s.setPhoneNumber("0700000000");
        s.setBarCategory(StaffCategory.values()[0].name());
        s.setStaffCode("K9");
        staff = barService.saveBarStaff(s).getData();

        StaffBillDTO b = new StaffBillDTO();
        b.setStaffCode("K9");
        bill = staffSellService.openBill(b).getData();
    }

    private StaffOrder write(int qty) {
        StaffOrderItemDTO dto = new StaffOrderItemDTO();
        dto.setSalesOpenedUID(bill.getUid());
        dto.setBarServiceUID(MSHIKAKI);
        dto.setQuantity(qty);
        var res = staffOrderService.addItem(dto);
        assertNotNull(res.getData(), res.getMessage());
        return res.getData();
    }

    private int billTotal() {
        return salesOpenedRepository.findById(bill.getUid()).orElseThrow().getBill();
    }

    private int beefOnHand() {
        String beef = barServiceRepository.findById(MSHIKAKI).orElseThrow().getStockSourceUid();
        return barServiceRepository.findById(beef).orElseThrow().getStockQuantity();
    }

    private StaffOrder sendAndGetPending() {
        assertEquals(1, staffSellService.sendOrders("k9").getData());
        List<StaffOrder> pending = staffOrderService.pending().getData();
        return pending.stream().filter(o -> o.getSalesOpenedUid().equals(bill.getUid())).findFirst()
                .orElseThrow(() -> new AssertionError("sent order not in the supervisor's queue"));
    }

    @Test
    void writingTouchesNeitherTheBillNorTheStore() {
        int beef = beefOnHand();
        write(1);
        StaffOrder order = write(2);
        assertEquals(StaffOrder.DRAFT, order.getStatus());
        assertEquals(1, order.getLines().size(), "the same service twice is one line");
        assertEquals(3, order.getLines().get(0).getQuantity());
        assertEquals(0, billTotal());
        assertEquals(beef, beefOnHand());

        var shown = staffSellService.findByCode("K9").getData().get("orders");
        assertEquals(1, ((List<?>) shown).size(), "Staff Sell shows the waiting order");
    }

    @Test
    void aDraftLineCanComeOffButNotOnceSent() {
        StaffOrder order = write(1);
        String lineUid = order.getLines().get(0).getUid();
        write(1); // keep the order alive after sending
        StaffOrder sent = sendAndGetPending();
        var res = staffOrderService.removeLine(sent.getUid(), lineUid);
        assertNull(res.getData());
        assertTrue(res.getMessage().contains("already with the supervisor"), res.getMessage());
    }

    @Test
    void sendingEmptiesNothingAndReceivingPutsItOnTheBillAsTheStaffMembers() {
        int beef = beefOnHand();
        write(2);
        StaffOrder pending = sendAndGetPending();
        assertEquals(StaffOrder.SENT, pending.getStatus());
        assertEquals(0, billTotal(), "still not on the bill while waiting");

        // The bill cannot be paid while the order waits.
        PayBillDTO pay = new PayBillDTO();
        pay.setSalesOpenedUID(bill.getUid());
        PayBillDTO.Part cash = new PayBillDTO.Part();
        cash.setMethod("cash");
        cash.setAmount(1);
        pay.setPayments(List.of(cash));
        var blocked = billPaymentService.payBill(pay);
        assertNull(blocked.getData());
        assertTrue(blocked.getMessage().contains("waiting for the supervisor"), blocked.getMessage());

        var received = staffOrderService.receive(pending.getUid());
        assertNotNull(received.getData(), received.getMessage());
        assertEquals(StaffOrder.RECEIVED, received.getData().getStatus());
        assertEquals(LOGIN, received.getData().getDecidedBy());
        assertEquals(2 * pending.getLines().get(0).getUnitPrice(), billTotal());
        assertTrue(beefOnHand() < beef, "stock leaves the store on receive");

        List<BarSales> lines = barSalesRepository.findAll().stream()
                .filter(l -> l.getSalesOpened() != null && bill.getUid().equals(l.getSalesOpened().getUid())).toList();
        assertEquals(1, lines.size());
        assertEquals(staff.getUid(), lines.get(0).getBarStaff().getUid(), "the sale is the staff member's");

        var again = staffOrderService.receive(pending.getUid());
        assertNull(again.getData(), "a received order cannot be received twice");
    }

    @Test
    void rejectingLeavesTheBillAloneAndTellsTheStaffMemberWhy() {
        write(1);
        StaffOrder pending = sendAndGetPending();
        StaffOrderRejectDTO why = new StaffOrderRejectDTO();
        why.setReason("  Imeisha stoo ");
        var rejected = staffOrderService.reject(pending.getUid(), why);
        assertEquals(StaffOrder.REJECTED, rejected.getData().getStatus());
        assertEquals("Imeisha stoo", rejected.getData().getRejectReason());
        assertEquals(0, billTotal());
        assertTrue(staffOrderService.pending().getData().stream().noneMatch(o -> o.getUid().equals(pending.getUid())));

        @SuppressWarnings("unchecked")
        List<StaffOrder> shown = (List<StaffOrder>) staffSellService.findByCode("K9").getData().get("orders");
        assertTrue(shown.stream().anyMatch(o -> StaffOrder.REJECTED.equals(o.getStatus())), "Staff Sell shows the rejection");
    }

    @Test
    void aReceiveTheStoreCannotCoverChangesNothing() {
        // More mshikaki than the beef in the store can make.
        write(1_000_000);
        StaffOrder pending = sendAndGetPending();
        assertThrows(BusinessException.class, () -> staffOrderService.receive(pending.getUid()));
    }

    /** Signs in as a user of a different branch - nothing of this branch's may be visible or movable. */
    private void signInElsewhere() {
        com.midland.bar.Setting.Model.Branch other = new com.midland.bar.Setting.Model.Branch();
        other.setUid("OTHER-BRANCH-TEST");
        User stranger = new User();
        stranger.setUsername("stranger@test");
        stranger.setBranch(other);
        stranger.setRoles(List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(stranger, null, List.of()));
    }

    @Test
    void anotherBranchSeesAndMovesNothingOfThisOne() {
        write(1);
        StaffOrder pending = sendAndGetPending();

        signInElsewhere();
        assertTrue(staffOrderService.pending().getData().stream().noneMatch(o -> o.getUid().equals(pending.getUid())),
                "another branch's supervisor does not see the order");
        assertNull(staffOrderService.receive(pending.getUid()).getData(), "nor receive it");
        StaffOrderRejectDTO why = new StaffOrderRejectDTO();
        why.setReason("not mine");
        assertNull(staffOrderService.reject(pending.getUid(), why).getData(), "nor reject it");
        assertNull(staffSellService.findByCode("K9").getData(), "nor find this branch's staff by code");
        StaffBillDTO b = new StaffBillDTO();
        b.setStaffCode("K9");
        assertNull(staffSellService.openBill(b).getData(), "nor open a bill for them");
        StaffOrderItemDTO item = new StaffOrderItemDTO();
        item.setSalesOpenedUID(bill.getUid());
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(1);
        assertNull(staffOrderService.addItem(item).getData(), "nor write onto this branch's bill");
        assertTrue(staffSellService.summary(null).getData().stream().noneMatch(r -> "K9".equals(r.get("staffCode"))),
                "nor see this branch's staff in the sales summary");
    }
}
