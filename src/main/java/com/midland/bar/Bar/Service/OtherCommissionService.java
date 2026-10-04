package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Dto.OtherCommissionItemDTO;
import com.midland.bar.Bar.Model.OtherCommissionItem;
import com.midland.bar.Bar.Model.OtherCommissionEntry;
import com.midland.bar.Bar.Repository.OtherCommissionEntryRepository;
import com.midland.bar.Bar.Repository.OtherCommissionItemRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The branch's own list of what its "Other" commission pays for. Set by the
 * CEO in POS Setting; every sale then splits its Other amount across these,
 * each into a pot of its own ("Other · Internet") that spending is recorded
 * against, like TRA or LUKU.
 */
@Service
@RequiredArgsConstructor
public class OtherCommissionService {

    /** The pot name of a share - kept apart from the eleven fixed buckets. */
    public static final String POT_PREFIX = "Other · ";

    private static final int MAX_ITEMS = 20;

    private final OtherCommissionItemRepository repository;
    private final OtherCommissionEntryRepository entryRepository;

    public ResponseList<OtherCommissionItem> findItems() {
        return new ResponseList<>(repository.findActive(LoggerUser.getBranchUID()));
    }

    /**
     * Replaces the branch's list. Empty clears it (Other is one pot again);
     * otherwise every name is filled in and different, and the shares add up
     * to exactly 100 so every shilling of Other has somewhere to go.
     */
    @Transactional
    public ResponseList<OtherCommissionItem> saveItems(List<OtherCommissionItemDTO> items) {
        String branchUID = LoggerUser.getBranchUID();
        if (branchUID == null)
            return new ResponseList<>("No branch found for the current user");
        List<OtherCommissionItemDTO> rows = items == null ? List.of() : items;
        if (rows.size() > MAX_ITEMS)
            return new ResponseList<>("At most " + MAX_ITEMS + " items");

        Set<String> seen = new HashSet<>();
        int total = 0;
        for (OtherCommissionItemDTO row : rows) {
            String name = row.getName() == null ? "" : row.getName().trim();
            if (name.isEmpty())
                return new ResponseList<>("Every item needs a name");
            if (name.length() > 60)
                return new ResponseList<>("\"" + name + "\" is too long - 60 letters at most");
            if (!seen.add(name.toLowerCase()))
                return new ResponseList<>("\"" + name + "\" is on the list twice");
            if (row.getPercent() == null || row.getPercent() < 1 || row.getPercent() > 100)
                return new ResponseList<>("Give \"" + name + "\" a share between 1% and 100%");
            total += row.getPercent();
        }
        if (!rows.isEmpty() && total != 100)
            return new ResponseList<>("The shares add up to " + total + "% - they must come to exactly 100%");

        for (OtherCommissionItem old : repository.findActive(branchUID)) {
            old.delete();
            repository.save(old);
        }
        List<OtherCommissionItem> saved = new ArrayList<>();
        int order = 0;
        for (OtherCommissionItemDTO row : rows) {
            OtherCommissionItem item = new OtherCommissionItem();
            item.setName(row.getName().trim());
            item.setPercent(row.getPercent());
            item.setSortOrder(order++);
            saved.add(repository.save(item));
        }
        return new ResponseList<>(saved);
    }

    /**
     * An Other amount split across the branch's items, pot name to shillings,
     * the last taking what rounding leaves so the parts add back to the whole.
     * Empty when the branch has not set any - the caller keeps one Other pot.
     */
    public Map<String, BigDecimal> split(BigDecimal otherAmount, String branchUID) {
        Map<String, BigDecimal> parts = new LinkedHashMap<>();
        List<OtherCommissionItem> list = repository.findActive(branchUID);
        if (list.isEmpty() || otherAmount == null)
            return parts;
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < list.size(); i++) {
            OtherCommissionItem item = list.get(i);
            BigDecimal share = i == list.size() - 1
                    ? otherAmount.subtract(given)
                    : otherAmount.multiply(BigDecimal.valueOf(item.getPercent()))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            given = given.add(share);
            parts.merge(POT_PREFIX + item.getName(), share, BigDecimal::add);
        }
        return parts;
    }

    /** Keeps each item's share of one sale's Other, on the sale's day, for the report. */
    public void record(Map<String, BigDecimal> parts, LocalDate day) {
        parts.forEach((pot, share) -> {
            OtherCommissionEntry entry = new OtherCommissionEntry();
            entry.setItemName(pot.startsWith(POT_PREFIX) ? pot.substring(POT_PREFIX.length()) : pot);
            entry.setAmount(share);
            entry.setEntryDate(day);
            entryRepository.save(entry);
        });
    }

    /**
     * How Other was split over a period (the Reports filters: DAY, YESTERDAY,
     * WEEK, LAST_WEEK, MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR, or a date).
     */
    public Response<Map<String, Object>> report(String filter) {
        LocalDate[] range = rangeFor(filter);
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] row : entryRepository.totalsByItem(LoggerUser.getBranchUID(), range[0], range[1])) {
            BigDecimal amount = row[1] instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(row[1]));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row[0]);
            item.put("amount", amount);
            items.add(item);
            total = total.add(amount);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", total);
        return new Response<>(body);
    }

    private static LocalDate[] rangeFor(String filter) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String f = filter == null ? "DAY" : filter.toUpperCase();
        return switch (f) {
            case "YESTERDAY" -> new LocalDate[]{today.minusDays(1), today};
            case "WEEK" -> new LocalDate[]{monday, monday.plusWeeks(1)};
            case "LAST_WEEK" -> new LocalDate[]{monday.minusWeeks(1), monday};
            case "MONTH" -> new LocalDate[]{today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1)};
            case "LAST_MONTH" -> new LocalDate[]{today.withDayOfMonth(1).minusMonths(1), today.withDayOfMonth(1)};
            case "THIS_YEAR", "YEAR" -> new LocalDate[]{today.withDayOfYear(1), today.withDayOfYear(1).plusYears(1)};
            case "LAST_YEAR" -> new LocalDate[]{today.withDayOfYear(1).minusYears(1), today.withDayOfYear(1)};
            case "DAY", "TODAY" -> new LocalDate[]{today, today.plusDays(1)};
            default -> {
                LocalDate d = LocalDate.parse(filter);
                yield new LocalDate[]{d, d.plusDays(1)};
            }
        };
    }
}
