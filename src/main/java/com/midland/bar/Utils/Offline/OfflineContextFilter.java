package com.midland.bar.Utils.Offline;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Reads the offline-replay headers into OfflineContext for the length of the request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OfflineContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        OfflineContext.set(request.getHeader("X-Offline"), request.getHeader("X-Op-Id"), request.getHeader("X-Client-Time"));
        try {
            chain.doFilter(request, response);
        } finally {
            OfflineContext.clear();
        }
    }
}
