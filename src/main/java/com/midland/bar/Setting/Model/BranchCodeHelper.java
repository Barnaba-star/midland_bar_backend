package com.midland.bar.Setting.Model;

import com.midland.bar.Setting.Repository.RegionRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
@Component
public class BranchCodeHelper {
    /**
     * The regions this started life with. They are no longer read at runtime -
     * RegionService copies them into the regions table on first startup and
     * the lookup goes there instead, so a new region is a form rather than a
     * deploy. Kept only as that seed.
     */
    public static final Map<String, String> SEED_REGION_CODES = Map.ofEntries(
            Map.entry("ARUSHA", "ARU"),
            Map.entry("DAR ES SALAAM", "DAR"),
            Map.entry("DODOMA", "DOD"),
            Map.entry("GEITA", "GEI"),
            Map.entry("IRINGA", "IRI"),
            Map.entry("KAGERA", "KAG"),
            Map.entry("KATAVI", "KAT"),
            Map.entry("KIGOMA", "KIG"),
            Map.entry("KILIMANJARO", "KIL"),
            Map.entry("LINDI", "LIN"),
            Map.entry("MANYARA", "MAN"),
            Map.entry("MARA", "MAR"),
            Map.entry("MBEYA", "MBE"),
            Map.entry("MOROGORO", "MOR"),
            Map.entry("MTWARA", "MTW"),
            Map.entry("MWANZA", "MWA"),
            Map.entry("NJOMBE", "NJO"),
            Map.entry("PWANI", "PWA"),
            Map.entry("RUKWA", "RUK"),
            Map.entry("RUVUMA", "RUV"),
            Map.entry("SHINYANGA", "SHI"),
            Map.entry("SIMIYU", "SIM"),
            Map.entry("SINGIDA", "SIN"),
            Map.entry("SONGWE", "SON"),
            Map.entry("TABORA", "TAB"),
            Map.entry("TANGA", "TAN")
    );
    private final RegionRepository regionRepository;

    public BranchCodeHelper(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    public String generateRegionCode(String region) {

        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException(
                    "Region cannot be empty"
            );
        }

        String normalizedRegion =
                region.trim().toUpperCase();

        String code = regionRepository.findByName(normalizedRegion)
                .map(r -> r.getCode())
                .orElse(null);

        if (code == null) {
            throw new IllegalArgumentException(
                    "Region code not configured for: "
                            + region
            );
        }

        return code;
    }


    public String generateBranchCode(
            String region,
            long sequence
    ) {

        String regionCode =
                generateRegionCode(region);

        return String.format(
                "%s-%03d",
                regionCode,
                sequence
        );
    }
}
