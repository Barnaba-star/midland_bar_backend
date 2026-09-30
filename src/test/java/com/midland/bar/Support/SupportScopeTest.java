package com.midland.bar.Support;

import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Support.Dto.NewMessageDTO;
import com.midland.bar.Support.Model.Guidance;
import com.midland.bar.Support.Repository.GuidanceRepository;
import com.midland.bar.Support.Service.BranchMessageService;
import com.midland.bar.Support.Service.GuidanceService;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Admin is the main office's, across every branch; the branch side of it
 * (messages and guidance) must only ever reach the caller's own branch and
 * what has been published. Rolled back afterwards.
 */
@SpringBootTest
@Transactional
class SupportScopeTest {

    @Autowired UserRepository userRepository;
    @Autowired GuidanceService guidanceService;
    @Autowired GuidanceRepository guidanceRepository;
    @Autowired BranchMessageService branchMessageService;

    /** A made-up user in a branch of its own, holding exactly these authorities. */
    private void signInAs(String branchUid, String... authorities) {
        Branch branch = new Branch();
        branch.setUid(branchUid);
        branch.setBranchName(branchUid);
        User user = new User();
        user.setUsername(branchUid + "@test");
        user.setFirstName("Test");
        user.setBranch(branch);
        user.setRoles(List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private Guidance draftWithFile() {
        Guidance g = new Guidance();
        g.setTitle("Draft rule");
        g.setBody("Not out yet");
        g.setPublished(false);
        g.setPosition(0);
        g.setFilePath("/tmp/does-not-matter.pdf");
        g.setFileName("rule.pdf");
        return guidanceRepository.save(g);
    }

    @Test
    void aBranchCannotFetchADraftsFileButGuidanceManagersCan() {
        signInAs("BRANCH-A-TEST", "ROOT");
        Guidance draft = draftWithFile();

        signInAs("BRANCH-A-TEST", "SAVE_SALES");
        assertTrue(guidanceService.findForDownload(draft.getUid()).isEmpty(), "a draft is not handed to a branch");

        signInAs("MAIN-TEST", "MANAGE_GUIDANCE");
        assertTrue(guidanceService.findForDownload(draft.getUid()).isPresent(), "the guidance manager can open it");

        draft.setPublished(true);
        guidanceRepository.save(draft);
        signInAs("BRANCH-A-TEST", "SAVE_SALES");
        assertTrue(guidanceService.findForDownload(draft.getUid()).isPresent(), "once published, every branch can");
    }

    @Test
    void branchesOnlyReachTheirOwnThreadsAndAnsweringTakesTheReplyPermission() {
        // Branch A raises a thread - pinned to its own branch.
        User root = userRepository.findByUsernameForAuthentication("root@root.com");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(root, null, List.of()));
        NewMessageDTO dto = new NewMessageDTO();
        dto.setCategory("QUESTION");
        dto.setBody("How do we close the day?");
        String uid = branchMessageService.raise(dto).getData().getUid();

        // Another branch: cannot open it, answer it, or see it in its own list.
        signInAs("BRANCH-B-TEST", "SAVE_SALES");
        assertEquals("NOT_ALLOWED", branchMessageService.findMessage(uid).getMessage());
        assertEquals("NOT_ALLOWED", branchMessageService.reply(uid, "hi").getMessage());
        assertTrue(branchMessageService.findMyMessages().getData().stream().noneMatch(m -> m.getUid().equals(uid)));

        // The main office with only the right to read: may read, may not answer.
        signInAs("MAIN-TEST", "VIEW_BRANCH_MESSAGE");
        assertNotNull(branchMessageService.findMessage(uid).getData());
        assertEquals("NOT_ALLOWED", branchMessageService.reply(uid, "we will look").getMessage());

        // With the right to answer, it goes through as the platform's answer.
        signInAs("MAIN-TEST", "VIEW_BRANCH_MESSAGE", "REPLY_BRANCH_MESSAGE");
        var answer = branchMessageService.reply(uid, "Use the day report.");
        assertNotNull(answer.getData(), answer.getMessage());
        assertFalse(answer.getData().isFromBranch());
    }
}
