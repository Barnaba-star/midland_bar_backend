package com.midland.bar.Setting.Controller;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Dto.BranchDTO;
import com.midland.bar.Setting.Dto.SubscriptionPaymentDTO;
import com.midland.bar.Setting.Dto.TableSizeDto;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Service.SettingService;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Projection.UserProjection;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/setting")
public class SettingController {

    private final SettingService service;
    private final UserRepository userRepository;

    public SettingController(SettingService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_TABLE_SIZE')")
    @GetMapping("/getTableSizes")
    public ResponseList<TableSizeDto> getTableSizes() {
        return service.getTableSizes();
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat() {

        try {
            User user = LoggerUser.getUser();

            user.setLastSeen(LocalDateTime.now());

            userRepository.save(user);

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "lastSeen", user.getLastSeen()
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("User Not Authenticated");
        }
    }


    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_USER')")
    @GetMapping("/findOnlineUsers")
    public ResponseList<UserProjection> findOnlineUsers(){
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(2);
        return new ResponseList<>(userRepository.findOnlineUsers(cutoffTime));
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_BRANCH')")
    @PostMapping("/saveBranchSubscription")
    public Response<Branch> saveBranchSubscription(@RequestBody BranchDTO branchDTO){
        return service.saveBranchSubscription(branchDTO);
    }

    // Any logged-in branch user can request a subscription payment for
    // their own branch - LoggerUser.getBranchUID() scopes it, the client
    // never gets to pick which branch pays.
    @PostMapping("/updateSubscription")
    public Response<Branch> updateSubscription(@Valid @RequestBody SubscriptionPaymentDTO subscriptionPaymentDTO){
        return service.updateSubscription(subscriptionPaymentDTO);
    }

}
