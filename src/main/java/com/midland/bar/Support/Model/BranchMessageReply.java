package com.midland.bar.Support.Model;

import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One turn in the conversation, from either side. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "branch_message_replies", indexes = {
        @Index(name = "idx_branch_message_replies_message", columnList = "message_uid, sent_at")
})
public class BranchMessageReply extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_uid")
    private BranchMessage message;

    @Column(name = "body", length = 4000)
    private String body;

    @Column(name = "author_uid")
    private String authorUid;

    @Column(name = "author_name", length = 150)
    private String authorName;

    /**
     * Which side is speaking. Stored rather than worked out from the author's
     * branch, because that answer would change if the person later moved.
     */
    @Column(name = "from_branch")
    private Boolean fromBranch;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
