package com.midland.bar.Config.Security;

import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Uaa.Model.Permission;
import com.midland.bar.Uaa.Model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import com.midland.bar.Setting.Service.PlatformSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtTokenUtil {
    @Value("${jwt.secret}")
    private String PRIVATE_KEY;

    private final PlatformSettingService platformSettingService;



    public String generateToken(User user){
        String username = user.getUsername();
        // Session length is configurable in Settings > Config; the fallback
        // matches the 24 hours this used to be fixed at.
        Integer sessionHours = platformSettingService.current().getSessionHours();
        long expirationTime = 1000L * 60 * 60 * (sessionHours == null ? 24 : sessionHours);
        Boolean isRoot = user.getIsRoot();
        String branchUID = user.getBranch().getUid();
        String fullName = String.format("%s             %s", user.getFirstName(), user.getLastName());
        String email = user.getEmail();
        // An account with no roles used to be handed "ROOT" here - the highest
        // authority in the system, given out precisely to the accounts nobody
        // had granted anything to. Stripping someone's last role promoted them.
        // No roles now means no roles; the real root account carries a ROOT role
        // of its own, and is independently recognised through the isRoot claim.
        List<Role> userRoles = user.getRoles() == null ? List.<Role>of() : user.getRoles();
        List<String> roles = userRoles.stream().map(Role::getName).toList();
        List<String> permissions = userRoles.stream()
                .flatMap(role -> role.getPermission().stream())
                .map(Permission::getName)
                .distinct()
                .toList();
        String userUID = user.getUid();


        return Jwts.builder()
                .setSubject(username)
                .claim("isRoot", isRoot)
                .claim("roles", roles)
                .claim("branchUID", branchUID)
                .claim("permissions", permissions)
                .claim("fullName", fullName)
                .claim("userUID", userUID)
                .claim("email", email)
                // Carried in the token so both sides read the same answer: the
                // filter decides what this token may reach, the login screen
                // decides where to send them. One source, no drift.
                .claim("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .setIssuedAt(new Date())
                .signWith(SignatureAlgorithm.HS512, PRIVATE_KEY)
                .compact();
    }

    public String extractUsername(String token){
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(PRIVATE_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    public Boolean isTokenExpired(String token){
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(PRIVATE_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getExpiration().before(new Date());
    }

    public Boolean isRoot(String token){
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(PRIVATE_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
       // return claims.get("isRoot", Boolean.class);
        Boolean isRoot = claims.get("isRoot", Boolean.class);
        return Boolean.TRUE.equals(isRoot);

    }

    public Boolean mustChangePassword(String token){
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(PRIVATE_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
        // Absent on tokens issued before this claim existed, which is the
        // same as "no, they don't".
        return Boolean.TRUE.equals(claims.get("mustChangePassword", Boolean.class));
    }

    public List<SimpleGrantedAuthority> extractRoles(String token){
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(PRIVATE_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
        List<String> roles = claims.get("roles", List.class);
        return roles.stream().map(SimpleGrantedAuthority::new).toList();
    }

    public List<SimpleGrantedAuthority> extractPermission(String token){
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(PRIVATE_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
        List<String> permissions =  claims.get("permissions", List.class);
        return permissions.stream().map(SimpleGrantedAuthority::new).toList();
    }

    public List<SimpleGrantedAuthority> extractActions(String token){
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(PRIVATE_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
        List<String> actions = claims.get("actions", List.class);
        return actions.stream().map(SimpleGrantedAuthority::new).toList();
    }


}
