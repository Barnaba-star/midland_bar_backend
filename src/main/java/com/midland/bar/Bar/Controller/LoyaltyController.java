package com.midland.bar.Bar.Controller;

import com.midland.bar.Bar.Dto.LoyaltyPinDTO;
import com.midland.bar.Bar.Model.LoyaltyRule;
import com.midland.bar.Bar.Service.LoyaltyService;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Customer loyalty: the branch rule, customers naming themselves with phone + PIN, and bills counted for them. */
@RestController
@RequestMapping("/bar/loyalty")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    /** The rule - every till needs the bill amount that asks for the customer's PIN. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @GetMapping("/rule")
    public Response<Map<String, Object>> rule() {
        return loyaltyService.ruleView();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_LOYALTY')")
    @PostMapping("/rule")
    public Response<Map<String, Object>> saveRule(@RequestBody LoyaltyRule rule) {
        return loyaltyService.saveRule(rule);
    }

    /** Who the customer is, by phone + PIN: name, offer, credit and progress. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/identify")
    public Response<Map<String, Object>> identify(@RequestBody LoyaltyPinDTO dto) {
        return new Response<>(loyaltyService.card(loyaltyService.verify(dto.getPhone(), dto.getPin())));
    }

    /** A new customer (or one without a PIN) sets their own PIN. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/register")
    public Response<Map<String, Object>> register(@RequestBody LoyaltyPinDTO dto) {
        return new Response<>(loyaltyService.card(loyaltyService.register(dto.getName(), dto.getPhone(), dto.getPin())));
    }

    /** Whether this bill asks for the customer's code (total from its lines, against the rule). */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @GetMapping("/bills/{billUid}/ask")
    public Response<Map<String, Object>> ask(@PathVariable String billUid) {
        return loyaltyService.askFor(billUid);
    }

    /** The customer named themselves on this bill: it counts for them when paid. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/bills/{billUid}")
    public Response<Map<String, Object>> attach(@PathVariable String billUid, @RequestBody LoyaltyPinDTO dto) {
        return loyaltyService.attach(billUid, dto.getPhone(), dto.getPin());
    }

    /** Customers holding an earned offer, with what is left of it. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_DEBT')")
    @GetMapping("/winners")
    public Response<Map<String, Object>> winners() {
        return loyaltyService.winners();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_DEBT')")
    @PostMapping("/customers/{uid}/resetPin")
    public Response<Map<String, Object>> resetPin(@PathVariable String uid) {
        return loyaltyService.resetPin(uid);
    }
}
