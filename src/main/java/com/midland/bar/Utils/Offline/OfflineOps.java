package com.midland.bar.Utils.Offline;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Applying a replayed operation once. Call claim() inside the operation's own
 * transaction: if the operation fails the claim rolls back with it and a
 * resend tries again; if it succeeded before, the resend gets the earlier
 * result's reference back instead of a second sale.
 */
@Service
@RequiredArgsConstructor
public class OfflineOps {

    private final OfflineOpRepository repository;

    /** The entity uid a repeat of this op touched, if this op was applied already. */
    public Optional<String> alreadyApplied() {
        String opId = OfflineContext.opId();
        if (opId == null)
            return Optional.empty();
        return repository.findById(opId).map(op -> op.getRefUid() == null ? "" : op.getRefUid());
    }

    /** Marks this op applied (no-op for ordinary requests). */
    public void claim(String kind, String refUid) {
        String opId = OfflineContext.opId();
        if (opId == null)
            return;
        OfflineOp op = new OfflineOp();
        op.setUid(opId);
        op.setKind(kind);
        op.setRefUid(refUid);
        op.setAppliedAt(LocalDateTime.now());
        repository.save(op);
    }
}
