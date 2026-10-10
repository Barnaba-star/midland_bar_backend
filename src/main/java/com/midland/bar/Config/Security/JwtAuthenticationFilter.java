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
    private final BranchAccess branchAccess;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final com.midland.bar.Setting.Repository.BranchRepository branchRepository;
    private final com.midland.bar.Bar.Repository.BarStaffRepository barStaffRepository;

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

        // Tokens that are not a user's login. A bad or expired one falls
        // through to the user path below, which treats it as before.
        String type = null;
        try {
            type = jwtTokenUtil.tokenType(token);
        } catch (Exception ignored) {
        }
        if (JwtTokenUtil.TYPE_DEVICE.equals(type)) {
            // A device ticket only proves which branch a device belongs to.
            filterChain.doFilter(request, response);
            return;
        }
        if (JwtTokenUtil.TYPE_STAFF.equals(type)) {
            staffSession(token, request, response, filterChain);
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
                branchAccess.find(user, branchUID).ifPresent(user::setActiveBranch);
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

        // Their branch was blocked by the main office after they signed in:
        // turned away at once (the main office itself never is).
        if (!isRoot && !isPasswordChangePath(request)
                && SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof User signedIn
                && signedIn.getBranch() != null && signedIn.getBranch().isBlocked()
                && !"ROOT".equalsIgnoreCase(signedIn.getBranch().getBranchCode())
                && !branchAccess.isMainOffice(signedIn)) {
            blocked(response);
            return;
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
     * A staff member signed in with code + PIN. They have no user account, so
     * the principal is a stand-in User (never saved) carrying their name and
     * branch - what the Staff Sell services read through LoggerUser - and the
     * request may only go where StaffSession allows.
     */
    private void staffSession(String token, HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        if (!StaffSession.allows(request.getMethod(), request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":403,\"code\":\"STAFF_SESSION_SCOPE\"}");
            return;
        }
        io.jsonwebtoken.Claims claims = jwtTokenUtil.claims(token);
        String branchUID = claims.get("branchUID", String.class);
        String staffUid = claims.get("staffUid", String.class);
        String staffCode = claims.get("staffCode", String.class);
        var branch = branchUID == null ? null : branchRepository.findById(branchUID).orElse(null);
        if (branch != null && branch.isBlocked()) {
            blocked(response);
            return;
        }
        // Removed (or moved) since signing in: the token no longer counts.
        boolean stillHere = branch != null && staffUid != null
                && barStaffRepository.findBarStaffByUID(staffUid, branchUID).isPresent();
        if (stillHere && SecurityContextHolder.getContext().getAuthentication() == null) {
            User standIn = new User();
            standIn.setUid("staff-" + staffUid);
            standIn.setUsername(claims.getSubject());
            standIn.setFirstName(claims.get("fullName", String.class));
            standIn.setBranch(branch);
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            JwtTokenUtil.STAFF_PERMISSIONS.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
            authorities.add(new SimpleGrantedAuthority("STAFF_SELLER"));
            authorities.add(new SimpleGrantedAuthority(JwtTokenUtil.STAFF_SESSION_AUTHORITY));
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(standIn, null, authorities);
            auth.setDetails(new StaffSession.Info(staffUid, staffCode));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }

    private static void blocked(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":403,\"code\":\"BRANCH_BLOCKED\"}");
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

