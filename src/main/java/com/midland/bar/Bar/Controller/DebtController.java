package com.midland.bar.Bar.Controller;

import com.midland.bar.Bar.Dto.DebtCustomerDTO;
import com.midland.bar.Bar.Dto.DebtPaymentDTO;
import com.midland.bar.Bar.Dto.DebtWriteOffDTO;
import com.midland.bar.Bar.Service.DebtService;
import com.midland.bar.Config.Security.AuthChecker;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/*
 MADENI - customers' debts. VIEW_DEBT (CEO, MANAGER, CASHIER) lists them,
 registers a customer and takes their payments; MANAGE_DEBT (CEO, MANAGER)
 sets the credit limit; WRITE_OFF_DEBT (CEO) gives up on a debt. Putting a
 bill on credit is part of paying it (/bar/payBill, method "deni").
 */
@RestController
@RequestMapping("/bar/debt")
@RequiredArgsConstructor
public class DebtController {

    private final DebtService debtService;
    private final AuthChecker authChecker;

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_DEBT')")
    @GetMapping("/customers")
    public ResponseList<Map<String, Object>> customers() {
        return debtService.customers();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_DEBT')")
    @GetMapping("/customers/{uid}")
    public Response<Map<String, Object>> ledger(@PathVariable String uid) {
        return debtService.ledger(uid);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_DEBT')")
    @PostMapping("/customers")
    public Response<Map<String, Object>> register(@RequestBody DebtCustomerDTO dto) {
        return debtService.save(null, dto, canSetLimit());
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_DEBT')")
    // POST too: the app's client only posts.
    @RequestMapping(value = "/customers/{uid}", method = {RequestMethod.PUT, RequestMethod.POST})
    public Response<Map<String, Object>> update(@PathVariable String uid, @RequestBody DebtCustomerDTO dto) {
        return debtService.save(uid, dto, canSetLimit());
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_DEBT') and @authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/pay")
    public Response<Map<String, Object>> pay(@RequestBody DebtPaymentDTO dto) {
        return debtService.pay(dto);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('WRITE_OFF_DEBT')")
    @PostMapping("/writeOff")
    public Response<Map<String, Object>> writeOff(@RequestBody DebtWriteOffDTO dto) {
        return debtService.writeOff(dto);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_DEBT')")
    @GetMapping("/report/{filter}")
    public Response<Map<String, Object>> report(@PathVariable String filter) {
        return debtService.report(filter);
    }

    private boolean canSetLimit() {
        return Boolean.TRUE.equals(authChecker.hasPermissionOrRoot("MANAGE_DEBT"));
    }
}
