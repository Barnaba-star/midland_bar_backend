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
    public Response<Map<String, Object>> customerView(String token) {
        String key = token == null ? "" : token.trim().toUpperCase();
        if (key.length() != TOKEN_LENGTH)
            return new Response<>("TABLE_NOT_FOUND");
        Map.Entry<Long, Map<String, Object>> cached = viewCache.get(key);
        if (cached != null && cached.getKey() > System.currentTimeMillis())
            return new Response<>(cached.getValue());
        BarTable t = tableRepository.findByToken(key).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("tableName", t.getName());
        view.put("tableNumber", t.getNumber());
        branchRepository.findById(t.getBranchUid()).ifPresent(b -> view.put("branchName", b.getBranchName()));
        List<Map<String, Object>> menu = new ArrayList<>();
        barServiceRepository.findAllBarServiceList(t.getBranchUid(), 0).forEach(s -> {
            if ("STOCK_ITEM".equals(s.getKind()) || s.getPrice() == null || s.getPrice() <= 0)
                return;
            if (s.getStatus() != null && !"active".equalsIgnoreCase(s.getStatus()))
                return;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", s.getServiceName());
            item.put("price", s.getPrice());
            item.put("category", s.getCategory());
            item.put("description", s.getDescription());
            menu.add(item);
        });
        view.put("menu", menu);
        if (viewCache.size() > 2000)
            viewCache.clear();
        viewCache.put(key, Map.entry(System.currentTimeMillis() + VIEW_TTL_MS, view));
        return new Response<>(view);
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
