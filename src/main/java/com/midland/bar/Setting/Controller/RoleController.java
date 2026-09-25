package com.midland.bar.Setting.Controller;

import com.midland.bar.Setting.Dto.AssignPermissionToRoleDto;
import com.midland.bar.Setting.Dto.RoleDto;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Setting.Projection.BranchProjection;
import com.midland.bar.Setting.Service.RoleService;
import com.midland.bar.Uaa.Model.Permission;
import com.midland.bar.Utils.PageableParam;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import com.midland.bar.Utils.Responses.ResponsePage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;


    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_ROLE')")
    @PostMapping("/saveRole")
    public Response<Role> saveRole(@Valid  @RequestBody RoleDto roleDto){
        return roleService.saveRole(roleDto);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_ROLE')")
    @GetMapping("/findRoleByUID/{roleUID}")
    public Response<Role> findRoleByUID(@PathVariable String roleUID){
        return roleService.findRoleByUID(roleUID);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('DELETE_ROLE')")
    @PostMapping("/deleteRole/{roleUID}")
    public Response<Role> deleteRole(@PathVariable String roleUID){
        return roleService.deleteRole(roleUID);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_ROLE')")
    @GetMapping("/findRoles")
    public ResponseList<Role> findRoles(){
        return roleService.findRoles();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_ROLE')")
    @PostMapping("/findRolePage")
    public ResponsePage<Role> findRolePage(@RequestBody PageableParam pageableParam){
        return roleService.findRolePage(pageableParam.getPage(), pageableParam.getSize());
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_PERMISSION')")
    @GetMapping("/findPermissionsByRole/{role}")
    public ResponseList<Permission> findPermissionsByRole(@PathVariable String role){
        return roleService.findPermissionsByRole(role);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_PERMISSION')")
    @GetMapping("/findRoleAndPermission/{roleUID}")
    public ResponseList<Role> findRoleAndPermission(@PathVariable String roleUID){
        return roleService.findRoleAndPermission(roleUID);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_PERMISSION')")
    @PostMapping("/assignPermissionToRole")
    public Response<Role> assignPermissionToRole(@RequestBody AssignPermissionToRoleDto assignPermissionToRoleDto){
        return roleService.assignPermissionToRole(assignPermissionToRoleDto);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_PERMISSION')")
    @GetMapping("/findDistinctModules")
    public ResponseList<String> findDistinctModules(){
        return roleService.findDistinctModules();
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_PERMISSION')")
    @GetMapping("/findPermissionByModuleName/{moduleName}")
    public ResponseList<Permission> findPermissionByModuleName(@PathVariable String moduleName){
        return roleService.findPermissionByModuleName(moduleName);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_ROLE')")
    @GetMapping("/findRoleByBranch")
    public ResponseList<BranchProjection> findRoleByBranch(){
        return roleService.findRoleByBranch();
    }
}
