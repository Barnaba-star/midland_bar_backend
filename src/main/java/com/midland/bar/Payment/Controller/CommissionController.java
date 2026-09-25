package com.midland.bar.Payment.Controller;

import com.midland.bar.Payment.Dto.StaffCommissionDTO;
import com.midland.bar.Payment.Dto.PayCommissionDTO;
import com.midland.bar.Payment.Dto.StaffBranchDTO;
import com.midland.bar.Payment.Model.CommissionPayout;
import com.midland.bar.Payment.Model.SubscriptionPayment;
import com.midland.bar.Payment.Service.CommissionService;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Who brought in which branches, which of those paid in a given month, and
 * what each staff member is therefore owed. Platform-level money, so it
 * stays behind VIEW_COMMISSION_REPORT (ROOT/DIRECTOR) - a STAFF member must
 * never be able to read what the others earned.
 */
@RestController
@RequestMapping("/commission")
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionService commissionService;

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_COMMISSION_REPORT')")
    @GetMapping("/staffCommissionReport")
    public ResponseList<StaffCommissionDTO> staffCommissionReport(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String search
    ) {
        return commissionService.staffCommissionReport(year, month, search);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_COMMISSION_REPORT')")
    @GetMapping("/staffPayments/{staffUID}")
    public ResponseList<SubscriptionPayment> staffPayments(
            @PathVariable String staffUID,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return commissionService.staffPayments(staffUID, year, month);
    }

    // Money leaving the business, so this sits behind its own permission
    // rather than riding along with the read-only report. ROOT always passes;
    // grant PAY_COMMISSION via Settings > Role to let anyone else pay.
    @PreAuthorize("@authChecker.hasPermissionOrRoot('PAY_COMMISSION')")
    @PostMapping("/payStaffCommission")
    public Response<CommissionPayout> payStaffCommission(@RequestBody PayCommissionDTO payCommissionDTO) {
        return commissionService.payStaffCommission(
                payCommissionDTO.getStaffUid(),
                payCommissionDTO.getYear(),
                payCommissionDTO.getMonth(),
                payCommissionDTO.getNote()
        );
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_COMMISSION_REPORT')")
    @GetMapping("/staffBranches/{staffUID}")
    public ResponseList<StaffBranchDTO> staffBranches(
            @PathVariable String staffUID,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return commissionService.staffBranches(staffUID, year, month);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_COMMISSION_REPORT')")
    @GetMapping("/staffPayouts/{staffUID}")
    public ResponseList<CommissionPayout> staffPayouts(
            @PathVariable String staffUID,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return commissionService.staffPayouts(staffUID, year, month);
    }
}
