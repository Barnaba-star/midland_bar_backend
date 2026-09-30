package com.midland.bar.Bar;

import com.midland.bar.Bar.Dto.BarStaffDTO;
import com.midland.bar.Bar.Dto.SaleItemsDTO;
import com.midland.bar.Bar.Dto.StaffBillDTO;
import com.midland.bar.Bar.Dto.StaffSellUnlockDTO;
import com.midland.bar.Bar.Dto.PayBillDTO;
import com.midland.bar.Bar.Service.BillPaymentService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.midland.bar.Bar.Model.BarReports;
import com.midland.bar.Bar.Model.BarSales;
import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Model.SalesOpened;
import com.midland.bar.Bar.Repository.BarReportsRepository;
import com.midland.bar.Bar.Repository.BarSalesRepository;
import com.midland.bar.Bar.Repository.StaffCommissionsRepository;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Bar.Service.StaffSellService;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
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
 * Staff Sell end to end against the local database, rolled back afterwards:
 * a typed staff code, bills numbered from it, and a sale on such a bill
 * landing on that staff member rather than on whoever is logged in.
 */
@SpringBootTest
@Transactional
class StaffSellFlowTest {

    // The local branch and its root login; a service that draws on beef, which is in stock.
    private static final String LOGIN = "root@root.com";
    private static final String MSHIKAKI = "7b57124d-a41d-40e2-b5e5-54fce93123f8";

    @Autowired UserRepository userRepository;
    @Autowired BarService barService;
    @Autowired StaffSellService staffSellService;
    @Autowired BarSalesRepository barSalesRepository;
    @Autowired BarReportsRepository barReportsRepository;
    @Autowired StaffCommissionsRepository staffCommissionsRepository;
    @Autowired BillPaymentService billPaymentService;
    @Autowired BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void signIn() {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private BarStaff newStaff(String code) {
        BarStaffDTO dto = new BarStaffDTO();
        dto.setFirstName("Maiko");
        dto.setLastName("Test");
        dto.setPhoneNumber("0700000000");
        dto.setBarCategory(com.midland.bar.Bar.Model.StaffCategory.values()[0].name());
        dto.setStaffCode(code);
        var res = barService.saveBarStaff(dto);
        assertNotNull(res.getData(), res.getMessage());
        return res.getData();
    }

    private SalesOpened open(String code) {
        StaffBillDTO dto = new StaffBillDTO();
        dto.setStaffCode(code);
        var res = staffSellService.openBill(dto);
        assertNotNull(res.getData(), res.getMessage());
        return res.getData();
    }

    @Test
    void typedCodeIsKeptUpperCaseAndCannotBeTakenTwice() {
        BarStaff staff = newStaff(" k9 ");
        assertEquals("K9", staff.getStaffCode());

        BarStaffDTO clash = new BarStaffDTO();
        clash.setFirstName("Other");
        clash.setLastName("Test");
        clash.setPhoneNumber("0700000001");
        clash.setBarCategory(com.midland.bar.Bar.Model.StaffCategory.values()[0].name());
        clash.setStaffCode("K9");
        var res = barService.saveBarStaff(clash);
        assertNull(res.getData());
        assertTrue(res.getMessage().contains("already used"), res.getMessage());
    }

    @Test
    void blankCodeGetsTheNextNumber() {
        BarStaff staff = newStaff("");
        assertTrue(staff.getStaffCode().matches("\\d{3,}"), staff.getStaffCode());
    }

    @Test
    void codeFindsTheStaffAndTheirBillsAreNumberedFromIt() {
        BarStaff staff = newStaff("K9");

        var found = staffSellService.findByCode("k9");
        assertNotNull(found.getData(), found.getMessage());
        @SuppressWarnings("unchecked")
        Map<String, Object> who = (Map<String, Object>) found.getData().get("staff");
        assertEquals(staff.getUid(), who.get("uid"));
        assertEquals(List.of(), found.getData().get("bills"));

        SalesOpened first = open("K9");
        SalesOpened second = open("k9");
        assertEquals("K9-1", first.getSalesCode());
        assertEquals("K9-2", second.getSalesCode());
        assertEquals(staff.getUid(), first.getStaffUid());
        assertEquals("Maiko Test", first.getStaffName());
        assertEquals("K9", first.getStaffCode());

        @SuppressWarnings("unchecked")
        List<SalesOpened> bills = (List<SalesOpened>) staffSellService.findByCode("K9").getData().get("bills");
        assertEquals(List.of("K9-1", "K9-2"), bills.stream().map(SalesOpened::getSalesCode).toList());

        assertNull(staffSellService.findByCode("ZZ-NOBODY").getData());
    }

    @Test
    void aSaleOnAStaffBillIsThatStaffMembersNotTheLogins() {
        BarStaff staff = newStaff("K9");
        SalesOpened bill = open("K9");

        SaleItemsDTO dto = new SaleItemsDTO();
        dto.setSalesOpenedUID(bill.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(1);
        dto.setItems(List.of(item));
        var res = barService.addSaleItems(dto);
        assertNotNull(res.getData(), res.getMessage());
        assertTrue(res.getData().getBill() > 0);

        List<BarSales> lines = barSalesRepository.findAll().stream()
                .filter(l -> l.getSalesOpened() != null && bill.getUid().equals(l.getSalesOpened().getUid()))
                .toList();
        assertEquals(1, lines.size());
        assertEquals(staff.getUid(), lines.get(0).getBarStaff().getUid(), "the line sells as the bill's staff member");
        assertEquals(LOGIN, lines.get(0).getSoldBy(), "soldBy still records who pressed the button");

        List<BarReports> reports = barReportsRepository.findAll().stream()
                .filter(r -> r.getBarSales() != null && lines.get(0).getUid().equals(r.getBarSales().getUid()))
                .toList();
        assertEquals(1, reports.size());
        assertEquals(staff.getUid(), reports.get(0).getBarStaff().getUid());

        assertTrue(staffCommissionsRepository.findAll().stream()
                .anyMatch(c -> c.getBarStaff() != null && staff.getUid().equals(c.getBarStaff().getUid())
                        && c.getTotalAmount() != null && c.getTotalAmount() > 0),
                "the staff member's commission row picked up their cut");
    }

    private StaffSellUnlockDTO login(String password) {
        StaffSellUnlockDTO dto = new StaffSellUnlockDTO();
        dto.setUsername(LOGIN);
        dto.setPassword(password);
        return dto;
    }

    @Test
    void leavingForPosTakesAManagersLoginAndFiveWrongTriesLockIt() {
        // Known password for the test only - rolled back with everything else.
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        user.setPassword(passwordEncoder.encode("right-pass"));
        userRepository.save(user);

        assertEquals(Boolean.TRUE, staffSellService.unlock(login("right-pass")).getData());

        for (int i = 0; i < 5; i++)
            assertNull(staffSellService.unlock(login("wrong")).getData());
        var locked = staffSellService.unlock(login("right-pass"));
        assertNull(locked.getData(), "locked after five wrong tries, even with the right password");
        assertTrue(locked.getMessage().startsWith("Too many wrong tries"), locked.getMessage());
    }

    @Test
    void summaryShowsEachStaffMembersTakingsByMethod() {
        newStaff("K9");
        SalesOpened bill = open("K9");

        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(bill.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(3);
        sale.setItems(List.of(item));
        int due = barService.addSaleItems(sale).getData().getBill();
        assertTrue(due >= 2, "needs at least 2 shillings to split two ways");

        // Unpaid first: it shows as an open bill.
        Map<String, Object> before = k9(staffSellService.summary(null).getData());
        assertEquals(1L, before.get("openBills"));
        assertEquals((long) due, before.get("openAmount"));
        assertEquals(0L, before.get("total"));

        PayBillDTO pay = new PayBillDTO();
        pay.setSalesOpenedUID(bill.getUid());
        PayBillDTO.Part cash = new PayBillDTO.Part();
        cash.setMethod("cash");
        cash.setAmount(due - 1);
        PayBillDTO.Part mpesa = new PayBillDTO.Part();
        mpesa.setMethod("mpesa");
        mpesa.setAmount(1);
        pay.setPayments(List.of(cash, mpesa));
        var paid = billPaymentService.payBill(pay);
        assertNotNull(paid.getData(), paid.getMessage());

        // The receipt names the staff member who served it, and still who took the money.
        Map<String, Object> receipt = billPaymentService.receipt(bill.getUid()).getData();
        assertEquals("K9", receipt.get("staffCode"));
        assertEquals("Maiko Test", receipt.get("staffName"));
        assertEquals(LOGIN, receipt.get("paidBy"));

        Map<String, Object> after = k9(staffSellService.summary(null).getData());
        assertEquals((long) due, after.get("total"));
        assertEquals(Map.of("cash", (long) due - 1, "mpesa", 1L), after.get("byMethod"));
        assertEquals(1L, after.get("paidBills"));
        assertEquals(0L, after.get("openBills"));
    }

    private static Map<String, Object> k9(List<Map<String, Object>> rows) {
        return rows.stream().filter(r -> "K9".equals(r.get("staffCode"))).findFirst()
                .orElseThrow(() -> new AssertionError("no K9 row in " + rows));
    }
}
