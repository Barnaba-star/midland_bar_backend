package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Dto.StockTakeDTO;
import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Model.StockAdjustment;
import com.midland.bar.Bar.Model.StockTake;
import com.midland.bar.Bar.Model.StockTakeLine;
import com.midland.bar.Bar.Repository.BarServiceRepository;
import com.midland.bar.Bar.Repository.StockAdjustmentRepository;
import com.midland.bar.Bar.Repository.StockTakeLineRepository;
import com.midland.bar.Bar.Repository.StockTakeRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counting the store at one go. Each product's shelf count is set against
 * what the system held; a difference becomes a COUNT adjustment (the store
 * now holds what was counted) and the whole count is kept, so the variance
 * report can say what went missing, of what, and what it was worth.
 */
@Service
@RequiredArgsConstructor
public class StockTakeService {

    private final BarServiceRepository barServiceRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final StockTakeRepository stockTakeRepository;
    private final StockTakeLineRepository lineRepository;

    /** What a count goes through: every product counted in the store, with what the system holds now. */
    public ResponseList<Map<String, Object>> countable() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BarServiceEntity s : barServiceRepository.findCountable(LoggerUser.getBranchUID())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uid", s.getUid());
            row.put("serviceName", s.getServiceName());
            row.put("serviceCode", s.getServiceCode());
            row.put("unit", s.getUnit());
            row.put("packUnit", s.getPackUnit());
            row.put("unitsPerPack", perPack(s));
            row.put("stockQuantity", s.getStockQuantity() == null ? 0 : s.getStockQuantity());
            row.put("buyingPrice", s.getBuyingPrice() == null ? 0 : s.getBuyingPrice());
            rows.add(row);
        }
        return new ResponseList<>(rows);
    }

    @Transactional
    public Response<StockTake> submit(StockTakeDTO dto) {
        String branchUID = LoggerUser.getBranchUID();
        if (dto == null || dto.getLines() == null || dto.getLines().isEmpty())
            return new Response<>("Count at least one product");
        Set<String> seen = new HashSet<>();
        for (StockTakeDTO.Line line : dto.getLines()) {
            if (line.getBarServiceUID() == null || !seen.add(line.getBarServiceUID()))
                return new Response<>("Each product may be counted once");
            if (line.getCountedUnits() == null || line.getCountedUnits() < 0)
                return new Response<>("Enter a count of zero or more for every product");
        }

        LocalDateTime now = LocalDateTime.now();
        StockTake take = new StockTake();
        take.setTakenBy(LoggerUser.getEmail());
        take.setTakenByName(nameOf(LoggerUser.getUser()));
        take.setTakenAt(now);
        String note = dto.getNote() == null ? null : dto.getNote().trim();
        take.setNote(note == null || note.isEmpty() ? null : (note.length() > 500 ? note.substring(0, 500) : note));
        take.setItemsCounted(dto.getLines().size());
        take = stockTakeRepository.save(take);

        int different = 0;
        long loss = 0, gain = 0;
        for (StockTakeDTO.Line in : dto.getLines()) {
            BarServiceEntity item = barServiceRepository.findForUpdate(in.getBarServiceUID(), branchUID).orElse(null);
            if (item == null || !Boolean.TRUE.equals(item.getTrackStock()))
                throw new IllegalArgumentException("A product on the count is not counted in the store");
            int before = item.getStockQuantity() == null ? 0 : item.getStockQuantity();
            int counted = in.getCountedUnits();
            int diff = counted - before;
            int per = perPack(item);
            long value = Math.round((double) diff * (item.getBuyingPrice() == null ? 0 : item.getBuyingPrice()) / per);

            StockTakeLine line = new StockTakeLine();
            line.setStockTake(take);
            line.setBarServiceUid(item.getUid());
            line.setServiceName(item.getServiceName());
            line.setServiceCode(item.getServiceCode());
            line.setUnit(item.getUnit());
            line.setPackUnit(item.getPackUnit());
            line.setUnitsPerPack(per);
            line.setSystemUnits(before);
            line.setCountedUnits(counted);
            line.setDifferenceUnits(diff);
            line.setDifferenceValue(value);
            lineRepository.save(line);

            if (diff != 0) {
                different++;
                if (value < 0) loss += value; else gain += value;
                item.setStockQuantity(counted);
                item.update();
                barServiceRepository.save(item);

                StockAdjustment adjustment = new StockAdjustment();
                adjustment.setBarService(item);
                adjustment.setMode("COUNT");
                adjustment.setReason("COUNT");
                adjustment.setUnitsChanged(diff);
                adjustment.setStockBefore(before);
                adjustment.setStockAfter(counted);
                adjustment.setCostValue(value);
                adjustment.setNote("Stock take");
                adjustment.setAdjustedBy(LoggerUser.getEmail());
                adjustment.setAdjustedAt(now);
                adjustment.setStockTakeUid(take.getUid());
                stockAdjustmentRepository.save(adjustment);
            }
        }
        take.setItemsDifferent(different);
        take.setLossValue(loss);
        take.setGainValue(gain);
        return new Response<>(stockTakeRepository.save(take));
    }

    public ResponseList<StockTake> findTaken(String filter) {
        LocalDateTime[] range = ReportRange.of(filter);
        return new ResponseList<>(stockTakeRepository.findTaken(LoggerUser.getBranchUID(), range[0], range[1]));
    }

    public ResponseList<StockTakeLine> findLines(String takeUid) {
        if (stockTakeRepository.findInBranch(takeUid, LoggerUser.getBranchUID()).isEmpty())
            return new ResponseList<>("Stock take not found");
        return new ResponseList<>(lineRepository.findByTake(takeUid));
    }

    /** Per product over a period: how often counted, units missing or over, and what that was worth. */
    public ResponseList<Map<String, Object>> varianceByProduct(String filter) {
        LocalDateTime[] range = ReportRange.of(filter);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] r : lineRepository.varianceByProduct(LoggerUser.getBranchUID(), range[0], range[1])) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("barServiceUid", r[0]);
            row.put("serviceName", r[1]);
            row.put("serviceCode", r[2]);
            row.put("unit", r[3]);
            row.put("packUnit", r[4]);
            row.put("unitsPerPack", r[5]);
            row.put("counts", ((Number) r[6]).intValue());
            row.put("differenceUnits", r[7] == null ? 0 : ((Number) r[7]).intValue());
            row.put("differenceValue", r[8] == null ? 0 : ((Number) r[8]).longValue());
            rows.add(row);
        }
        return new ResponseList<>(rows);
    }

    private static int perPack(BarServiceEntity s) {
        return s.getUnitsPerPack() == null || s.getUnitsPerPack() < 1 ? 1 : s.getUnitsPerPack();
    }

    private static String nameOf(User user) {
        if (user == null)
            return null;
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getUsername() : name;
    }
}
