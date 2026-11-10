package com.midland.bar.Bar.Table;

import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** POS Setting > Meza & QR - the same permissions as bill codes on that screen. */
@RestController
@RequestMapping("/bar/tables")
@RequiredArgsConstructor
public class BarTableController {

    private final BarTableService tableService;
    private final TableActionsService actions;
    private final BillQrService billQrService;

    /**
     * A bill's own QR (owner, 2026-11-10), for the waiter to show or print:
     * the token the customers' window opens with (/meza/TOKEN). Only for a
     * waiter's unpaid bill; a waiter signed in with code + PIN gets their own only.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/billQr/{billUid}")
    public com.midland.bar.Utils.Responses.Response<java.util.Map<String, Object>> billQr(@PathVariable String billUid) {
        return billQrService.qrFor(billUid);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SERVICE')")
    @GetMapping
    public ResponseList<BarTable> list() {
        return tableService.list();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SERVICE')")
    @PostMapping("/count")
    public ResponseList<BarTable> setCount(@RequestBody Map<String, Integer> body) {
        return tableService.setCount(body.get("count"));
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SERVICE')")
    @PostMapping("/{uid}/rename")
    public Response<BarTable> rename(@PathVariable String uid, @RequestBody Map<String, String> body) {
        return tableService.rename(uid, body.get("name"));
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SERVICE')")
    @PostMapping("/{uid}/newCard")
    public Response<BarTable> newCard(@PathVariable String uid) {
        return tableService.newCard(uid);
    }

    /** Tables calling now (waiter / bill) - for the counter, cashier and managers. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES') or @authChecker.hasPermissionOrRoot('RECEIVE_ORDERS')")
    @GetMapping("/calls")
    public ResponseList<TableCall> calls() {
        return actions.openCalls();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES') or @authChecker.hasPermissionOrRoot('RECEIVE_ORDERS')")
    @PostMapping("/calls/{uid}/done")
    public Response<TableCall> callDone(@PathVariable String uid) {
        return actions.done(uid);
    }

    /** What customers wrote from the tables, in a period (today, week, month...). */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_CUSTOMER_FEEDBACK')")
    @GetMapping("/feedback/{filter}")
    public ResponseList<CustomerFeedback> feedback(@PathVariable String filter) {
        return actions.feedbackList(filter);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_CUSTOMER_FEEDBACK')")
    @GetMapping("/feedbackUnread")
    public Response<Long> feedbackUnread() {
        return actions.unreadFeedback();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_CUSTOMER_FEEDBACK')")
    @PostMapping("/feedback/{uid}/read")
    public Response<CustomerFeedback> feedbackRead(@PathVariable String uid) {
        return actions.markRead(uid);
    }

    /** Orders from tables waiting to be taken. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES') or @authChecker.hasPermissionOrRoot('RECEIVE_ORDERS')")
    @GetMapping("/orders")
    public ResponseList<Map<String, Object>> pendingOrders() {
        return actions.pendingOrders();
    }

    /** {staffUid} - onto that waiter's bill for the table, and on to the counter / kitchen. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/orders/{uid}/accept")
    public Response<TableOrder> acceptOrder(@PathVariable String uid, @RequestBody Map<String, String> body) {
        return actions.acceptOrder(uid, body.get("staffUid"));
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES') or @authChecker.hasPermissionOrRoot('RECEIVE_ORDERS')")
    @PostMapping("/orders/{uid}/reject")
    public Response<TableOrder> rejectOrder(@PathVariable String uid, @RequestBody Map<String, String> body) {
        return actions.rejectOrder(uid, body.get("reason"));
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SERVICE')")
    @GetMapping("/info")
    public Response<BarTableInfo> info() {
        return actions.info();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SERVICE')")
    @PostMapping("/info")
    public Response<BarTableInfo> saveInfo(@RequestBody Map<String, String> body) {
        return actions.saveInfo(body);
    }
}
