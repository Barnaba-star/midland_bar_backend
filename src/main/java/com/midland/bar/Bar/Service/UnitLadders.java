package com.midland.bar.Bar.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.midland.bar.Bar.Dto.LadderLevel;
import com.midland.bar.Utils.Exceptions.BusinessException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A stock item's measures, smallest first: Nyama; Mshikaki = 3 Nyama;
 * Portion = 10 Mshikaki; Nusu = 2 Portion; Kilo = 2 Nusu. Stock is kept in
 * the smallest, so every rung's size is worked out here as a count of it
 * (Kilo = 120).
 */
final class UnitLadders {

    private static final ObjectMapper JSON = new ObjectMapper();

    private UnitLadders() {}

    /** Checks the rungs and fills in base for each. Throws with the reason when they do not make sense. */
    static List<LadderLevel> normalise(List<LadderLevel> levels) {
        if (levels == null || levels.isEmpty())
            throw new BusinessException("Add at least the smallest measure, e.g. Nyama");
        List<LadderLevel> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int base = 1;
        for (int i = 0; i < levels.size(); i++) {
            LadderLevel level = levels.get(i);
            String name = level == null || level.getName() == null ? "" : level.getName().trim();
            if (name.isEmpty())
                throw new BusinessException("Every measure needs a name");
            if (!names.add(name.toLowerCase()))
                throw new BusinessException("The measure " + name + " appears twice");
            int per = 1;
            if (i > 0) {
                per = level.getPer() == null ? 0 : level.getPer();
                if (per < 2)
                    throw new BusinessException(name + " must be made of at least 2 " + out.get(i - 1).getName());
                base = Math.multiplyExact(base, per);
            }
            out.add(new LadderLevel(name, per, base));
        }
        return out;
    }

    static String toJson(List<LadderLevel> levels) {
        try {
            return JSON.writeValueAsString(levels);
        } catch (Exception e) {
            throw new BusinessException("Could not store the measures");
        }
    }

    static List<LadderLevel> fromJson(String json) {
        if (json == null || json.isBlank())
            return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<LadderLevel>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /** How many of the smallest measure one of the named rung is. */
    static Optional<Integer> baseOf(String ladderJson, String name) {
        if (name == null)
            return Optional.empty();
        return fromJson(ladderJson).stream()
                .filter(l -> name.equalsIgnoreCase(l.getName()))
                .map(LadderLevel::getBase)
                .findFirst();
    }
}
