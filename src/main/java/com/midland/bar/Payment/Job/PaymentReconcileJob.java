package com.midland.bar.Payment.Job;

import com.midland.bar.Payment.Service.PaymentReconcileService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Picks up subscription payments whose webhook never arrived - the tunnel
 * was down, the laptop was asleep, Snippe gave up retrying. Without it a
 * customer who has paid stays PENDING until someone presses reconcile.
 * The webhook still does the job instantly when it lands; this only closes
 * the gap when it doesn't.
 */
@Component
@RequiredArgsConstructor
public class PaymentReconcileJob {

    private final PaymentReconcileService paymentReconcileService;

    @Scheduled(fixedDelay = 60000, initialDelay = 30000) // every minute, first run 30s after startup
    public void reconcilePendingPayments() {
        paymentReconcileService.reconcilePending();
    }
}
