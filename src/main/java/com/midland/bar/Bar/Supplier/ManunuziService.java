package com.midland.bar.Bar.Supplier;

import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Model.IncomeExpenses;
import com.midland.bar.Bar.Model.IncomeExpensesDescription;
import com.midland.bar.Bar.Model.StockAndPurchase;
import com.midland.bar.Bar.Model.StockAndPurchaseDescriptions;
import com.midland.bar.Bar.Repository.BarServiceRepository;
import com.midland.bar.Bar.Repository.IncomeExpensesDescriptionRepository;
import com.midland.bar.Bar.Repository.IncomeExpensesRepository;
import com.midland.bar.Bar.Repository.StockAndPurchaseDescriptionsRepository;
import com.midland.bar.Bar.Repository.StockAndPurchaseRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The Manunuzi (Stock Purchase) budget behind suppliers (owner, 2026-10-19):
 * each product's savings from its sales, less what is already promised -
 * orders still to arrive and deliveries not yet paid - is what may be spent
 * on it. Paying a supplier takes each product's part from those savings,
 * oldest week first, exactly as paying from the Manunuzi report does, so the
 * report stays right.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManunuziService {

    private final StockAndPurchaseRepository stockAndPurchaseRepository;
    private final StockAndPurchaseDescriptionsRepository descriptionsRepository;
    private final IncomeExpensesRepository incomeExpensesRepository;
    private final IncomeExpensesDescriptionRepository spendRepository;
    private final PurchaseOrderLineRepository poLineRepository;
    private final SupplierDeliveryItemRepository deliveryItemRepository;
    private final BarServiceRepository barServiceRepository;

    /** Per product: savings left, promised to open orders, delivered unpaid, and what is still free to spend. */
    public Map<String, long[]> budgets(String branchUID) {
        Map<String, long[]> out = new HashMap<>();
        for (Object[] r : stockAndPurchaseRepository.remainingByService(branchUID))
            out.computeIfAbsent((String) r[0], k -> new long[4])[0] += ((Number) r[1]).longValue();
        for (Object[] r : poLineRepository.openByService(branchUID))
            out.computeIfAbsent((String) r[0], k -> new long[4])[1] += ((Number) r[1]).longValue();
        for (Object[] r : deliveryItemRepository.unpaidByService(branchUID))
            out.computeIfAbsent((String) r[0], k -> new long[4])[2] += ((Number) r[1]).longValue();
        out.values().forEach(v -> v[3] = v[0] - v[1] - v[2]);
        return out;
    }

    /** What may still be spent on one product. */
    public long available(String branchUID, String serviceUid) {
        long[] b = budgets(branchUID).get(serviceUid);
        return b == null ? 0 : b[3];
    }

    /** The budget table for the screens: every stock-counted product with its four figures. */
    public ResponseList<Map<String, Object>> budgetList() {
        String b = LoggerUser.getBranchUIDOrMain();
        Map<String, long[]> budgets = budgets(b);
        List<Map<String, Object>> out = new ArrayList<>();
        for (BarServiceEntity s : barServiceRepository.findAll()) {
            if (!b.equals(s.getBranchUid()) || Boolean.FALSE.equals(s.getIsActive()) || !Boolean.TRUE.equals(s.getTrackStock()))
                continue;
            long[] v = budgets.getOrDefault(s.getUid(), new long[4]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("serviceUid", s.getUid());
            m.put("serviceName", s.getServiceName());
            m.put("saved", v[0]);
            m.put("onOrder", v[1]);
            m.put("deliveredUnpaid", v[2]);
            m.put("available", v[3]);
            out.add(m);
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("serviceName"))));
        return new ResponseList<>(out);
    }

    /**
     * Pays [amount] for a product out of its Manunuzi savings, oldest week
     * first: each week's row records it as paid, the week's Stock Purchase
     * pot as spent (with a line in its details), and the Manunuzi report a
     * history line. [payer] is who paid out of their drawer (their cash-up
     * counts it), or null when paid from outside. Returns what the savings
     * covered; the rest had no savings to come from.
     */
    public long spend(String branchUID, String serviceUid, long amount, String label, String method, String payer, String paidTo) {
        long left = amount;
        for (StockAndPurchase row : stockAndPurchaseRepository.withRemaining(branchUID, serviceUid)) {
            if (left <= 0)
                break;
            int remaining = row.getRemainingAmount() == null ? 0 : row.getRemainingAmount();
            int piece = (int) Math.min(left, remaining);
            if (piece <= 0)
                continue;
            row.setRemainingAmount(remaining - piece);
            row.setPayedAmount((row.getPayedAmount() == null ? 0 : row.getPayedAmount()) + piece);
            row.setPaymentStatus(row.getRemainingAmount() <= 0 ? "PAID" : "PARTIAL");
            stockAndPurchaseRepository.save(row);

            StockAndPurchaseDescriptions line = new StockAndPurchaseDescriptions();
            line.setStockAndPurchase(row);
            line.setDescriptions(label);
            line.setWeekDate(row.getWeekDate());
            line.setAmount(piece);
            descriptionsRepository.save(line);

            LocalDate week = row.getWeekDate();
            Optional<IncomeExpenses> pot = incomeExpensesRepository.findIncomeAndExpensesByWeekDate(branchUID, week);
            if (pot.isPresent()) {
                IncomeExpenses p = pot.get();
                p.setExpenses((p.getExpenses() == null ? BigDecimal.ZERO : p.getExpenses()).add(BigDecimal.valueOf(piece)));
                incomeExpensesRepository.save(p);
                IncomeExpensesDescription spend = new IncomeExpensesDescription();
                spend.setIncomeExpenses(p);
                spend.setDescription(label);
                spend.setStaffName(paidTo);
                spend.setSpendAmount(BigDecimal.valueOf(piece));
                spend.setDescriptionDate(LocalDate.now());
                spend.setPaidBy(payer);
                spend.setPaidAt(LocalDateTime.now());
                spend.setMethod(method);
                spendRepository.save(spend);
            }
            left -= piece;
        }
        log.info("Manunuzi of {} paid {} of {} ({})", serviceUid, amount - left, amount, label);
        return amount - left;
    }
}
