package com.midland.bar.Bar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.midland.bar.Bar.Service.*;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * How many SQL statements each busy screen costs, JSON included (open-in-view
 * lets serialization load lazy relations). A count that grows with the rows
 * is an N+1. Prints a table; asserts nothing. Local database, rolled back.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@org.junit.jupiter.api.Tag("local")
@Transactional
class QueryCountProbeTest {

    @Autowired UserRepository userRepository;
    @Autowired BarService barService;
    @Autowired BillPaymentService billPaymentService;
    @Autowired StaffOrderService staffOrderService;
    @Autowired CashUpService cashUpService;
    @Autowired WorkShiftService workShiftService;
    @Autowired InsightService insightService;
    @Autowired StaffSellService staffSellService;
    @Autowired BillCodeService billCodeService;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired ObjectMapper objectMapper;

    @Test
    void probe() {
        String login = System.getProperty("probe.user", "MERY@2026");
        User user = userRepository.findByUsernameForAuthentication(login);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        Map<String, Supplier<Object>> calls = new LinkedHashMap<>();
        calls.put("salesOpenedList (open bills)", () -> barService.salesOpenedList());
        calls.put("salesOpenedListByStatus THIS_YEAR", () -> barService.salesOpenedListByStatus("THIS_YEAR"));
        calls.put("findBarSalesListActiveTrue", () -> barService.findBarSalesListActiveTrue());
        calls.put("findBarSalesPage 0/50", () -> barService.findBarSalesPage(0, 50));
        calls.put("findBarServiceList", () -> barService.findBarServiceList());
        calls.put("findBarStaffList", () -> barService.findBarStaffList());
        calls.put("findStoreList", () -> barService.findStoreList());
        calls.put("findCurrentBarRevenueReport THIS_YEAR", () -> barService.findCurrentBarRevenueReport("THIS_YEAR"));
        calls.put("findCurrentBarRevenueByService THIS_YEAR", () -> barService.findCurrentBarRevenueByService("THIS_YEAR"));
        calls.put("getIncomeExpenses THIS_YEAR", () -> barService.getIncomeExpenses("THIS_YEAR"));
        calls.put("getStockAndPurchaseByFilter THIS_YEAR", () -> barService.getStockAndPurchaseByFilter("THIS_YEAR"));
        calls.put("findBranchDashboard", () -> barService.findBranchDashboard());
        calls.put("findStaffEarnings", () -> barService.findStaffEarnings());
        calls.put("paymentNotes year", () -> billPaymentService.paymentNotes(java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.now()));
        calls.put("staffOrders pending", () -> staffOrderService.pending());
        calls.put("cashUp preview", () -> cashUpService.preview());
        calls.put("cashUp list THIS_YEAR", () -> cashUpService.findClosed("THIS_YEAR"));
        calls.put("shift list THIS_YEAR", () -> workShiftService.list("THIS_YEAR"));
        calls.put("insights products THIS_YEAR", () -> insightService.products("THIS_YEAR"));
        String bill = System.getProperty("probe.bill", "841c5737-ee0f-4b1f-bbdd-d691cd46a34d");
        String code = System.getProperty("probe.code", "002");
        calls.put("findBarSalesList (one bill's lines)", () -> barService.findBarSalesList(bill));
        calls.put("receipt (one bill)", () -> billPaymentService.receipt(bill));
        calls.put("staffSell findByCode", () -> staffSellService.findByCode(code));
        calls.put("staffSell handover", () -> staffSellService.handover(code));
        calls.put("staffSell summary today", () -> staffSellService.summary(java.time.LocalDate.now()));
        calls.put("heartbeat touchLastSeen (rows)", () -> userRepository.touchLastSeen(user.getUid(), java.time.LocalDateTime.now()));
        calls.put("load user for auth", () -> userRepository.findByUsernameForAuthentication(login));

        System.out.println("\n===== QUERY PROBE as " + login);
        for (Map.Entry<String, Supplier<Object>> c : calls.entrySet()) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
            em.clear();
            stats.clear();
            long t = System.nanoTime();
            String outcome;
            int bytes = 0;
            try {
                Object result = c.getValue().get();
                bytes = objectMapper.writeValueAsBytes(result).length;
                outcome = "ok";
            } catch (Exception e) {
                outcome = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).lines().findFirst().orElse("");
            }
            long ms = (System.nanoTime() - t) / 1_000_000;
            System.out.printf("PROBE | %-42s | sql %5d | entities %6d | %5d ms | %8d B | %s%n",
                    c.getKey(), stats.getPrepareStatementCount(), stats.getEntityLoadCount(), ms, bytes, outcome);
        }
    }

    /** The selling path: open a bill, add 1 line, add 5 lines, pay - SQL each, flush included. */
    @Test
    void probeSelling() {
        User root = userRepository.findByUsernameForAuthentication("root@root.com");
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(root, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROOT"))));
        workShiftService.open();
        em.flush();
        List<String> products = List.of("7b57124d-a41d-40e2-b5e5-54fce93123f8", "f397d3a7-b3b3-4b4c-8754-97184eb72c4c",
                "9b67f3ef-7fea-4b8a-b0ce-a6c840dd1c5f", "5daffc15-a298-4f72-9f28-fc17349efb8c", "fd195409-59ee-43f0-aac9-69648ad97666");
        String[] bill = new String[1];
        Map<String, Runnable> steps = new LinkedHashMap<>();
        steps.put("open bill", () -> {
            com.midland.bar.Bar.Dto.SaleOpenedDTO open = new com.midland.bar.Bar.Dto.SaleOpenedDTO();
            open.setSalesCode(billCodeService.findAvailable().getData().get(0));
            bill[0] = barService.saveOpenSale(open).getData().getUid();
        });
        steps.put("add 1 line (week's first)", () -> barService.addSaleItems(items(bill[0], products.subList(0, 1))));
        steps.put("add 1 line", () -> barService.addSaleItems(items(bill[0], products.subList(1, 2))));
        steps.put("add 5 lines", () -> barService.addSaleItems(items(bill[0], products)));
        steps.put("pay cash", () -> {
            com.midland.bar.Bar.Dto.PayBillDTO dto = new com.midland.bar.Bar.Dto.PayBillDTO();
            dto.setSalesOpenedUID(bill[0]);
            com.midland.bar.Bar.Dto.PayBillDTO.Part part = new com.midland.bar.Bar.Dto.PayBillDTO.Part();
            part.setMethod("cash");
            part.setAmount((int) barSalesRepository.billTotal(bill[0]));
            dto.setPayments(List.of(part));
            billPaymentService.payBill(dto);
        });
        for (Map.Entry<String, Runnable> st : steps.entrySet()) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(root, null,
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROOT"))));
            em.clear();
            // A new request: per-transaction memos (TxMemo) start over too.
            new java.util.ArrayList<>(org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap().keySet()).stream()
                    .filter(k -> k instanceof String key && key.startsWith("txMemo:"))
                    .forEach(org.springframework.transaction.support.TransactionSynchronizationManager::unbindResource);
            stats.clear();
            long t = System.nanoTime();
            String outcome = "ok";
            try {
                st.getValue().run();
                em.flush();
            } catch (Exception e) {
                outcome = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            System.out.println("PROBE-END " + st.getKey());
            System.out.printf("PROBE | %-42s | sql %5d | entities %6d | %5d ms | %s%n", st.getKey(),
                    stats.getPrepareStatementCount(), stats.getEntityLoadCount(), (System.nanoTime() - t) / 1_000_000, outcome);
        }
    }

    @Autowired com.midland.bar.Bar.Repository.BarSalesRepository barSalesRepository;

    private static com.midland.bar.Bar.Dto.SaleItemsDTO items(String bill, List<String> products) {
        com.midland.bar.Bar.Dto.SaleItemsDTO dto = new com.midland.bar.Bar.Dto.SaleItemsDTO();
        dto.setSalesOpenedUID(bill);
        dto.setItems(products.stream().map(p -> {
            com.midland.bar.Bar.Dto.SaleItemsDTO.Item i = new com.midland.bar.Bar.Dto.SaleItemsDTO.Item();
            i.setBarServiceUID(p);
            i.setQuantity(2);
            return i;
        }).toList());
        return dto;
    }
}
