package com.midland.bar.Monitoring.Filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * Keeps a copy of the request body so a failed request can be recorded with
 * what was actually posted. Without this the body is gone by the time the
 * exception handler runs - the controller has already consumed the stream.
 *
 * Outermost filter on purpose: the wrapper has to be in place before anything
 * downstream reads the body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestPayloadCachingFilter extends OncePerRequestFilter {

    /** Plenty for a JSON body; a bigger one is not worth holding in memory. */
    private static final int MAX_CACHED_BYTES = 16_384;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!shouldCache(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrapped =
                new ContentCachingRequestWrapper(request, MAX_CACHED_BYTES);
        filterChain.doFilter(wrapped, response);
    }

    private boolean shouldCache(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method)) {
            return false;
        }
        // File uploads would mean holding image bytes in memory for nothing.
        String contentType = request.getContentType();
        return contentType == null || !contentType.toLowerCase().startsWith("multipart/");
    }
}
