package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Dto.SaleItemsDTO;
import com.midland.bar.Bar.Dto.StaffOrderItemDTO;
import com.midland.bar.Bar.Dto.StaffOrderRejectDTO;
import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Model.SalesOpened;
import com.midland.bar.Bar.Model.ServiceKind;
import com.midland.bar.Bar.Model.StaffOrder;
import com.midland.bar.Bar.Model.StaffOrderLine;
import com.midland.bar.Bar.Repository.BarServiceRepository;
import com.midland.bar.Bar.Repository.SalesOpenedRepository;
import com.midland.bar.Bar.Repository.StaffOrderRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Exceptions.BusinessException;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Staff orders and the supervisor. At Staff Sell, "Add Service" writes onto
 * the bill's waiting (DRAFT) order instead of the bill. Handing over - switch
 * staff, or Send - sends it. The supervisor receives it, which is the moment
 * it becomes a sale (stock, bill, split, the staff member's commission), or
 * rejects it with a reason. Until then the drinks do not leave the counter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffOrderService {

    /** Selling and paying out need the login's shift open. */
    private final WorkShiftService workShiftService;
    private final StaffOrderRepository staffOrderRepository;
    private final SalesOpenedRepository salesOpenedRepository;
    private final BarServiceRepository barServiceRepository;
    private final BarService barService;
    private final com.midland.bar.Utils.Offline.OfflineOps offlineOps;

    /** Rejected orders stay on the staff member's bill this long, so they see why. */
    private static final int REJECTED_SHOWN_HOURS = 12;

    /** Write an item onto the bill's waiting order (one is started if there is none). */
    @Transactional
    public Response<StaffOrder> addItem(StaffOrderItemDTO dto) {
        workShiftService.requireOpen();
        String branchUID = branch();
        SalesOpened bill = salesOpenedRepository.findById(dto.getSalesOpenedUID())
                .filter(b -> branchUID.equals(b.getBranchUid()))
                .orElse(null);
        if (bill == null)
            return new Response<>("Open Sale Not Found");
        com.midland.bar.Config.Security.StaffSession.requireOwnBill(bill);
        if (!"PENDING".equals(bill.getPaymentStatus()))
            return new Response<>("Bill " + bill.getSalesCode() + " is already paid - open a new bill");
        if (bill.getStaffUid() == null)
            return new Response<>("Bill " + bill.getSalesCode() + " is not a staff bill");

        BarServiceEntity service = barServiceRepository.findBarServiceByUID(dto.getBarServiceUID(), LoggerUser.getBranchUIDOrMain()).orElse(null);
        if (service == null)
            return new Response<>("Service Not Found");
        if (ServiceKind.of(service.getKind()) == ServiceKind.STOCK_ITEM)
            return new Response<>(service.getServiceName() + " is kept in the store, not sold - sell a service made from it");
        if (service.getPrice() == null || service.getPrice() <= 0)
            return new Response<>("No selling price set for " + service.getServiceName());

        StaffOrder order = staffOrderRepository.findDraft(bill.getUid(), branchUID).orElseGet(() -> {
            StaffOrder o = new StaffOrder();
            o.setSalesOpenedUid(bill.getUid());
            o.setSalesCode(bill.getSalesCode());
            o.setStaffUid(bill.getStaffUid());
            o.setStaffCode(bill.getStaffCode());
            o.setStaffName(bill.getStaffName());
            return o;
        });
        // The same service twice is one line.
        Optional<StaffOrderLine> same = order.getLines().stream()
                .filter(l -> l.getBarServiceUid().equals(service.getUid()))
                .findFirst();
        if (same.isPresent()) {
            same.get().setQuantity(same.get().getQuantity() + dto.getQuantity());
            same.get().setUnitPrice(service.getPrice());
        } else {
            StaffOrderLine line = new StaffOrderLine();
            line.setOrder(order);
            line.setBarServiceUid(service.getUid());
            line.setServiceName(service.getServiceName());
            line.setQuantity(dto.getQuantity());
            line.setUnitPrice(service.getPrice());
            order.getLines().add(line);
        }
        return new Response<>(staffOrderRepository.save(order));
    }

    /** Take a line off an order the staff member has not sent yet. */
    @Transactional
    public Response<StaffOrder> removeLine(String orderUid, String lineUid) {
        StaffOrder order = staffOrderRepository.findByUid(orderUid, branch()).orElse(null);
        if (order == null)
            return new Response<>("Order Not Found");
        com.midland.bar.Config.Security.StaffSession.requireOwnStaff(order.getStaffUid());
        if (!StaffOrder.DRAFT.equals(order.getStatus()))
            return new Response<>("This order is already with the supervisor");
        boolean removed = order.getLines().removeIf(l -> l.getUid().equals(lineUid));
        if (!removed)
            return new Response<>("Item Not Found");
        if (order.getLines().isEmpty()) {
            staffOrderRepository.delete(order);
            return new Response<>(order);
        }
        return new Response<>(staffOrderRepository.save(order));
    }

    /** Hand everything the staff member has written to the supervisor. Returns how many orders went. */
    @Transactional
    public Response<Integer> send(BarStaff staff) {
        LocalDateTime now = LocalDateTime.now();
        int sent = 0;
        for (StaffOrder order : staffOrderRepository.findDraftsByStaff(staff.getUid(), branch())) {
            if (order.getLines().isEmpty()) {
                staffOrderRepository.delete(order);
                continue;
            }
            order.setStatus(StaffOrder.SENT);
            order.setSentAt(now);
            staffOrderRepository.save(order);
            sent++;
        }
        if (sent > 0)
            log.info("{} sent {} order(s) to the supervisor", staff.getStaffCode(), sent);
        return new Response<>(sent);
    }

    /** What the supervisor still has to decide, oldest first. */
    public Response<List<StaffOrder>> pending() {
        return new Response<>(staffOrderRepository.findPending(branch()));
    }

    /**
     * Receive: the drinks may leave. The order becomes a sale on its bill -
     * stock, bill, split and the staff member's commission - through the same
     * code as any sale. If that refuses (not enough stock, bill already paid)
     * nothing changes and the supervisor sees why.
     */
    @Transactional
    public Response<StaffOrder> receive(String orderUid) {
        StaffOrder order = staffOrderRepository.findForUpdate(orderUid, branch()).orElse(null);
        if (order == null)
            return new Response<>("Order Not Found");
        if (!StaffOrder.SENT.equals(order.getStatus()))
            return new Response<>("Order on " + order.getSalesCode() + " has already been " + order.getStatus().toLowerCase());

        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(order.getSalesOpenedUid());
        List<SaleItemsDTO.Item> items = new ArrayList<>();
        for (StaffOrderLine line : order.getLines()) {
            SaleItemsDTO.Item item = new SaleItemsDTO.Item();
            item.setBarServiceUID(line.getBarServiceUid());
            item.setQuantity(line.getQuantity());
            items.add(item);
        }
        sale.setItems(items);
        Response<SalesOpened> result = barService.addItemsToBill(sale);
        if (result.getData() == null)
            // Rolls the whole receive back and reaches the supervisor as the reason.
            throw new BusinessException(result.getMessage());

        order.setStatus(StaffOrder.RECEIVED);
        order.setDecidedAt(LocalDateTime.now());
        order.setDecidedBy(LoggerUser.getEmail());
        log.info("{} received order on {} from {}", order.getDecidedBy(), order.getSalesCode(), order.getStaffCode());
        return new Response<>(staffOrderRepository.save(order));
    }

    /** Reject: nothing goes on the bill; the staff member sees the reason. */
    @Transactional
    public Response<StaffOrder> reject(String orderUid, StaffOrderRejectDTO dto) {
        StaffOrder order = staffOrderRepository.findForUpdate(orderUid, branch()).orElse(null);
        if (order == null)
            return new Response<>("Order Not Found");
        if (!StaffOrder.SENT.equals(order.getStatus()))
            return new Response<>("Order on " + order.getSalesCode() + " has already been " + order.getStatus().toLowerCase());
        order.setStatus(StaffOrder.REJECTED);
        order.setRejectReason(dto.getReason().trim());
        order.setDecidedAt(LocalDateTime.now());
        order.setDecidedBy(LoggerUser.getEmail());
        log.info("{} rejected order on {} from {}: {}", order.getDecidedBy(), order.getSalesCode(), order.getStaffCode(), order.getRejectReason());
        return new Response<>(staffOrderRepository.save(order));
    }

    /** For Staff Sell: the orders to show on these bills - waiting, and recently rejected. */
    /**
     * An order written at Staff Sell with no internet. No supervisor could see
     * it then, so it went straight onto the bill on the device; now it does so
     * here - stock, split, the staff member's commission, at the time it was
     * written - and is kept RECEIVED and marked offline, for the supervisor to
     * look over. Sent twice, it is applied once.
     */
    @Transactional
    public Response<StaffOrder> recordOffline(com.midland.bar.Bar.Dto.StaffOfflineOrderDTO dto) {
        Optional<String> done = offlineOps.alreadyApplied();
        if (done.isPresent())
            return staffOrderRepository.findById(done.get()).map(Response::new).orElseGet(() -> new Response<>("Order Not Found"));
        workShiftService.requireOpen();
        String branchUID = branch();
        SalesOpened bill = dto == null || dto.getSalesOpenedUID() == null ? null
                : salesOpenedRepository.findById(dto.getSalesOpenedUID()).filter(b -> branchUID.equals(b.getBranchUid())).orElse(null);
        if (bill == null)
            return new Response<>("Open Sale Not Found");
        com.midland.bar.Config.Security.StaffSession.requireOwnBill(bill);
        if (bill.getStaffUid() == null)
            return new Response<>("Bill " + bill.getSalesCode() + " is not a staff bill");
        if (dto.getItems() == null || dto.getItems().isEmpty())
            return new Response<>("Nothing on the order");

        LocalDateTime at = com.midland.bar.Utils.Offline.OfflineContext.now();
        StaffOrder order = new StaffOrder();
        order.setSalesOpenedUid(bill.getUid());
        order.setSalesCode(bill.getSalesCode());
        order.setStaffUid(bill.getStaffUid());
        order.setStaffCode(bill.getStaffCode());
        order.setStaffName(bill.getStaffName());
        order.setStatus(StaffOrder.RECEIVED);
        order.setOffline(true);
        order.setSentAt(at);
        order.setDecidedAt(at);
        order.setDecidedBy(LoggerUser.getEmail());
        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(bill.getUid());
        List<SaleItemsDTO.Item> items = new ArrayList<>();
        for (com.midland.bar.Bar.Dto.StaffOfflineOrderDTO.Item it : dto.getItems()) {
            BarServiceEntity service = barServiceRepository.findBarServiceByUID(it.getBarServiceUID(), branchUID).orElse(null);
            if (service == null)
                return new Response<>("Service Not Found");
            int qty = it.getQuantity() == null || it.getQuantity() < 1 ? 1 : it.getQuantity();
            StaffOrderLine line = new StaffOrderLine();
            line.setOrder(order);
            line.setBarServiceUid(service.getUid());
            line.setServiceName(service.getServiceName());
            line.setQuantity(qty);
            line.setUnitPrice(service.getPrice());
            order.getLines().add(line);
            SaleItemsDTO.Item item = new SaleItemsDTO.Item();
            item.setBarServiceUID(service.getUid());
            item.setQuantity(qty);
            items.add(item);
        }
        sale.setItems(items);
        Response<SalesOpened> result = barService.addItemsToBill(sale);
        if (result.getData() == null)
            throw new BusinessException(result.getMessage());
        StaffOrder saved = staffOrderRepository.save(order);
        offlineOps.claim("STAFF_ORDER", saved.getUid());
        return new Response<>(saved);
    }

    /** Offline orders still to be looked over by the supervisor, oldest first. */
    public Response<List<StaffOrder>> offlineUnreviewed() {
        return new Response<>(staffOrderRepository.findOfflineUnreviewed(branch()));
    }

    /** The supervisor has looked an offline order over. */
    @Transactional
    public Response<StaffOrder> review(String orderUid) {
        StaffOrder order = staffOrderRepository.findById(orderUid).filter(o -> branch().equals(o.getBranchUid())).orElse(null);
        if (order == null || !Boolean.TRUE.equals(order.getOffline()))
            return new Response<>("Order Not Found");
        order.setReviewedBy(LoggerUser.getEmail());
        order.setReviewedAt(LocalDateTime.now());
        return new Response<>(staffOrderRepository.save(order));
    }

    public List<StaffOrder> shownOn(Collection<String> billUids) {
        if (billUids.isEmpty())
            return List.of();
        return staffOrderRepository.findShownOnBills(billUids, branch(), LocalDateTime.now().minusHours(REJECTED_SHOWN_HOURS));
    }

    private static String branch() {
        return LoggerUser.getBranchUIDOrMain();
    }
}
