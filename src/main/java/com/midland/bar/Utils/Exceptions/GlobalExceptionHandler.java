package com.midland.bar.Utils.Exceptions;

import com.midland.bar.Monitoring.Service.ErrorLogService;
import com.midland.bar.Utils.Responses.ResponseList;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Log
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorLogService errorLogService;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseList<Object>> handleBusinessException(
            BusinessException e,
            HttpServletRequest request) {

        // A rejected business rule ("open your shift before selling"), not a
        // crash: the user is told why. Kept out of Admin > Makosa, where these
        // buried the real errors - the server log still has it.
        log.info("Refused " + request.getMethod() + " " + request.getRequestURI() + ": " + e.getMessage());

        return ResponseEntity
                .badRequest()
                .body(new ResponseList<>(e.getMessage()));
    }

    /**
     * Security decisions are not application errors. Spring Security turns
     * these into the 401/403 bodies configured in WebSecurityConfiguration,
     * but only if they get past this advice - catching Exception below would
     * otherwise swallow them and report a 500 instead.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityException(RuntimeException e) throws RuntimeException {
        throw e;
    }

    /**
     * The phone or browser left a live (/bar/live) stream or a slow request
     * before the answer was written - nobody is there to answer, and nothing
     * went wrong on the server.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientGone(AsyncRequestNotUsableException e) {
        // Nothing to send and nothing to record.
    }

    /** A request for a path that does not exist is a 404, not a crash. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResponseList<Object>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ResponseList<>("Not Found"));
    }

    /**
     * Anything that was never meant to reach the client. Before this existed
     * these only ever went to the console, which is exactly where nobody was
     * looking.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseList<Object>> handleUnexpectedException(
            Exception e,
            HttpServletRequest request) {

        log.severe("Unhandled exception on " + request.getMethod() + " " + request.getRequestURI() + ": " + e);
        errorLogService.recordBackendError(e, ErrorLogService.LEVEL_ERROR, request);

        // The message itself stays out of the response - it can carry SQL,
        // file paths and the like. The full detail lives in the error log.
        return ResponseEntity
                .internalServerError()
                .body(new ResponseList<>("Something went wrong. The error has been recorded."));
    }
}
