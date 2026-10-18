package com.midland.bar.Bar.Controller;

import com.midland.bar.Bar.Dto.RefundRequestDTO;
import com.midland.bar.Bar.Model.BillRefund;
import com.midland.bar.Bar.Service.RefundService;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/*
 REFUNDS - money back on a paid bill. Whoever takes payments (SAVE_SALES)
 asks; REFUND_SALES (CEO, MANAGER) approves or rejects.
 */
@RestController
@RequestMapping("/bar/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/request")
    public Response<BillRefund> request(@RequestBody RefundRequestDTO dto) {
        return refundService.request(dto);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('REFUND_SALES')")
    @PostMapping("/{uid}/approve")
    public Response<BillRefund> approve(@PathVariable String uid) {
        return refundService.decide(uid, true, null);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('REFUND_SALES')")
    @PostMapping("/{uid}/reject")
    public Response<BillRefund> reject(@PathVariable String uid, @RequestBody(required = false) Map<String, String> body) {
        return refundService.decide(uid, false, body == null ? null : body.get("note"));
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('REFUND_SALES')")
    @GetMapping("/pending")
    public ResponseList<BillRefund> pending() {
        return refundService.pending();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES') or @authChecker.hasPermissionOrRoot('REFUND_SALES')")
    @GetMapping("/list/{filter}")
    public ResponseList<BillRefund> list(@PathVariable String filter) {
        return refundService.list(filter);
    }
}
