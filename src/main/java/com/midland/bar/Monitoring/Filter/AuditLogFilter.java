package com.midland.bar.Monitoring.Filter;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Monitoring.Model.AuditLog;
import com.midland.bar.Monitoring.Service.AuditLogService;
import com.midland.bar.Uaa.Model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Records every request that changes something.
 *
 * A filter rather than a call inside each service: an endpoint added next
 * month is covered without anyone remembering to wire it up, and one place
 * decides what is worth keeping.
 *
 * Ordered after the security filters so the principal is already resolved -
 * an audit trail without a name on it is not an audit trail.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuditLogFilter extends OncePerRequestFilter {

    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * Paths that change something on paper but say nothing about who did
     * what. Heartbeat alone runs every thirty seconds per signed-in user -
     * left in, it would bury everything worth reading.
     */
    private static final List<String> IGNORED = List.of(
            "/setting/heartbeat",
            "/monitoring/reportClientError",
            "/notification/markAsRead",
            "/notification/markAllAsRead",
            // Snippe posts these server-to-server; they are already recorded
            // as subscription payments.
            "/setting/webhooks"
    );

    /**
     * Login is deliberately absent from IGNORED - a failed sign-in is one of
     * the most useful lines in an audit trail. The body is redacted before
     * storage, so the password never lands.
     */
    private static final Pattern USERNAME_IN_BODY =
            Pattern.compile("\"username\"\\s*:\\s*\"([^\"]{1,255})\"", Pattern.CASE_INSENSITIVE);

    private final AuditLogService auditLogService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!shouldAudit(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            // In a finally block so a request that blew up is still recorded -
            // an attempt that failed is worth as much as one that worked.
            write(request, response, System.currentTimeMillis() - startedAt);
        }
    }

    private boolean shouldAudit(HttpServletRequest request) {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (IGNORED.stream().anyMatch(path::startsWith)) {
            return false;
        }
        return !isRead(path);
    }

    /**
     * This API posts its searches, because they carry a PageableParam in the
     * body - so "POST" is not the same as "changed something" here. Every one
     * of them is named find*, and nothing that changes anything is, so the
     * name is what decides.
     *
     * Without this the log filled with people looking at pages: opening the
     * audit screen wrote an audit row about opening the audit screen.
     */
    private static boolean isRead(String path) {
        int lastSlash = path.lastIndexOf('/');
        String endpoint = lastSlash < 0 ? path : path.substring(lastSlash + 1);
        return endpoint.toLowerCase().startsWith("find");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        try {
            AuditLog entry = new AuditLog();
            entry.setOccurredAt(LocalDateTime.now());
            entry.setHttpMethod(request.getMethod());
            entry.setPath(truncate(request.getRequestURI(), 500));
            entry.setQueryString(truncate(request.getQueryString(), 2000));
            entry.setAction(truncate(AuditLogService.describe(request.getMethod(), request.getRequestURI()), 255));
            String body = readBody(request);
            entry.setPayload(body);
            entry.setStatusCode(response.getStatus());
            entry.setOutcome(response.getStatus() < 400 ? AuditLogService.SUCCESS : AuditLogService.FAILED);
            entry.setDurationMs(durationMs);
            entry.setIpAddress(truncate(clientIp(request), 60));
            entry.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
            applyActor(entry, body);
            auditLogService.record(entry);
        } catch (Exception e) {
            // Never let the trail break the request it is describing.
            logger.warn("Could not build audit entry: " + e.getMessage());
        }
    }

    private void applyActor(AuditLog entry, String body) {
        try {
            User user = LoggerUser.getUser();
            entry.setUsername(user.getUsername());
            entry.setFullName(String.format("%s %s", user.getFirstName(), user.getLastName()).trim());
            entry.setBranchUid(user.getBranch() != null ? user.getBranch().getUid() : null);
            return;
        } catch (Exception ignored) {
            // No principal - see below.
        }

        // Sign-in and the pay-while-expired route run before any principal
        // exists, and "who tried" is the most useful line an audit trail can
        // hold. The name is taken from the body; a username is not a secret,
        // and the password beside it is redacted before storage.
        entry.setUsername(truncate(usernameFromBody(body), 255));
    }

    private static String usernameFromBody(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = USERNAME_IN_BODY.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Only present when RequestPayloadCachingFilter kept a copy. */
    private static String readBody(HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper =
                WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper == null) {
            return null;
        }
        byte[] body = wrapper.getContentAsByteArray();
        return body == null || body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // First entry is the original client; the rest are proxies.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
