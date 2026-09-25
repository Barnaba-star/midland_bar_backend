package com.midland.bar.Config.Security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("authChecker")
public class AuthChecker {
    public Boolean hasPermissionOrRoot(String permission){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null)
            return false;
        boolean isRoot = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROOT"));
        boolean hasPermission = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(permission));
        return  isRoot || hasPermission;
    }
}
