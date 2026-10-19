package com.midland.bar.Utils;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * A value read once per transaction. Adding a bill's lines prices, splits and
 * books every line on its own, and each line used to read the same branch
 * rows again (the week's pots, the Other list, the offers) - a 5-line sale
 * read the pots 55 times. Within one transaction those rows do not change
 * underneath us, so the first read is kept until the transaction ends.
 *
 * Outside a transaction it is a plain read every time. Code that changes
 * what a key holds in the same transaction calls {@link #forget}.
 */
public final class TxMemo {

    private static final String PREFIX = "txMemo:";

    private TxMemo() {}

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> load) {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            return load.get();
        String k = PREFIX + key;
        Object held = TransactionSynchronizationManager.getResource(k);
        if (held != null)
            return (T) held;
        T value = load.get();
        if (value == null)
            return null;
        TransactionSynchronizationManager.bindResource(k, value);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(k);
            }
        });
        return value;
    }

    /** Drops every key starting with this, e.g. after saving an offer. */
    public static void forget(String keyPrefix) {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            return;
        for (Object k : new ArrayList<>(TransactionSynchronizationManager.getResourceMap().keySet()))
            if (k instanceof String s && s.startsWith(PREFIX + keyPrefix))
                TransactionSynchronizationManager.unbindResourceIfPossible(k);
    }
}
