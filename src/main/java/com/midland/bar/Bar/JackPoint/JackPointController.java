package com.midland.bar.Bar.JackPoint;

import com.midland.bar.Utils.Responses.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Jack Point: the waiters' game - set up and approved by the CEO, manager or accountant. */
@RestController
@RequestMapping("/bar/jackPoints")
@RequiredArgsConstructor
public class JackPointController {

    private final JackPointService jackPointService;

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_JACK_POINT')")
    @GetMapping
    public Response<Map<String, Object>> overview() {
        return jackPointService.overview();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_JACK_POINT')")
    @PostMapping
    public Response<JackPoint> save(@Valid @RequestBody JackPointDTO dto) {
        return jackPointService.save(dto);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_JACK_POINT')")
    @PostMapping("/{uid}/delete")
    public Response<Boolean> delete(@PathVariable String uid) {
        return jackPointService.delete(uid);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_JACK_POINT')")
    @PostMapping("/awards/{uid}/decide")
    public Response<JackPointAward> decide(@PathVariable String uid, @RequestBody AwardDecisionDTO dto) {
        return jackPointService.decide(uid, dto);
    }

    /**
     * Staff Sell: a staff member's own progress on the games (a staff session
     * only its own). POST so the code travels in the body, which the logs
     * mask, not in the query string.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @PostMapping("/mine")
    public Response<Map<String, Object>> mine(@RequestBody Map<String, String> body) {
        return jackPointService.mine(body == null ? null : body.get("staffCode"));
    }
}
