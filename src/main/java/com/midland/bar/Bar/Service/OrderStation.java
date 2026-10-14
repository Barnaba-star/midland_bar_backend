package com.midland.bar.Bar.Service;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Uaa.Model.User;

import java.util.List;
import java.util.Set;

/**
 * Where a staff order is received: food by the CHEF (the kitchen), any Biss
 * at BISS, any fresh juice at JUICE, shisha at SHISHER (owner's choice,
 * 2026-10-14), pool games at POOL, every other drink at the COUNTER. A login holding only one of
 * these station roles sees and decides only that station's orders; CEO,
 * MANAGER and anyone with a wider role see every station.
 */
public final class OrderStation {

    public static final String COUNTER = "COUNTER";
    public static final String CHEF = "CHEF";
    public static final String JUICE = "JUICE";
    public static final String BISS = "BISS";
    public static final String SHISHER = "SHISHER";
    /** The pool table: games are received (and the table started) there. */
    public static final String POOL = "POOL";

    /** The roles that are a station - holding only these limits what you see. */
    public static final List<String> STATIONS = List.of(COUNTER, CHEF, JUICE, BISS, SHISHER, POOL);

    private OrderStation() {}

    /** FOOD goes to the kitchen, SHISHA to the shisha station, POOL to the pool table; drinks and everything else to the counter. */
    public static String forCategory(String category) {
        if ("FOOD".equalsIgnoreCase(category))
            return CHEF;
        if ("SHISHA".equalsIgnoreCase(category))
            return SHISHER;
        if ("POOL".equalsIgnoreCase(category))
            return POOL;
        return COUNTER;
    }

    /**
     * The station an item is made at, by its name (same test as the ticket
     * colours): Biss, fresh juice, shisha - otherwise by category.
     */
    public static String forItem(String name, String category) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.contains("biss"))
            return BISS;
        if (n.contains("fresh") && n.contains("juice"))
            return JUICE;
        if (n.contains("shisha") || n.contains("shisher"))
            return SHISHER;
        if (n.contains("pool"))
            return POOL;
        return forCategory(category);
    }

    /** A role's station, or null for any other role. Roles added from Settings carry a long code, so the name counts too. */
    public static String stationOf(Role role) {
        if (role == null)
            return null;
        for (String s : STATIONS) {
            if (s.equalsIgnoreCase(role.getCode()) || s.equalsIgnoreCase(role.getName()))
                return s;
        }
        return null;
    }

    /**
     * The station(s) this login receives for, comma-joined ("COUNTER" or
     * "COUNTER,JUICE"); null = every station. Someone holding two station
     * roles (counter and juice) sees both - and nothing of the others.
     */
    public static String mine() {
        User user = LoggerUser.getUser();
        if (Boolean.TRUE.equals(user.getIsRoot()))
            return null;
        List<Role> roles = user.getRoles() == null ? List.of() : user.getRoles();
        Set<String> stations = new java.util.TreeSet<>();
        for (Role r : roles) {
            String s = stationOf(r);
            if (s == null)
                return null; // a wider role sees every station
            stations.add(s);
        }
        return stations.isEmpty() ? null : String.join(",", stations);
    }

    /** Whether a login of station(s) {@code mine} sees an order for {@code station}. No station on the order = everyone. */
    public static boolean sees(String mine, String station) {
        if (mine == null || station == null)
            return true;
        for (String s : mine.split(","))
            if (s.equals(station))
                return true;
        return false;
    }

    /** For messages: where an order is received. */
    public static String placeName(String station) {
        if (station == null)
            return "counter";
        return switch (station) {
            case CHEF -> "kitchen";
            case JUICE -> "juice station";
            case BISS -> "Biss station";
            case SHISHER -> "shisha station";
            case POOL -> "pool table";
            default -> "counter";
        };
    }
}
