package com.midland.bar.Monitoring.Service;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Monitoring.Dto.ClientErrorDTO;
import com.midland.bar.Monitoring.Model.ErrorLog;
import com.midland.bar.Monitoring.Repository.ErrorLogRepository;
import com.midland.bar.Monitoring.Support.SensitiveData;
import com.midland.bar.Utils.PageableParam;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponsePage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Log
@RequiredArgsConstructor
public class ErrorLogService {

    public static final String SOURCE_BACKEND = "BACKEND";
    public static final String SOURCE_FRONTEND = "FRONTEND";
    public static final String LEVEL_ERROR = "ERROR";
    public static final String LEVEL_WARN = "WARN";

    // Enough of the trace to find the bug without turning the table into a
    // dumping ground - the top frames are the ones that matter.
    private static final int MAX_STACK_TRACE = 8000;
    private static final int MAX_MESSAGE = 2000;
    private static final int MAX_PAYLOAD = 8000;

    private final ErrorLogRepository errorLogRepository;

    /**
     * Records a backend exception. Runs in its own transaction: the request
     * that failed is usually rolling back, and this row has to outlive it.
     * Never throws - a broken logger must not turn a 400 into a 500.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBackendError(Throwable throwable, String level, HttpServletRequest request) {
        try {
            ErrorLog errorLog = new ErrorLog();
            errorLog.setSource(SOURCE_BACKEND);
            errorLog.setLevel(level);
            errorLog.setOccurredAt(LocalDateTime.now());
            errorLog.setMessage(SensitiveData.truncate(throwable.getMessage(), MAX_MESSAGE));
            errorLog.setExceptionType(throwable.getClass().getName());
            errorLog.setStackTrace(SensitiveData.truncate(stackTraceOf(throwable), MAX_STACK_TRACE));
            if (request != null) {
                errorLog.setPath(SensitiveData.truncate(request.getRequestURI(), 500));
                errorLog.setHttpMethod(request.getMethod());
                errorLog.setUserAgent(SensitiveData.truncate(request.getHeader("User-Agent"), 500));
                errorLog.setQueryString(SensitiveData.truncate(request.getQueryString(), 2000));
                errorLog.setPayload(SensitiveData.truncate(SensitiveData.redact(readBody(request)), MAX_PAYLOAD));
            }
            applyCurrentUser(errorLog);
            errorLogRepository.save(errorLog);
        } catch (Exception e) {
            // Deliberately swallowed - see the method contract above.
            log.warning("Could not record error log: " + e.getMessage());
        }
    }

    /** Records an error the browser reported. Same no-throw contract. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Response<Boolean> recordClientError(ClientErrorDTO dto, HttpServletRequest request) {
        try {
            ErrorLog errorLog = new ErrorLog();
            errorLog.setSource(SOURCE_FRONTEND);
            errorLog.setLevel(dto.getLevel() == null ? LEVEL_ERROR : dto.getLevel());
            errorLog.setOccurredAt(LocalDateTime.now());
            errorLog.setMessage(SensitiveData.truncate(dto.getMessage(), MAX_MESSAGE));
            errorLog.setExceptionType(SensitiveData.truncate(dto.getExceptionType(), 255));
            errorLog.setStackTrace(SensitiveData.truncate(dto.getStackTrace(), MAX_STACK_TRACE));
            errorLog.setPath(SensitiveData.truncate(dto.getPath(), 500));
            errorLog.setHttpMethod(dto.getHttpMethod());
            errorLog.setPayload(SensitiveData.truncate(SensitiveData.redact(dto.getPayload()), MAX_PAYLOAD));
            if (request != null) {
                errorLog.setUserAgent(SensitiveData.truncate(request.getHeader("User-Agent"), 500));
            }
            applyCurrentUser(errorLog);
            errorLogRepository.save(errorLog);
            return new Response<>(true);
        } catch (Exception e) {
            log.warning("Could not record client error log: " + e.getMessage());
            return new Response<>(false);
        }
    }

    public ResponsePage<ErrorLog> findErrorPage(PageableParam pageableParam, String source, String level) {
        return new ResponsePage<>(
                errorLogRepository.findErrorPage(
                        blankToNull(source),
                        blankToNull(level),
                        pageableParam.pageable(false)
                )
        );
    }

    /** Counters for the page header: how noisy the last day and week have been. */
    public Response<Map<String, Long>> findErrorSummary() {
        LocalDateTime now = LocalDateTime.now();
        return new Response<>(Map.of(
                "lastDay", errorLogRepository.countSince(now.minusDays(1)),
                "lastWeek", errorLogRepository.countSince(now.minusDays(7))
        ));
    }

    @Transactional
    public Response<Integer> clearAll() {
        return new Response<>(errorLogRepository.clearAll());
    }

    private void applyCurrentUser(ErrorLog errorLog) {
        // An error can happen before or after authentication, and LoggerUser
        // throws when there is no principal - so never let that decide whether
        // the error itself gets recorded.
        try {
            errorLog.setUsername(LoggerUser.getEmail());
        } catch (Exception ignored) {
            errorLog.setUsername(null);
        }
        try {
            errorLog.setBranchUid(LoggerUser.getBranchUID());
        } catch (Exception ignored) {
            errorLog.setBranchUid(null);
        }
    }

    /**
     * The body as it was posted, if RequestPayloadCachingFilter kept a copy.
     * Anything it did not wrap (GET, multipart) simply has no payload.
     */
    private static String readBody(HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper =
                WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper == null) {
            return null;
        }
        byte[] body = wrapper.getContentAsByteArray();
        if (body == null || body.length == 0) {
            return null;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static String stackTraceOf(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
