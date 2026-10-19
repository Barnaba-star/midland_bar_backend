package com.midland.bar.Bar;

import com.midland.bar.Utils.MoneyLock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two transactions of one branch take the money lock one after the other;
 * another branch is not held up. Real transactions on two threads, against
 * the local database; both roll back, nothing is written.
 */
@SpringBootTest
@Tag("local")
class MoneyLockTest {

    @Autowired MoneyLock moneyLock;
    @Autowired TransactionTemplate tx;

    @Test
    void theSecondWaitsForTheFirstOfTheSameBranchOnly() throws Exception {
        CountDownLatch firstHolds = new CountDownLatch(1);
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            moneyLock.hold("lock-test-branch");
            firstHolds.countDown();
            sleep(800);
            status.setRollbackOnly();
        }));
        assertTrue(firstHolds.await(10, TimeUnit.SECONDS));

        long otherBranch = timeToHold("lock-test-other-branch");
        long sameBranch = timeToHold("lock-test-branch");
        first.get(10, TimeUnit.SECONDS);

        assertTrue(otherBranch < 400, "another branch goes straight on: " + otherBranch + " ms");
        assertTrue(sameBranch >= 500, "the same branch waits for the first to finish: " + sameBranch + " ms");
    }

    @Test
    void holdingTwiceInOneTransactionDoesNotBlockItself() {
        long ms = timeToHold("lock-test-branch", 2);
        assertTrue(ms < 400, "re-entrant: " + ms + " ms");
    }

    private long timeToHold(String branch) {
        return timeToHold(branch, 1);
    }

    private long timeToHold(String branch, int times) {
        long[] took = new long[1];
        tx.executeWithoutResult(status -> {
            long t = System.nanoTime();
            for (int i = 0; i < times; i++)
                moneyLock.hold(branch);
            took[0] = (System.nanoTime() - t) / 1_000_000;
            status.setRollbackOnly();
        });
        return took[0];
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
