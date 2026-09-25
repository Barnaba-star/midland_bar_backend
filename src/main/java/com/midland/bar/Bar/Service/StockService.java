package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Dto.StockAdjustmentDTO;
import com.midland.bar.Bar.Dto.StockReceiptDTO;
import com.midland.bar.Bar.Model.AdjustmentReason;
import com.midland.bar.Bar.Model.StockAdjustment;
import com.midland.bar.Bar.Projection.StockAdjustmentProjection;
import com.midland.bar.Bar.Repository.StockAdjustmentRepository;
import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Model.StockReceipt;
import com.midland.bar.Bar.Projection.StockMovementProjection;
import com.midland.bar.Bar.Projection.StockReceiptProjection;
import com.midland.bar.Bar.Repository.BarServiceRepository;
import com.midland.bar.Bar.Repository.StockReceiptRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponsePage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/** Stock coming into the store. Sales take it out (see BarService). */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final BarServiceRepository barServiceRepository;
    private final StockReceiptRepository stockReceiptRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;

    /**
     * Records a delivery and raises the service's count by it. The service's
     * row is locked for the length of the transaction so a sale landing at
     * the same moment cannot read the old count and write over this one.
     */
    @Transactional
    public Response<StockReceipt> addStock(StockReceiptDTO dto) {
        Optional<BarServiceEntity> found =
                barServiceRepository.findForUpdate(dto.getBarServiceUID(), LoggerUser.getBranchUID());
        if (found.isEmpty())
            return new Response<>("Service Not Found");
        BarServiceEntity service = found.get();

        if (!Boolean.TRUE.equals(service.getTrackStock()))
            return new Response<>(service.getServiceName() + " is not counted in the store");

        int perPack = service.getUnitsPerPack() == null || service.getUnitsPerPack() < 1 ? 1 : service.getUnitsPerPack();
        boolean hasPack = service.getPackUnit() != null && perPack > 1;
        int packs = hasPack && dto.getPacks() != null ? dto.getPacks() : 0;
        int loose = dto.getLooseUnits() == null ? 0 : dto.getLooseUnits();
        int unitsAdded = packs * perPack + loose;
        if (unitsAdded <= 0)
            return new Response<>("Enter how much stock arrived");

        // The delivery's own price when given; otherwise what the service
        // was last bought at. Either way it becomes the service's buying
        // price, so the store's value follows the latest cost.
        int packPrice = dto.getPackPrice() != null
                ? dto.getPackPrice()
                : (service.getBuyingPrice() == null ? 0 : service.getBuyingPrice());

        int stockAfter = (service.getStockQuantity() == null ? 0 : service.getStockQuantity()) + unitsAdded;
        service.setStockQuantity(stockAfter);
        service.setBuyingPrice(packPrice);
        service.update();
        barServiceRepository.save(service);

        StockReceipt receipt = new StockReceipt();
        receipt.setBarService(service);
        receipt.setPacks(packs);
        receipt.setLooseUnits(loose);
        receipt.setUnitsPerPack(perPack);
        receipt.setUnitsAdded(unitsAdded);
        receipt.setPackPrice(packPrice);
        receipt.setTotalCost(Math.round((double) unitsAdded * packPrice / perPack));
        receipt.setSupplier(trimToNull(dto.getSupplier()));
        receipt.setNote(trimToNull(dto.getNote()));
        receipt.setReceivedBy(LoggerUser.getEmail());
        receipt.setReceivedAt(LocalDateTime.now());
        receipt.setStockAfter(stockAfter);
        StockReceipt saved = stockReceiptRepository.save(receipt);

        log.info("{} added {} units of {} (now {})", LoggerUser.getEmail(), unitsAdded, service.getServiceName(), stockAfter);
        return new Response<>(saved);
    }

    /**
     * Corrects a count outside sales and deliveries. REMOVE takes units out
     * for a reason (spoiled, broken, shrank on the grill); COUNT sets the
     * count to what was found on the shelf and records the difference either
     * way. The row is locked, as for a delivery, so a sale landing at the
     * same moment is not lost.
     */
    @Transactional
    public Response<StockAdjustment> adjustStock(StockAdjustmentDTO dto) {
        Optional<BarServiceEntity> found =
                barServiceRepository.findForUpdate(dto.getBarServiceUID(), LoggerUser.getBranchUID());
        if (found.isEmpty())
            return new Response<>("Service Not Found");
        BarServiceEntity item = found.get();
        if (!Boolean.TRUE.equals(item.getTrackStock()))
            return new Response<>(item.getServiceName() + " is not counted in the store");

        int before = item.getStockQuantity() == null ? 0 : item.getStockQuantity();
        int units = dto.getUnits() == null ? 0 : dto.getUnits();
        int change;
        String reason;
        if ("REMOVE".equals(dto.getMode())) {
            if (units <= 0)
                return new Response<>("Enter how much to remove");
            if (units > before)
                return new Response<>("Only " + before + " left - cannot remove " + units);
            if (!AdjustmentReason.isValid(dto.getReason()))
                return new Response<>("Choose why it is being removed");
            if (AdjustmentReason.OTHER.name().equals(dto.getReason()) && trimToNull(dto.getNote()) == null)
                return new Response<>("Say what happened in the note");
            change = -units;
            reason = dto.getReason();
        } else {
            change = units - before;
            if (change == 0)
                return new Response<>("The count matches the store - nothing to correct");
            reason = "COUNT";
        }

        int after = before + change;
        item.setStockQuantity(after);
        item.update();
        barServiceRepository.save(item);

        int perPack = item.getUnitsPerPack() == null || item.getUnitsPerPack() < 1 ? 1 : item.getUnitsPerPack();
        long costValue = Math.round((double) change * (item.getBuyingPrice() == null ? 0 : item.getBuyingPrice()) / perPack);

        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setBarService(item);
        adjustment.setMode(dto.getMode());
        adjustment.setReason(reason);
        adjustment.setUnitsChanged(change);
        adjustment.setStockBefore(before);
        adjustment.setStockAfter(after);
        adjustment.setCostValue(costValue);
        adjustment.setNote(trimToNull(dto.getNote()));
        adjustment.setAdjustedBy(LoggerUser.getEmail());
        adjustment.setAdjustedAt(LocalDateTime.now());
        StockAdjustment saved = stockAdjustmentRepository.save(adjustment);

        log.info("{} adjusted {} by {} ({}), now {}", LoggerUser.getEmail(), item.getServiceName(), change, reason, after);
        return new Response<>(saved);
    }

    public ResponsePage<StockAdjustmentProjection> findAdjustments(String serviceUID, Integer page, Integer size) {
        return new ResponsePage<>(stockAdjustmentRepository.findByService(
                LoggerUser.getBranchUID(), serviceUID, PageRequest.of(page == null ? 0 : page, size == null ? 10 : size)));
    }

    public ResponsePage<StockReceiptProjection> findReceipts(String serviceUID, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        return new ResponsePage<>(stockReceiptRepository.findByService(
                LoggerUser.getBranchUID(), serviceUID, PageRequest.of(p, s)));
    }

    /**
     * Bought and used per service between two dates, both inclusive.
     * Missing dates default to the current month so far.
     */
    public ResponsePage<StockMovementProjection> findMovements(String search, LocalDate fromDate, LocalDate toDate,
                                                               Integer page, Integer size) {
        LocalDate to = toDate == null ? LocalDate.now() : toDate;
        LocalDate from = fromDate == null ? to.withDayOfMonth(1) : fromDate;
        if (from.isAfter(to))
            return new ResponsePage<>("The start date is after the end date");
        return new ResponsePage<>(stockReceiptRepository.findMovements(
                LoggerUser.getBranchUID(), likePattern(search),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                from, to,
                PageRequest.of(page == null ? 0 : page, size == null ? 10 : size)));
    }

    /** Same matching as the Services page search. */
    private static String likePattern(String search) {
        if (search == null || search.isBlank())
            return "%";
        String escaped = search.trim().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
