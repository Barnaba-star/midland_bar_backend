package com.midland.bar.Support.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One turn in the conversation. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BranchMessageReplyDTO {
    private String uid;
    private String body;
    private String authorName;
    private boolean fromBranch;
    private LocalDateTime sentAt;
}
