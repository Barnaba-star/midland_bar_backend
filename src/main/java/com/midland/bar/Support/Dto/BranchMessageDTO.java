package com.midland.bar.Support.Dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** A thread as either side reads it. */
@Getter
@Setter
@NoArgsConstructor
public class BranchMessageDTO {

    private String uid;
    private String branchUid;
    private String branchName;
    private String branchCode;
    private String category;
    private String subject;
    private String body;
    private String status;
    private String raisedByName;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private boolean awaitingReply;
    private int replyCount;

    /** Only filled when one thread is opened, not on the list. */
    private List<BranchMessageReplyDTO> replies;
}
