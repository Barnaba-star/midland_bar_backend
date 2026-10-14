package com.midland.bar.Bar.Service;

import com.midland.bar.Setting.Model.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderStationTest {

    @Test
    void eachItemGoesToWhereItIsMade() {
        assertEquals(OrderStation.BISS, OrderStation.forItem("Biss Kubwa", "DRINK"));
        assertEquals(OrderStation.JUICE, OrderStation.forItem("Fresh Juice Embe", "DRINK"));
        assertEquals(OrderStation.SHISHER, OrderStation.forItem("Shisha Apple", "OTHER"));
        assertEquals(OrderStation.CHEF, OrderStation.forItem("Kuku Choma", "FOOD"));
        assertEquals(OrderStation.COUNTER, OrderStation.forItem("Azam Juice", "DRINK"));
        assertEquals(OrderStation.COUNTER, OrderStation.forItem("Castle Lite", "DRINK"));
    }

    @Test
    void aRoleAddedFromSettingsIsKnownByItsName() {
        Role role = new Role();
        role.setCode("ROLE_BAR_JUICE");
        role.setName("Juice");
        assertEquals(OrderStation.JUICE, OrderStation.stationOf(role));
        Role ceo = new Role();
        ceo.setCode("CEO");
        ceo.setName("CEO");
        assertNull(OrderStation.stationOf(ceo));
    }
}
