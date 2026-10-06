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
    @Autowired com.midland.bar.Bar.Service.WorkShiftService workShiftService;
    @Autowired com.midland.bar.Bar.Service.StaffOrderService staffOrderService;
    @Autowired com.midland.bar.Setting.Repository.RoleRepository roleRepository;
    @Autowired com.midland.bar.Bar.Controller.StaffAccessController staffAccessController;
    @Autowired com.midland.bar.Config.Security.JwtTokenUtil jwtTokenUtil;

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

    @Test
    @SuppressWarnings("unchecked")
    void handoverSplitsUnpaidBillsIntoCashAndPhoneAndListsWhatIsPaid() {
        workShiftService.open(); // already open is fine - selling just needs one
        String code = freeCode();
        SalesOpened cashBill = open(code);
        SalesOpened phoneBill = open(code);
        SalesOpened paidBill = open(code);
        int cashDue = sell(cashBill, 1), phoneDue = sell(phoneBill, 2), paidDue = sell(paidBill, 1);

        com.midland.bar.Bar.Dto.PaymentNoteDTO note = new com.midland.bar.Bar.Dto.PaymentNoteDTO();
        note.setMethod("tigopesa");
        note.setPayerName("JUMA ALI");
        assertNotNull(billPaymentService.paymentNote(phoneBill.getUid(), note).getData());

        PayBillDTO pay = new PayBillDTO();
        pay.setSalesOpenedUID(paidBill.getUid());
        PayBillDTO.Part mpesa = new PayBillDTO.Part();
        mpesa.setMethod("mpesa");
        mpesa.setAmount(paidDue);
        pay.setPayments(List.of(mpesa));
        assertNotNull(billPaymentService.payBill(pay).getData());

        Map<String, Object> h = staffSellService.handover(code).getData();
        Map<String, Object> open = (Map<String, Object>) h.get("open");
        assertEquals((long) cashDue + phoneDue, open.get("total"));
        assertEquals(List.of(Map.of("method", "cash", "amount", (long) cashDue, "bills", 1L),
                             Map.of("method", "tigopesa", "amount", (long) phoneDue, "bills", 1L)), open.get("byMethod"));
        assertEquals(2, ((List<?>) open.get("bills")).size());

        Map<String, Object> paid = (Map<String, Object>) h.get("paid");
        assertEquals((long) paidDue, paid.get("total"));
        assertEquals(List.of(Map.of("method", "mpesa", "amount", (long) paidDue, "bills", 1L)), paid.get("byMethod"));

        assertNull(staffSellService.handover("ZZ-NOBODY").getData());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cashierReceivesAMethodsBillsInOneGoOnlyAsTheSummaryShowedThem() {
        workShiftService.open();
        String code = freeCode();
        SalesOpened cash1 = open(code), cash2 = open(code), phone = open(code);
        int c1 = sell(cash1, 1), c2 = sell(cash2, 2), p = sell(phone, 1);
        com.midland.bar.Bar.Dto.PaymentNoteDTO note = new com.midland.bar.Bar.Dto.PaymentNoteDTO();
        note.setMethod("airtelmoney");
        note.setPayerName("ASHA");
        billPaymentService.paymentNote(phone.getUid(), note);

        assertEquals(3, staffSellService.sendHandover(code).getData());
        assertNotNull(staffSellService.handover(code).getData().get("sentAt"));

        // The summary showed only cash1 - cash2 is left out, so nothing is paid.
        var stale = staffSellService.receiveHandover(receive(code, "cash", Map.of(cash1.getUid(), (long) c1)));
        assertNull(stale.getData());
        assertTrue(stale.getMessage().contains("changed"), stale.getMessage());

        var ok = staffSellService.receiveHandover(receive(code, "cash", Map.of(cash1.getUid(), (long) c1, cash2.getUid(), (long) c2)));
        assertNotNull(ok.getData(), ok.getMessage());
        assertEquals((long) c1 + c2, ok.getData().get("amount"));

        Map<String, Object> h = staffSellService.handover(code).getData();
        Map<String, Object> open = (Map<String, Object>) h.get("open");
        assertEquals((long) p, open.get("total"));
        Map<String, Object> paid = (Map<String, Object>) h.get("paid");
        assertEquals(List.of(Map.of("method", "cash", "amount", (long) c1 + c2, "bills", 2L)), paid.get("byMethod"));

        assertNotNull(staffSellService.receiveHandover(receive(code, "airtelmoney", Map.of(phone.getUid(), (long) p))).getData());
        assertEquals(0L, ((Map<String, Object>) staffSellService.handover(code).getData().get("open")).get("total"));
    }

    @Test
    void coldAndWarmOfTheSameDrinkAreSeparateLinesForTheSupervisor() {
        workShiftService.open();
        String code = freeCode();
        SalesOpened bill = open(code);
        write(bill, 2, "cold");
        write(bill, 1, "WARM");
        write(bill, 1, " Cold ");
        var order = write(bill, 1, "hot"); // not a choice: as if nothing was said

        Map<String, Integer> byServing = new java.util.HashMap<>();
        order.getLines().forEach(l -> byServing.merge(String.valueOf(l.getServing()), l.getQuantity(), Integer::sum));
        assertEquals(Map.of("COLD", 3, "WARM", 1, "null", 1), byServing);
        assertEquals(3, order.getLines().size());
    }

    @Test
    void aPhoneRegisteredInAnotherBranchStillLetsTheStaffMemberIn() {
        String code = freeCode(); // PIN 4826, in this login's branch
        // Someone from another branch signed in on this phone earlier.
        String otherDevice = jwtTokenUtil.generateDeviceToken("another-branch-uid", "someone@else");

        com.midland.bar.Bar.Dto.StaffLoginDTO dto = new com.midland.bar.Bar.Dto.StaffLoginDTO();
        dto.setDeviceToken(otherDevice);
        dto.setStaffCode(code);
        dto.setPin("4826");
        var res = staffAccessController.staffLogin(dto);
        assertEquals(200, res.getStatusCode().value(), String.valueOf(res.getBody()));
        assertNotNull(res.getBody().get("token"));
    }

    /** Serengeti Lite - a drink in the local branch. */
    private static final String SERENGETI = "cc2090bd-079c-4d82-ac49-8c6d4876e9c5";

    @Test
    void drinksGoToTheCounterAndFoodToTheChefEachSeeingOnlyTheirOwn() {
        workShiftService.open();
        String code = freeCode();
        SalesOpened bill = open(code);
        var drinkOrder = writeItem(bill, SERENGETI);
        var foodOrder = writeItem(bill, MSHIKAKI);
        assertNotEquals(drinkOrder.getUid(), foodOrder.getUid(), "drinks and food are separate orders");
        assertEquals("COUNTER", drinkOrder.getStation());
        assertEquals("CHEF", foodOrder.getStation());
        assertEquals(2, staffSellService.sendOrders(code).getData());

        // The manager-level login sees both.
        java.util.Set<String> all = new java.util.HashSet<>();
        staffOrderService.pending().getData().forEach(o -> all.add(o.getUid()));
        assertTrue(all.containsAll(java.util.List.of(drinkOrder.getUid(), foodOrder.getUid())));

        // A COUNTER-only login: its drinks, never the kitchen's food.
        actAs("COUNTER");
        var counterSees = staffOrderService.pending().getData().stream().map(o -> o.getUid()).toList();
        assertTrue(counterSees.contains(drinkOrder.getUid()));
        assertFalse(counterSees.contains(foodOrder.getUid()));
        var refused = assertThrows(com.midland.bar.Utils.Exceptions.BusinessException.class,
                () -> staffOrderService.receive(foodOrder.getUid()));
        assertTrue(refused.getMessage().contains("kitchen"), refused.getMessage());

        // And the CHEF the other way round.
        actAs("CHEF");
        var chefSees = staffOrderService.pending().getData().stream().map(o -> o.getUid()).toList();
        assertTrue(chefSees.contains(foodOrder.getUid()));
        assertFalse(chefSees.contains(drinkOrder.getUid()));
    }

    private com.midland.bar.Bar.Model.StaffOrder writeItem(SalesOpened bill, String serviceUid) {
        com.midland.bar.Bar.Dto.StaffOrderItemDTO dto = new com.midland.bar.Bar.Dto.StaffOrderItemDTO();
        dto.setSalesOpenedUID(bill.getUid());
        dto.setBarServiceUID(serviceUid);
        dto.setQuantity(1);
        var res = staffOrderService.addItem(dto);
        assertNotNull(res.getData(), res.getMessage());
        return res.getData();
    }

    /** The same login, holding only this role (the test rolls back, so nothing sticks). */
    private void actAs(String roleCode) {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        user.setIsRoot(false);
        user.setRoles(new java.util.ArrayList<>(java.util.List.of(roleRepository.findByCode(roleCode))));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private com.midland.bar.Bar.Model.StaffOrder write(SalesOpened bill, int qty, String serving) {
        com.midland.bar.Bar.Dto.StaffOrderItemDTO dto = new com.midland.bar.Bar.Dto.StaffOrderItemDTO();
        dto.setSalesOpenedUID(bill.getUid());
        dto.setBarServiceUID(MSHIKAKI);
        dto.setQuantity(qty);
        dto.setServing(serving);
        var res = staffOrderService.addItem(dto);
        assertNotNull(res.getData(), res.getMessage());
        return res.getData();
    }

    private static com.midland.bar.Bar.Dto.HandoverReceiveDTO receive(String code, String method, Map<String, Long> bills) {
        com.midland.bar.Bar.Dto.HandoverReceiveDTO dto = new com.midland.bar.Bar.Dto.HandoverReceiveDTO();
        dto.setStaffCode(code);
        dto.setMethod(method);
        dto.setBills(bills.entrySet().stream().map(e -> {
            com.midland.bar.Bar.Dto.HandoverReceiveDTO.Bill b = new com.midland.bar.Bar.Dto.HandoverReceiveDTO.Bill();
            b.setUid(e.getKey());
            b.setAmount(e.getValue());
            return b;
        }).toList());
        return dto;
    }

    /** A staff member under today's rules (3-digit code, PIN), on the first code nobody holds. */
    private String freeCode() {
        for (int c = 900; c <= 999; c++) {
            BarStaffDTO dto = new BarStaffDTO();
            dto.setFirstName("Maiko");
            dto.setLastName("Test");
            dto.setPhoneNumber("0700000000");
            dto.setBarCategory(com.midland.bar.Bar.Model.StaffCategory.values()[0].name());
            dto.setStaffCode(String.valueOf(c));
            dto.setPin("4826");
            if (barService.saveBarStaff(dto).getData() != null)
                return String.valueOf(c);
        }
        throw new AssertionError("no free staff code in 900-999");
    }

    private int sell(SalesOpened bill, int quantity) {
        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(bill.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(quantity);
        sale.setItems(List.of(item));
        return barService.addSaleItems(sale).getData().getBill();
    }

    private static Map<String, Object> k9(List<Map<String, Object>> rows) {
        return rows.stream().filter(r -> "K9".equals(r.get("staffCode"))).findFirst()
                .orElseThrow(() -> new AssertionError("no K9 row in " + rows));
    }
}
