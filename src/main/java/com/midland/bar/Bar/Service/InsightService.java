package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Repository.InsightRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The owner's view of what the bar earns and holds:
 * - profit per product (revenue against buying cost) and what sells, or does not;
 * - when it sells, by weekday and hour;
 * - every pot's balance since the branch began.
 */
@Service
@RequiredArgsConstructor
public class InsightService {

    private final InsightRepository repository;

    /** Profit per product, best sellers and slow movers for a Reports period. */
    public Response<Map<String, Object>> products(String filter) {
        LocalDateTime[] range = ReportRange.of(filter);
        LocalDate from = range[0].toLocalDate(), to = range[1].toLocalDate();
        String branchUID = LoggerUser.getBranchUID();

        List<Map<String, Object>> rows = new ArrayList<>();
        long revenue = 0, cost = 0, revenueWithCost = 0, units = 0;
        for (Object[] r : repository.marginByProduct(branchUID, from, to)) {
            long qty = num(r[4]), rev = num(r[5]), cst = num(r[6]), missing = num(r[7]);
            boolean costKnown = missing == 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uid", r[0]);
            row.put("serviceName", r[1]);
            row.put("serviceCode", r[2]);
            row.put("category", r[3]);
            row.put("units", qty);
            row.put("revenue", rev);
            row.put("cost", costKnown ? cst : null);
            row.put("profit", costKnown ? rev - cst : null);
            row.put("marginPercent", costKnown && rev > 0 ? Math.round((rev - cst) * 1000.0 / rev) / 10.0 : null);
            row.put("costKnown", costKnown);
            rows.add(row);
            revenue += rev;
            units += qty;
            if (costKnown) {
                cost += cst;
                revenueWithCost += rev;
            }
        }

        List<Map<String, Object>> slow = new ArrayList<>();
        long idleValue = 0;
        for (Object[] r : repository.slowMovers(branchUID, from, to)) {
            long stock = num(r[3]), buying = num(r[4]), perPack = Math.max(1, num(r[5]));
            long value = Math.round((double) stock * buying / perPack);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uid", r[0]);
            row.put("serviceName", r[1]);
            row.put("serviceCode", r[2]);
            row.put("stock", stock);
            row.put("stockValue", value);
            row.put("lastSold", r[6]);
            slow.add(row);
            idleValue += value;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("products", rows);
        body.put("revenue", revenue);
        body.put("units", units);
        // Profit only where the cost is known (drinks bought by the crate); food has no buying price.
        body.put("revenueWithCost", revenueWithCost);
        body.put("cost", cost);
        body.put("profit", revenueWithCost - cost);
        body.put("marginPercent", revenueWithCost > 0 ? Math.round((revenueWithCost - cost) * 1000.0 / revenueWithCost) / 10.0 : null);
        body.put("slowMovers", slow);
        body.put("idleStockValue", idleValue);
        return new Response<>(body);
    }

    /** Sales by weekday (1 Monday .. 7 Sunday) and hour (0-23), and the busiest hours. */
    public Response<Map<String, Object>> peakHours(String filter) {
        LocalDateTime[] range = ReportRange.of(filter);
        List<Map<String, Object>> cells = new ArrayList<>();
        Map<Integer, Long> byHour = new TreeMap<>();
        Map<Integer, Long> byDay = new TreeMap<>();
        for (Object[] r : repository.salesByHour(LoggerUser.getBranchUID(), range[0].toLocalDate(), range[1].toLocalDate())) {
            int dow = (int) num(r[0]), hour = (int) num(r[1]);
            long amount = num(r[2]), units = num(r[3]);
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("day", dow);
            cell.put("hour", hour);
            cell.put("amount", amount);
            cell.put("units", units);
            cells.add(cell);
            byHour.merge(hour, amount, Long::sum);
            byDay.merge(dow, amount, Long::sum);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cells", cells);
        body.put("byHour", byHour);
        body.put("byDay", byDay);
        return new Response<>(body);
    }

    /**
     * Every pot since the branch began - collected, spent, left - with this
     * week beside it. Pots are kept per week (from Monday); this is the running total.
     */
    public Response<Map<String, Object>> potsLedger() {
        String branchUID = LoggerUser.getBranchUID();
        LocalDateTime[] week = ReportRange.of("WEEK");
        Map<String, long[]> weekly = new LinkedHashMap<>();
        for (Object[] r : repository.potsInWeeks(branchUID, week[0].toLocalDate(), week[1].toLocalDate()))
            weekly.put(String.valueOf(r[0]), new long[]{num(r[1]), num(r[2])});

        List<Map<String, Object>> pots = new ArrayList<>();
        long in = 0, out = 0;
        for (Object[] r : repository.potsAllTime(branchUID)) {
            String name = String.valueOf(r[0]);
            long collected = num(r[1]), spent = num(r[2]);
            long[] w = weekly.getOrDefault(name, new long[]{0, 0});
            Map<String, Object> pot = new LinkedHashMap<>();
            pot.put("name", name);
            pot.put("collected", collected);
            pot.put("spent", spent);
            pot.put("balance", collected - spent);
            pot.put("weekCollected", w[0]);
            pot.put("weekSpent", w[1]);
            pots.add(pot);
            in += collected;
            out += spent;
        }
        pots.sort((a, b) -> Long.compare((long) b.get("balance"), (long) a.get("balance")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pots", pots);
        body.put("collected", in);
        body.put("spent", out);
        body.put("balance", in - out);
        return new Response<>(body);
    }

    private static long num(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return Math.round(n.doubleValue());
        return Math.round(Double.parseDouble(String.valueOf(o)));
    }
}
