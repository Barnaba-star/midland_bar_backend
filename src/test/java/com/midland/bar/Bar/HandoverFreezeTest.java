package com.midland.bar.Bar;

import com.midland.bar.Bar.Dto.*;
import com.midland.bar.Bar.Model.*;
import com.midland.bar.Bar.Repository.BarSalesRepository;
import com.midland.bar.Bar.Service.BarService;
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
 * Once a staff member sends their handover their bills are frozen until the
 * cashier receives it - against the local database, rolled back.
 */
@SpringBootTest
@Transactional
class HandoverFreezeTest {

    private static final String LOGIN = "root@root.com";
    private static final String MSHIKAKI = "7b57124d-a41d-40e2-b5e5-54fce93123f8";

    @Autowired UserRepository userRepository;
    @Autowired BarService barService;
    @Autowired StaffSellService staffSellService;
    @Autowired StaffOrderService staffOrderService;
    @Autowired BarSalesRepository barSalesRepository;
    @Autowired com.midland.bar.Bar.Service.WorkShiftService workShiftService;

    private BarStaff staff;
    private SalesOpened bill;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        workShiftService.open();

        BarStaffDTO s = new BarStaffDTO();
        s.setFirstName("Freeze");
        s.setLastName("Test");
        s.setPhoneNumber("0700000001");
        s.setBarCategory(StaffCategory.values()[0].name());
        s.setStaffCode("");
        staff = barService.saveBarStaff(s).getData();
        bill = openBill();
        assertNotNull(bill);

        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(bill.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(1);
        sale.setItems(List.of(item));
        assertNotNull(barService.addItemsToBill(sale).getData());
    }

    private SalesOpened openBill() {
        StaffBillDTO b = new StaffBillDTO();
        b.setStaffCode(staff.getStaffCode());
        return staffSellService.openBill(b).getData();
    }

    private StaffOrderItemDTO oneMore() {
        StaffOrderItemDTO dto = new StaffOrderItemDTO();
        dto.setSalesOpenedUID(bill.getUid());
        dto.setBarServiceUID(MSHIKAKI);
        dto.setQuantity(1);
        return dto;
    }

    @Test
    void sentHandoverFreezesTheBillsUntilTheCashierTakesIt() {
        StaffOrder draft = staffOrderService.addItem(oneMore()).getData();
        assertNotNull(draft, "before sending, adding works");
        // Taken off again: a written order still waiting would hold the payment up.
        assertNotNull(staffOrderService.removeLine(draft.getUid(), draft.getLines().get(0).getUid()).getData());

        assertEquals(1, staffSellService.sendHandover(staff.getStaffCode()).getData());

        BusinessException add = assertThrows(BusinessException.class, () -> staffOrderService.addItem(oneMore()));
        assertEquals(StaffOrderService.HANDOVER_SENT, add.getMessage());
        assertThrows(BusinessException.class, this::openBill);
        assertThrows(BusinessException.class, () -> staffSellService.sendOrders(staff.getStaffCode()));

        HandoverReceiveDTO take = new HandoverReceiveDTO();
        take.setStaffCode(staff.getStaffCode());
        take.setMethod("cash");
        HandoverReceiveDTO.Bill line = new HandoverReceiveDTO.Bill();
        line.setUid(bill.getUid());
        line.setAmount(barSalesRepository.billTotal(bill.getUid()));
        take.setBills(List.of(line));
        assertNotNull(staffSellService.receiveHandover(take).getData());

        assertNotNull(openBill(), "received: a new bill opens again");
    }

    @Test
    void recallingTheHandoverOpensTheBillsAgain() {
        assertEquals(1, staffSellService.sendHandover(staff.getStaffCode()).getData());
        assertThrows(BusinessException.class, this::openBill);

        assertEquals(1, staffSellService.recallHandover(staff.getStaffCode()).getData());
        assertFalse((Boolean) staffSellService.findByCode(staff.getStaffCode()).getData().get("handoverSent"));
        assertNotNull(staffOrderService.addItem(oneMore()).getData(), "recalled: adding works again");
        assertNotNull(openBill());
    }
}
