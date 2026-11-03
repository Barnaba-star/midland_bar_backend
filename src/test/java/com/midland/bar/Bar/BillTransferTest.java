package com.midland.bar.Bar;

import com.midland.bar.Bar.Dto.*;
import com.midland.bar.Bar.Model.*;
import com.midland.bar.Bar.Repository.BarSalesRepository;
import com.midland.bar.Bar.Repository.StaffCommissionsRepository;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Bar.Service.StaffOrderService;
import com.midland.bar.Bar.Service.StaffSellService;
import com.midland.bar.Config.Security.LoggerUser;
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

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A staff member going off hands an unpaid bill to a colleague: the bill, its
 * lines, waiting orders and the commission move - against the local
 * database, rolled back.
 */
@SpringBootTest
@org.junit.jupiter.api.Tag("local")
@Transactional
class BillTransferTest {

    private static final String LOGIN = "root@root.com";
    private static final String MSHIKAKI = "7b57124d-a41d-40e2-b5e5-54fce93123f8";

    @Autowired UserRepository userRepository;
    @Autowired BarService barService;
    @Autowired StaffSellService staffSellService;
    @Autowired StaffOrderService staffOrderService;
    @Autowired BarSalesRepository barSalesRepository;
    @Autowired StaffCommissionsRepository staffCommissionsRepository;
    @Autowired com.midland.bar.Bar.Service.WorkShiftService workShiftService;

    private BarStaff leaving;
    private BarStaff staying;
    private SalesOpened bill;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        workShiftService.open();
        leaving = staff("Juma", "Mussa", "0700000011");
        staying = staff("Asha", "Kombo", "0700000012");

        bill = openBill(leaving);
        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(bill.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(2);
        sale.setItems(List.of(item));
        assertNotNull(barService.addItemsToBill(sale).getData());
    }

    private BarStaff staff(String first, String last, String phone) {
        BarStaffDTO s = new BarStaffDTO();
        s.setFirstName(first);
        s.setLastName(last);
        s.setPhoneNumber(phone);
        s.setBarCategory(StaffCategory.values()[0].name());
        s.setStaffCode("");
        return barService.saveBarStaff(s).getData();
    }

    private SalesOpened openBill(BarStaff s) {
        StaffBillDTO b = new StaffBillDTO();
        b.setStaffCode(s.getStaffCode());
        return staffSellService.openBill(b).getData();
    }

    private int commissionOf(BarStaff s) {
        return staffCommissionsRepository.findCommission(s.getUid(), LoggerUser.getBranchUIDOrMain(), LocalDate.now())
                .map(c -> c.getRemainingAmount() == null ? 0 : c.getRemainingAmount()).orElse(0);
    }

    private BillTransferDTO to(BarStaff s) {
        BillTransferDTO dto = new BillTransferDTO();
        dto.setBillUid(bill.getUid());
        dto.setToStaffUid(s.getUid());
        dto.setReason("anatoka zamu");
        return dto;
    }

    @Test
    void theBillItsLinesOrdersAndCommissionGoToTheColleague() {
        int earned = commissionOf(leaving);
        assertTrue(earned > 0, "the sale earned the leaving staff member commission");
        long total = barSalesRepository.billTotal(bill.getUid());

        StaffOrderItemDTO more = new StaffOrderItemDTO();
        more.setSalesOpenedUID(bill.getUid());
        more.setBarServiceUID(MSHIKAKI);
        more.setQuantity(1);
        StaffOrder draft = staffOrderService.addItem(more).getData();

        SalesOpened moved = barService.transferBill(to(staying)).getData();
        assertNotNull(moved);
        assertEquals(staying.getUid(), moved.getStaffUid());
        assertEquals("AK-1", moved.getSalesCode(), "the bill joins the new staff member's series");
        assertEquals(total, barSalesRepository.billTotal(bill.getUid()), "the money on the bill is unchanged");

        assertEquals(0, commissionOf(leaving), "the commission left the leaving staff member");
        assertEquals(earned, commissionOf(staying), "and landed on the colleague");
        barSalesRepository.findLinesOfBill(bill.getUid())
                .forEach(l -> assertEquals(staying.getUid(), l.getBarStaff().getUid()));
        assertTrue(staffOrderService.shownOn(List.of(bill.getUid())).stream()
                .filter(o -> o.getUid().equals(draft.getUid()))
                .allMatch(o -> staying.getUid().equals(o.getStaffUid())), "the waiting order follows the bill");

        assertTrue(staffSellService.findByCode(leaving.getStaffCode()).getData().get("bills") instanceof List<?> l && l.isEmpty());
        assertEquals(1, barService.findBillTransfers("TODAY").getData().stream()
                .filter(t -> t.getBillUid().equals(bill.getUid()) && t.getCommission() == earned).count());
    }

    @Test
    void aSentHandoverOrAPaidBillCannotMove() {
        assertEquals(1, staffSellService.sendHandover(leaving.getStaffCode()).getData());
        BusinessException sent = assertThrows(BusinessException.class, () -> barService.transferBill(to(staying)));
        assertEquals(StaffOrderService.HANDOVER_SENT, sent.getMessage());
        staffSellService.recallHandover(leaving.getStaffCode());

        assertNull(barService.transferBill(to(leaving)).getData(), "not to the same staff member");
        assertNotNull(barService.transferBill(to(staying)).getData());
    }

    @Test
    void aColleagueWhoSentTheirHandoverCannotTakeIt() {
        SalesOpened theirs = openBill(staying);
        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(theirs.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(1);
        sale.setItems(List.of(item));
        barService.addItemsToBill(sale);
        assertEquals(1, staffSellService.sendHandover(staying.getStaffCode()).getData());

        assertNull(barService.transferBill(to(staying)).getData());
        assertTrue(staffSellService.colleagues(leaving.getUid()).getData().stream()
                .anyMatch(c -> c.get("uid").equals(staying.getUid()) && Boolean.TRUE.equals(c.get("handoverSent"))));
    }
}
