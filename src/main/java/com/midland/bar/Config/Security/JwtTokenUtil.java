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
    private final BranchAccess branchAccess;
    private final com.midland.bar.Uaa.Repository.PermissionRepository permissionRepository;



    public String generateToken(User user){
        String username = user.getUsername();
        // Session length is configurable in Settings > Config; the fallback
        // matches the 24 hours this used to be fixed at.
        Integer sessionHours = platformSettingService.current().getSessionHours();
        long expirationTime = 1000L * 60 * 60 * (sessionHours == null ? 24 : sessionHours);
        Boolean isRoot = user.getIsRoot();
        // Read, not dereferenced blind. A user whose branch is missing - never
        // set, or pointing at one that has since gone - used to throw here,
        // and login turned that into a 500 saying "Error in setting Token".
        // The branch claim is what narrows a screen to one branch; a token
        // without it opens nothing, which is the right answer for an account
        // in that state and a far better one than a crash.
        String branchUID = branchUidOf(user);
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
        // Main office (STAFF, DIRECTOR) can always look around the branch it
        // works in - MIDLAND or a customer's - so it gets every VIEW_*; its
        // roles alone give it nothing to read in POS. STAFF in a customer's
        // branch may only look: their own write permissions are dropped for
        // this session. ROOT passes every check anyway.
        boolean viewOnly = branchAccess.isSupportViewOnly(user);
        if (branchAccess.isMainOffice(user)) {
            java.util.Set<String> merged = new java.util.LinkedHashSet<>(viewOnly ? List.of() : permissions);
            permissionRepository.findAll().stream().map(Permission::getName)
                    .filter(n -> n != null && n.startsWith("VIEW_"))
                    .forEach(merged::add);
            permissions = new java.util.ArrayList<>(merged);
        }
        String userUID = user.getUid();


        return Jwts.builder()
                .setSubject(username)
                .claim("isRoot", isRoot)
                .claim("roles", roles)
                .claim("branchUID", branchUID)
                .claim("permissions", permissions)
                // STAFF in a customer's branch: the screens show "view only".
                .claim("viewOnly", viewOnly)
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

    /** Token kinds besides a user's own login (which carries no "type"). */
    public static final String TYPE_DEVICE = "DEVICE";
    public static final String TYPE_STAFF = "STAFF";

    /** The authority every staff-code session holds; what StaffSession looks for. */
    public static final String STAFF_SESSION_AUTHORITY = "STAFF_SELL_SESSION";

    /** What a staff session may do - Staff Sell and nothing else (see StaffSession for the paths). */
    public static final List<String> STAFF_PERMISSIONS = List.of("VIEW_SALES", "SAVE_SALES", "VIEW_SERVICE");

    private static final long DEVICE_TOKEN_MS = 365L * 24 * 60 * 60 * 1000;
    private static final long STAFF_TOKEN_MS = 12L * 60 * 60 * 1000;

    /**
     * A ticket saying "this device belongs to this branch", handed to a
     * device when a member of the branch signs in on it. Staff signing in
     * with their code present it, so the code is looked up in the right
     * branch - and only on devices the branch itself has set up.
     * Never accepted as a login: the filter ignores DEVICE tokens.
     */
    public String generateDeviceToken(String branchUID, String registeredBy) {
        return Jwts.builder()
                .setSubject("device")
                .claim("type", TYPE_DEVICE)
                .claim("branchUID", branchUID)
                .claim("registeredBy", registeredBy)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + DEVICE_TOKEN_MS))
                .signWith(SignatureAlgorithm.HS512, PRIVATE_KEY)
                .compact();
    }

    /** The branch a device ticket names, or null if it is not a valid, current device ticket. */
    public String deviceBranch(String deviceToken) {
        try {
            Claims claims = claims(deviceToken);
            if (!TYPE_DEVICE.equals(claims.get("type", String.class)))
                return null;
            return claims.get("branchUID", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** A staff member signed in with their code and PIN: Staff Sell, their own bills, nothing more. */
    public String generateStaffToken(com.midland.bar.Bar.Model.BarStaff staff, String branchUID, String fullName) {
        return Jwts.builder()
                .setSubject("STAFF-" + staff.getStaffCode())
                .claim("type", TYPE_STAFF)
                .claim("isRoot", false)
                .claim("roles", List.of("STAFF_SELLER"))
                .claim("permissions", STAFF_PERMISSIONS)
                .claim("branchUID", branchUID)
                .claim("staffUid", staff.getUid())
                .claim("staffCode", staff.getStaffCode())
                .claim("fullName", fullName)
                .claim("viewOnly", false)
                .claim("mustChangePassword", false)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + STAFF_TOKEN_MS))
                .signWith(SignatureAlgorithm.HS512, PRIVATE_KEY)
                .compact();
    }

    /** "DEVICE", "STAFF", or null for an ordinary user login. */
    public String tokenType(String token) {
        return claims(token).get("type", String.class);
    }

    public Claims claims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(PRIVATE_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private static String branchUidOf(User user) {
        try {
            return user.getBranch() == null ? null : user.getBranch().getUid();
        } catch (Exception e) {
            // An eager association pointing at a row that no longer exists.
            return null;
        }
    }

    public String extractBranchUID(String token){
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(PRIVATE_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("branchUID", String.class);
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
