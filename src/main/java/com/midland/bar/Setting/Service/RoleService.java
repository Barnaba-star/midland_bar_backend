package com.midland.bar.Setting.Service;

import com.midland.bar.Config.Security.MainOfficeRoles;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Dto.AssignPermissionToRoleDto;
import com.midland.bar.Setting.Dto.RoleDto;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Setting.Projection.BranchProjection;
import com.midland.bar.Setting.Repository.RoleRepository;
import com.midland.bar.Uaa.Model.Permission;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.PermissionRepository;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import com.midland.bar.Utils.Responses.ResponsePage;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Log
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final JdbcTemplate jdbcTemplate;
    public Response<Role> saveRole(RoleDto roleDto){
        log.info(LoggerUser.getEmail() + "is saving Role");
        log.info( "PayLoad Data to be saved" + roleDto);
        if(roleDto == null)
            return new Response<>("Provide Data For Role");
        Role role = null;
        if(roleDto.getUid() != null){
            Optional<Role> optionalRole = roleRepository.findById(roleDto.getUid());
            if(optionalRole.isEmpty()){
                return  new Response<>("Role Not Found");
            }
            role = optionalRole.get();
            role.update();
        }else{
            role = new Role();
        }
        role.setName(roleDto.getName());
        role.setCode(generateRoleCode(roleDto.getName(), roleDto.getCategory()));
        role.setStatus("ACTIVE");
        role.setCategory(roleDto.getCategory());
        role.setDescription(roleDto.getDescription());
        try{
            return new Response<>(roleRepository.save(role));
        }
        catch (Exception e){
            e.printStackTrace();
            return new Response<>("Error Occurred in saving Role");
        }
    }
    public String generateRoleCode(String name, String category) {

        if (name == null || name.trim().isEmpty()
                || category == null || category.trim().isEmpty()) {
            return null;
        }

        String formattedName = name
                .trim()
                .toUpperCase()
                .replaceAll("\\s+", "_");

        String formattedCategory = category
                .trim()
                .toUpperCase()
                .replaceAll("\\s+", "_");

        return "ROLE_" + formattedCategory + "_" + formattedName;
    }


    public Response<Role> findRoleByUID(String roleUID){
        log.info(LoggerUser.getEmail() + "is accessing Role");
        if(roleUID == null)
            return new Response<>("Role REF UID is Required");
        Optional<Role> optionalRole = roleRepository.findById(roleUID);
        return optionalRole.map(Response::new).orElseGet(()->new Response<>("Role Not Found"));
    }
    public Response<Role> deleteRole(String roleUID){
        log.info(LoggerUser.getEmail() + "is Deleting Role");
        if(roleUID == null)
            return new Response<>("Role Ref UID is Required");
        Optional<Role> optionalRole = roleRepository.findById(roleUID);
        if(optionalRole.isEmpty())
            return new Response<>("Role Not Found");
        jdbcTemplate.update("DELETE FROM user_roles WHERE role_uid = ?", roleUID);
        jdbcTemplate.update("DELETE FROM role_permission WHERE role_uid = ?", roleUID);
        optionalRole.get().getPermission().clear();
        roleRepository.delete(optionalRole.get());
        return new Response<>(optionalRole.get());
    }
    public ResponseList<Role> findRoles(){
        log.info(LoggerUser.getEmail() + "is Accessing Roles");
        if (!seesAllRoles()) {
            return new ResponseList<>(roleRepository.findByCodeIn(MainOfficeRoles.visibleRoleCodes(LoggerUser.getUser())));
        }
        return new ResponseList<>(roleRepository.findAll());
    }

    public ResponsePage<Role> findRolePage(int page, int size){
        log.info(LoggerUser.getEmail() + "is accessing Role");
        Pageable pageable = PageRequest.of(page, size);
        if (!seesAllRoles()) {
            return new ResponsePage<>(roleRepository.findRolePageByCodes(MainOfficeRoles.visibleRoleCodes(LoggerUser.getUser()), pageable));
        }
        return new ResponsePage<>(roleRepository.findRolePage(pageable));
    }
    // ROOT and DIRECTOR manage every role; STAFF only the branch ones, ADMIN
    // those plus STAFF (MainOfficeRoles).
    private boolean seesAllRoles() {
        return MainOfficeRoles.seesAllRoles(LoggerUser.getUser());
    }

    public ResponseList<Permission> findPermissionsByRole(String roleUID){
        if(roleUID == null)
            return new ResponseList<>("Provide Role Module");
        return new ResponseList<>(roleRepository.findPermissionByRole(roleUID));
    }
    public ResponseList<Role> findRoleAndPermission(String roleUID){
        if(roleUID == null)
            return new ResponseList<>("Provide Role Ref UID");
        return new ResponseList<>(roleRepository.findRoleAndPermission(roleUID));
    }
    public Response<Role> assignPermissionToRole(AssignPermissionToRoleDto assignPermissionToRoleDto) {
        log.info(LoggerUser.getEmail() + " is saving Roles");
        if (assignPermissionToRoleDto == null)
            return new Response<>("Provide Data");
        Optional<Role> optionalRole = roleRepository.findById(assignPermissionToRoleDto.getRoleUID());
        if (optionalRole.isEmpty())
            return new Response<>("Role Not Found");
        Role role = optionalRole.get();
        User caller = LoggerUser.getUser();
        if (!MainOfficeRoles.canSee(caller, role))
            return new Response<>("Role Not Allowed");
        if (MainOfficeRoles.isAdmin(caller) && !onlyHeldByCaller(caller, role, assignPermissionToRoleDto.getPermissions()))
            return new Response<>("Permission Not Allowed");
        List<Permission> existingPermissions = role.getPermission();
        if (existingPermissions == null) {
            existingPermissions = new ArrayList<>();
        }
        existingPermissions.removeIf(p -> !assignPermissionToRoleDto.getPermissions().contains(p));
        for (Permission p : assignPermissionToRoleDto.getPermissions()) {
            if (!existingPermissions.contains(p)) {
                existingPermissions.add(p);
            }
        }
        role.setPermission(existingPermissions);
        return new Response<>(roleRepository.save(role));
    }
    /**
     * ADMIN may only add to a role what its own roles hold (reading aside), so
     * it cannot give a branch role a power - deleting branches, say - it was
     * never given itself. Permissions the role already has may stay.
     */
    private boolean onlyHeldByCaller(User caller, Role role, List<Permission> wanted) {
        java.util.Set<String> held = new java.util.HashSet<>();
        if (caller.getRoles() != null)
            caller.getRoles().forEach(r -> r.getPermission().forEach(p -> held.add(p.getName())));
        java.util.Set<String> already = new java.util.HashSet<>();
        if (role.getPermission() != null)
            role.getPermission().forEach(p -> already.add(p.getName()));
        for (Permission p : wanted == null ? List.<Permission>of() : wanted) {
            Permission full = p.getName() != null ? p : permissionRepository.findById(p.getUid()).orElse(null);
            if (full == null || full.getName() == null)
                return false;
            String name = full.getName();
            if (!already.contains(name) && !held.contains(name) && !name.startsWith("VIEW_"))
                return false;
        }
        return true;
    }

    public ResponseList<String> findDistinctModules(){
        log.info(LoggerUser.getEmail() + "is accessing Modules");
        return new ResponseList<>(permissionRepository.findDistinctModules());
    }

    public ResponseList<Permission> findPermissionByModuleName(String moduleName){
        log.info(LoggerUser.getEmail() + "is accessing the Permission");
        if(moduleName == null)
            return new ResponseList<>("Provide Module name");
        return new ResponseList<>(permissionRepository.findPermissionByModuleName(moduleName));
    }
    public ResponseList<BranchProjection> findRoleByBranch(){
        log.info(LoggerUser.getEmail() + "Is Accessing Role");
        return new ResponseList<>(roleRepository.findRoleByBranch());
    }
}
