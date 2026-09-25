package com.midland.bar.Monitoring.Service;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Monitoring.Model.AuditLog;
import com.midland.bar.Monitoring.Repository.AuditLogRepository;
import com.midland.bar.Monitoring.Support.SensitiveData;
import com.midland.bar.Utils.PageableParam;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponsePage;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Log
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_PAYLOAD = 8000;

    /**
     * Nothing from the last month can be removed, whoever asks. A trail that
     * can be cleared the same week is not one.
     */
    private static final int MIN_PURGE_DAYS = 30;

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    /**
     * Turns a path into something readable. Only the endpoints worth naming
     * are listed; anything else keeps its path, which still says what
     * happened - it just says it in the API's words rather than a person's.
     */
    private static final Map<String, String> ACTIONS = buildActions();

    private static Map<String, String> buildActions() {
        Map<String, String> actions = new LinkedHashMap<>();
        actions.put("/branch/saveBranch", "Saved a branch");
        actions.put("/branch/deleteBranch", "Deleted a branch");
        actions.put("/role/saveRole", "Saved a role");
        actions.put("/role/deleteRole", "Deleted a role");
        actions.put("/role/savePermission", "Changed role permissions");
        actions.put("/user/saveUser", "Saved a user");
        actions.put("/user/deleteUser", "Deleted a user");
        actions.put("/user/assignOrUnAssignUserRole", "Changed a user's role");
        actions.put("/user/enableOrDisableUser", "Enabled or disabled a user");
        actions.put("/authentication/login", "Signed in");
        actions.put("/authentication/changePassword", "Changed a password");
        actions.put("/authentication/paySubscription", "Paid an expired subscription");
        actions.put("/setting/updateSubscription", "Started a subscription payment");
        actions.put("/setting/saveBranchSubscription", "Changed a branch's plan");
        actions.put("/platformSetting/savePlatformSetting", "Changed platform settings");
        actions.put("/commission/payStaffCommission", "Paid staff commission");
        actions.put("/monitoring/clearErrors", "Cleared the error log");
        actions.put("/bar/saveBarSales", "Recorded a sale");
        actions.put("/bar/saveCommissions", "Changed a service's commission");
        actions.put("/bar/deleteCommission", "Deleted a commission");
        actions.put("/bar/saveBarEntity", "Saved a service");
        actions.put("/bar/deleteServiceBarByUID", "Deleted a service");
        actions.put("/bar/saveBarStaff", "Saved a staff member");
        actions.put("/bar/deleteBarStaff", "Deleted a staff member");
        actions.put("/bar/payStaffCommission", "Paid a staff member");
        return actions;
    }

    private final AuditLogRepository auditLogRepository;

    /**
     * Its own transaction: a rejected request is rolling back, and the record
     * that it was attempted has to outlive it. Never throws - failing to
     * write the trail must not fail the request it is describing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditLog auditLog) {
        try {
            auditLog.setPayload(SensitiveData.truncate(SensitiveData.redact(auditLog.getPayload()), MAX_PAYLOAD));
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warning("Could not write audit log: " + e.getMessage());
        }
    }

    /** Readable where the path is known, the path itself otherwise. */
    public static String describe(String method, String path) {
        if (path == null) {
            return method;
        }
        for (Map.Entry<String, String> entry : ACTIONS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return method + " " + path;
    }

    public ResponsePage<AuditLog> findAuditPage(PageableParam pageableParam, String username, String outcome) {
        return new ResponsePage<>(
                auditLogRepository.findAuditPage(
                        blankToNull(lower(username)),
                        blankToNull(outcome),
                        blankToNull(lower(pageableParam.getSearchParam())),
                        pageableParam.pageable(false)
                )
        );
    }

    public Response<Map<String, Long>> findAuditSummary() {
        LocalDateTime now = LocalDateTime.now();
        return new Response<>(Map.of(
                "lastDay", auditLogRepository.countSince(now.minusDays(1)),
                "lastWeek", auditLogRepository.countSince(now.minusDays(7)),
                "activeUsers", auditLogRepository.countActiveUsersSince(now.minusDays(7))
        ));
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Removes entries older than a number of days.
     *
     * Deliberately by age and nothing else. There is no way to delete one row
     * or to empty the table: either would let somebody erase the record of
     * what they had just done, which is the only thing an audit log is for.
     * Old entries are a storage question; recent ones are the evidence.
     */
    @Transactional
    public Response<Integer> purgeOlderThan(Integer days) {
        if (days == null || days < MIN_PURGE_DAYS) {
            return new Response<>("Entries newer than " + MIN_PURGE_DAYS + " days cannot be removed");
        }
        int removed = auditLogRepository.purgeOlderThan(LocalDateTime.now().minusDays(days));
        log.info(LoggerUser.getEmail() + " purged " + removed + " audit entries older than " + days + " days");
        return new Response<>(removed);
    }

    /** How much is sitting there, so the decision is an informed one. */
    public Response<Map<String, Long>> findAuditStorage() {
        LocalDateTime now = LocalDateTime.now();
        return new Response<>(Map.of(
                "total", auditLogRepository.count(),
                "olderThan30", auditLogRepository.countOlderThan(now.minusDays(30)),
                "olderThan90", auditLogRepository.countOlderThan(now.minusDays(90)),
                "olderThan365", auditLogRepository.countOlderThan(now.minusDays(365))
        ));
    }

}
