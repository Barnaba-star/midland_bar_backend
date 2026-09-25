package com.midland.bar.Monitoring.Controller;

import com.midland.bar.Monitoring.Dto.ClientErrorDTO;
import com.midland.bar.Monitoring.Model.ErrorLog;
import com.midland.bar.Monitoring.Service.ErrorLogService;
import com.midland.bar.Utils.PageableParam;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponsePage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
public class ErrorLogController {

    private final ErrorLogService errorLogService;

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_ERROR_LOG')")
    @PostMapping("/findErrorPage")
    public ResponsePage<ErrorLog> findErrorPage(
            @RequestBody PageableParam pageableParam,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String level
    ) {
        return errorLogService.findErrorPage(pageableParam, source, level);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_ERROR_LOG')")
    @GetMapping("/findErrorSummary")
    public Response<Map<String, Long>> findErrorSummary() {
        return errorLogService.findErrorSummary();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('DELETE_ERROR_LOG')")
    @DeleteMapping("/clearErrors")
    public Response<Integer> clearErrors() {
        return errorLogService.clearAll();
    }

    /**
     * Where the browser posts its own errors. Deliberately open to any logged-in
     * user rather than gated on VIEW_ERROR_LOG - a CASHIER hitting a bug is
     * exactly the report worth having, even though they cannot read the list.
     */
    @PostMapping("/reportClientError")
    public Response<Boolean> reportClientError(
            @RequestBody ClientErrorDTO clientErrorDTO,
            HttpServletRequest request
    ) {
        return errorLogService.recordClientError(clientErrorDTO, request);
    }
}
