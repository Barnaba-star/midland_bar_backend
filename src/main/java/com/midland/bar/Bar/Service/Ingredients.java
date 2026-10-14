package com.midland.bar.Bar.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The other stock items a service is made from, beside its first one
 * (stockSourceUid): a wali samaki takes a piece of fish AND a plate of rice.
 * Kept on the service as JSON, like its ladder:
 * [{"uid":"<stock item>","unit":"Sahani","count":1,"units":4}] - "units" is
 * how many of the item's smallest measure one sale takes.
 */
public final class Ingredients {

    public record Ingredient(String uid, String unit, Integer count, int units) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private Ingredients() {}

    public static List<Ingredient> parse(String json) {
        if (json == null || json.isBlank())
            return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<Ingredient>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String toJson(List<Ingredient> list) {
        if (list == null || list.isEmpty())
            return null;
        try {
            return JSON.writeValueAsString(new ArrayList<>(list));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
