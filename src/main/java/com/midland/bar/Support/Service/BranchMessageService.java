package com.midland.bar.Support.Service;

import com.midland.bar.Config.Security.AuthChecker;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Support.Dto.BranchMessageDTO;
import com.midland.bar.Support.Dto.BranchMessageReplyDTO;
import com.midland.bar.Support.Dto.NewMessageDTO;
import com.midland.bar.Support.Model.BranchMessage;
import com.midland.bar.Support.Model.BranchMessageReply;
import com.midland.bar.Support.Repository.BranchMessageReplyRepository;
import com.midland.bar.Support.Repository.BranchMessageRepository;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Utils.PageableParam;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import com.midland.bar.Utils.Responses.ResponsePage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The conversation between a branch and the people running the platform.
 *
 * A branch only ever sees its own threads, and that is enforced here rather
 * than by what the screen asks for: the branch comes from the caller's own
 * record, never from the request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchMessageService {

    private static final Set<String> CATEGORIES =
            Set.of(BranchMessage.COMMENT, BranchMessage.QUESTION, BranchMessage.COMPLAINT);

    private static final Set<String> STATUSES =
            Set.of(BranchMessage.NEW, BranchMessage.IN_PROGRESS, BranchMessage.CLOSED);

    private static final int MAX_BODY = 4000;
    private static final int MAX_SUBJECT = 200;

    private final BranchMessageRepository messageRepository;
    private final BranchMessageReplyRepository replyRepository;
    private final AuthChecker authChecker;

    // ============================================================
    // THE BRANCH SIDE
    // ============================================================

    public Response<BranchMessageDTO> raise(NewMessageDTO dto) {
        User caller = LoggerUser.getUser();
        log.info(caller.getUsername() + " is raising a branch message");

        if (dto == null || dto.getBody() == null || dto.getBody().isBlank()) {
            return new Response<>("EMPTY");
        }
        if (dto.getBody().length() > MAX_BODY) {
            return new Response<>("TOO_LONG");
        }

        Branch branch = branchOf(caller);
        if (branch == null) {
            // Every thread belongs to a branch; without one there is nothing
            // for an admin to answer about.
            return new Response<>("NO_BRANCH");
        }

        String category = dto.getCategory() == null ? "" : dto.getCategory().trim().toUpperCase();
        if (!CATEGORIES.contains(category)) {
            category = BranchMessage.COMMENT;
        }

        BranchMessage message = new BranchMessage();
        message.setBranch(branch);
        message.setBranchName(branch.getBranchName());
        message.setCategory(category);
        message.setSubject(trim(dto.getSubject(), MAX_SUBJECT));
        message.setBody(dto.getBody().trim());
        message.setStatus(BranchMessage.NEW);
        message.setRaisedByUid(caller.getUid());
        message.setRaisedByName(displayName(caller));
        message.setLastActivityAt(LocalDateTime.now());
        message.setAwaitingReply(true);

        return new Response<>(toDTO(messageRepository.save(message), false));
    }

    /** This branch's own threads. The branch is the caller's, not the request's. */
    public ResponseList<BranchMessageDTO> findMyMessages() {
        User caller = LoggerUser.getUser();
        Branch branch = branchOf(caller);
        if (branch == null) {
            return new ResponseList<>(new ArrayList<>());
        }
        List<BranchMessageDTO> rows = new ArrayList<>();
        for (BranchMessage message : messageRepository.findForBranch(branch.getUid())) {
            rows.add(toDTO(message, false));
        }
        return new ResponseList<>(rows);
    }

    // ============================================================
    // THE ADMIN SIDE
    // ============================================================

    public ResponsePage<BranchMessageDTO> findMessages(PageableParam pageableParam, String status, String branchUid) {
        log.info(LoggerUser.getEmail() + " is reading branch messages");

        String wantedStatus = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? null
                : status.trim().toUpperCase();

        String search = pageableParam.getSearchParam() == null || pageableParam.getSearchParam().isBlank()
                ? null
                : pageableParam.getSearchParam().trim().toLowerCase();

        Page<BranchMessage> page = messageRepository.findMessages(
                wantedStatus,
                branchUid == null || branchUid.isBlank() ? null : branchUid,
                search,
                // The query sets its own order, so the pageable must not.
                pageableParam.pageable(false));

        return new ResponsePage<>(page.map(message -> toDTO(message, false)));
    }

    /** How many threads are still waiting on us, for the menu badge. */
    public Response<Long> countAwaitingReply() {
        Long count = messageRepository.countAwaitingReply();
        Response<Long> response = new Response<>();
        response.setData(count == null ? 0L : count);
        response.setStatus(com.midland.bar.Utils.Responses.ResponseStatus.SUCCESS);
        return response;
    }

    // ============================================================
    // BOTH SIDES
    // ============================================================

    /**
     * One thread with its replies. A branch may only open its own; an admin
     * needs the permission. Anyone else is told nothing beyond "no".
     */
    public Response<BranchMessageDTO> findMessage(String uid) {
        Optional<BranchMessage> optional = messageRepository.findById(uid);
        if (optional.isEmpty()) {
            return new Response<>("NOT_FOUND");
        }
        BranchMessage message = optional.get();
        if (!mayRead(message)) {
            return new Response<>("NOT_ALLOWED");
        }
        return new Response<>(toDTO(message, true));
    }

    @Transactional
    public Response<BranchMessageReplyDTO> reply(String uid, String body) {
        User caller = LoggerUser.getUser();

        if (body == null || body.isBlank()) {
            return new Response<>("EMPTY");
        }
        if (body.length() > MAX_BODY) {
            return new Response<>("TOO_LONG");
        }

        Optional<BranchMessage> optional = messageRepository.findById(uid);
        if (optional.isEmpty()) {
            return new Response<>("NOT_FOUND");
        }
        BranchMessage message = optional.get();
        if (!mayRead(message)) {
            return new Response<>("NOT_ALLOWED");
        }

        boolean fromBranch = isOwnBranch(message, caller);
        // Reading another branch's thread is VIEW_BRANCH_MESSAGE; answering
        // for the platform is REPLY_BRANCH_MESSAGE, as setStatus asks.
        if (!fromBranch && !authChecker.hasPermissionOrRoot("REPLY_BRANCH_MESSAGE")) {
            return new Response<>("NOT_ALLOWED");
        }

        BranchMessageReply reply = new BranchMessageReply();
        reply.setMessage(message);
        reply.setBody(body.trim());
        reply.setAuthorUid(caller.getUid());
        reply.setAuthorName(displayName(caller));
        reply.setFromBranch(fromBranch);
        reply.setSentAt(LocalDateTime.now());
        BranchMessageReply saved = replyRepository.save(reply);

        message.setLastActivityAt(reply.getSentAt());
        // Whoever spoke last decides whether this is still waiting on us.
        message.setAwaitingReply(fromBranch);
        // An answer moves a new thread along on its own; nobody should have
        // to remember to also mark it. A closed one stays closed until
        // somebody reopens it deliberately.
        if (BranchMessage.NEW.equals(message.getStatus()) && !fromBranch) {
            message.setStatus(BranchMessage.IN_PROGRESS);
        }
        messageRepository.save(message);

        return new Response<>(toReplyDTO(saved));
    }

    /** Only the admin side moves a thread's status. */
    public Response<String> setStatus(String uid, String status) {
        if (!authChecker.hasPermissionOrRoot("REPLY_BRANCH_MESSAGE")) {
            return code("NOT_ALLOWED");
        }
        String wanted = status == null ? "" : status.trim().toUpperCase();
        if (!STATUSES.contains(wanted)) {
            return code("UNKNOWN_STATUS");
        }
        Optional<BranchMessage> optional = messageRepository.findById(uid);
        if (optional.isEmpty()) {
            return code("NOT_FOUND");
        }
        BranchMessage message = optional.get();
        message.setStatus(wanted);
        if (BranchMessage.CLOSED.equals(wanted)) {
            // A closed thread is not waiting on anybody.
            message.setAwaitingReply(false);
        }
        message.setLastActivityAt(LocalDateTime.now());
        messageRepository.save(message);
        return code("SAVED");
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private boolean mayRead(BranchMessage message) {
        User caller = LoggerUser.getUser();
        return isOwnBranch(message, caller) || authChecker.hasPermissionOrRoot("VIEW_BRANCH_MESSAGE");
    }

    private boolean isOwnBranch(BranchMessage message, User caller) {
        Branch branch = branchOf(caller);
        if (branch == null) {
            return false;
        }
        try {
            return message.getBranch() != null && branch.getUid().equals(message.getBranch().getUid());
        } catch (Exception e) {
            // The branch reference can dangle; a thread nobody can claim is
            // not anybody's own.
            return false;
        }
    }

    /**
     * Guarded: branch is an eager association and some rows point at a branch
     * that no longer exists, there being no foreign key to have stopped it.
     */
    private static Branch branchOf(User user) {
        try {
            return user.getBranch();
        } catch (Exception e) {
            return null;
        }
    }

    private BranchMessageDTO toDTO(BranchMessage message, boolean withReplies) {
        BranchMessageDTO dto = new BranchMessageDTO();
        dto.setUid(message.getUid());
        dto.setBranchName(message.getBranchName());
        try {
            if (message.getBranch() != null) {
                dto.setBranchUid(message.getBranch().getUid());
                dto.setBranchCode(message.getBranch().getBranchCode());
            }
        } catch (Exception e) {
            // The stored branchName still names it.
        }
        dto.setCategory(message.getCategory());
        dto.setSubject(message.getSubject());
        dto.setBody(message.getBody());
        dto.setStatus(message.getStatus());
        dto.setRaisedByName(message.getRaisedByName());
        dto.setCreatedAt(message.getLastActivityAt());
        dto.setLastActivityAt(message.getLastActivityAt());
        dto.setAwaitingReply(Boolean.TRUE.equals(message.getAwaitingReply()));

        List<BranchMessageReply> replies = replyRepository.findForMessage(message.getUid());
        dto.setReplyCount(replies.size());
        if (withReplies) {
            List<BranchMessageReplyDTO> rows = new ArrayList<>();
            for (BranchMessageReply reply : replies) {
                rows.add(toReplyDTO(reply));
            }
            dto.setReplies(rows);
        }
        return dto;
    }

    private static BranchMessageReplyDTO toReplyDTO(BranchMessageReply reply) {
        return new BranchMessageReplyDTO(
                reply.getUid(),
                reply.getBody(),
                reply.getAuthorName(),
                Boolean.TRUE.equals(reply.getFromBranch()),
                reply.getSentAt());
    }

    private static String displayName(User user) {
        String name = String.format("%s %s",
                user.getFirstName() == null ? "" : user.getFirstName(),
                user.getLastName() == null ? "" : user.getLastName()).trim();
        return name.isBlank() ? user.getUsername() : name;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** Response<String> picks the message constructor on its own - see UserService. */
    private static Response<String> code(String value) {
        Response<String> response = new Response<>();
        response.setData(value);
        response.setStatus(com.midland.bar.Utils.Responses.ResponseStatus.SUCCESS);
        return response;
    }
}
