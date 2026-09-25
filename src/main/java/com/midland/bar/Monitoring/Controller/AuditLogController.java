package com.midland.bar.Monitoring.Controller;

import com.midland.bar.Monitoring.Model.AuditLog;
import com.midland.bar.Monitoring.Service.AuditLogService;
import com.midland.bar.Utils.PageableParam;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponsePage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Reading, and removing by age. There is no endpoint that deletes one entry
 * or empties the table - either would let somebody erase the record of what
 * they had just done.
 */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_AUDIT_LOG')")
    @PostMapping("/findAuditPage")
    public ResponsePage<AuditLog> findAuditPage(
            @RequestBody PageableParam pageableParam,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String outcome
    ) {
        return auditLogService.findAuditPage(pageableParam, username, outcome);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_AUDIT_LOG')")
    @GetMapping("/findAuditStorage")
    public Response<Map<String, Long>> findAuditStorage() {
        return auditLogService.findAuditStorage();
    }

    /** Removing by age only - never a single entry, never all of them. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('PURGE_AUDIT_LOG')")
    @PostMapping("/purgeAuditLog")
    public Response<Integer> purgeAuditLog(@RequestParam Integer days) {
        return auditLogService.purgeOlderThan(days);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_AUDIT_LOG')")
    @GetMapping("/findAuditSummary")
    public Response<Map<String, Long>> findAuditSummary() {
        return auditLogService.findAuditSummary();
    }
}
