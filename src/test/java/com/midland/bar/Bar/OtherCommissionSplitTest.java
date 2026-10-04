package com.midland.bar.Bar;

import com.midland.bar.Bar.Model.OtherCommissionItem;
import com.midland.bar.Bar.Repository.OtherCommissionItemRepository;
import com.midland.bar.Bar.Service.OtherCommissionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The Other amount is shared by the branch's items, and the parts always add back to the whole. */
class OtherCommissionSplitTest {

    private static OtherCommissionItem item(String name, int percent) {
        OtherCommissionItem i = new OtherCommissionItem();
        i.setName(name);
        i.setPercent(percent);
        return i;
    }

    private OtherCommissionService serviceWith(List<OtherCommissionItem> items) {
        OtherCommissionItemRepository repo = mock(OtherCommissionItemRepository.class);
        when(repo.findActive("B1")).thenReturn(items);
        return new OtherCommissionService(repo, mock(com.midland.bar.Bar.Repository.OtherCommissionEntryRepository.class));
    }

    @Test
    void noItemsMeansNoSplit() {
        assertTrue(serviceWith(List.of()).split(new BigDecimal("1000"), "B1").isEmpty());
    }

    @Test
    void sharesFollowThePercentages() {
        Map<String, BigDecimal> parts = serviceWith(List.of(
                item("Internet", 20), item("Walinzi", 50), item("Generator", 30)))
                .split(new BigDecimal("10000"), "B1");
        assertEquals(0, new BigDecimal("2000").compareTo(parts.get("Other · Internet")));
        assertEquals(0, new BigDecimal("5000").compareTo(parts.get("Other · Walinzi")));
        assertEquals(0, new BigDecimal("3000").compareTo(parts.get("Other · Generator")));
    }

    @Test
    void roundingNeverLosesAShilling() {
        BigDecimal other = new BigDecimal("1000");
        Map<String, BigDecimal> parts = serviceWith(List.of(
                item("A", 33), item("B", 33), item("C", 34)))
                .split(other, "B1");
        BigDecimal sum = parts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, other.compareTo(sum));

        BigDecimal odd = new BigDecimal("7.01");
        parts = serviceWith(List.of(item("A", 33), item("B", 33), item("C", 34))).split(odd, "B1");
        sum = parts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, odd.compareTo(sum));
    }
}
