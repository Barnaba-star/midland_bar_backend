package com.midland.bar.Setting.Controller;

import com.midland.bar.Setting.Model.Region;
import com.midland.bar.Setting.Service.RegionService;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/region")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    /**
     * Open to anyone who can register a branch - the branch form needs the
     * list to offer a choice, not just the screen that edits it.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_BRANCH')")
    @GetMapping("/findRegions")
    public ResponseList<Region> findRegions() {
        return regionService.findRegions();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_REGION')")
    @PostMapping("/saveRegion")
    public Response<Region> saveRegion(@RequestBody Region region) {
        return regionService.saveRegion(region);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('DELETE_REGION')")
    @PostMapping("/deleteRegion/{uid}")
    public Response<Region> deleteRegion(@PathVariable String uid) {
        return regionService.deleteRegion(uid);
    }
}
