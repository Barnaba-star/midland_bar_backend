package com.midland.bar.Bar;

import com.midland.bar.Bar.Table.BarTable;
import com.midland.bar.Bar.Table.BarTableService;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tables + QR tokens + the customers' window (owner, 2026-11-09) - local DB sample data, rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@org.junit.jupiter.api.Tag("local")
@Transactional
class BarTableTest {

    private static final String LOGIN = "METHEW@2026";

    @Autowired BarTableService tableService;
    @Autowired UserRepository userRepository;
    @Autowired MockMvc mvc;

    @BeforeEach
    void signIn() {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        assertNotNull(user, "sample user");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void countAddsAndRemovesAndEachTableHasItsOwnToken() {
        tableService.setCount(0);
        List<BarTable> three = tableService.setCount(3).getData();
        assertEquals(List.of("Meza 1", "Meza 2", "Meza 3"), three.stream().map(BarTable::getName).toList());
        assertEquals(3, three.stream().map(BarTable::getToken).distinct().count());
        three.forEach(t -> assertEquals(10, t.getToken().length()));

        List<BarTable> two = tableService.setCount(2).getData();
        assertEquals(2, two.size());
        assertEquals(2, tableService.list().getData().size());
        String removed = three.get(2).getToken();
        assertEquals("TABLE_NOT_FOUND", tableService.customerView(removed).getMessage(), "a removed table's card stops working");
        assertNull(tableService.setCount(301).getData());
    }

    @Test
    void theCustomersWindowShowsTheTableAndTheMenuButNoStoreItems() {
        tableService.setCount(0);
        BarTable t = tableService.setCount(1).getData().get(0);
        Map<String, Object> view = tableService.customerView(t.getToken().toLowerCase()).getData();
        assertNotNull(view);
        assertEquals("Meza 1", view.get("tableName"));
        assertNotNull(view.get("branchName"));
        List<?> menu = (List<?>) view.get("menu");
        assertFalse(menu.isEmpty(), "the sample branch sells things");
        menu.forEach(m -> assertTrue(((Integer) ((Map<?, ?>) m).get("price")) > 0));

        String old = t.getToken();
        String fresh = tableService.newCard(t.getUid()).getData().getToken();
        assertNotEquals(old, fresh);
        assertNull(tableService.customerView(old).getData(), "the old card stops working");
    }

    @Test
    void theWindowNeedsNoLogin() throws Exception {
        tableService.setCount(0);
        String token = tableService.setCount(1).getData().get(0).getToken();
        SecurityContextHolder.clearContext();
        mvc.perform(get("/public/table/" + token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.tableName").value("Meza 1"));
        mvc.perform(get("/bar/tables")).andExpect(status().is4xxClientError());
    }
}
