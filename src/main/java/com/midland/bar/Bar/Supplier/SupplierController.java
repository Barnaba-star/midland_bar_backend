package com.midland.bar.Bar.Supplier;

import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/*
 SUPPLIERS. VIEW_STORE sees them, their orders and price history; SAVE_STORE
 receives goods on an order; MANAGE_SUPPLIERS (CEO, MANAGER) keeps the list,
 places and cancels orders and pays suppliers.
 */
@RestController
@RequestMapping("/bar/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;
    private final ManunuziService manunuziService;

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @GetMapping
    public ResponseList<Map<String, Object>> list() { return service.suppliers(); }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @GetMapping("/{uid}")
    public Response<Map<String, Object>> ledger(@PathVariable String uid) { return service.ledger(uid); }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_SUPPLIERS')")
    @PostMapping
    public Response<Map<String, Object>> create(@RequestBody Map<String, Object> body) { return service.saveSupplier(null, body); }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_SUPPLIERS')")
    @PostMapping("/{uid}")
    public Response<Map<String, Object>> update(@PathVariable String uid, @RequestBody Map<String, Object> body) { return service.saveSupplier(uid, body); }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_SUPPLIERS')")
    @PostMapping("/pay")
    public Response<Map<String, Object>> pay(@RequestBody Map<String, Object> body) { return service.pay(body); }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @GetMapping("/orders/list")
    public ResponseList<Map<String, Object>> orders() { return service.orders(); }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @GetMapping("/orders/{uid}")
    public Response<Map<String, Object>> order(@PathVariable String uid) { return service.order(uid); }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_SUPPLIERS')")
    @PostMapping("/orders")
    public Response<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body) { return service.createOrder(body); }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_STORE')")
    @PostMapping("/orders/{uid}/receive")
    public Response<Map<String, Object>> receive(@PathVariable String uid, @RequestBody Map<String, Object> body) { return service.receive(uid, body); }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_SUPPLIERS')")
    @PostMapping("/orders/{uid}/cancel")
    public Response<Map<String, Object>> cancel(@PathVariable String uid) { return service.cancel(uid); }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @GetMapping("/prices/{serviceUid}")
    public ResponseList<Map<String, Object>> prices(@PathVariable String serviceUid) { return service.priceHistory(serviceUid); }

    /** Each stock-counted product's Manunuzi: saved, on order, delivered unpaid, free to spend. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @GetMapping("/budget")
    public com.midland.bar.Utils.Responses.ResponseList<Map<String, Object>> budget() { return manunuziService.budgetList(); }
}
