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
    private final StockService stockService;
    private final BillCodeService billCodeService;
    private final BillPaymentService billPaymentService;
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
        return barService.findServiceCommissionPage(pageableParam.getPage(), pageableParam.getSize(), pageableParam.getSearchParam());
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
    public ResponsePage<BarProjection> findBarStaffPage(@RequestBody PageableParam pageableParam){
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

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/findBillReceipt/{billUid}")
    public Response<java.util.Map<String, Object>> findBillReceipt(@PathVariable String billUid){
        return billPaymentService.receipt(billUid);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/addSaleItems")
    public Response<SalesOpened> addSaleItems(@Valid @RequestBody SaleItemsDTO saleItemsDTO){
        return barService.addSaleItems(saleItemsDTO);
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
}
