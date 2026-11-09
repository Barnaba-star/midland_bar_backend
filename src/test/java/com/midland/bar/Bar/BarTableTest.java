package com.midland.bar.Bar;

import com.midland.bar.Bar.Table.BarTable;
import com.midland.bar.Bar.Table.BarTableService;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tables + QR tokens + the customers' window (owner, 2026-11-09) - local DB sample data, rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@org.junit.jupiter.api.Tag("local")
@Transactional
class BarTableTest {

    private static final String LOGIN = "METHEW@2026";

    @Autowired BarTableService tableService;
    @Autowired com.midland.bar.Bar.Table.TableActionsService actions;
    @Autowired com.midland.bar.Bar.Service.BarService barService;
    @Autowired com.midland.bar.Bar.Service.WorkShiftService workShiftService;
    @Autowired com.midland.bar.Bar.Repository.SalesOpenedRepository salesOpenedRepository;
    @Autowired com.midland.bar.Bar.Repository.StaffOrderRepository staffOrderRepository;
    @Autowired UserRepository userRepository;
    @Autowired MockMvc mvc;

    @BeforeEach
    void signIn() {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        assertNotNull(user, "sample user");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void countAddsAndRemovesAndEachTableHasItsOwnToken() {
        tableService.setCount(0);
        List<BarTable> three = tableService.setCount(3).getData();
        assertEquals(List.of("Meza 1", "Meza 2", "Meza 3"), three.stream().map(BarTable::getName).toList());
        assertEquals(3, three.stream().map(BarTable::getToken).distinct().count());
        three.forEach(t -> assertEquals(10, t.getToken().length()));

        List<BarTable> two = tableService.setCount(2).getData();
        assertEquals(2, two.size());
        assertEquals(2, tableService.list().getData().size());
        String removed = three.get(2).getToken();
        assertEquals("TABLE_NOT_FOUND", tableService.customerView(removed).getMessage(), "a removed table's card stops working");
        assertNull(tableService.setCount(301).getData());
    }

    @Test
    void theCustomersWindowShowsTheTableAndTheMenuButNoStoreItems() {
        tableService.setCount(0);
        BarTable t = tableService.setCount(1).getData().get(0);
        Map<String, Object> view = tableService.customerView(t.getToken().toLowerCase()).getData();
        assertNotNull(view);
        assertEquals("Meza 1", view.get("tableName"));
        assertNotNull(view.get("branchName"));
        List<?> menu = (List<?>) view.get("menu");
        assertFalse(menu.isEmpty(), "the sample branch sells things");
        menu.forEach(m -> assertTrue(((Integer) ((Map<?, ?>) m).get("price")) > 0));

        String old = t.getToken();
        String fresh = tableService.newCard(t.getUid()).getData().getToken();
        assertNotEquals(old, fresh);
        assertNull(tableService.customerView(old).getData(), "the old card stops working");
    }

    @Test
    void theWindowNeedsNoLogin() throws Exception {
        tableService.setCount(0);
        String token = tableService.setCount(1).getData().get(0).getToken();
        SecurityContextHolder.clearContext();
        mvc.perform(get("/public/table/" + token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.tableName").value("Meza 1"));
        mvc.perform(get("/bar/tables")).andExpect(status().is4xxClientError());
        // A phone holding an old or broken login still opens the window, never the sign-in.
        mvc.perform(get("/public/table/" + token).header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tableName").value("Meza 1"));
    }

    @Test
    void aTableCallsTheWaiterOnceAndTheBarClosesIt() {
        tableService.setCount(0);
        BarTable t = tableService.setCount(1).getData().get(0);
        assertNotNull(actions.call(t.getToken(), "WAITER").getData());
        assertEquals("TOO_SOON", actions.call(t.getToken(), "WAITER").getMessage(), "a second tap within a minute");
        assertNotNull(actions.call(t.getToken(), "BILL").getData(), "the bill is its own call");
        var open = actions.openCalls().getData().stream().filter(c -> c.getTableUid().equals(t.getUid())).toList();
        assertEquals(2, open.size());
        assertEquals(t.getBranchUid(), open.get(0).getBranchUid(), "the table's branch, not a login's");
        actions.done(open.get(0).getUid());
        assertEquals(1, actions.openCalls().getData().stream().filter(c -> c.getTableUid().equals(t.getUid())).count());
        assertEquals("TABLE_NOT_FOUND", actions.call("ZZZZZZZZZZ", "WAITER").getMessage());
    }

    @Test
    void feedbackReachesTheBranchAndOneTableCannotFloodIt() {
        tableService.setCount(0);
        BarTable t = tableService.setCount(1).getData().get(0);
        long before = actions.unreadFeedback().getData();
        var res = actions.feedback(t.getToken(), java.util.Map.of("kind", "COMPLAINT", "message", "Bia ilikuwa ya moto", "rating", 2));
        assertNotNull(res.getData(), res.getMessage());
        assertEquals(before + 1, actions.unreadFeedback().getData());
        var mine = actions.feedbackList("TODAY").getData().stream().filter(f -> t.getUid().equals(f.getTableUid())).toList();
        assertEquals(1, mine.size());
        assertEquals("COMPLAINT", mine.get(0).getKind());
        assertEquals(2, mine.get(0).getRating());
        actions.markRead(mine.get(0).getUid());
        assertEquals(before, actions.unreadFeedback().getData());
        for (int i = 0; i < 4; i++)
            actions.feedback(t.getToken(), java.util.Map.of("kind", "ADVICE", "message", "ujumbe " + i));
        assertEquals("TOO_MANY", actions.feedback(t.getToken(), java.util.Map.of("message", "tena")).getMessage());
    }

    @Test
    void theMenuSaysWhatIsSoldOutAndAWrongPinTellsNothing() {
        tableService.setCount(0);
        BarTable t = tableService.setCount(1).getData().get(0);
        java.util.List<?> menu = (java.util.List<?>) tableService.customerView(t.getToken()).getData().get("menu");
        menu.forEach(m -> assertNotNull(((java.util.Map<?, ?>) m).get("available")));
        assertEquals("WRONG_PHONE_OR_PIN", actions.card(t.getToken(), java.util.Map.of("phone", "0700000001", "pin", "0000")).getMessage());
    }

    private com.midland.bar.Bar.Model.BarStaff waiter() {
        var dto = new com.midland.bar.Bar.Dto.BarStaffDTO();
        dto.setFirstName("Juma");
        dto.setLastName("Meza");
        dto.setPhoneNumber("0711000000");
        dto.setBarCategory(com.midland.bar.Bar.Model.StaffCategory.values()[0].name());
        dto.setStaffCode("");
        var saved = barService.saveBarStaff(dto);
        assertNotNull(saved.getData(), saved.getMessage());
        return saved.getData();
    }

    @Test
    void aTableOrderGoesOnTheWaitersTableBillAndToTheStations() {
        workShiftService.open();
        tableService.setCount(0);
        BarTable t = tableService.setCount(1).getData().get(0);
        java.util.List<?> menu = (java.util.List<?>) tableService.customerView(t.getToken()).getData().get("menu");
        String uid = (String) ((java.util.Map<?, ?>) menu.get(0)).get("uid");
        assertNotNull(uid, "menu items carry their uid for ordering");

        var placed = actions.placeOrder(t.getToken(), java.util.Map.of("items", java.util.List.of(java.util.Map.of("uid", uid, "qty", 2))));
        assertNotNull(placed.getData(), placed.getMessage());
        var pending = actions.pendingOrders().getData();
        var mine = pending.stream().filter(o -> t.getName().equals(o.get("tableName"))).toList();
        assertEquals(1, mine.size());
        String orderUid = (String) mine.get(0).get("uid");

        var w = waiter();
        var accepted = actions.acceptOrder(orderUid, w.getUid());
        assertNotNull(accepted.getData(), accepted.getMessage());
        var bill = salesOpenedRepository.findById(accepted.getData().getBillUid()).orElseThrow();
        assertEquals(t.getUid(), bill.getTableUid());
        assertEquals(w.getUid(), bill.getStaffUid());
        assertEquals("SENT", staffOrderRepository.findAll().stream().filter(o -> bill.getUid().equals(o.getSalesOpenedUid()))
                .findFirst().orElseThrow().getStatus(), "straight to the station");
        assertNull(actions.acceptOrder(orderUid, w.getUid()).getData(), "taken once");

        // A second order from the table lands on the same bill.
        actions.placeOrder(t.getToken(), java.util.Map.of("items", java.util.List.of(java.util.Map.of("uid", uid, "qty", 1))));
        String second = (String) actions.pendingOrders().getData().stream().filter(o -> t.getName().equals(o.get("tableName"))).findFirst().orElseThrow().get("uid");
        assertEquals(bill.getUid(), actions.acceptOrder(second, w.getUid()).getData().getBillUid());

        var status = actions.tableOrders(t.getToken()).getData();
        assertEquals("Juma Meza", status.get("waiter"));
    }

    @Test
    void aDrinkOrderedColdAndWarmReachesTheStationThatWay() {
        workShiftService.open();
        tableService.setCount(0);
        BarTable t = tableService.setCount(1).getData().get(0);
        java.util.List<?> menu = (java.util.List<?>) tableService.customerView(t.getToken()).getData().get("menu");
        var drink = menu.stream().map(m -> (java.util.Map<?, ?>) m)
                .filter(m -> com.midland.bar.Bar.Table.TableActionsService.asksServing((String) m.get("category")))
                .findFirst().orElseThrow();
        String uid = (String) drink.get("uid");

        var placed = actions.placeOrder(t.getToken(), java.util.Map.of("items", java.util.List.of(
                java.util.Map.of("uid", uid, "qty", 2, "serving", "COLD"),
                java.util.Map.of("uid", uid, "qty", 1, "serving", "warm"))));
        assertNotNull(placed.getData(), placed.getMessage());
        var pending = actions.pendingOrders().getData().stream().filter(o -> t.getName().equals(o.get("tableName"))).findFirst().orElseThrow();
        assertEquals(java.util.List.of("COLD", "WARM"), ((java.util.List<?>) pending.get("lines")).stream()
                .map(l -> ((java.util.Map<?, ?>) l).get("serving")).toList(), "the waiter sees baridi / moto");

        var accepted = actions.acceptOrder((String) pending.get("uid"), waiter().getUid());
        assertNotNull(accepted.getData(), accepted.getMessage());
        var lines = staffOrderRepository.findAll().stream().filter(o -> accepted.getData().getBillUid().equals(o.getSalesOpenedUid()))
                .flatMap(o -> o.getLines().stream()).toList();
        assertEquals(2, lines.stream().filter(l -> "COLD".equals(l.getServing())).mapToInt(l -> l.getQuantity()).sum());
        assertEquals(1, lines.stream().filter(l -> "WARM".equals(l.getServing())).mapToInt(l -> l.getQuantity()).sum());
    }

    @Test
    void aRejectedOrderTellsWhyAndPaidAndInfoReachTheBar() {
        tableService.setCount(0);
        BarTable t = tableService.setCount(1).getData().get(0);
        java.util.List<?> menu = (java.util.List<?>) tableService.customerView(t.getToken()).getData().get("menu");
        String uid = (String) ((java.util.Map<?, ?>) menu.get(0)).get("uid");
        actions.placeOrder(t.getToken(), java.util.Map.of("items", java.util.List.of(java.util.Map.of("uid", uid, "qty", 1))));
        String o = (String) actions.pendingOrders().getData().stream().filter(x -> t.getName().equals(x.get("tableName"))).findFirst().orElseThrow().get("uid");
        assertNull(actions.rejectOrder(o, " ").getData(), "a reason is required");
        assertEquals("REJECTED", actions.rejectOrder(o, "Kimeisha").getData().getStatus());
        assertNull(actions.placeOrder(t.getToken(), java.util.Map.of("items", java.util.List.of(java.util.Map.of("uid", uid, "qty", 50)))).getData());

        assertNotNull(actions.paid(t.getToken(), java.util.Map.of("method", "M-Pesa", "name", "Asha", "ref", "QX1")).getData());
        assertTrue(actions.openCalls().getData().stream().anyMatch(c -> "PAID".equals(c.getKind()) && c.getNote().contains("Asha")));

        actions.saveInfo(java.util.Map.of("wifiName", "Baronix", "wifiPassword", "bia2026", "payMpesa", "123456"));
        var info = (java.util.Map<?, ?>) tableService.customerView(t.getToken()).getData().get("info");
        assertEquals("bia2026", info.get("wifiPassword"));
        assertEquals("123456", info.get("payMpesa"));
    }

    @Test
    void aWaiterSignedInWithCodeSeesTheTablesAndTakesAnOrderOntoTheirOwnBill() {
        assertTrue(com.midland.bar.Config.Security.StaffSession.allows("GET", "/bar/tables/calls"));
        assertTrue(com.midland.bar.Config.Security.StaffSession.allows("GET", "/bar/tables/orders"));
        assertTrue(com.midland.bar.Config.Security.StaffSession.allows("POST", "/bar/tables/orders/x/accept"));
        assertTrue(com.midland.bar.Config.Security.StaffSession.allows("POST", "/bar/tables/calls/x/done"));
        assertFalse(com.midland.bar.Config.Security.StaffSession.allows("GET", "/bar/tables/feedback/WEEK"), "feedback stays with the boss");

        workShiftService.open();
        tableService.setCount(0);
        BarTable t = tableService.setCount(1).getData().get(0);
        java.util.List<?> menu = (java.util.List<?>) tableService.customerView(t.getToken()).getData().get("menu");
        String uid = (String) ((java.util.Map<?, ?>) menu.get(0)).get("uid");
        actions.placeOrder(t.getToken(), java.util.Map.of("items", java.util.List.of(java.util.Map.of("uid", uid, "qty", 1))));
        String o = (String) actions.pendingOrders().getData().stream().filter(x -> t.getName().equals(x.get("tableName"))).findFirst().orElseThrow().get("uid");
        var me = waiter();
        var other = waiter();
        // Signed in as "me" with code + PIN: asking for a colleague still lands on my bill.
        var user = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        auth.setDetails(new com.midland.bar.Config.Security.StaffSession.Info(me.getUid(), me.getStaffCode()));
        SecurityContextHolder.getContext().setAuthentication(auth);
        var taken = actions.acceptOrder(o, other.getUid());
        assertNotNull(taken.getData(), taken.getMessage());
        assertEquals(me.getUid(), taken.getData().getStaffUid());
    }
}
