package com.midland.bar.Bar.Service;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports > Ofa na Punguzo (owner, 2026-10-19): everything the bar gave away
 * or took off in a period, kept apart from sales. Sales everywhere are what
 * was charged (after offers, discounts and refunds); this report is what
 * was not - by happy hour / offer, bill discounts, customers' monthly
 * offers (ofa) and refunds - so the two never mix.
 */
@Service
@RequiredArgsConstructor
public class GiveawayReportService {

    private final NamedParameterJdbcTemplate jdbc;

    public Response<Map<String, Object>> report(String filter) {
        LocalDateTime[] range = ReportRange.of(filter);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("b", LoggerUser.getBranchUIDOrMain())
                .addValue("from", range[0])
                .addValue("to", range[1]);

        // Happy hours / offers on products: per offer and product, units sold,
        // units free, what was charged, and what the menu price would have been.
        List<Map<String, Object>> offers = jdbc.queryForList("""
                SELECT s.price_rule_name AS offer, v.service_name AS product,
                       COALESCE(SUM(s.quantity), 0) AS units, COALESCE(SUM(s.free_units), 0) AS free_units,
                       COALESCE(SUM(s.line_total + COALESCE(s.discount_amount, 0) + COALESCE(s.offer_amount, 0)), 0) AS charged,
                       COALESCE(SUM(COALESCE(s.list_price, s.unit_price) * s.quantity), 0) AS menu_value
                FROM bar_sales s JOIN bar_services v ON v.uid = s.bar_service_uid
                WHERE s.branch_uid = :b AND s.is_active = true AND s.price_rule_name IS NOT NULL
                  AND s.sold_at >= :from AND s.sold_at < :to
                GROUP BY s.price_rule_name, v.service_name ORDER BY s.price_rule_name, v.service_name
                """, p);
        long offerGiven = 0;
        for (Map<String, Object> o : offers) {
            long given = ((Number) o.get("menu_value")).longValue() - ((Number) o.get("charged")).longValue();
            o.put("given", given);
            offerGiven += given;
        }

        List<Map<String, Object>> discounts = jdbc.queryForList("""
                SELECT sales_code, bill_total, amount, reason, given_by_name, given_by, given_at
                FROM bill_discounts WHERE branch_uid = :b AND is_active = true AND given_at >= :from AND given_at < :to
                ORDER BY given_at DESC
                """, p);
        List<Map<String, Object>> customerOffers = jdbc.queryForList("""
                SELECT customer_name, bill_code, amount, used_by_name, used_by, used_at
                FROM offer_uses WHERE branch_uid = :b AND is_active = true AND used_at >= :from AND used_at < :to
                ORDER BY used_at DESC
                """, p);
        List<Map<String, Object>> refunds = jdbc.queryForList("""
                SELECT sales_code, amount, method, reason, requested_by_name, decided_by_name, decided_at
                FROM bill_refunds WHERE branch_uid = :b AND is_active = true AND status = 'APPROVED'
                  AND decided_at >= :from AND decided_at < :to ORDER BY decided_at DESC
                """, p);
        Long sales = jdbc.queryForObject("""
                SELECT COALESCE(SUM(line_total), 0) FROM bar_sales
                WHERE branch_uid = :b AND is_active = true AND sold_at >= :from AND sold_at < :to
                """, p, Long.class);

        long discountTotal = discounts.stream().mapToLong(d -> ((Number) d.get("amount")).longValue()).sum();
        long customerOfferTotal = customerOffers.stream().mapToLong(d -> ((Number) d.get("amount")).longValue()).sum();
        long refundTotal = refunds.stream().mapToLong(d -> ((Number) d.get("amount")).longValue()).sum();

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("sales", sales);
        totals.put("offers", offerGiven);
        totals.put("discounts", discountTotal);
        totals.put("customerOffers", customerOfferTotal);
        totals.put("refunds", refundTotal);
        totals.put("given", offerGiven + discountTotal + customerOfferTotal);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totals", totals);
        out.put("offers", offers);
        out.put("discounts", discounts);
        out.put("customerOffers", customerOffers);
        out.put("refunds", refunds);
        return new Response<>(out);
    }
}
