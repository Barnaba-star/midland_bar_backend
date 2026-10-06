package com.midland.bar.Bar.Controller;

import com.midland.bar.Bar.Dto.*;
import com.midland.bar.Bar.Model.*;
import com.midland.bar.Bar.Projection.*;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Bar.Service.StockService;
import com.midland.bar.Bar.Service.BillCodeService;
import com.midland.bar.Bar.Service.BillPaymentService;
import com.midland.bar.Bar.Service.ServiceAndStoreReportResponse;
import com.midland.bar.Uaa.Dto.AssignUserRoleDTO;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Service.UserService;
import com.midland.bar.Utils.PageableParam;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import com.midland.bar.Utils.Responses.ResponsePage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/bar")
@RequiredArgsConstructor
public class BarController {
    private final BarService barService;
    private final com.midland.bar.Bar.Service.OtherCommissionService otherCommissionService;
    private final StockService stockService;
    private final com.midland.bar.Bar.Service.InsightService insightService;
    private final com.midland.bar.Bar.Service.CashUpService cashUpService;
    private final com.midland.bar.Bar.Service.WorkShiftService workShiftService;
    private final com.midland.bar.Bar.Service.StockTakeService stockTakeService;
    private final BillCodeService billCodeService;
    private final BillPaymentService billPaymentService;
    private final com.midland.bar.Bar.Service.StaffSellService staffSellService;
    private final com.midland.bar.Bar.Live.LiveEvents liveEvents;
    private final com.midland.bar.Bar.Service.StaffOrderService staffOrderService;
    private final UserService userService;

    /***
     METHODS FOR BAR SETTING
     ***/
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_COMMISSION')")
    @PostMapping("/saveCommissions")
    public Response<Commission> saveCommissions(@RequestBody CommissionDTO commissionDTO){
        return barService.saveCommissions(commissionDTO);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_COMMISSION')")
    @GetMapping("/findCommissionByUID/{commissionUID}")
    public Response<Commission> findCommissionByUID(@PathVariable String commissionUID){
        return barService.findCommissionByUID(commissionUID);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_COMMISSION')")
    @PostMapping("/findCommissionList")
    public ResponseList<CommissionProjection> findCommissionList(){
        return barService.findCommissionList();
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_COMMISSION')")
    @PostMapping("/findServiceCommissionPage")
    public ResponsePage<ServiceCommissionProjection> findServiceCommissionPage(@RequestBody PageableParam pageableParam){
        return barService.findServiceCommissionPage(pageableParam.getPage(), pageableParam.getSize(), pageableParam.getSearchParam(), pageableParam.getFilter());
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_COMMISSION')")
    @GetMapping("/countServicesWithoutCommission")
    public Response<Long> countServicesWithoutCommission(){
        return barService.countServicesWithoutCommission();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_COMMISSION')")
    @PostMapping("/findCommissionPage")
    public ResponsePage<CommissionProjection> findCommissionPage(@RequestBody PageableParam pageableParam){
        return barService.findCommissionPage(pageableParam.getPage(), pageableParam.getSize());
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('DELETE_COMMISSION')")
    @PostMapping("/deleteCommission/{commissionUID}")
    public Response<Commission> deleteCommission(@PathVariable String commissionUID){
        return barService.deleteCommission(commissionUID);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_USER')")
    @PostMapping("/findUserPageByBranch")
    public ResponsePage<User> findUserPageByBranch(@RequestBody PageableParam pageableParam){
        return barService.findUserPageByBranch(pageableParam.getPage(), pageableParam.getSize());
    }
    /**
     * The POS side of revoking a branch user's access. Same rule as the
     * settings one - blocked, not deleted, because their uid is what the
     * commission report and every payment record point at.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('ENABLE_OR_DISABLE_USER')")
    @PostMapping("/setUserBlocked/{userUID}/{blocked}")
    public Response<String> setUserBlocked(@PathVariable String userUID, @PathVariable Boolean blocked){
        return userService.setAccountBlocked(userUID, blocked);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('ASSIGN_USER_ROLE')")
    @PostMapping("/assignOrUnAssignUserRoleByBranch")
    public Response<User> assignOrUnAssignUserRoleByBranch(@RequestBody AssignUserRoleDTO assignUserRoleDTO){
        return userService.assignOrUnAssignUserRole(assignUserRoleDTO);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('DELETE_USER')")
    @PostMapping("/deleteUserByBranch/{userUID}")
    public Response<User> deleteUserByBranch(@PathVariable String userUID) {
        return userService.deleteUser(userUID);
    }

    /***
     METHODS FOR BAR SERVICE
     ***/
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SERVICE')")
    @PostMapping("/saveBarEntity")
    public Response<BarServiceEntity> saveBarEntity(@Valid @RequestBody BarServiceDTO barServiceDTO){
        return barService.saveBarService(barServiceDTO);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SERVICE')")
    @GetMapping("/findBarServiceByUID/{barServiceUID}")
    public Response<BarServiceEntity> findBarServiceByUID(@PathVariable String barServiceUID){
        return barService.findBarServiceByUID(barServiceUID);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('DELETE_SERVICE')")
    @PostMapping("/deleteServiceBarByUID/{barServiceUID}")
    public Response<BarServiceEntity> deleteServiceBarByUID(@PathVariable String barServiceUID){
        return barService.deleteServiceBarByUID(barServiceUID);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SERVICE')")
    @GetMapping("/findBarServiceList")
    public ResponseList<BarProjection> findBarServiceList(){
        return barService.findBarServiceList();
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SERVICE')")
    @PostMapping("/findBarServicePage")
    public ResponsePage<BarProjection> findBarServicePage(@RequestBody PageableParam pageableParam){
        return barService.findBarServicePage(pageableParam.getPage(), pageableParam.getSize(), pageableParam.getSearchParam(), pageableParam.getFilter());
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SERVICE')")
    @GetMapping("/findServiceEntityUIDList/{status}")
    public ResponseList<BarProjection> findServiceEntityUIDList(@PathVariable String status){
        return barService.findServiceEntityUIDList(status);
    }

    /***
     METHODS FOR BAR STAFFS
     ***/
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_STAFF')")
    @PostMapping("/saveBarStaff")
    public Response<BarStaff> saveBarStaff(@RequestBody BarStaffDTO barStaffDTO){
        return barService.saveBarStaff(barStaffDTO);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('DELETE_STAFF')")
    @PostMapping("/deleteBarStaff/{barStaffUID}")
    public Response<BarStaff> deleteBarStaff(@PathVariable String barStaffUID){
        return barService.deleteBarStaff(barStaffUID);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STAFF')")
    @GetMapping("/findBarStaffByUID/{barStaffUID}")
    public Response<BarStaff> findBarStaffByUID(@PathVariable String barStaffUID){
        return barService.findBarStaffByUID(barStaffUID);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STAFF')")
    @GetMapping("/findBarStaffList")
    public ResponseList<BarProjection> findBarStaffList(){
        return barService.findBarStaffList();
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STAFF')")
    @PostMapping("/findBarStaffPage")
    public ResponsePage<StaffRowDTO> findBarStaffPage(@RequestBody PageableParam pageableParam){
        return barService.findBarStaffPage(pageableParam.getPage(), pageableParam.getSize(), pageableParam.getSearchParam(), pageableParam.getFilter());
    }

    /***
     METHODS FOR BAR SALES
     ***/
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/saveBarSales")
    public ResponseList<BarSales> saveBarSales(@RequestBody BarSalesDTO barSalesDTO){
        return barService.saveBarSales(barSalesDTO);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/findBarSalesByUID/{barSalesUID}")
    public Response<BarProjection> findBarSalesByUID(@PathVariable String barSalesUID){
        return barService.findBarSalesByUID(barSalesUID);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/findBarSalesList/{barOpenUID}")
    public ResponseList<BarProjection> findBarSalesList(@PathVariable String barOpenUID){
        return barService.findBarSalesList(barOpenUID);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/findBarSalesListActiveTrue")
    public ResponseList<BarProjection> findBarSalesListActiveTrue(){
        return barService.findBarSalesListActiveTrue();
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/findBarSalesPage")
    public ResponsePage<BarProjection> findBarSalesPage(@RequestBody PageableParam pageableParam){
        return barService.findBarSalesPage(pageableParam.getPage(), pageableParam.getSize());
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('DELETE_SALES')")
    @PostMapping("/deleteBarSales/{barSalesUID}")
    public Response<BarSales> deleteBarSales(@PathVariable String barSalesUID){
        return barService.deleteBarSales(barSalesUID);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/salesOpenedList")
    public ResponseList<SalesOpened> salesOpenedList(){
        return barService.salesOpenedList();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/payBill")
    public Response<SalesOpened> payBill(@Valid @RequestBody PayBillDTO payBillDTO){
        return billPaymentService.payBill(payBillDTO);
    }

    /** Every bill noted "paid by phone" between two days (yyyy-MM-dd, inclusive) - the names for the manager to check. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/paymentNotes")
    public com.midland.bar.Utils.Responses.ResponseList<java.util.Map<String, Object>> paymentNotes(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to){
        return billPaymentService.paymentNotes(from, to);
    }

    /** "Paid by phone, from this name" on an unpaid bill - for the cashier to check at handover. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/bills/{billUid}/paymentNote")
    public Response<com.midland.bar.Bar.Model.SalesOpened> paymentNote(@PathVariable String billUid, @RequestBody com.midland.bar.Bar.Dto.PaymentNoteDTO dto){
        return billPaymentService.paymentNote(billUid, dto);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/findBillReceipt/{billUid}")
    public Response<java.util.Map<String, Object>> findBillReceipt(@PathVariable String billUid){
        return billPaymentService.receipt(billUid);
    }

    /** Live nudges for the branch's open screens: "orders" / "bills" changed - fetch again. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping(value = "/live", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter live(){
        boolean seesOrders = !com.midland.bar.Config.Security.StaffSession.active()
                && new com.midland.bar.Config.Security.AuthChecker().hasPermissionOrRoot("RECEIVE_ORDERS");
        String station = seesOrders ? com.midland.bar.Bar.Service.OrderStation.mine() : null;
        return liveEvents.subscribe(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain(), seesOrders, station);
    }

    /** Staff Sell: the staff member behind a code, and their unpaid bills. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/staffSell/{staffCode}")
    public Response<java.util.Map<String, Object>> findStaffSell(@PathVariable String staffCode){
        return staffSellService.findByCode(staffCode);
    }

    /** Staff Sell: the staff member's cash and phone money to hand over, and what is already paid. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/staffSell/handover/{staffCode}")
    public Response<java.util.Map<String, Object>> staffSellHandover(@PathVariable String staffCode){
        return staffSellService.handover(staffCode);
    }

    /** Staff Sell: the staff member is ready to hand over - their unpaid bills are marked for the cashier. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/staffSell/handover/{staffCode}/send")
    public Response<Integer> sendStaffHandover(@PathVariable String staffCode){
        return staffSellService.sendHandover(staffCode);
    }

    /** Sales: the cashier took one method's money from a staff member - all their bills in it are paid. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/staffSell/handover/receive")
    public Response<java.util.Map<String, Object>> receiveStaffHandover(@Valid @RequestBody com.midland.bar.Bar.Dto.HandoverReceiveDTO dto){
        return staffSellService.receiveHandover(dto);
    }

    /** Staff Sell: write an item onto the bill's order for the supervisor (not onto the bill). */
    /** An order written at Staff Sell offline - straight onto the bill, for the supervisor to look over. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/staffOrders/offline")
    public Response<com.midland.bar.Bar.Model.StaffOrder> staffOfflineOrder(@RequestBody com.midland.bar.Bar.Dto.StaffOfflineOrderDTO dto){
        return staffOrderService.recordOffline(dto);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('RECEIVE_ORDERS')")
    @GetMapping("/staffOrders/offlineUnreviewed")
    public Response<java.util.List<com.midland.bar.Bar.Model.StaffOrder>> staffOfflineUnreviewed(){
        return staffOrderService.offlineUnreviewed();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('RECEIVE_ORDERS')")
    @PostMapping("/staffOrders/{orderUid}/review")
    public Response<com.midland.bar.Bar.Model.StaffOrder> reviewStaffOrder(@PathVariable String orderUid){
        return staffOrderService.review(orderUid);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/staffOrders/addItem")
    public Response<com.midland.bar.Bar.Model.StaffOrder> addStaffOrderItem(@Valid @RequestBody com.midland.bar.Bar.Dto.StaffOrderItemDTO dto){
        return staffOrderService.addItem(dto);
    }

    /** Staff Sell: take a line off an order not yet sent. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/staffOrders/{orderUid}/removeLine/{lineUid}")
    public Response<com.midland.bar.Bar.Model.StaffOrder> removeStaffOrderLine(@PathVariable String orderUid, @PathVariable String lineUid){
        return staffOrderService.removeLine(orderUid, lineUid);
    }

    /** Staff Sell: send the staff member's written orders to the supervisor. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/staffOrders/send/{staffCode}")
    public Response<Integer> sendStaffOrders(@PathVariable String staffCode){
        return staffSellService.sendOrders(staffCode);
    }

    /** Supervisor: orders waiting to be received, oldest first. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('RECEIVE_ORDERS')")
    @GetMapping("/staffOrders/pending")
    public Response<java.util.List<com.midland.bar.Bar.Model.StaffOrder>> pendingStaffOrders(){
        return staffOrderService.pending();
    }

    /** Supervisor: receive - the order goes on the bill and the drinks may leave. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('RECEIVE_ORDERS')")
    @PostMapping("/staffOrders/{orderUid}/receive")
    public Response<com.midland.bar.Bar.Model.StaffOrder> receiveStaffOrder(@PathVariable String orderUid){
        return staffOrderService.receive(orderUid);
    }

    /** Supervisor: reject, with the reason the staff member will see. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('RECEIVE_ORDERS')")
    @PostMapping("/staffOrders/{orderUid}/reject")
    public Response<com.midland.bar.Bar.Model.StaffOrder> rejectStaffOrder(@PathVariable String orderUid, @Valid @RequestBody com.midland.bar.Bar.Dto.StaffOrderRejectDTO dto){
        return staffOrderService.reject(orderUid, dto);
    }

    /** Staff Sell: a manager's login to leave for POS. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @PostMapping("/staffSell/unlock")
    public Response<Boolean> unlockStaffSell(@Valid @RequestBody com.midland.bar.Bar.Dto.StaffSellUnlockDTO dto){
        return staffSellService.unlock(dto);
    }

    /** Sales page: each staff member's takings for a day by payment method, and their unpaid bills. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/staffSell/summary/{date}")
    public Response<java.util.List<java.util.Map<String, Object>>> staffSalesSummary(@PathVariable java.time.LocalDate date){
        return staffSellService.summary(date);
    }

    /** Staff Sell: open a bill that belongs to the staff member. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/staffSell/openBill")
    public Response<SalesOpened> openStaffBill(@Valid @RequestBody com.midland.bar.Bar.Dto.StaffBillDTO staffBillDTO){
        return staffSellService.openBill(staffBillDTO);
    }

    /** Take some or all of one line off an unpaid bill - undone everywhere, and recorded with a reason. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/removeBillLine")
    public Response<SalesOpened> removeBillLine(@RequestBody com.midland.bar.Bar.Dto.RemoveBillLineDTO dto){
        return barService.removeBillLine(dto);
    }

    /** A staff member's handover shortage - off their commission and the cashier's expected cash. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/staffLoss")
    public Response<com.midland.bar.Bar.Model.StaffLoss> recordStaffLoss(@RequestBody com.midland.bar.Bar.Dto.StaffLossDTO dto){
        return barService.recordStaffLoss(dto);
    }

    /** What was taken off bills in a period: everyone's for CEO/manager, a cashier's own otherwise. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/billLineVoids/{filter}")
    public ResponseList<com.midland.bar.Bar.Model.BillLineVoid> billLineVoids(@PathVariable String filter){
        return barService.findBillLineVoids(filter);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/addSaleItems")
    public Response<SalesOpened> addSaleItems(@Valid @RequestBody SaleItemsDTO saleItemsDTO){
        return barService.addSaleItems(saleItemsDTO);
    }

    /** An empty bill opened by mistake - whoever may open a bill may take an empty one away. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/deleteEmptyBill/{billUid}")
    public Response<SalesOpened> deleteEmptyBill(@PathVariable String billUid){
        return barService.deleteEmptyBill(billUid);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/saveOpenSale")
    public Response<SalesOpened> saveOpenSale(@RequestBody SaleOpenedDTO saleOpenedDTO){
        return barService.saveOpenSale(saleOpenedDTO);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/salesOpenedListByStatus/{filter}")
    public ResponseList<SalesOpened> salesOpenedListByStatus(@PathVariable String filter) {
        return barService.salesOpenedListByStatus(filter);
    }

    /***
     METHODS FOR REPORT PERMISSION
     ***/
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/findBarReportByUID/{barReportUID}")
    public Response<BarReports> findBarReportByUID(@PathVariable String barReportUID){
        return barService.findBarReportByUID(barReportUID);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @PostMapping("/findBarReportsPage")
    public ResponsePage<BarProjection> findBarReportsPage(@RequestBody PageableParam pageableParam){
        return barService.findBarReportsPage(pageableParam.getPage(), pageableParam.getSize(), pageableParam.getDate());
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @PostMapping("/findCurrentBarReportsPage")
    public ResponsePage<BarProjection> findCurrentBarReportsPage(@RequestBody PageableParam pageableParam) {
        return barService.findCurrentBarReportsPage(pageableParam.getPage(), pageableParam.getSize(), pageableParam.getFilter());
    }


    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/findBarRevenueReport/{date}")
    public Response<BarProjection> findBarRevenueReport(@PathVariable LocalDate date){
        return barService.findBarRevenueReport(date);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/findCurrentBarRevenueReport/{filter}")
    public Response<BarProjection> findCurrentBarRevenueReport(@PathVariable String filter){
        return barService.findCurrentBarRevenueReport(filter);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/findBarRevenueByService/{date}")
    public ResponseList<BarServiceRevenueProjection> findBarRevenueByService(@PathVariable LocalDate date) {
        return barService.findBarRevenueByService(date);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/findCurrentBarRevenueByService/{filter}")
    public ResponseList<BarServiceRevenueProjection> findCurrentBarRevenueByService(@PathVariable String filter) {
        return barService.findCurrentBarRevenueByService(filter);
    }


    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @PostMapping("/findStaffCommissionPage")
    public ResponsePage<BarProjection> findStaffCommissionPage(@RequestBody PageableParam pageableParam) {
        return barService.findStaffCommissionPage(pageableParam.getFilter(), pageableParam.getPage(), pageableParam.getSize());
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('PAY_STAFF')")
    @PostMapping("/payStaffCommission")
    public Response<StaffCommissions> payStaffCommission(@RequestBody  StaffCommissionDTO staffCommissionDTO){
        return barService.payStaffCommission(staffCommissionDTO);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @PostMapping("/findServiceAndStoreReportPage")
    public ResponsePage<StoreReportSummaryProjection> findServiceAndStoreReportPage(@RequestBody PageableParam pageableParam) {
        return barService.findServiceAndStoreReportPage(pageableParam.getPage(), pageableParam.getSize(), pageableParam.getSearchParam());
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/findIncomeExpenses/{filter}")
    public ResponseList<IncomeExpenses> findIncomeExpenses(@PathVariable String filter){
        return barService.getIncomeExpenses(filter);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_EXPENSES')")
    @PostMapping("/addSpend")
    public Response<IncomeExpenses> addSpend(@RequestBody SpendDTO spendDTO) {
        return barService.addSpend(spendDTO);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_EXPENSES')")
    @GetMapping("/findIncomeExpensesAndDescription/{incomeRef}")
    public Response<IncomeExpenses> findIncomeExpensesAndDescription(@PathVariable String incomeRef){
        return barService.findIncomeExpensesAndDescription(incomeRef);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/getStockAndPurchaseByFilter/{weekFilter}")
    public ResponseList<StockAndPurchaseProjection> getStockAndPurchaseByFilter(@PathVariable  String weekFilter){
        return barService.getStockAndPurchaseByFilter(weekFilter);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_STOCK_AND_PURCHASE')")
    @PostMapping("/payStockAndPurchase")
    public Response<StockAndPurchase> payStockAndPurchase(@RequestBody  PayStockAndPurchaseDTO payStockAndPurchaseDTO){
        return barService.payStockAndPurchase(payStockAndPurchaseDTO);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STOCK_AND_PURCHASE')")
    @GetMapping("/findStockPurchaseByUid/{stockUID}")
    public ResponseList<StockAndPurchaseDescriptions> findStockPurchaseByUid(@PathVariable  String stockUID){
        return barService.findStockPurchaseByUid(stockUID);
    }

    /***
     METHODS FOR BAR SETTING
     ***/
    /*
     BILL CODES - set up in POS Setting (SAVE_SERVICE, like the rest of that
     screen); anyone who can sell may see which are free.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SERVICE')")
    @PostMapping("/saveBillCode")
    public Response<BillCode> saveBillCode(@RequestBody java.util.Map<String, String> body){
        return billCodeService.save(body.get("code"));
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SERVICE')")
    @PostMapping("/deleteBillCode/{uid}")
    public Response<BillCode> deleteBillCode(@PathVariable String uid){
        return billCodeService.delete(uid);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SERVICE')")
    @GetMapping("/findBillCodes")
    public ResponseList<BillCodeProjection> findBillCodes(){
        return billCodeService.findAll();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/findAvailableBillCodes")
    public ResponseList<String> findAvailableBillCodes(){
        return billCodeService.findAvailable();
    }

    /*
     STOCK - deliveries into the store. SAVE_STORE is seeded to CEO and
     MANAGER only; a cashier can see the store but not add to it.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_STORE')")
    @PostMapping("/addStock")
    public Response<StockReceipt> addStock(@Valid @RequestBody StockReceiptDTO stockReceiptDTO){
        return stockService.addStock(stockReceiptDTO);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_STORE')")
    @PostMapping("/adjustStock")
    public Response<StockAdjustment> adjustStock(@Valid @RequestBody StockAdjustmentDTO stockAdjustmentDTO){
        return stockService.adjustStock(stockAdjustmentDTO);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @PostMapping("/findStockAdjustments/{serviceUID}")
    public ResponsePage<StockAdjustmentProjection> findStockAdjustments(@PathVariable String serviceUID, @RequestBody PageableParam pageableParam){
        return stockService.findAdjustments(serviceUID, pageableParam.getPage(), pageableParam.getSize());
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @PostMapping("/findStockMovementPage")
    public ResponsePage<StockMovementProjection> findStockMovementPage(@RequestBody PageableParam pageableParam){
        return stockService.findMovements(pageableParam.getSearchParam(), pageableParam.getFromDate(), pageableParam.getToDate(),
                pageableParam.getPage(), pageableParam.getSize());
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @PostMapping("/findStockReceipts/{serviceUID}")
    public ResponsePage<StockReceiptProjection> findStockReceipts(@PathVariable String serviceUID, @RequestBody PageableParam pageableParam){
        return stockService.findReceipts(serviceUID, pageableParam.getPage(), pageableParam.getSize());
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_STORE')")
    @PostMapping("/saveStore")
    public Response<Store> saveStore(@RequestBody StoreDTO storeDTO){
        return barService.saveStore(storeDTO);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_STORE')")
    @PostMapping("/openStore")
    public Response<Store> openStore(@RequestBody StoreDTO storeDTO){
        return barService.openStore(storeDTO);
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_STORE')")
    @PostMapping("/addQuantityToStore")
    public Response<Store> addQuantityToStore(@RequestBody StoreDTO storeDTO){
        return barService.addQuantityToStore(storeDTO);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @GetMapping("/findBarStoreList")
    public ResponseList<BarProjection> findBarStoreList(){
        return barService.findStoreList();
    }
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @PostMapping("/findBarStorePage")
    public ResponsePage<BarProjection> findBarStorePage(@RequestBody PageableParam pageableParam){
        return barService.findStorePage(pageableParam.getPage(), pageableParam.getSize());
    }


    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @PostMapping("/findOpenStorePage")
    public ResponsePage<BarProjection> findOpenStorePage(@RequestBody PageableParam pageableParam){
        return barService.findOpenStorePage(pageableParam.getPage(), pageableParam.getSize(), pageableParam.getSearchParam());
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('DELETE_STORE')")
    @PostMapping("/deleteStore/{storeUID}")
    public Response<Store> deleteStore(@PathVariable String storeUID){
        return barService.deleteBarStore(storeUID);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_STORE')")
    @PostMapping("/closeOpenStore")
    public Response<StoreOpen> closeOpenStore(@RequestBody StoreDTO storeDTO){
        return barService.closeOpenStore(storeDTO);
    }

    // ---- POS home dashboard ----

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/findBranchDashboard")
    public Response<DashboardSummaryDTO> findBranchDashboard() {
        return barService.findBranchDashboard();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/findRevenueTrend/{days}")
    public ResponseList<DailyRevenueDTO> findRevenueTrend(@PathVariable int days) {
        return barService.findRevenueTrend(days);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/findStaffEarnings")
    public ResponseList<StaffEarningsProjection> findStaffEarnings() {
        return barService.findStaffEarnings();
    }

    /** What the branch's Other commission pays for - the CEO's list. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_OTHER_COMMISSION')")
    @GetMapping("/otherCommissionItems")
    public ResponseList<com.midland.bar.Bar.Model.OtherCommissionItem> otherCommissionItems(){
        return otherCommissionService.findItems();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_OTHER_COMMISSION')")
    @PostMapping("/saveOtherCommissionItems")
    public ResponseList<com.midland.bar.Bar.Model.OtherCommissionItem> saveOtherCommissionItems(@RequestBody java.util.List<com.midland.bar.Bar.Dto.OtherCommissionItemDTO> items){
        return otherCommissionService.saveItems(items);
    }

    /** How the Other commission was split over a period - the table under Other in Reports. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/otherSplitReport/{filter}")
    public Response<java.util.Map<String, Object>> otherSplitReport(@PathVariable String filter){
        return otherCommissionService.report(filter);
    }

    /*
     CASH-UP - counting a closed shift. Whoever takes payments (SAVE_SALES) cashes
     up their own; the list follows VIEW_REPORT, narrowed to a cashier's own unless a manager.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @GetMapping("/cashUp/preview")
    public Response<java.util.Map<String, Object>> cashUpPreview(){
        return cashUpService.preview();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/cashUp/submit")
    public Response<com.midland.bar.Bar.Model.CashUp> cashUpSubmit(@RequestBody com.midland.bar.Bar.Dto.CashUpDTO dto){
        return cashUpService.submit(dto);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/cashUp/list/{filter}")
    public ResponseList<com.midland.bar.Bar.Model.CashUp> cashUps(@PathVariable String filter){
        return cashUpService.findClosed(filter);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/cashUp/{uid}/lines")
    public ResponseList<com.midland.bar.Bar.Model.CashUpLine> cashUpLines(@PathVariable String uid){
        return cashUpService.findLines(uid);
    }

    /*
     SHIFTS - a seller opens one before selling and closes it before the
     cash-up. Opening and closing follow SAVE_SALES; the list (CEO/manager
     see everyone's, a cashier their own) follows VIEW_REPORT.
     */
    // Only reads: anyone who can see sales may ask (the shift bar, main office looking in).
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/shift/current")
    public Response<java.util.Map<String, Object>> currentShift(){
        return workShiftService.current();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/shift/open")
    public Response<com.midland.bar.Bar.Model.WorkShift> openShift(){
        return workShiftService.open();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/shift/close")
    public Response<com.midland.bar.Bar.Model.WorkShift> closeShift(){
        return workShiftService.close();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/shift/list/{filter}")
    public ResponseList<java.util.Map<String, Object>> shifts(@PathVariable String filter){
        return workShiftService.list(filter);
    }

    /*
     STOCK TAKE - counting the whole store at once (SAVE_STORE: CEO, MANAGER);
     its history and the variance by product follow VIEW_STORE.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_STORE')")
    @GetMapping("/stockTake/items")
    public ResponseList<java.util.Map<String, Object>> stockTakeItems(){
        return stockTakeService.countable();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_STORE')")
    @PostMapping("/stockTake/submit")
    public Response<com.midland.bar.Bar.Model.StockTake> stockTakeSubmit(@RequestBody com.midland.bar.Bar.Dto.StockTakeDTO dto){
        return stockTakeService.submit(dto);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @GetMapping("/stockTake/list/{filter}")
    public ResponseList<com.midland.bar.Bar.Model.StockTake> stockTakes(@PathVariable String filter){
        return stockTakeService.findTaken(filter);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @GetMapping("/stockTake/{uid}/lines")
    public ResponseList<com.midland.bar.Bar.Model.StockTakeLine> stockTakeLines(@PathVariable String uid){
        return stockTakeService.findLines(uid);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_STORE')")
    @GetMapping("/stockTake/variance/{filter}")
    public ResponseList<java.util.Map<String, Object>> stockVariance(@PathVariable String filter){
        return stockTakeService.varianceByProduct(filter);
    }

    /*
     INSIGHTS - profit per product, best sellers and slow movers, peak hours,
     and every pot's balance since the start. Report readers (VIEW_REPORT).
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/insights/products/{filter}")
    public Response<java.util.Map<String, Object>> insightProducts(@PathVariable String filter){
        return insightService.products(filter);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/insights/peak/{filter}")
    public Response<java.util.Map<String, Object>> insightPeak(@PathVariable String filter){
        return insightService.peakHours(filter);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_REPORT')")
    @GetMapping("/insights/ledger")
    public Response<java.util.Map<String, Object>> insightLedger(){
        return insightService.potsLedger();
    }
}
