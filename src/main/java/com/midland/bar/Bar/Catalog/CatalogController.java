package com.midland.bar.Bar.Catalog;

import com.midland.bar.Bar.Dto.BarServiceDTO;
import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Model.ProductCategory;
import com.midland.bar.Bar.Repository.BarServiceRepository;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Config.Security.MainOfficeRoles;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import com.midland.bar.Utils.Responses.ResponseStatus;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * The main office's drink catalog (owner, 2026-11-14).
 *
 *  - Settings > Vinywaji: ROOT, DIRECTOR and ADMIN describe each drink once -
 *    name, flavour, size, bottles to a crate, barcodes.
 *  - POS > Setting > Huduma: a branch ticks the drinks it sells and gives
 *    each its own selling and buying price. That makes an ordinary product
 *    of the branch (counted, sold, reported like any other), remembering
 *    which catalog drink it came from. Done again, it only changes prices.
 */
@Slf4j
@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogItemRepository catalogRepository;
    private final BarServiceRepository barServiceRepository;
    private final BarService barService;

    private static final Set<String> CATEGORIES = Set.of("DRINK", "JUICE");

    /** Everyone signed in reads it: the main office to edit, a branch to choose from. */
    @GetMapping("/items")
    public ResponseList<CatalogItem> items() {
        return new ResponseList<>(catalogRepository.findAllSorted());
    }

    @PostMapping("/save")
    public Response<CatalogItem> save(@RequestBody CatalogItem body) {
        if (!canEdit())
            return new Response<>(null, "NOT_ALLOWED", ResponseStatus.WARNING);
        if (body == null || body.getName() == null || body.getName().isBlank())
            return new Response<>(null, "Andika jina la kinywaji", ResponseStatus.WARNING);

        // The body is a fresh entity, so it always carries a uid (a new one
        // when the form sent none): an existing drink is one found by it.
        CatalogItem item;
        Optional<CatalogItem> found = body.getUid() == null ? Optional.empty()
                : catalogRepository.findById(body.getUid()).filter(c -> Boolean.TRUE.equals(c.getIsActive()));
        if (found.isPresent()) {
            item = found.get();
            item.update();
        } else {
            item = new CatalogItem();
            item.setCreatedBy(LoggerUser.getUser().getUid());
        }

        String barcode = clean(body.getBarcode());
        String packBarcode = clean(body.getPackBarcode());
        if (barcode != null && barcode.equals(packBarcode))
            return new Response<>(null, "Barcode ya chupa na ya kreti haziwezi kuwa moja", ResponseStatus.WARNING);
        for (String code : new String[]{barcode, packBarcode}) {
            if (code == null)
                continue;
            if (code.length() > 64)
                return new Response<>(null, "Barcode ni ndefu mno", ResponseStatus.WARNING);
            for (CatalogItem other : catalogRepository.findByAnyBarcode(code))
                if (!other.getUid().equals(item.getUid()))
                    return new Response<>(null, "Barcode " + code + " tayari ni ya " + other.displayName(), ResponseStatus.WARNING);
        }

        String packUnit = clean(body.getPackUnit());
        Integer perPack = body.getUnitsPerPack();
        if (packUnit != null && (perPack == null || perPack < 2))
            return new Response<>(null, "Sema chupa ngapi zinakaa kwenye " + packUnit, ResponseStatus.WARNING);

        String category = body.getCategory() == null ? "DRINK" : body.getCategory().trim().toUpperCase();
        item.setName(body.getName().trim());
        item.setFlavour(clean(body.getFlavour()));
        item.setSize(clean(body.getSize()));
        item.setCategory(CATEGORIES.contains(category) ? category : "DRINK");
        item.setUnit(clean(body.getUnit()) == null ? "Chupa" : clean(body.getUnit()));
        item.setPackUnit(packUnit);
        item.setUnitsPerPack(packUnit == null ? 1 : perPack);
        item.setBarcode(barcode);
        item.setPackBarcode(packBarcode);
        log.info(LoggerUser.getEmail() + " saved catalog drink " + item.displayName());
        return new Response<>(catalogRepository.save(item));
    }

    /** Set aside; branches that already sell it keep their product. */
    @PostMapping("/delete/{uid}")
    public Response<CatalogItem> delete(@PathVariable String uid) {
        if (!canEdit())
            return new Response<>(null, "NOT_ALLOWED", ResponseStatus.WARNING);
        Optional<CatalogItem> found = catalogRepository.findById(uid).filter(c -> Boolean.TRUE.equals(c.getIsActive()));
        if (found.isEmpty())
            return new Response<>(null, "Kinywaji hakipo", ResponseStatus.WARNING);
        found.get().delete();
        return new Response<>(catalogRepository.save(found.get()));
    }

    /** The catalog as this branch sees it: each drink, and its prices here if it already sells it. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SERVICE')")
    @GetMapping("/forBranch")
    public ResponseList<Map<String, Object>> forBranch() {
        Map<String, BarServiceEntity> mine = mineByCatalog();
        List<Map<String, Object>> out = new ArrayList<>();
        for (CatalogItem c : catalogRepository.findAllSorted()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uid", c.getUid());
            row.put("name", c.getName());
            row.put("flavour", c.getFlavour());
            row.put("size", c.getSize());
            row.put("displayName", c.displayName());
            row.put("category", c.getCategory());
            row.put("unit", c.getUnit());
            row.put("packUnit", c.getPackUnit());
            row.put("unitsPerPack", c.getUnitsPerPack());
            row.put("barcode", c.getBarcode());
            row.put("packBarcode", c.getPackBarcode());
            BarServiceEntity s = mine.get(c.getUid());
            if (s == null)
                s = handMade(c);
            row.put("added", s != null);
            row.put("serviceUid", s == null ? null : s.getUid());
            row.put("price", s == null ? null : s.getPrice());
            row.put("buyingPrice", s == null ? null : s.getBuyingPrice());
            out.add(row);
        }
        return new ResponseList<>(out);
    }

    @Data
    public static class Pick {
        private String catalogUid;
        /** Selling price of one bottle in this branch. */
        private Integer price;
        /** Buying price of one crate (one bottle without a crate) in this branch. */
        private Integer buyingPrice;
    }

    @Data
    public static class PickRequest {
        private List<Pick> items;
    }

    /**
     * Adds the ticked drinks to this branch with its prices, or changes the
     * prices of ones it already has. Each one stands alone: a refusal (a
     * barcode the branch already uses) is reported and the rest still go in.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SERVICE')")
    @PostMapping("/addToBranch")
    public ResponseList<Map<String, Object>> addToBranch(@RequestBody PickRequest request) {
        if (LoggerUser.getBranchUID() == null)
            return new ResponseList<>("Choose a branch first");
        if (request == null || request.getItems() == null || request.getItems().isEmpty())
            return new ResponseList<>("Chagua kinywaji kimoja au zaidi");

        Map<String, BarServiceEntity> mine = mineByCatalog();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Pick pick : request.getItems()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("catalogUid", pick.getCatalogUid());
            CatalogItem c = pick.getCatalogUid() == null ? null
                    : catalogRepository.findById(pick.getCatalogUid()).filter(x -> Boolean.TRUE.equals(x.getIsActive())).orElse(null);
            if (c == null) {
                r.put("ok", false);
                r.put("message", "Kinywaji hakipo");
                results.add(r);
                continue;
            }
            r.put("name", c.displayName());
            if (pick.getPrice() == null || pick.getPrice() <= 0 || pick.getBuyingPrice() == null || pick.getBuyingPrice() <= 0) {
                r.put("ok", false);
                r.put("message", "Weka bei ya kuuza na ya kununua");
                results.add(r);
                continue;
            }
            BarServiceEntity existing = mine.get(c.getUid());
            // Already sold here, made by hand before the catalog: take that
            // product over (same barcode, else same name) rather than add a
            // second Castle Lite beside it.
            if (existing == null)
                existing = handMade(c);
            BarServiceDTO dto = new BarServiceDTO();
            dto.setUid(existing == null ? null : existing.getUid());
            dto.setServiceName(existing == null ? c.displayName() : existing.getServiceName());
            dto.setDescription(existing == null ? c.getFlavour() : existing.getDescription());
            dto.setKind("SERVICE");
            dto.setStockSource("SELF");
            dto.setCategory(ProductCategory.valueOf(c.getCategory() == null ? "DRINK" : c.getCategory()).name());
            dto.setUnit(c.getUnit());
            dto.setPackUnit(c.getPackUnit());
            dto.setUnitsPerPack(c.getUnitsPerPack());
            dto.setBarcode(existing == null || existing.getBarcode() == null ? c.getBarcode() : existing.getBarcode());
            dto.setPackBarcode(existing == null || existing.getPackBarcode() == null ? c.getPackBarcode() : existing.getPackBarcode());
            dto.setPrice(pick.getPrice());
            dto.setBuyingPrice(pick.getBuyingPrice());
            dto.setTrackStock(true);
            dto.setUsageType(existing == null ? null : existing.getUsageType());
            Response<BarServiceEntity> saved = barService.saveBarService(dto);
            if (saved.getData() == null) {
                r.put("ok", false);
                r.put("message", saved.getMessage());
            } else {
                BarServiceEntity s = saved.getData();
                if (!c.getUid().equals(s.getCatalogUid())) {
                    s.setCatalogUid(c.getUid());
                    barServiceRepository.save(s);
                }
                r.put("ok", true);
                r.put("serviceUid", s.getUid());
                r.put("updated", existing != null);
            }
            results.add(r);
        }
        log.info(LoggerUser.getEmail() + " took " + results.size() + " catalog drink(s) into branch " + LoggerUser.getBranchUID());
        return new ResponseList<>(results);
    }

    private BarServiceEntity handMade(CatalogItem c) {
        String branch = LoggerUser.getBranchUID();
        for (String code : new String[]{c.getBarcode(), c.getPackBarcode()}) {
            if (code == null)
                continue;
            for (BarServiceEntity s : barServiceRepository.findByBarcode(branch, code))
                if (s.getCatalogUid() == null)
                    return s;
        }
        for (String name : new String[]{c.displayName(), c.getName()}) {
            if (name == null || name.isBlank())
                continue;
            for (BarServiceEntity s : barServiceRepository.findByNameIgnoreCase(branch, name.trim().toLowerCase()))
                if (s.getCatalogUid() == null)
                    return s;
        }
        return null;
    }

    private Map<String, BarServiceEntity> mineByCatalog() {
        Map<String, BarServiceEntity> mine = new HashMap<>();
        String branch = LoggerUser.getBranchUID();
        if (branch == null)
            return mine;
        for (BarServiceEntity s : barServiceRepository.findFromCatalog(branch))
            mine.putIfAbsent(s.getCatalogUid(), s);
        return mine;
    }

    /** ROOT, DIRECTOR and ADMIN keep the catalog. */
    private static boolean canEdit() {
        User u = LoggerUser.getUser();
        return u != null && (MainOfficeRoles.seesAllRoles(u) || MainOfficeRoles.isAdmin(u));
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
