package com.midland.bar.Bar.Config;

import com.midland.bar.Bar.Supplier.*;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Midland Solutions' PO-001 and PO-002 (2026-10-19) were received and paid
 * before deliveries used Manunuzi, so neither report showed them. The owner
 * asked (2026-10-19) for them in that day's Manunuzi: each product's
 * delivery is recorded as it would be now - its savings used, the rest
 * shown as beyond Manunuzi - and as already paid, since the supplier was
 * paid in full that morning (that payment is already on the CEO's
 * cash-up). Idempotent: a delivery that has its items is left alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierManunuziBackfill implements ApplicationRunner {

    private static final String BRANCH = "bd6f9f1f-cba2-42e0-8291-d34764db6986";
    private static final List<String> ORDERS = List.of("73fb3b8c-3db5-4fa2-b6e9-a369ed227fef", "7a5c2e9a-f5c7-40e2-a986-e7424d358b8d");

    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierEntryRepository entryRepository;
    private final SupplierDeliveryItemRepository itemRepository;
    private final ManunuziService manunuziService;
    private final UserRepository userRepository;
    private final TransactionTemplate tx;

    @Override
    public void run(ApplicationArguments args) {
        try {
            tx.executeWithoutResult(s -> backfill());
        } catch (Exception e) {
            log.warn("Supplier Manunuzi backfill skipped: {}", e.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void backfill() {
        for (String uid : ORDERS) {
            PurchaseOrder po = poRepository.findById(uid).filter(p -> BRANCH.equals(p.getBranchUid())).orElse(null);
            if (po == null)
                continue;
            SupplierEntry delivery = entryRepository.findAll().stream()
                    .filter(e -> BRANCH.equals(e.getBranchUid()) && SupplierEntry.DELIVERY.equals(e.getKind()) && po.getCode().equals(e.getPoCode())
                            && po.getSupplierUid().equals(e.getSupplierUid()) && Boolean.TRUE.equals(e.getIsActive()))
                    .findFirst().orElse(null);
            if (delivery == null || itemRepository.countByEntryUid(delivery.getUid()) > 0)
                continue;
            // Saved as the login who received it, so the rows land in its branch.
            User user = userRepository.findByUsername(delivery.getEntryBy());
            if (user == null || user.getBranch() == null || !BRANCH.equals(user.getBranch().getUid()))
                continue;
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
            String supplierName = supplierRepository.findById(po.getSupplierUid()).map(Supplier::getName).orElse("");
            long total = 0;
            for (PurchaseOrderLine line : lineRepository.findByPo(uid)) {
                long amount = (long) line.getReceivedPacks() * line.getPackPrice();
                if (amount <= 0)
                    continue;
                SupplierDeliveryItem item = new SupplierDeliveryItem();
                item.setEntryUid(delivery.getUid());
                item.setSupplierUid(po.getSupplierUid());
                item.setServiceUid(line.getServiceUid());
                item.setServiceName(line.getServiceName());
                item.setPoCode(po.getCode());
                item.setAmount(amount);
                item.setPaid(amount);
                item.setReceivedAt(delivery.getEntryAt());
                manunuziService.takeOnDelivery(BRANCH, item, supplierName);
                itemRepository.save(item);
                total += amount;
                log.info("Backfill {} {}: {} of {} from Manunuzi", po.getCode(), line.getServiceName(), item.getPotCovered(), amount);
            }
            log.info("Backfilled {} into Manunuzi ({})", po.getCode(), total);
        }
    }
}
