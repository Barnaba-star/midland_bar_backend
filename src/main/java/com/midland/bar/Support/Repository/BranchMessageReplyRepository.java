package com.midland.bar.Support.Repository;

import com.midland.bar.Support.Model.BranchMessageReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BranchMessageReplyRepository extends JpaRepository<BranchMessageReply, String> {

    @Query("SELECT r FROM BranchMessageReply r WHERE r.message.uid = :messageUid ORDER BY r.sentAt ASC")
    List<BranchMessageReply> findForMessage(@Param("messageUid") String messageUid);
}
