package com.midland.bar.Bar.Controller;

import com.midland.bar.Bar.Model.BillDiscount;
import com.midland.bar.Bar.Model.PriceRule;
import com.midland.bar.Bar.Model.SalesOpened;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Bar.Service.PriceRuleService;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/*
 OFFERS AND DISCOUNTS. Happy hours / offers are kept by MANAGE_PRICE_RULES
 (CEO, MANAGER) and priced in as items are sold; anyone selling sees which
 run now. A discount on an open bill: SAVE_SALES within the cashier limit,
 DISCOUNT_ANY beyond it.
 */
@RestController
@RequestMapping("/bar")
@RequiredArgsConstructor
public class PriceRuleController {

    private final PriceRuleService priceRuleService;
    private final BarService barService;

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/priceRules")
    public ResponseList<PriceRule> list() {
        return priceRuleService.list();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/priceRules/now")
    public ResponseList<PriceRule> now() {
        return priceRuleService.runningNow();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/priceRules/preview")
    public Response<Map<String, Object>> preview(@RequestParam String serviceUid, @RequestParam(defaultValue = "1") int qty) {
        return priceRuleService.preview(serviceUid, qty);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_PRICE_RULES')")
    @PostMapping("/priceRules")
    public Response<PriceRule> create(@RequestBody Map<String, Object> body) {
        return priceRuleService.save(null, body);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_PRICE_RULES')")
    @PostMapping("/priceRules/{uid}")
    public Response<PriceRule> update(@PathVariable String uid, @RequestBody Map<String, Object> body) {
        return priceRuleService.save(uid, body);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_PRICE_RULES')")
    @PostMapping("/priceRules/{uid}/delete")
    public Response<Boolean> delete(@PathVariable String uid) {
        return priceRuleService.delete(uid);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/bills/{uid}/discount")
    public Response<SalesOpened> discount(@PathVariable String uid, @RequestBody Map<String, Object> body) {
        return barService.discountBill(uid, num(body.get("amount")), num(body.get("percent")),
                body.get("reason") == null ? null : String.valueOf(body.get("reason")));
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/discounts/{filter}")
    public ResponseList<BillDiscount> discounts(@PathVariable String filter) {
        return barService.discounts(filter);
    }

    private static Integer num(Object v) {
        if (v == null || String.valueOf(v).isBlank())
            return null;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(v)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
