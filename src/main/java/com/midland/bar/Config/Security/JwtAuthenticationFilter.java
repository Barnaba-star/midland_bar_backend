package com.midland.bar.Config.Security;

import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                Optional<String> optional = Arrays.stream(cookies).filter(cookie -> cookie.getName().equals("bar_jwt_token")).map(Cookie::getValue).findFirst();
                token = optional.orElse(null);
            }
        }

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String username = jwtTokenUtil.extractUsername(token);
       // Boolean isRoot = jwtTokenUtil.isRoot(token);
        boolean isRoot = Boolean.TRUE.equals(jwtTokenUtil.isRoot(token));
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            User user = userRepository.findByUsernameForAuthentication(username);
            // The branch chosen at login - honoured only while it is still one of theirs.
            String branchUID = jwtTokenUtil.extractBranchUID(token);
            if (user != null && branchUID != null && user.getHomeBranch() != null
                    && !branchUID.equals(user.getHomeBranch().getUid())) {
                user.getWorkBranches().stream()
                        .filter(b -> branchUID.equals(b.getUid()))
                        .findFirst()
                        .ifPresent(user::setActiveBranch);
            }
            List<SimpleGrantedAuthority> authorities;
            if (isRoot) {
                authorities = List.of(new SimpleGrantedAuthority("ROOT"));
            } else {
                List<SimpleGrantedAuthority> roles = jwtTokenUtil.extractRoles(token);
                List<SimpleGrantedAuthority> permissions = jwtTokenUtil.extractPermission(token);
                authorities = mergeAuthorities(roles, permissions);
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(user, null, authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        // An account still on its texted password holds a token that opens
        // one door only. Hiding the rest in the UI is not enough - the token
        // is a bearer credential, and whoever read that SMS has it too.
        if (jwtTokenUtil.mustChangePassword(token) && !isPasswordChangePath(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            // A code, no message: the wording belongs to the screen, which
            // has it translated - the same arrangement as SUBSCRIPTION_EXPIRED.
            response.getWriter().write("{\"status\":403,\"code\":\"PASSWORD_CHANGE_REQUIRED\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * The doors left open: /authentication, which is permitAll anyway, so a
     * half-trusted token buys nothing extra there. Blocking the prefix instead
     * would trap them - a stale cookie carrying this flag would make even
     * logging in again impossible.
     */
    private static boolean isPasswordChangePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.contains("/authentication/");
    }

    public static List<SimpleGrantedAuthority> mergeAuthorities(List<SimpleGrantedAuthority> permissions, List<SimpleGrantedAuthority> roles){
        List<SimpleGrantedAuthority> combineAuthorities = new ArrayList<>();
        combineAuthorities.addAll(permissions);
        combineAuthorities.addAll(roles);
        return combineAuthorities;
    }
}

