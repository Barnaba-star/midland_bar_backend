package com.midland.bar.Bar.Table;

import com.midland.bar.Bar.Repository.BarServiceRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;

/** Tables and their QR cards (POS Setting), and the customers' window behind each card. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarTableService {

    /** No 0/O, 1/I/L - a token may be read aloud or typed off a damaged card. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int TOKEN_LENGTH = 10;
    public static final int MAX_TABLES = 300;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BarTableRepository tableRepository;
    private final BarServiceRepository barServiceRepository;
    private final BranchRepository branchRepository;
    private final com.midland.bar.Bar.Service.PriceRuleService priceRuleService;
    private final BarTableInfoRepository infoRepository;
    private final BillQrService billQrService;

    /**
     * The customers' window, kept 60 s per token: a whole table scanning at
     * once, or a customer scrolling back, is answered without the database.
     * Any change to the tables clears it; a price change shows within a minute.
     */
    private static final long VIEW_TTL_MS = 60_000;
    private final java.util.concurrent.ConcurrentHashMap<String, Map.Entry<Long, Map<String, Object>>> viewCache = new java.util.concurrent.ConcurrentHashMap<>();

    public ResponseList<BarTable> list() {
        return new ResponseList<>(tableRepository.findAllInBranch(LoggerUser.getBranchUIDOrMain()));
    }

    /**
     * The branch has this many tables: missing ones are added as "Meza n",
     * extra ones (the highest numbers) are removed - their cards stop working.
     */
    @Transactional
    public ResponseList<BarTable> setCount(Integer count) {
        if (count == null || count < 0 || count > MAX_TABLES)
            return new ResponseList<>(null, "Number of tables must be between 0 and " + MAX_TABLES);
        String branch = LoggerUser.getBranchUIDOrMain();
        List<BarTable> tables = new ArrayList<>(tableRepository.findAllInBranch(branch));
        int highest = tables.stream().mapToInt(BarTable::getNumber).max().orElse(0);
        while (tables.size() < count) {
            BarTable t = new BarTable();
            t.setNumber(++highest);
            t.setName("Meza " + highest);
            t.setToken(newToken());
            tables.add(tableRepository.save(t));
        }
        tables.sort(Comparator.comparing(BarTable::getNumber));
        while (tables.size() > count) {
            BarTable last = tables.remove(tables.size() - 1);
            last.delete();
            tableRepository.save(last);
        }
        viewCache.clear();
        log.info("{} set {} tables in branch {}", LoggerUser.getEmail(), count, branch);
        return new ResponseList<>(tables);
    }

    @Transactional
    public Response<BarTable> rename(String uid, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 40)
            return new Response<>("A table name is 1 to 40 characters");
        BarTable t = tableRepository.findInBranch(uid, LoggerUser.getBranchUIDOrMain()).orElse(null);
        if (t == null)
            return new Response<>("Table Not Found");
        t.setName(n);
        viewCache.clear();
        return new Response<>(tableRepository.save(t));
    }

    /** A new secret for one table: its old card stops working, print a new one. */
    @Transactional
    public Response<BarTable> newCard(String uid) {
        BarTable t = tableRepository.findInBranch(uid, LoggerUser.getBranchUIDOrMain()).orElse(null);
        if (t == null)
            return new Response<>("Table Not Found");
        t.setToken(newToken());
        viewCache.clear();
        log.info("{} gave {} a new QR card", LoggerUser.getEmail(), t.getName());
        return new Response<>(tableRepository.save(t));
    }

    /**
     * What a customer's phone sees after scanning a table's card: the bar,
     * the table and the menu with prices. No login - the token is the key,
     * and it unlocks only this read-only view.
     */
    @Transactional(readOnly = true)
    public Response<Map<String, Object>> customerView(String token) {
        String key = token == null ? "" : token.trim().toUpperCase();
        if (key.length() != TOKEN_LENGTH && key.length() != BillQrService.TOKEN_LENGTH)
            return new Response<>("TABLE_NOT_FOUND");
        Map.Entry<Long, Map<String, Object>> cached = viewCache.get(key);
        if (cached != null && cached.getKey() > System.currentTimeMillis())
            return new Response<>(cached.getValue());
        // A bill's own QR: the same window, for that bill only (owner, 2026-11-10).
        var bill = key.length() == BillQrService.TOKEN_LENGTH ? billQrService.billFor(key).orElse(null) : null;
        BarTable t = bill != null ? BillQrService.asTable(bill, key) : tableRepository.findByToken(key).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("tableName", t.getName());
        view.put("tableNumber", t.getNumber());
        if (bill != null) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("code", bill.getSalesCode());
            b.put("staffName", bill.getStaffName());
            b.put("open", "PENDING".equals(bill.getPaymentStatus()));
            view.put("bill", b);
        }
        branchRepository.findById(t.getBranchUid()).ifPresent(b -> view.put("branchName", b.getBranchName()));
        List<Map<String, Object>> menu = new ArrayList<>();
        var all = barServiceRepository.findAllBarServiceList(t.getBranchUid(), 0);
        // Units on hand of everything in the branch, for "Kimeisha" (sold out).
        Map<String, Integer> onHand = new HashMap<>();
        all.forEach(s -> onHand.put(s.getUid(), s.getStockQuantity() == null ? 0 : s.getStockQuantity()));
        List<com.midland.bar.Bar.Projection.BarProjection> sellable = new ArrayList<>();
        all.forEach(s -> {
            if ("STOCK_ITEM".equals(s.getKind()) || s.getPrice() == null || s.getPrice() <= 0)
                return;
            if (s.getStatus() != null && !"active".equalsIgnoreCase(s.getStatus()))
                return;
            sellable.add(s);
        });
        // Offers running now (Happy hour etc.) - the same pricing a sale uses, for one unit.
        Map<String, com.midland.bar.Bar.Model.BarServiceEntity> entities = new HashMap<>();
        barServiceRepository.findAllById(sellable.stream().map(com.midland.bar.Bar.Projection.BarProjection::getUid).toList())
                .forEach(e -> entities.put(e.getUid(), e));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (var s : sellable) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("uid", s.getUid());
            item.put("name", s.getServiceName());
            item.put("price", s.getPrice());
            item.put("category", s.getCategory());
            item.put("description", s.getDescription());
            item.put("available", available(s, onHand));
            var e = entities.get(s.getUid());
            if (e != null) {
                try {
                    var p = priceRuleService.price(e, 1, now, null);
                    if (p.unitPrice() < s.getPrice()) {
                        item.put("offerPrice", p.unitPrice());
                        item.put("offerName", p.ruleName());
                    }
                } catch (Exception ex) {
                    log.debug("no offer price for {}: {}", s.getServiceName(), ex.getMessage());
                }
            }
            menu.add(item);
        }
        view.put("menu", menu);
        infoRepository.findInBranch(t.getBranchUid()).ifPresent(i -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("wifiName", i.getWifiName());
            info.put("wifiPassword", i.getWifiPassword());
            info.put("hours", i.getHours());
            info.put("phone", i.getPhone());
            info.put("note", i.getNote());
            info.put("payMpesa", i.getPayMpesa());
            info.put("payTigo", i.getPayTigo());
            info.put("payAirtel", i.getPayAirtel());
            info.put("payHalo", i.getPayHalo());
            info.put("payBank", i.getPayBank());
            view.put("info", info);
        });
        if (viewCache.size() > 2000)
            viewCache.clear();
        viewCache.put(key, Map.entry(System.currentTimeMillis() + VIEW_TTL_MS, view));
        return new Response<>(view);
    }

    /** Info or prices changed: customers see it on their next load. */
    public void forgetViews() {
        viewCache.clear();
    }

    /** Can one be sold right now - the same stock rule a sale applies (BarService.addItemsToBill). */
    static boolean available(com.midland.bar.Bar.Projection.BarProjection s, Map<String, Integer> onHand) {
        if (s.getStockSourceUid() != null) {
            int perSale = s.getUnitsPerSale() == null || s.getUnitsPerSale() < 1 ? 1 : s.getUnitsPerSale();
            if (onHand.getOrDefault(s.getStockSourceUid(), 0) < perSale)
                return false;
            for (var in : com.midland.bar.Bar.Service.Ingredients.parse(s.getExtraIngredients()))
                if (onHand.getOrDefault(in.uid(), 0) < Math.max(1, in.units()))
                    return false;
            return true;
        }
        if (Boolean.TRUE.equals(s.getTrackStock()))
            return s.getStockQuantity() != null && s.getStockQuantity() >= 1;
        return true;
    }

    private String newToken() {
        for (int tries = 0; tries < 20; tries++) {
            StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
            for (int i = 0; i < TOKEN_LENGTH; i++)
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            String token = sb.toString();
            if (tableRepository.countToken(token) == 0)
                return token;
        }
        throw new IllegalStateException("Could not find a free table token");
    }
}
