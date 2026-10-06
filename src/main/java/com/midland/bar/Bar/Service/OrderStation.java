package com.midland.bar.Bar.Service;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Uaa.Model.User;

import java.util.List;

/**
 * Where a staff order is received: drinks at the COUNTER, food by the CHEF
 * (the kitchen). A COUNTER login sees and decides only counter orders, a
 * CHEF login only kitchen orders; CEO, MANAGER and anyone with a wider role
 * see every station.
 */
public final class OrderStation {

    public static final String COUNTER = "COUNTER";
    public static final String CHEF = "CHEF";

    private OrderStation() {}

    /** FOOD goes to the kitchen; drinks and everything else to the counter. */
    public static String forCategory(String category) {
        return "FOOD".equalsIgnoreCase(category) ? CHEF : COUNTER;
    }

    /** The station this login receives for; null = every station. */
    public static String mine() {
        User user = LoggerUser.getUser();
        if (Boolean.TRUE.equals(user.getIsRoot()))
            return null;
        List<String> codes = user.getRoles() == null ? List.of() : user.getRoles().stream().map(Role::getCode).toList();
        boolean counter = codes.contains(COUNTER), chef = codes.contains(CHEF);
        boolean wider = codes.stream().anyMatch(c -> !COUNTER.equals(c) && !CHEF.equals(c));
        if (wider || counter == chef)
            return null;
        return counter ? COUNTER : CHEF;
    }

    /** Whether a login of station {@code mine} sees an order for {@code station}. No station on the order = everyone. */
    public static boolean sees(String mine, String station) {
        return mine == null || station == null || mine.equals(station);
    }
}
