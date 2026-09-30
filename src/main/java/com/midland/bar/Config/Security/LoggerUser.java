package com.midland.bar.Config.Security;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Uaa.Model.Permission;
import com.midland.bar.Uaa.Model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
@Service
@RequiredArgsConstructor
public class LoggerUser {
    public static String getEmail(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null ||  ! authentication.isAuthenticated())
            throw new RuntimeException("Either User Not Authenticated or Null");
        Object object = authentication.getPrincipal();
        if(!(object instanceof User))
            throw new RuntimeException("Invalid User Authentication");
        return ((User) object).getUsername();
    }

    public static User getUser(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null ||  ! authentication.isAuthenticated())
            throw new RuntimeException("User Not Authenticated");
        Object object = authentication.getPrincipal();
        if(!(object instanceof  User))
            throw new RuntimeException("Invalid User Authentication");
        return ((User)object);
    }
    public static String getRoleUid(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || ! authentication.isAuthenticated())
            throw new RuntimeException("Invalid User Authentication");
        Object object = authentication.getPrincipal();
        if(!(object instanceof User user))
            throw new RuntimeException("Invalid User Authentication");
        Optional<String> role = user.getRoles().stream().map(Role::getUid).findFirst();
        if(role.isEmpty())
            throw new RuntimeException("Role Uid For Logged User Not Found");
        return role.get();
    }

    public List<Role> getRoles(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || ! authentication.isAuthenticated())
            throw new RuntimeException("Invalid User Authentication");
        Object object = authentication.getPrincipal();
        if(!(object instanceof User user))
            throw new RuntimeException("Invalid User Authentication");
        return user.getRoles();
    }



    public List<String> getPermissions(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || ! authentication.isAuthenticated())
            throw new RuntimeException("Invalid User Authentication");
        Object object = authentication.getPrincipal();
        if(!(object instanceof User user))
            throw new RuntimeException("Invalid User Authentication");
        return user.getRoles().stream().flatMap(role ->role.getPermission().stream().map(Permission::getName)).toList();
    }

    public static String getBranchUID() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User user)) {
            return null;
        }
        return user.getBranch() != null ? user.getBranch().getUid() : null;
    }

    /**
     * The branch as rows are stamped with it: a user with no branch writes
     * MAIN_OFFICE (see TenantEntity.prePersist), so reads must look there too.
     */
    public static String getBranchUIDOrMain() {
        String branchUID = getBranchUID();
        return branchUID == null ? "MAIN_OFFICE" : branchUID;
    }



}
