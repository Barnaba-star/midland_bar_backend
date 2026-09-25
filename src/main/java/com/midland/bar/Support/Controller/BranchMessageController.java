package com.midland.bar.Support.Controller;

import com.midland.bar.Support.Dto.BranchMessageDTO;
import com.midland.bar.Support.Dto.BranchMessageReplyDTO;
import com.midland.bar.Support.Dto.NewMessageDTO;
import com.midland.bar.Support.Dto.ReplyDTO;
import com.midland.bar.Support.Service.BranchMessageService;
import com.midland.bar.Utils.PageableParam;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import com.midland.bar.Utils.Responses.ResponsePage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/branchMessage")
@RequiredArgsConstructor
public class BranchMessageController {

    private final BranchMessageService branchMessageService;

    // ============================================================
    // THE BRANCH SIDE - open to anyone signed in
    //
    // No @PreAuthorize on purpose: a cashier raising a complaint is the
    // whole point, and they hold no settings permission. The service pins
    // every thread to the caller's own branch, so being able to call this
    // buys nobody a view of anyone else's.
    // ============================================================

    @PostMapping("/raise")
    public Response<BranchMessageDTO> raise(@RequestBody NewMessageDTO dto) {
        return branchMessageService.raise(dto);
    }

    @GetMapping("/findMyMessages")
    public ResponseList<BranchMessageDTO> findMyMessages() {
        return branchMessageService.findMyMessages();
    }

    /** One thread. The service decides whether this caller may see it. */
    @GetMapping("/findMessage/{uid}")
    public Response<BranchMessageDTO> findMessage(@PathVariable String uid) {
        return branchMessageService.findMessage(uid);
    }

    /** Either side answers through the same door; the service works out which. */
    @PostMapping("/reply/{uid}")
    public Response<BranchMessageReplyDTO> reply(@PathVariable String uid, @RequestBody ReplyDTO dto) {
        return branchMessageService.reply(uid, dto == null ? null : dto.getBody());
    }

    // ============================================================
    // THE ADMIN SIDE
    // ============================================================

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_BRANCH_MESSAGE')")
    @PostMapping("/findMessages")
    public ResponsePage<BranchMessageDTO> findMessages(@RequestBody PageableParam pageableParam,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String branchUid) {
        return branchMessageService.findMessages(pageableParam, status, branchUid);
    }

    /** For the badge on the menu: how many are still waiting on an answer. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_BRANCH_MESSAGE')")
    @GetMapping("/countAwaitingReply")
    public Response<Long> countAwaitingReply() {
        return branchMessageService.countAwaitingReply();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('REPLY_BRANCH_MESSAGE')")
    @PostMapping("/setStatus/{uid}/{status}")
    public Response<String> setStatus(@PathVariable String uid, @PathVariable String status) {
        return branchMessageService.setStatus(uid, status);
    }
}
