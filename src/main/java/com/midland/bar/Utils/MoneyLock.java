package com.midland.bar.Utils;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * One branch's money, written by one transaction at a time.
 *
 * The pots (income_expenses), the Manunuzi rows and the staff commission
 * days are read, added to in Java and written back. Two sales of the same
 * branch at the same moment both read 10,000, both write 10,000 + their own
 * share, and one share is lost. Every transaction that writes those rows
 * takes this lock before reading them, so the second waits for the first.
 *
 * A Postgres transaction-level advisory lock: released on commit or rollback,
 * never left behind. Take it at the very start of the action - before a bill
 * or a product is locked FOR UPDATE - so two actions never hold each other's
 * locks (deadlock). Taking it again in the same transaction costs nothing.
 */
@Component
@RequiredArgsConstructor
public class MoneyLock {

    private final EntityManager entityManager;

    public void hold(String branchUID) {
        if (branchUID == null || !TransactionSynchronizationManager.isActualTransactionActive())
            return;
        TxMemo.get("moneyLock:" + branchUID, () -> {
            entityManager.createNativeQuery("SELECT CAST(pg_advisory_xact_lock(hashtextextended(:k, 0)) AS text)")
                    .setParameter("k", "money:" + branchUID)
                    .getSingleResult();
            return Boolean.TRUE;
        });
    }
}
