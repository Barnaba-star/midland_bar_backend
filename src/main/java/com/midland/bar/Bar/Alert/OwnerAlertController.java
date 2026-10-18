package com.midland.bar.Bar.Alert;

import com.midland.bar.Config.Security.BranchAccess;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/*
 The owner's numbers: one branch's day (the same figures as the morning
 summary), and every branch this login may use side by side - the
 all-branches dashboard for an owner with more than one bar.
 */
@RestController
@RequestMapping("/bar/owner")
@RequiredArgsConstructor
public class OwnerAlertController {

    private final OwnerAlertService alerts;
    private final BranchAccess branchAccess;

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/day")
    public Response<Map<String, Object>> day(@RequestParam(required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        return new Response<>(alerts.digestOf(LoggerUser.getBranchUIDOrMain(), d));
    }

    /** Today and yesterday for each branch this login may work in. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/branches")
    public ResponseList<Map<String, Object>> branches() {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Branch b : branchAccess.allowed(LoggerUser.getUser())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uid", b.getUid());
            row.put("branchName", b.getBranchName());
            row.put("branchCode", b.getBranchCode());
            row.put("current", b.getUid().equals(LoggerUser.getBranchUID()));
            row.put("today", alerts.digestOf(b.getUid(), today));
            row.put("yesterday", alerts.digestOf(b.getUid(), today.minusDays(1)));
            row.put("openShifts", alerts.openShifts(b.getUid()));
            out.add(row);
        }
        return new ResponseList<>(out);
    }
}
