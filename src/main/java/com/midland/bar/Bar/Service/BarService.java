package com.midland.bar.Bar.Service;

import java.util.LinkedHashMap;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Notification.Service.NotificationService;
import com.midland.bar.Bar.Dto.*;
import com.midland.bar.Bar.Model.*;
import com.midland.bar.Setting.Service.PlatformSettingService;
import com.midland.bar.Bar.Projection.*;
import com.midland.bar.Bar.Repository.*;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Utils.Exceptions.BusinessException;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import com.midland.bar.Utils.Responses.ResponsePage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;


@Service
@Log
@RequiredArgsConstructor
public class BarService {
  private final BarServiceRepository barServiceRepository;
  private final UserRepository userRepository;
  private final CommissionRepository commissionRepository;
  private final BarStaffRepository barStaffRepository;
  private final BarSalesRepository barSalesRepository;
  private final BarReportsRepository barReportsRepository;
  private final SalesOpenedRepository salesOpenedRepository;
  private final StaffCommissionsRepository staffCommissionsRepository;
  private final StoreRepository storeRepository;
  private final OpenStoreRepository openStoreRepository;
  private final ServiceAndStoreReportRepository serviceAndStoreReportRepository;
  private final IncomeExpensesRepository incomeExpensesRepository;
  private final IncomeExpensesDescriptionRepository incomeExpensesDescriptionRepository;
  private final StockAndPurchaseRepository stockAndPurchaseRepository;
  private final StockAndPurchaseDescriptionsRepository stockAndPurchaseDescriptionsRepository;
  private final NotificationService notificationService;
  private final PlatformSettingService platformSettingService;
  private final BillCodeService billCodeService;
  private final StaffCodeService staffCodeService;
  private final StaffOrderRepository staffOrderRepository;

    /*
   BAR SERVICE METHODS
    */
  public Response<BarServiceEntity> saveBarService(BarServiceDTO barServiceDTO){
      log.info(LoggerUser.getEmail() + "Is Saving Bar Services");
      if(barServiceDTO == null)
          return new Response<>("Provide Data for Bar Service");
      BarServiceEntity serviceEntity = null;
      if(barServiceDTO.getUid() != null){
          // Scoped to the caller's branch - a bare findById let one branch
          // edit another's product by its uid.
          Optional<BarServiceEntity> optionalBarServiceEntity = barServiceRepository.findBarServiceByUID(barServiceDTO.getUid(), LoggerUser.getBranchUID());
          if(optionalBarServiceEntity.isEmpty())
              return new Response<>("Service Not Found");
          serviceEntity = optionalBarServiceEntity.get();
          serviceEntity.update();
      }else{
          serviceEntity = new BarServiceEntity();
          // A new product starts empty and on sale. Stock arrives only through
          // Add Stock, so editing the product can never overwrite a count that
          // sales and deliveries have moved.
          serviceEntity.setStockQuantity(0);
          serviceEntity.setStatus("active");
          // Generated, not typed. It stays with the product for life - even if
          // its category is edited later - so receipts and reports that quote
          // it keep pointing at the same thing.
          serviceEntity.setServiceCode(ServiceKind.of(barServiceDTO.getKind()) == ServiceKind.STOCK_ITEM
                  ? nextCode(ProductCategory.STOCK_ITEM_PREFIX)
                  : nextProductCode(ProductCategory.valueOf(barServiceDTO.getCategory())));
      }
      ServiceKind kind = ServiceKind.of(barServiceDTO.getKind());
      serviceEntity.setKind(kind.name());
      ProductCategory category = ProductCategory.valueOf(barServiceDTO.getCategory());
      serviceEntity.setServiceName(barServiceDTO.getServiceName());
      serviceEntity.setDescription(barServiceDTO.getDescription());
      serviceEntity.setPrice(barServiceDTO.getPrice());
      serviceEntity.setCategory(category.name());
      serviceEntity.setUnit(barServiceDTO.getUnit());
      boolean hasPack = barServiceDTO.getPackUnit() != null && !barServiceDTO.getPackUnit().isBlank();
      if (hasPack && (barServiceDTO.getUnitsPerPack() == null || barServiceDTO.getUnitsPerPack() < 2))
          return new Response<>("Say how many units are in one " + barServiceDTO.getPackUnit());
      // Without a pack a unit is its own pack, so the same arithmetic
      // (stock / unitsPerPack) holds for every product.
      serviceEntity.setPackUnit(hasPack ? barServiceDTO.getPackUnit() : null);
      serviceEntity.setUnitsPerPack(hasPack ? barServiceDTO.getUnitsPerPack() : 1);
      serviceEntity.setBuyingPrice(barServiceDTO.getBuyingPrice());

      boolean ladderChanged = false;
      if (kind == ServiceKind.STOCK_ITEM) {
          // Kept in the store, never sold: counted, no price, no source.
          if (barServiceDTO.getBuyingPrice() == null)
              return new Response<>("Enter the buying price");
          // Its measures, smallest first. Without a ladder the plain unit and
          // pack fields make a two-rung one (Bottle; Crate = 24).
          List<com.midland.bar.Bar.Dto.LadderLevel> requested = barServiceDTO.getUnitLadder();
          if (requested == null || requested.isEmpty()) {
              requested = new ArrayList<>();
              requested.add(new com.midland.bar.Bar.Dto.LadderLevel(
                      barServiceDTO.getUnit() == null || barServiceDTO.getUnit().isBlank() ? "Unit" : barServiceDTO.getUnit(), 1, null));
              if (hasPack)
                  requested.add(new com.midland.bar.Bar.Dto.LadderLevel(barServiceDTO.getPackUnit(), barServiceDTO.getUnitsPerPack(), null));
          }
          List<com.midland.bar.Bar.Dto.LadderLevel> ladder;
          try {
              ladder = UnitLadders.normalise(requested);
          } catch (BusinessException e) {
              return new Response<>(e.getMessage());
          }
          String ladderJson = UnitLadders.toJson(ladder);
          ladderChanged = !ladderJson.equals(serviceEntity.getUnitLadder());
          serviceEntity.setUnitLadder(ladderJson);
          // Counted in the smallest rung, bought in the largest.
          com.midland.bar.Bar.Dto.LadderLevel top = ladder.get(ladder.size() - 1);
          serviceEntity.setUnit(ladder.get(0).getName());
          serviceEntity.setPackUnit(ladder.size() > 1 ? top.getName() : null);
          serviceEntity.setUnitsPerPack(top.getBase());
          serviceEntity.setPrice(null);
          serviceEntity.setTrackStock(true);
          serviceEntity.setStockSourceUid(null);
          serviceEntity.setUnitsPerSale(null);
          serviceEntity.setSaleUnitName(null);
          serviceEntity.setSaleUnitCount(null);
      } else {
          if (barServiceDTO.getPrice() == null)
              return new Response<>("Enter the selling price");
          String source = barServiceDTO.getStockSource();
          if (source == null || source.isBlank())
              source = category.tracksStockByDefault() ? "SELF" : "NONE";
          if ("SELF".equals(source)) {
              if (barServiceDTO.getBuyingPrice() == null)
                  return new Response<>("Enter the buying price");
              serviceEntity.setTrackStock(true);
              serviceEntity.setStockSourceUid(null);
              serviceEntity.setUnitsPerSale(null);
          } else if ("NONE".equals(source)) {
              serviceEntity.setTrackStock(false);
              serviceEntity.setStockSourceUid(null);
              serviceEntity.setUnitsPerSale(null);
          } else {
              Optional<BarServiceEntity> stockItem = barServiceRepository.findBarServiceByUID(source, LoggerUser.getBranchUID())
                      .filter(s -> ServiceKind.of(s.getKind()) == ServiceKind.STOCK_ITEM);
              if (stockItem.isEmpty())
                  return new Response<>("Choose a stock item from the store");
              // One sale is a count of one rung of the item's ladder
              // (1 x Nusu = 60 Nyama); or, for an item without one, a
              // plain number of its units.
              int perSale;
              String saleUnit = barServiceDTO.getSaleUnitName();
              int saleCount = barServiceDTO.getSaleUnitCount() == null ? 1 : barServiceDTO.getSaleUnitCount();
              if (saleUnit != null && !saleUnit.isBlank()) {
                  Optional<Integer> base = UnitLadders.baseOf(stockItem.get().getUnitLadder(), saleUnit);
                  if (base.isEmpty())
                      return new Response<>(stockItem.get().getServiceName() + " has no measure called " + saleUnit);
                  perSale = base.get() * saleCount;
                  serviceEntity.setSaleUnitName(saleUnit);
                  serviceEntity.setSaleUnitCount(saleCount);
              } else if (barServiceDTO.getUnitsPerSale() != null) {
                  perSale = barServiceDTO.getUnitsPerSale();
                  serviceEntity.setSaleUnitName(null);
                  serviceEntity.setSaleUnitCount(null);
              } else {
                  return new Response<>("Say how much of " + stockItem.get().getServiceName() + " one sale takes");
              }
              // Counted through the stock item, not on its own.
              serviceEntity.setTrackStock(false);
              serviceEntity.setStockSourceUid(stockItem.get().getUid());
              serviceEntity.setUnitsPerSale(perSale);
              serviceEntity.setPackUnit(null);
              serviceEntity.setUnitsPerPack(1);
              serviceEntity.setBuyingPrice(null);
          }
      }
      serviceEntity.setUsageType(barServiceDTO.getUsageType());
      try {
          BarServiceEntity saved = barServiceRepository.save(serviceEntity);
          if (ladderChanged && saved.getUid() != null) {
              // A rung resized (a Portion is now 12 Mshikaki) changes what
              // every service made from this item takes per sale.
              for (BarServiceEntity drawing : barServiceRepository.findDrawingOn(saved.getUid(), LoggerUser.getBranchUID())) {
                  if (drawing.getSaleUnitName() == null)
                      continue;
                  UnitLadders.baseOf(saved.getUnitLadder(), drawing.getSaleUnitName()).ifPresent(base -> {
                      drawing.setUnitsPerSale(base * (drawing.getSaleUnitCount() == null ? 1 : drawing.getSaleUnitCount()));
                      barServiceRepository.save(drawing);
                  });
              }
          }
          return new Response<>(saved);
      } catch (Exception e) {
          e.printStackTrace();
          return new Response<>("Error in Saving New Service");
      }
  }
  public Response<BarServiceEntity> findBarServiceByUID(String barServiceUID){
      log.info(LoggerUser.getEmail() + " Is Accessing Bar Service");
      if(barServiceUID == null)
          return new Response<>("Provide Bar Service Ref");
      Optional<BarServiceEntity> optionalBarServiceEntity =  barServiceRepository.findBarServiceByUID(barServiceUID, LoggerUser.getBranchUID());
      return  optionalBarServiceEntity.map(Response::new).orElseGet(()->new Response<>("Service Not Found"));
  }
  public Response<BarServiceEntity> deleteServiceBarByUID(String barServiceUID){
      log.info(LoggerUser.getEmail() + "Is Deleting Bar Service");
      if(barServiceUID == null)
          return new Response<>("Provide Bar service Ref");
      Optional<BarServiceEntity> optionalBarServiceEntity=barServiceRepository.findBarServiceByUID(barServiceUID,LoggerUser.getBranchUID());
      if (optionalBarServiceEntity.isEmpty())
          return new Response<>("Service Not Found");
      BarServiceEntity serviceEntity = optionalBarServiceEntity.get();
      long drawing = barServiceRepository.countDrawingOn(serviceEntity.getUid(), LoggerUser.getBranchUID());
      if (drawing > 0)
          return new Response<>(drawing + " service(s) are made from " + serviceEntity.getServiceName()
                  + " - point them elsewhere before deleting it");
      try{
          // Set aside, not removed: deliveries, sales, corrections and its
          // commission all point at this row, and deleting it would either
          // fail on those links or take the history with it. Marked
          // inactive, it drops out of every list, the till and the reports
          // (TenantEntity's is_active filter), while the records stay.
          serviceEntity.delete();
          barServiceRepository.save(serviceEntity);
          return new Response<>(serviceEntity);
      } catch (Exception e) {
          e.printStackTrace();
          return new Response<>("Error in Deleting Service");
      }
  }
  /** One past the highest code in use for this category in the caller's branch: DRK-001, DRK-002... */
  private String nextProductCode(ProductCategory category){
      return nextCode(category.codePrefix());
  }

  private String nextCode(String prefix){
      int highest = barServiceRepository.findCodesByPrefix(LoggerUser.getBranchUID(), prefix).stream()
              .map(code -> code.substring(prefix.length() + 1))
              .filter(number -> number.matches("\\d+"))
              .mapToInt(Integer::parseInt)
              .max()
              .orElse(0);
      return String.format("%s-%03d", prefix, highest + 1);
  }

  /** "Ginger" -> "%ginger%"; blank -> "%", which matches everything. */
  private static String likePattern(String search){
      if (search == null || search.isBlank())
          return "%";
      String escaped = search.trim().toLowerCase()
              .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      return "%" + escaped + "%";
  }

  /** Settings > Config decides when a product counts as running low. */
  private int lowStockLevel(){
      Integer level = platformSettingService.current().getLowStockLevel();
      return level == null ? 0 : level;
  }
  public ResponseList<BarProjection> findBarServiceList(){
      log.info(LoggerUser.getEmail() + "Is Accessing Bar Service");
      try{
          return new ResponseList<>(barServiceRepository.findAllBarServiceList(LoggerUser.getBranchUID(), lowStockLevel()));
      }catch (Exception e){
          e.printStackTrace();
          return new ResponseList<>("Error in Accessing Services");
      }
  }
  /** search matches name, code or description - "ginger" finds every drink described as ginger. */
  /**
   * filter "COUNTED" keeps only what has a count of its own - the store's
   * view: bottles and stock items, not the services drawn from them.
   */
  public ResponsePage<BarProjection> findBarServicePage(Integer page, Integer size, String search, String filter){
        log.info(LoggerUser.getEmail() + " Is Accessing Bar Services");
        Pageable pageable = PageRequest.of(page, size);
        try{
            Page<BarProjection> response = barServiceRepository.findBarServicePage(pageable, LoggerUser.getBranchUID(), lowStockLevel(), likePattern(search), "COUNTED".equals(filter));
            return new ResponsePage<>(response);
        }catch(Exception e){
            e.printStackTrace();
            return new ResponsePage<>("Error in Accessing Bar Service");
        }
  }
  public ResponsePage<User>  findUserPageByBranch(Integer page, Integer size){
      log.info(LoggerUser.getEmail() + " Is Accessing User by using Branch");
      Pageable pageable = PageRequest.of(page, size);
      return new ResponsePage<>(userRepository.findUserPageByBranch(pageable, LoggerUser.getBranchUID()));
  }

  /*
  BAR COMMISSIONS METHODS
   */

    public Response<Commission> saveCommissions(CommissionDTO commissionDTO){
        log.info(LoggerUser.getEmail() + "Is Saving Bar Commissions");
        if(commissionDTO == null)
            return new Response<>("Provide Commissions Data");
        Integer[] parts = {
                commissionDTO.getStaffPercent(), commissionDTO.getOwnerPercent(), commissionDTO.getTraPercent(),
                commissionDTO.getMaintenancePercent(), commissionDTO.getEmergencyPercent(), commissionDTO.getOtherPercent(),
                commissionDTO.getRentPercent(), commissionDTO.getLoanPercent(), commissionDTO.getLukuPercent(),
                commissionDTO.getWaterPercent(), commissionDTO.getStockPurchasePercent()};
        int total = 0;
        for (Integer part : parts) {
            if (part != null && (part < 0 || part > 100))
                return new Response<>("Each percentage must be between 0 and 100");
            total += part == null ? 0 : part;
        }
        if (total > 100)
            return new Response<>("The percentages add up to " + total + "% - they cannot pass 100%");

        Commission commission = null;
        if(commissionDTO.getUid() !=null){
            Optional<Commission> optionalCommission = commissionRepository.findCommissionByUID(commissionDTO.getUid(), LoggerUser.getBranchUID());
            if(optionalCommission.isEmpty())
                return new Response<>("Commission Not Found");
            commission = optionalCommission.get();
        }
        if(commissionDTO.getBarServiceUID() != null) {
            // Scoped to the caller's branch - a bare findById let a split be
            // hung on another branch's service.
            Optional<BarServiceEntity> optionalBarServiceEntity = barServiceRepository.findBarServiceByUID(commissionDTO.getBarServiceUID(), LoggerUser.getBranchUID());
            if(optionalBarServiceEntity.isEmpty())
                return new Response<>("Service Not Found");
            if (commission == null) {
                // One split per service: a second "add" edits the first
                // rather than leaving two for a sale to choose between.
                commission = commissionRepository.findCommissionByService(optionalBarServiceEntity.get(), LoggerUser.getBranchUID())
                        .orElseGet(Commission::new);
            }
            commission.setBarService(optionalBarServiceEntity.get());
        }
        if (commission == null)
            return new Response<>("Choose the service");
        commission.setEmergencyPercent(commissionDTO.getEmergencyPercent());
        commission.setMaintenancePercent(commissionDTO.getMaintenancePercent());
        commission.setStaffPercent(commissionDTO.getStaffPercent());
        commission.setOwnerPercent(commissionDTO.getOwnerPercent());
        commission.setOtherPercent(commissionDTO.getOtherPercent());
        commission.setTraPercent(commissionDTO.getTraPercent());
        // Worked out here, not trusted from the client.
        commission.setTotalPercent(total);
        commission.setLoanPercent(commissionDTO.getLoanPercent());
        commission.setLukuPercent(commissionDTO.getLukuPercent());
        commission.setWaterPercent(commissionDTO.getWaterPercent());
        commission.setRentPercent(commissionDTO.getRentPercent());
        commission.setStockPurchasePercent(commissionDTO.getStockPurchasePercent());
        try{
            return new Response<>(commissionRepository.save(commission));
        } catch (Exception e) {
            e.printStackTrace();
            return new Response<>("Error in Saving new Commission");
        }

    }
    public ResponseList<CommissionProjection> findCommissionList(){
        log.info(LoggerUser.getEmail() + "Is accessing Commissions");
        return new ResponseList<>(commissionRepository.findCommissionList(LoggerUser.getBranchUID()));
    }
    public ResponsePage<ServiceCommissionProjection> findServiceCommissionPage(Integer page, Integer size, String search){
        return new ResponsePage<>(commissionRepository.findServiceCommissionPage(
                LoggerUser.getBranchUID(), likePattern(search), PageRequest.of(page, size)));
    }
    public ResponsePage<CommissionProjection> findCommissionPage(Integer page, Integer size){
        Pageable pageable = PageRequest.of(page, size);
        return new ResponsePage<>(commissionRepository.findCommissionPage(pageable, LoggerUser.getBranchUID()));
    }
    public Response<Commission> deleteCommission(String commissionUID){
        log.info(LoggerUser.getEmail() + "Is deleting Commission");
        if(commissionUID == null)
            return new Response<>("Provide Commission Ref UID");
        Optional<Commission> optionalCommission = commissionRepository.findCommissionByUID(commissionUID, LoggerUser.getBranchUID());
        if(optionalCommission.isEmpty())
            return new Response<>("Commission Not Found");
        Commission commission = optionalCommission.get();
        try{
            commissionRepository.delete(optionalCommission.get());
            return new Response<>(commission);
        } catch (Exception e) {
            return new Response<>("Error in Deleting Commission");
        }
    }
    public Response<Commission> findCommissionByUID(String commissionUID){
        log.info(LoggerUser.getEmail() + "Is Accessing Commission");
        if(commissionUID == null)
            return new Response<>("Provide Commission REF");
        Optional<Commission> optionalCommission=commissionRepository.findCommissionByUID(commissionUID, LoggerUser.getBranchUID());
        return optionalCommission.map(Response::new).orElseGet(()->new Response<>("Commission Not Found"));
    }

    /*
  BAR STAFF METHODS
 */
    public Response<BarStaff> saveBarStaff(BarStaffDTO barStaffDTO){
        log.info(LoggerUser.getEmail() + "is Saving Staff");
        if(barStaffDTO ==null)
            return new Response<>("Provide Staff Data");
        if (isBlank(barStaffDTO.getFirstName()) || isBlank(barStaffDTO.getLastName()))
            return new Response<>("First and last name are required");
        if (isBlank(barStaffDTO.getPhoneNumber()))
            return new Response<>("Phone number is required");
        if (!StaffCategory.isValid(barStaffDTO.getBarCategory()))
            return new Response<>("Choose the staff member's category");
        BarStaff barStaff = null;
        if(barStaffDTO.getUid() != null){
            Optional<BarStaff> optionalBarStaff = barStaffRepository.findBarStaffByUID(barStaffDTO.getUid(), LoggerUser.getBranchUID());
            if(optionalBarStaff.isEmpty())
                return new Response<>("Staff Not Found");
            barStaff = optionalBarStaff.get();
            barStaff.update();
        }else{
            barStaff = new BarStaff();
        }

        // A typed code (K1) is checked and kept; left blank, a new staff member
        // gets the next number and an existing one keeps theirs.
        String code = StaffCodeService.normalise(barStaffDTO.getStaffCode());
        if (!code.isEmpty()) {
            if (!StaffCodeService.isValid(code))
                return new Response<>("Staff code can only have letters, numbers and dashes (up to 10)");
            Optional<BarStaff> holder = barStaffRepository.findByStaffCodeAnyStatus(code, LoggerUser.getBranchUIDOrMain());
            if (holder.isPresent() && !holder.get().getUid().equals(barStaff.getUid()))
                return new Response<>("Staff code " + code + " is already used by " + holder.get().getFirstName());
            barStaff.setStaffCode(code);
        } else if (barStaff.getStaffCode() == null) {
            barStaff.setStaffCode(staffCodeService.nextCode(LoggerUser.getBranchUIDOrMain()));
        }

        barStaff.setDateOfBirth(barStaffDTO.getDateOfBirth());
        barStaff.setFirstName(barStaffDTO.getFirstName());
        // Optional - most staff go by two names.
        barStaff.setMiddleName(isBlank(barStaffDTO.getMiddleName()) ? null : barStaffDTO.getMiddleName().trim());
        barStaff.setLastName(barStaffDTO.getLastName());
        barStaff.setPhoneNumber(barStaffDTO.getPhoneNumber());
        barStaff.setBarCategory(barStaffDTO.getBarCategory());
        barStaff.setDescription(barStaffDTO.getDescription());
        barStaff.setGender(barStaffDTO.getGender());
        try{
            return new Response<>(barStaffRepository.save(barStaff));
        } catch (Exception e) {
            e.printStackTrace();
            return new Response<>("Error in Saving Staff");
        }

    }
    public Response<BarStaff> findBarStaffByUID(String barStaffUID){
        log.info(LoggerUser.getEmail() + "is accessing Staff");
        Optional<BarStaff> optionalBarStaff = barStaffRepository.findBarStaffByUID(barStaffUID, LoggerUser.getBranchUID());
        return optionalBarStaff.map(Response::new).orElseGet(()-> new Response<>("Staff Not Found"));
    }
    public ResponseList<BarProjection> findBarStaffList(){
        log.info(LoggerUser.getEmail() + "Is Accessing Bar Staff");
        return new ResponseList<>(barStaffRepository.findBarStaffList(LoggerUser.getBranchUID()));
    }
    /**
     * search matches any name or the phone number; category, when given, is
     * one StaffCategory name (WAITER...). Either may be blank.
     */
    public ResponsePage<BarProjection> findBarStaffPage(Integer page, Integer size, String search, String category){
        Pageable pageable = PageRequest.of(page, size);
        String categoryFilter = StaffCategory.isValid(category) ? category : "";
        return new ResponsePage<>(barStaffRepository.findBarStaffPage(pageable, LoggerUser.getBranchUID(), likePattern(search), categoryFilter));
    }

    private static boolean isBlank(String value){
        return value == null || value.isBlank();
    }
    public Response<BarStaff> deleteBarStaff(String barStaffUID){
        log.info(LoggerUser.getEmail() + "Is Deleting Staff");
        Optional<BarStaff> optionalBarStaff = barStaffRepository.findBarStaffByUID(barStaffUID, LoggerUser.getBranchUID());
        if(optionalBarStaff.isEmpty())
            return new Response<>("Staff Not Found");
        BarStaff barStaff = optionalBarStaff.get();
        try{
            barStaffRepository.delete(optionalBarStaff.get());
            return new Response<>(barStaff);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response<>("Error in Deleting Staff");
        }
    }


    /***
     BAR_SALES_METHODS
     */
    @Transactional
    public ResponseList<BarSales> saveBarSales(BarSalesDTO barSalesDTO) {
        log.info("{} is saving bar sales"+ LoggerUser.getEmail());
        if (barSalesDTO == null) {
            return new ResponseList<>("Weka Taarifa za Mauzo");
        }
        if (barSalesDTO.getBarStaffUID() == null || barSalesDTO.getBarStaffUID().isBlank()) {
            return new ResponseList<>("Weka Staff REF");
        }

        if (barSalesDTO.getBarServiceUID() == null || barSalesDTO.getBarServiceUID().isEmpty()) {
            return new ResponseList<>("Weka REF ya Hududma");
        }

        if (barSalesDTO.getSalesOpenedUID() == null || barSalesDTO.getSalesOpenedUID().isBlank()) {
            return new ResponseList<>("Provide Open Sale REF");
        }

        String branchUID = LoggerUser.getBranchUID();

        // ============================
        // FIND STAFF
        // ============================

        Optional<BarStaff> optionalBarStaff = barStaffRepository.findBarStaffByUID(barSalesDTO.getBarStaffUID(), branchUID);
        if (optionalBarStaff.isEmpty()) {
            log.info("Staff not found. Staff UID: {}, Branch: {}"+ barSalesDTO.getBarStaffUID());
            return new ResponseList<>("Staff Hapatikani");
        }

        // ============================
        // FIND OPEN SALE
        // ============================

        Optional<SalesOpened> optionalSalesOpened = salesOpenedRepository.findById(barSalesDTO.getSalesOpenedUID());
        if (optionalSalesOpened.isEmpty()) {
            log.info("Open sale not found. UID: {}"+ barSalesDTO.getSalesOpenedUID());
            return new ResponseList<>("Open Sale Not Found");
        }
        BarStaff staff = optionalBarStaff.get();
        SalesOpened salesOpened = optionalSalesOpened.get();

        // ============================
        // FIND SERVICES
        // ============================

        List<BarServiceEntity> barServiceEntities = barServiceRepository.findAllById(barSalesDTO.getBarServiceUID());

        if (barServiceEntities == null || barServiceEntities.isEmpty()) {
            log.info("Services not found. Requested services: {}"+ barSalesDTO.getBarServiceUID());
            return new ResponseList<>("Services Not Found");
        }
        log.info("Services found: {}"+ barServiceEntities.size());

        // ============================
        // CREATE SALES
        // ============================
        List<Integer> bills = new ArrayList<>();
        List<Integer> staffBill = new ArrayList<>();
        List<BarSales> sales = new ArrayList<>();
        List<Commission> commissions = new ArrayList<>();
        List<StockAndPurchase> stockAndPurchases = new ArrayList<>();
        Map<String, Commission> commissionsByService = loadCommissionsByService(barServiceEntities);
        for (BarServiceEntity service : barServiceEntities) {
            if (service == null) {log.info("Skipping null service");
                continue;
            }
            log.info("Preparing sale for service: {}, price: {}"+ service.getPrice());
            if (service.getPrice() == null) {
                log.info("Service price is null. Service: {}"+ service.getUid());
                return new ResponseList<>("Price Not Found For:  " + service.getServiceName());
            }


            Commission commission = commissionsByService.get(service.getUid());
            if(commission == null)
                return new ResponseList<>("No Commission Found ");
            staffBill.add(commission.getStaffPercent()* (service.getPrice()/100));
            bills.add(service.getPrice());
            BarSales sale = new BarSales();
            sale.setBarStaff(staff);
            sale.setBarServiceEntity(service);
            sale.setSalesOpened(salesOpened);
            commissions.add(commission);
            sales.add(sale);

        }
// ============================
// CALCULATE BILL
// ============================

        int currentBill = salesOpened.getBill() != null
                ? salesOpened.getBill()
                : 0;

        int totalBills = bills.stream()
                .mapToInt(Integer::intValue)
                .sum();

        salesOpened.setBill(currentBill + totalBills);

        if (sales.isEmpty()) {
            log.info("No sales to save");
            return new ResponseList<>("Hakuna Mauzo yoyote ya kusave");
        }

        // ============================
        // SAVE SALES
        // ============================

        try {
            log.info("Saving {} bar sales..."+ sales.size());
            salesOpenedRepository.save(salesOpened);
            List<BarSales> savedBarSales = barSalesRepository.saveAll(sales);
            if (savedBarSales == null || savedBarSales.isEmpty()) {
                log.info("Mauzo hayaja Hifadhiwa");

                throw new BusinessException("Sales were not saved");

            }


            log.info(
                    "SALES SAVED SUCCESSFULLY. Count: {}"+
                    savedBarSales.size()
            );

            // ============================
            // CREATE REPORTS
            // ============================

            log.info(
                    "Starting report creation for {} sales..."+
                    savedBarSales.size()
            );

            ResponseList<BarReports> reportsResponse = saveBarReport(savedBarSales, staff, commissionsByService);

            // ============================
            // CHECK REPORT RESPONSE
            // ============================

            if (reportsResponse == null) {

                log.info(
                        "Report response is NULL"
                );

                throw new BusinessException(
                        "Failed to create bar reports"
                );
            }

            if (reportsResponse.getMessage() != null &&
                    !reportsResponse.getMessage().isBlank()) {

                log.info(
                        "Report creation failed: {}"+
                        reportsResponse.getMessage()
                );
                throw new BusinessException(reportsResponse.getMessage());
            }

            log.info(
                    "SALON SALES AND REPORTS SAVED SUCCESSFULLY"
            );
            ResponseList<ServiceAndStoreReport> serviceAndStoreReports = saveServiceAndStoreReport(reportsResponse);
            if(serviceAndStoreReports.getMessage() !=null)
                throw new BusinessException(serviceAndStoreReports.getMessage());
            if(serviceAndStoreReports.getData() == null || serviceAndStoreReports.getData().isEmpty())
                throw new BusinessException("Error in Serving Service and Report");

            StaffCommissions staffCommissions = addStaffCommission(barSalesDTO.getBarStaffUID(), LoggerUser.getBranchUID(), LocalDate.now(), staffBill);
            ResponseList<IncomeExpenses> savedIncome = saveIncomeAndExpenses(commissions);

            if (savedIncome.getData() == null ||
                    savedIncome.getData().isEmpty()) {

                throw new BusinessException(
                        "Error in saving Income and Expenses"
                );
            }
            ResponseList<StockAndPurchase> andPurchaseResponseList = saveStockAndPurchase(barServiceEntities, commissionsByService);
            if (andPurchaseResponseList.getData() == null ||
                    andPurchaseResponseList.getData().isEmpty()) {

                throw new BusinessException(
                        "Error in saving Stock And Purchase"
                );
            }

            return new ResponseList<>(savedBarSales);

        } catch (Exception e) {

            log.info(
                    "Error while saving bar sales and reports"+
                    e
            );

            throw e;
        }
    }
    /**
     * Rings lines up on an open bill: 3 x Castle Lite, 1 x chips. The seller
     * is whoever is logged in. In one transaction each line
     *  - takes its units out of the store (refused, all of it, if a counted
     *    service does not have enough),
     *  - is charged at the service's current price, copied onto the line,
     *  - is split into the service's commission buckets on price x quantity,
     *    feeding the reports, the seller's commission, the weekly income pots
     *    and the stock-purchase pot, exactly as a single service used to.
     */
    @Transactional
    public Response<SalesOpened> addSaleItems(SaleItemsDTO dto) {
        String branchUID = LoggerUser.getBranchUID();
        SalesOpened bill = salesOpenedRepository.findById(dto.getSalesOpenedUID())
                .filter(so -> Optional.ofNullable(branchUID).orElse("MAIN_OFFICE").equals(so.getBranchUid()))
                .orElse(null);
        if (bill == null)
            return new Response<>("Open Sale Not Found");
        if ("PAID".equals(bill.getPaymentStatus()))
            return new Response<>("Bill " + bill.getSalesCode() + " is already paid - open a new bill");

        // The same service twice on one request is one line.
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (SaleItemsDTO.Item item : dto.getItems())
            quantities.merge(item.getBarServiceUID(), item.getQuantity(), Integer::sum);

        List<BarServiceEntity> services = new ArrayList<>();
        // Every store row this sale takes from - the services counted
        // themselves and the stock items the others are made from - locked
        // until commit, so two tills selling the last units cannot both see
        // them on the shelf. Keyed by uid so a mshikaki and a robo drawing on
        // the same beef share one row and one running count.
        Map<String, BarServiceEntity> storeRows = new HashMap<>();
        for (String serviceUid : quantities.keySet()) {
            BarServiceEntity service = barServiceRepository.findForUpdate(serviceUid, branchUID)
                    .orElseThrow(() -> new BusinessException("Service Not Found"));
            if (ServiceKind.of(service.getKind()) == ServiceKind.STOCK_ITEM)
                throw new BusinessException(service.getServiceName() + " is kept in the store, not sold - sell a service made from it");
            if (service.getPrice() == null || service.getPrice() <= 0)
                throw new BusinessException("No selling price set for " + service.getServiceName());
            services.add(service);
            if (service.getStockSourceUid() != null && !storeRows.containsKey(service.getStockSourceUid())) {
                BarServiceEntity stockItem = barServiceRepository.findForUpdate(service.getStockSourceUid(), branchUID)
                        .orElseThrow(() -> new BusinessException("The stock item " + service.getServiceName() + " is made from is missing"));
                storeRows.put(stockItem.getUid(), stockItem);
            } else if (Boolean.TRUE.equals(service.getTrackStock())) {
                storeRows.put(service.getUid(), service);
            }
        }
        Map<String, Commission> commissionsByService = loadCommissionsByService(services);

        // A bill opened at Staff Sell sells as its staff member, commission and
        // all; any other bill sells as whoever is logged in. soldBy stays the
        // login either way - it records who actually pressed the button.
        BarStaff seller = bill.getStaffUid() != null
                ? barStaffRepository.findBarStaffByUID(bill.getStaffUid(), branchUID)
                        .orElseThrow(() -> new BusinessException("The staff member on bill " + bill.getSalesCode() + " is no longer active"))
                : sellerStaff();
        String soldBy = LoggerUser.getEmail();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<BarSales> lines = new ArrayList<>();
        List<BarReports> reports = new ArrayList<>();
        List<Integer> sellerCuts = new ArrayList<>();
        List<IncomeExpenses> pots = new ArrayList<>();
        Map<String, StockAndPurchase> purchasePots = getCurrentWeekByServices(services);
        int added = 0;

        for (BarServiceEntity service : services) {
            int quantity = quantities.get(service.getUid());
            Commission commission = commissionsByService.get(service.getUid());
            if (commission == null)
                throw new BusinessException("No commission split set for " + service.getServiceName()
                        + " - set it in Setting > Commission");

            // Where this line's units come out of: the stock item it is made
            // from (units per sale each), itself (one each), or nowhere.
            BarServiceEntity storeRow = null;
            int perSale = 1;
            if (service.getStockSourceUid() != null) {
                storeRow = storeRows.get(service.getStockSourceUid());
                perSale = service.getUnitsPerSale() == null || service.getUnitsPerSale() < 1 ? 1 : service.getUnitsPerSale();
            } else if (Boolean.TRUE.equals(service.getTrackStock())) {
                storeRow = storeRows.get(service.getUid());
            }
            int stockUnits = quantity * perSale;
            if (storeRow != null) {
                int onHand = storeRow.getStockQuantity() == null ? 0 : storeRow.getStockQuantity();
                if (onHand < stockUnits)
                    throw new BusinessException(storeRow == service
                            ? "Only " + onHand + " " + service.getServiceName() + " left in the store"
                            : "Only " + onHand + " units of " + storeRow.getServiceName() + " left - "
                              + quantity + " " + service.getServiceName() + " need " + stockUnits);
                storeRow.setStockQuantity(onHand - stockUnits);
                barServiceRepository.save(storeRow);
            }

            int unitPrice = service.getPrice();
            int lineTotal = unitPrice * quantity;
            // Cost of one sold unit, from whatever row the stock came out of:
            // a mshikaki costs its share of the beef.
            BarServiceEntity costRow = storeRow != null ? storeRow : service;
            int perPack = costRow.getUnitsPerPack() == null || costRow.getUnitsPerPack() < 1 ? 1 : costRow.getUnitsPerPack();
            int unitCost = costRow.getBuyingPrice() == null ? 0
                    : Math.round((float) costRow.getBuyingPrice() * perSale / perPack);

            BarSales line = new BarSales();
            line.setBarStaff(seller);
            line.setBarServiceEntity(service);
            line.setSalesOpened(bill);
            line.setQuantity(quantity);
            line.setUnitPrice(unitPrice);
            line.setLineTotal(lineTotal);
            line.setUnitCost(unitCost);
            line.setStockItemUid(storeRow == null ? null : storeRow.getUid());
            line.setStockUnits(storeRow == null ? null : stockUnits);
            line.setSoldBy(soldBy);
            lines.add(line);

            BarReports report = new BarReports();
            report.setBarStaff(seller);
            report.setBarSales(line);
            report.setBarServiceEntity(service);
            report.setStaffAmount(calculatePercentage(commission.getStaffPercent(), lineTotal));
            report.setOwnerAmount(calculatePercentage(commission.getOwnerPercent(), lineTotal));
            report.setTraAmount(calculatePercentage(commission.getTraPercent(), lineTotal));
            report.setMaintenanceAmount(calculatePercentage(commission.getMaintenancePercent(), lineTotal));
            report.setEmergencyAmount(calculatePercentage(commission.getEmergencyPercent(), lineTotal));
            report.setOthersAmount(calculatePercentage(commission.getOtherPercent(), lineTotal));
            report.setRentAmount(calculatePercentage(commission.getRentPercent(), lineTotal));
            report.setLoanAmount(calculatePercentage(commission.getLoanPercent(), lineTotal));
            report.setLukuAmount(calculatePercentage(commission.getLukuPercent(), lineTotal));
            report.setWaterAmount(calculatePercentage(commission.getWaterPercent(), lineTotal));
            report.setStockPurchaseAmount(calculatePercentage(commission.getStockPurchasePercent(), lineTotal));
            reports.add(report);

            // The seller's cut is the same staffAmount the report carries, so
            // what they are owed and what the report says always agree.
            sellerCuts.add(report.getStaffAmount());

            BigDecimal amount = BigDecimal.valueOf(lineTotal);
            addIncomeExpense("Staff", amount, commission.getStaffPercent(), branchUID, weekStart, pots);
            addIncomeExpense("Owner", amount, commission.getOwnerPercent(), branchUID, weekStart, pots);
            addIncomeExpense("TRA", amount, commission.getTraPercent(), branchUID, weekStart, pots);
            addIncomeExpense("Emergency", amount, commission.getEmergencyPercent(), branchUID, weekStart, pots);
            addIncomeExpense("Maintenance", amount, commission.getMaintenancePercent(), branchUID, weekStart, pots);
            addIncomeExpense("Other", amount, commission.getOtherPercent(), branchUID, weekStart, pots);
            addIncomeExpense("LUKU", amount, commission.getLukuPercent(), branchUID, weekStart, pots);
            addIncomeExpense("Water", amount, commission.getWaterPercent(), branchUID, weekStart, pots);
            addIncomeExpense("Rent", amount, commission.getRentPercent(), branchUID, weekStart, pots);
            addIncomeExpense("Loan", amount, commission.getLoanPercent(), branchUID, weekStart, pots);
            addIncomeExpense("Stock Purchase", amount, commission.getStockPurchasePercent(), branchUID, weekStart, pots);

            int toPurchasePot = calculatePercentage(commission.getStockPurchasePercent(), lineTotal);
            StockAndPurchase pot = purchasePots.get(service.getUid());
            if (pot == null) {
                pot = new StockAndPurchase();
                pot.setBarService(service);
                pot.setCommission(commission);
                pot.setTotalAmount(toPurchasePot);
                pot.setRemainingAmount(toPurchasePot);
                purchasePots.put(service.getUid(), pot);
            } else {
                pot.setTotalAmount((pot.getTotalAmount() == null ? 0 : pot.getTotalAmount()) + toPurchasePot);
                pot.setRemainingAmount((pot.getRemainingAmount() == null ? 0 : pot.getRemainingAmount()) + toPurchasePot);
            }

            added += lineTotal;
        }

        bill.setBill((bill.getBill() == null ? 0 : bill.getBill()) + added);
        bill.update();
        salesOpenedRepository.save(bill);
        barSalesRepository.saveAll(lines);
        barReportsRepository.saveAll(reports);
        addStaffCommission(seller.getUid(), branchUID, today, sellerCuts);
        stockAndPurchaseRepository.saveAll(purchasePots.values());

        log.info(soldBy + " added " + lines.size() + " line(s) worth " + added + " to bill " + bill.getSalesCode());
        return new Response<>(bill);
    }

    /**
     * The staff row the logged-in user sells as. Made from their own account
     * the first time they sell, so every seller has somewhere for their
     * commission to land without anyone setting it up by hand.
     */
    private BarStaff sellerStaff() {
        User user = LoggerUser.getUser();
        String branchUID = LoggerUser.getBranchUID();
        List<BarStaff> linked = barStaffRepository.findByUserUid(user.getUid(), branchUID);
        if (!linked.isEmpty())
            return linked.get(0);
        BarStaff staff = new BarStaff();
        staff.setUserUid(user.getUid());
        staff.setFirstName(user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        staff.setMiddleName(user.getMiddleName());
        staff.setLastName(user.getLastName());
        staff.setPhoneNumber(user.getPhone());
        staff.setGender(user.getGender());
        staff.setDescription("Created from login " + user.getUsername() + " on first sale");
        staff.setStaffCode(staffCodeService.nextCode(branchUID));
        return barStaffRepository.save(staff);
    }

    public Response<BarProjection> findBarSalesByUID(String barSalesUID){
        log.info(LoggerUser.getEmail() + "Is accessing sales");
        if(barSalesUID == null)
            return new Response<>("Provide sales REF");
        Optional<BarProjection> optionalBarProjection = barSalesRepository.findBarSalesByUID(barSalesUID, LoggerUser.getBranchUID());
        return optionalBarProjection.map(Response::new).orElseGet(()->new Response<>("Sales Not Found"));
    }
    public ResponseList<BarProjection> findBarSalesList(String barOpenUID){
        log.info(LoggerUser.getEmail() + "Is accessing Sales");
        return new ResponseList<>(barSalesRepository.findBarSalesList(LoggerUser.getBranchUID(), barOpenUID));
    }
    public ResponseList<BarProjection> findBarSalesListActiveTrue(){
        log.info(LoggerUser.getEmail() + "Is accessing Sales");
        return new ResponseList<>(barSalesRepository.findBarSalesListActiveTrue(LoggerUser.getBranchUID(), LocalDate.now()));
    }
    public ResponsePage<BarProjection> findBarSalesPage(Integer page, Integer size){
        log.info(LoggerUser.getEmail() + "is Accessing Bar Sales");
        Pageable pageable = PageRequest.of(page, size);
        return new ResponsePage<>(barSalesRepository.findBarSalesPage(pageable, LoggerUser.getBranchUID()));
    }
    public Response<BarSales> deleteBarSales(String barSalesUID){
        log.info(LoggerUser.getEmail() + "Is Deleting Sales");
        Optional<BarSales> optionalBarSales = barSalesRepository.findSalesByUID(barSalesUID, LoggerUser.getBranchUID());
        if(optionalBarSales.isEmpty())
            return new Response<>("Sales Not Found");
        BarSales sales=optionalBarSales.get();
        try{
            barSalesRepository.delete(optionalBarSales.get());
            return new Response<>(sales);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response<>("Error in deleting sales");
        }
    }
    /**
     * Removes a bill opened by mistake. Only while nothing is on it - no line, and no
     * waiter's order still waiting to go on - so no sale or money is ever lost with it.
     * Cancelled rather than erased, the way an empty arrival bill goes with its booking;
     * its code is free again straight away.
     */
    @Transactional
    public Response<SalesOpened> deleteEmptyBill(String billUid) {
        SalesOpened bill = salesOpenedRepository.findForUpdate(billUid, LoggerUser.getBranchUID())
                .filter(b -> b.getIsActive() == null || b.getIsActive())
                .orElse(null);
        if (bill == null || !"PENDING".equals(bill.getPaymentStatus()))
            return new Response<>("That bill is not open any more");
        if (barSalesRepository.countLines(billUid) > 0 || (bill.getBill() != null && bill.getBill() > 0))
            return new Response<>("Only an empty bill can be deleted - bill " + bill.getSalesCode() + " has items on it");
        if (staffOrderRepository.countUndecided(billUid) > 0)
            return new Response<>("A waiter has an order waiting to go on bill " + bill.getSalesCode());
        bill.setPaymentStatus("CANCELLED");
        bill.delete();
        return new Response<>(salesOpenedRepository.save(bill));
    }

    public Response<SalesOpened> saveOpenSale(SaleOpenedDTO saleOpenedDTO) {
        log.info(LoggerUser.getEmail() + " is Opening Sale");
        if (saleOpenedDTO == null) {
            return new Response<>("Provide Data For Opening new sale");
        }
        SalesOpened salesOpened;
        boolean wasAlreadyPaid = false;
        if (saleOpenedDTO.getUid() != null) {
            Optional<SalesOpened> optionalSalesOpened = salesOpenedRepository.findById(saleOpenedDTO.getUid())
                    // Another branch's bill is as good as missing.
                    // (branchless users write MAIN_OFFICE, as TenantEntity does).
                    .filter(so -> Optional.ofNullable(LoggerUser.getBranchUID()).orElse("MAIN_OFFICE").equals(so.getBranchUid()));
            if (optionalSalesOpened.isEmpty()) {
                return new Response<>("Open Sale Not Found");
            }
            salesOpened = optionalSalesOpened.get();
            wasAlreadyPaid = "PAID".equals(salesOpened.getPaymentStatus());
            salesOpened.update();
        } else {
            // A new bill takes one of the codes from POS Setting that no
            // unpaid bill is holding.
            if (!billCodeService.isAvailable(saleOpenedDTO.getSalesCode()))
                return new Response<>("Code " + saleOpenedDTO.getSalesCode() + " is not available - choose a free code");
            salesOpened = new SalesOpened();
        }
        // Paying goes through /payBill, which works the amount out from the
        // bill's lines. Taking method, amount or PAID from here let the
        // screen mark any bill paid for any amount.
        if(saleOpenedDTO.getUid() == null && saleOpenedDTO.getSalesCode() != null)
            salesOpened.setSalesCode(saleOpenedDTO.getSalesCode());
        if(saleOpenedDTO.getStatus() != null)
            salesOpened.setStatus(saleOpenedDTO.getStatus());

        try {
            SalesOpened savedSale = salesOpenedRepository.save(salesOpened);

            // Notify the branch only the moment a sale first becomes
            // PAID — not on every subsequent edit of an already-paid sale.
            if (!wasAlreadyPaid && "PAID".equals(savedSale.getPaymentStatus())) {
                try {
                    notificationService.notifySaleCompleted(savedSale);
                } catch (Exception notifyError) {
                    log.warning("Failed to send sale-completed notification: " + notifyError.getMessage());
                }
            }

            return new Response<>(savedSale);
        } catch (Exception e) {
            return new Response<>(
                    "Error in Opening new sale: " + e.getMessage()
            );
        }
    }
    public ResponseList<SalesOpened> salesOpenedList(){
        log.info(LoggerUser.getEmail() + " Is Accessing opened Sales");
        return new ResponseList<>(salesOpenedRepository.findOpenBills(LoggerUser.getBranchUID()));
    }
    public ResponseList<SalesOpened> salesOpenedListByStatus(String filter) {

        log.info(LoggerUser.getEmail() + " is accessing Sales");

        log.info("Filter" + filter);
        LocalDate today = LocalDate.now();

        LocalDate startDate;
        LocalDate endDate;

        if (filter == null || filter.isBlank()) {

            // Default = leo
            startDate = today;
            endDate = today.plusDays(1);

        } else {

            switch (filter.toUpperCase()) {

                case "DAY":
                    // Leo
                    startDate = today;
                    endDate = today.plusDays(1);
                    break;
                case "YESTERDAY":
                    // Jana
                    startDate = today.minusDays(1);
                    endDate = today;
                    break;

                case "WEEK":
                    // Wiki hii
                    startDate = today.with(
                            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                    );
                    endDate = startDate.plusWeeks(1);
                    break;

                case "LAST_WEEK":
                    // Wiki iliyopita
                    startDate = today.with(
                            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                    ).minusWeeks(1);

                    endDate = startDate.plusWeeks(1);
                    break;

                case "MONTH":
                    // Mwezi huu
                    startDate = today.withDayOfMonth(1);
                    endDate = startDate.plusMonths(1);
                    break;

                case "LAST_MONTH":
                    // Mwezi uliopita
                    startDate = today.withDayOfMonth(1).minusMonths(1);
                    endDate = startDate.plusMonths(1);
                    break;

                case "THIS_YEAR":
                    // Mwaka huu
                    startDate = LocalDate.of(today.getYear(), 1, 1);
                    endDate = startDate.plusYears(1);
                    break;
                case "LAST_YEAR":
                    // Mwaka uliopita
                    startDate = today
                            .with(TemporalAdjusters.firstDayOfYear())
                            .minusYears(1);

                    endDate = today
                            .with(TemporalAdjusters.firstDayOfYear());

                    break;

                default:
                    try {
                        DateTimeFormatter formatter =
                                DateTimeFormatter.ofPattern("yyyy-MM-dd");

                        startDate = LocalDate.parse(filter, formatter);
                        endDate = startDate.plusDays(1);

                    } catch (DateTimeParseException e) {

                        throw new IllegalArgumentException(
                                "Filter must be DAY, WEEK, LAST_WEEK, MONTH, " +
                                        "LAST_MONTH, THIS_YEAR or date in format yyyy-MM-dd"
                        );
                    }
            }
        }

        return new ResponseList<>(
                salesOpenedRepository.salesOpenedListByStatus(
                        LoggerUser.getBranchUID(),
                        startDate,
                        endDate
                )
        );
    }
    private ResponseList<IncomeExpenses> saveIncomeAndExpenses(List<Commission> commissions) {

        log.info(
                "{} is serving income and expenses"+
                LoggerUser.getEmail()
        );

        if (commissions == null || commissions.isEmpty()) {
            throw new BusinessException("Empty Commissions");
        }

        List<IncomeExpenses> result = new ArrayList<>();

        /*
         * Leo
         */
        LocalDate today = LocalDate.now();

        /*
         * Wiki inaanza Monday
         *
         * Mfano:
         * Sunday 13/09/2026
         *
         * weekStartDate:
         * Monday 07/09/2026
         */
        LocalDate weekStartDate = today.with(
                TemporalAdjusters.previousOrSame(
                        DayOfWeek.MONDAY
                )
        );

        for (Commission commission : commissions) {

            if (commission == null) {
                continue;
            }

            /*
             * Hakikisha commission ina service
             */
            if (commission.getBarService() == null) {
                continue;
            }

            /*
             * Amount ya service
             */
            BigDecimal amount = BigDecimal.valueOf(
                    commission
                            .getBarService()
                            .getPrice()
            );

            /*
             * Branch
             */
            String branchUID = commission.getBranchUid();

            /*
             * =========================
             * STAFF
             * =========================
             */
            addIncomeExpense(
                    "Staff",
                    amount,
                    commission.getStaffPercent(),
                    branchUID,
                    weekStartDate,
                    result
            );

            /*
             * =========================
             * OWNER
             * =========================
             */
            addIncomeExpense(
                    "Owner",
                    amount,
                    commission.getOwnerPercent(),
                    branchUID,
                    weekStartDate,
                    result
            );

            /*
             * =========================
             * TRA
             * =========================
             */
            addIncomeExpense(
                    "TRA",
                    amount,
                    commission.getTraPercent(),
                    branchUID,
                    weekStartDate,
                    result
            );

            /*
             * =========================
             * EMERGENCY
             * =========================
             */
            addIncomeExpense(
                    "Emergency",
                    amount,
                    commission.getEmergencyPercent(),
                    branchUID,
                    weekStartDate,
                    result
            );

            /*
             * =========================
             * MAINTENANCE
             * =========================
             */
            addIncomeExpense(
                    "Maintenance",
                    amount,
                    commission.getMaintenancePercent(),
                    branchUID,
                    weekStartDate,
                    result
            );

            /*
             * =========================
             * OTHER
             * =========================
             */
            addIncomeExpense(
                    "Other",
                    amount,
                    commission.getOtherPercent(),
                    branchUID,
                    weekStartDate,
                    result
            );

            /*
             * =========================
             * LUKU
             * =========================
             */
            addIncomeExpense(
                    "LUKU",
                    amount,
                    commission.getLukuPercent(),
                    branchUID,
                    weekStartDate,
                    result
            );

            /*
             * =========================
             * WATER
             * =========================
             */
            addIncomeExpense(
                    "Water",
                    amount,
                    commission.getWaterPercent(),
                    branchUID,
                    weekStartDate,
                    result
            );

            /*
             * =========================
             * RENT
             * =========================
             */
            addIncomeExpense(
                    "Rent",
                    amount,
                    commission.getRentPercent(),
                    branchUID,
                    weekStartDate,
                    result
            );

            /*
             * =========================
             * LOAN
             * =========================
             */
            addIncomeExpense(
                    "Loan",
                    amount,
                    commission.getLoanPercent(),
                    branchUID,
                    weekStartDate,
                    result
            );

            /*
             * =========================
             * STOCK PURCHASE
             * =========================
             */
            addIncomeExpense(
                    "Stock Purchase",
                    amount,
                    commission.getStockPurchasePercent(),
                    branchUID,
                    weekStartDate,
                    result
            );
        }

        return new ResponseList<>(
                result
        );
    }


    private void addIncomeExpense(String name, BigDecimal amount, Integer percent, String branchUID, LocalDate weekStartDate, List<IncomeExpenses> result) {

        /*
         * Kama percentage haipo
         * au ni 0, hakuna cha kufanya.
         */
        if (percent == null || percent <= 0) {
            return;
        }

        /*
         * Calculate percentage.
         *
         * Mfano:
         *
         * amount = 100,000
         * percent = 20
         *
         * calculatedAmount = 20,000
         */
        BigDecimal calculatedAmount = amount
                .multiply(
                        BigDecimal.valueOf(percent)
                )
                .divide(
                        BigDecimal.valueOf(100)
                );

        /*
         * Search:
         *
         * name
         * branch
         * week
         */
        Optional<IncomeExpenses> existing =
                incomeExpensesRepository.findByNameAndWeek(
                        name,
                        branchUID,
                        weekStartDate
                );

        /*
         * ============================
         * RECORD IPO
         * ============================
         */
        if (existing.isPresent()) {

            IncomeExpenses incomeExpenses =
                    existing.get();

            /*
             * Existing income
             */
            BigDecimal currentIncome =
                    incomeExpenses.getIncome() == null
                            ? BigDecimal.ZERO
                            : incomeExpenses.getIncome();

            /*
             * Add new amount
             */
            incomeExpenses.setIncome(
                    currentIncome.add(
                            calculatedAmount
                    )
            );

            /*
             * Update description
             */
            incomeExpenses.setDescriptions(
                    name + " commission"
            );

            /*
             * Save update
             */
            IncomeExpenses updated =
                    incomeExpensesRepository.save(
                            incomeExpenses
                    );

            /*
             * Add kwenye response
             */
            result.add(updated);

        }

        /*
         * ============================
         * RECORD HAIPO
         * ============================
         */
        else {

            IncomeExpenses incomeExpenses =
                    new IncomeExpenses();

            /*
             * Name
             */
            incomeExpenses.setName(name);

            /*
             * Income
             */
            incomeExpenses.setIncome(
                    calculatedAmount
            );

            /*
             * Expenses
             */
            incomeExpenses.setExpenses(
                    BigDecimal.ZERO
            );

            /*
             * Description
             */
            incomeExpenses.setDescriptions(
                    name + " commission"
            );

            /*
             * Week
             */
            incomeExpenses.setWeekStartDate(
                    weekStartDate
            );

            /*
             * Branch
             */
            incomeExpenses.setBranchUid(
                    branchUID
            );

            /*
             * Save
             */
            IncomeExpenses saved =
                    incomeExpensesRepository.save(
                            incomeExpenses
                    );

            /*
             * Add kwenye response
             */
            result.add(saved);
        }
    }
    public ResponseList<IncomeExpenses> getIncomeExpenses(String filter) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        switch (filter.toUpperCase()) {

            case "THIS_WEEK":

                startDate = today.with(
                        TemporalAdjusters.previousOrSame(
                                DayOfWeek.MONDAY
                        )
                );

                endDate = startDate.plusDays(6);

                break;


            case "LAST_WEEK":

                startDate = today
                        .with(
                                TemporalAdjusters.previousOrSame(
                                        DayOfWeek.MONDAY
                                )
                        )
                        .minusWeeks(1);

                endDate = startDate.plusDays(6);

                break;


            case "THIS_MONTH":

                startDate = today.with(
                        TemporalAdjusters.firstDayOfMonth()
                );

                endDate = today.with(
                        TemporalAdjusters.lastDayOfMonth()
                );

                break;


            case "LAST_MONTH":

                LocalDate lastMonth = today.minusMonths(1);

                startDate = lastMonth.with(
                        TemporalAdjusters.firstDayOfMonth()
                );

                endDate = lastMonth.with(
                        TemporalAdjusters.lastDayOfMonth()
                );

                break;


            case "THIS_YEAR":

                startDate = today.with(
                        TemporalAdjusters.firstDayOfYear()
                );

                endDate = today.with(
                        TemporalAdjusters.lastDayOfYear()
                );

                break;


            case "LAST_YEAR":

                LocalDate lastYear = today.minusYears(1);

                startDate = lastYear.with(
                        TemporalAdjusters.firstDayOfYear()
                );

                endDate = lastYear.with(
                        TemporalAdjusters.lastDayOfYear()
                );

                break;


            default:

                throw new BusinessException(
                        "Invalid income and expenses filter: " + filter
                );
        }

        return new ResponseList<>(incomeExpensesRepository.findByBranchAndWeekRange(
                LoggerUser.getBranchUID(),
                startDate,
                endDate));
    }
    @Transactional
    public Response<IncomeExpenses> addSpend(SpendDTO spendDTO) {

        log.info(LoggerUser.getEmail() + " is Saving Spend");

        if (spendDTO == null) {
            return new Response<>("Provide Spend Data");
        }

        if (spendDTO.getUid() == null) {
            return new Response<>("Provide Spend REF");
        }

        if (spendDTO.getAmount() == null || spendDTO.getAmount() <= 0) {
            return new Response<>("Provide valid Spend Amount");
        }

        if (spendDTO.getDescription() == null ||
                spendDTO.getDescription().trim().isEmpty()) {
            return new Response<>("Provide Spend Description");
        }


        // ==============================
        // FIND INCOME EXPENSE
        // ==============================

        Optional<IncomeExpenses> optionalIncomeExpenses =
                incomeExpensesRepository.findById(
                        spendDTO.getUid()
                );

        if (optionalIncomeExpenses.isEmpty()) {
            return new Response<>("Income Expenses Not Found");
        }

        IncomeExpenses incomeExpenses =
                optionalIncomeExpenses.get();


        // ==============================
        // CHECK AVAILABLE BALANCE
        // ==============================

        BigDecimal currentExpenses =
                incomeExpenses.getExpenses() != null
                        ? incomeExpenses.getExpenses()
                        : BigDecimal.ZERO;

        BigDecimal income =
                incomeExpenses.getIncome() != null
                        ? incomeExpenses.getIncome()
                        : BigDecimal.ZERO;

        BigDecimal spendAmount =
                BigDecimal.valueOf(spendDTO.getAmount());


        BigDecimal availableAmount =
                income.subtract(currentExpenses);


        if (spendAmount.compareTo(availableAmount) > 0) {
            return new Response<>(
                    "Spend amount cannot exceed available amount"
            );
        }


        // ==============================
        // UPDATE EXPENSES
        // ==============================

        incomeExpenses.setExpenses(
                currentExpenses.add(spendAmount)
        );


        // ==============================
        // CREATE SPEND HISTORY
        // ==============================

        IncomeExpensesDescription spend =
                new IncomeExpensesDescription();

        spend.setDescription(
                spendDTO.getDescription().trim()
        );

        spend.setDescriptionDate(
                LocalDate.now()
        );

        spend.setSpendAmount(
                spendAmount
        );

        spend.setIncomeExpenses(
                incomeExpenses
        );


        // ==============================
        // SAVE SPEND HISTORY
        // ==============================

        incomeExpensesDescriptionRepository.save(spend);
        return new Response<>(incomeExpensesRepository.save(incomeExpenses));
    }
    public Response<IncomeExpenses> findIncomeExpensesAndDescription(String incomeUID){
        if(incomeUID == null)
            return new Response<>("Provide Income REF");
        Optional<IncomeExpenses> optionalIncomeExpenses = incomeExpensesRepository.findIncomeExpensesAndDescription(incomeUID, LoggerUser.getBranchUID());
        return optionalIncomeExpenses.map(Response::new).orElseGet(()->new Response<>("Income Expenses Not Found"));
    }

    /***
     BAR_REPORT_METHODS
     */
    // Commissions arrive already looked up by the caller, keyed on service UID.
    private Map<String, Commission> loadCommissionsByService(List<BarServiceEntity> services) {
        List<String> serviceUids = services.stream()
                .filter(Objects::nonNull)
                .map(BarServiceEntity::getUid)
                .toList();
        if (serviceUids.isEmpty()) {
            return Map.of();
        }
        Map<String, Commission> byService = new HashMap<>();
        for (Commission commission : commissionRepository.findCommissionsByServices(serviceUids, LoggerUser.getBranchUID())) {
            if (commission.getBarService() != null) {
                byService.put(commission.getBarService().getUid(), commission);
            }
        }
        return byService;
    }

    @Transactional
    private ResponseList<BarReports> saveBarReport(List<BarSales> sales, BarStaff staff, Map<String, Commission> commissionsByService) {
        log.info("{} is saving bar reports"+ LoggerUser.getEmail());
        log.info("Staff received for reports: {}"+ staff);
        List<String> errors = new ArrayList<>();
        List<BarReports> barReports = new ArrayList<>();
        // ============================
        // VALIDATION
        // ============================
        if (sales == null || sales.isEmpty()) {
            log.info("No sales found to create reports");
            throw new BusinessException("No sales found to create reports");
        }

        if (staff == null) {
            log.info("Staff is null");
            throw new BusinessException("Staff not found");
        }

        String branchUID = LoggerUser.getBranchUID();
        log.info("Creating reports for branch: {}"+ branchUID);
        // ============================
        // LOOP SALES
        // ============================

        for (BarSales sale : sales) {

            if (sale == null) {

                log.info(
                        "Invalid sale data: sale is null"
                );

                errors.add("Invalid sale data");
                continue;
            }

            log.info(
                    "Processing sale: {}"+
                    sale.getUid()
            );

            // ============================
            // GET SERVICE
            // ============================

            BarServiceEntity service =
                    sale.getBarServiceEntity();

            if (service == null) {

                log.info(
                        "Service not found for sale: {}"+
                        sale.getUid()
                );

                errors.add(
                        "Service not found for sale: "
                                + sale.getUid()
                );

                continue;
            }

            log.info(
                    "Service found. Service UID: {}"+
                    service.getUid()
            );

            // ============================
            // SERVICE PRICE
            // ============================

            Integer serviceValue = service.getPrice();

            if (serviceValue == null) {

                log.info(
                        "Service price not found. Service: {}"+
                        service.getUid()
                );

                errors.add(
                        "Service price not found for service: "
                                + service.getUid()
                );

                continue;
            }

            log.info(
                    "Service price: {}"+
                    serviceValue
            );

            // ============================
            // FIND COMMISSION
            // ============================

            log.info(
                    "Searching commission. Service: {}, Branch: {}"+
                    service.getUid()
            );

            Commission commission =
                    commissionsByService.get(service.getUid());

            if (commission == null) {

                log.info(
                        "COMMISSION NOT FOUND. Service: {}, Branch: {}"+
                        service.getUid()
                );

                errors.add(
                        "Commission not found for service: "
                                + service.getUid()
                );

                continue;
            }

            log.info(
                    "COMMISSION FOUND. Service: {}"+
                    service.getUid()
            );

            // ============================
            // CREATE REPORT
            // ============================

            BarReports barReport =
                    new BarReports();

            barReport.setBarStaff(staff);
            barReport.setBarSales(sale);
            barReport.setBarServiceEntity(service);

            // ============================
            // COMMISSION AMOUNTS
            // ============================

            barReport.setEmergencyAmount(
                    calculatePercentage(
                            commission.getEmergencyPercent(),
                            serviceValue
                    )
            );

            barReport.setOwnerAmount(
                    calculatePercentage(
                            commission.getOwnerPercent(),
                            serviceValue
                    )
            );

            barReport.setOthersAmount(
                    calculatePercentage(
                            commission.getOtherPercent(),
                            serviceValue
                    )
            );

            barReport.setMaintenanceAmount(
                    calculatePercentage(
                            commission.getMaintenancePercent(),
                            serviceValue
                    )
            );

            barReport.setStaffAmount(
                    calculatePercentage(
                            commission.getStaffPercent(),
                            serviceValue
                    )
            );

            barReport.setLoanAmount(
                    calculatePercentage(
                            commission.getLoanPercent(),
                            serviceValue
                    )
            );
            barReport.setRentAmount(
                    calculatePercentage(
                            commission.getRentPercent(),
                            serviceValue
                    )
            );
            barReport.setWaterAmount(
                    calculatePercentage(
                            commission.getWaterPercent(),
                            serviceValue
                    )
            );
            barReport.setLukuAmount(
                    calculatePercentage(
                            commission.getLukuPercent(),
                            serviceValue
                    )
            );
            barReport.setStockPurchaseAmount(
                    calculatePercentage(
                            commission.getStockPurchasePercent(),
                            serviceValue
                    )
            );
            barReport.setTraAmount(
                    calculatePercentage(
                            commission.getTraPercent(),
                            serviceValue
                    )
            );

            // ============================
            // LOG CALCULATED AMOUNTS
            // ============================
            barReports.add(barReport);
        }

        // ============================
        // NOTHING TO SAVE
        // ============================

        if (barReports.isEmpty()) {

            log.info(
                    "NO BAR REPORTS WERE PREPARED"
            );

            if (!errors.isEmpty()) {

                log.info(
                        "Report errors: {}" +
                        errors
                );

                return new ResponseList<>(
                        null,
                        errors
                );
            }

            return new ResponseList<>(
                    "No bar reports to save"
            );
        }

        // ============================
        // SAVE REPORTS
        // ============================

        try {

            log.info(
                    "Saving {} bar reports..."+
                    barReports.size()
            );

            List<BarReports> savedReports =
                    barReportsRepository.saveAll(
                            barReports
                    );

            if (savedReports == null ||
                    savedReports.isEmpty()) {

                log.info(
                        "SALON REPORTS WERE NOT SAVED"
                );

                throw new IllegalStateException(
                        "Bar reports were not saved"
                );
            }

            log.info(
                    "SALON REPORTS SAVED SUCCESSFULLY. Count: {}"+
                    savedReports.size()
            );

            // ============================
            // PARTIAL ERRORS
            // ============================

            if (!errors.isEmpty()) {

                log.info(
                        "Some reports were skipped: {}"+
                        errors
                );
            }

            return new ResponseList<>(
                    savedReports
            );

        } catch (Exception e) {

            log.info(
                    "ERROR WHILE SAVING BAR REPORTS"+
                    e
            );

            throw e;
        }
    }
    private ResponseList<ServiceAndStoreReport> saveServiceAndStoreReport(
            ResponseList<BarReports> reports) {

        log.info("{} Is Saving Service and Store Report"+ LoggerUser.getEmail());

        if (reports == null ||
                reports.getData() == null ||
                reports.getData().isEmpty()) {

            throw new BusinessException("Provide Bar Reports");
        }

        List<ServiceAndStoreReport> serviceAndStoreReports = new ArrayList<>();

        Map<String, List<StoreOpen>> openStoresByService =
                getOpenStoresByService(reports.getData());

        for (BarReports report : reports.getData()) {
            Integer storeOpened=0;
            List<StoreOpen> storeOpens =
                    openStoresByService.getOrDefault(
                            report.getBarServiceEntity().getUid(),
                            List.of()
                    );

            if (storeOpens.isEmpty()) {
                throw new BusinessException(
                        "No Open Store Found For Service:  " + report.getBarServiceEntity().getServiceName()
                );
            }

            storeOpened=storeOpens.size();
            for (StoreOpen storeOpen : storeOpens) {

                ServiceAndStoreReport storeReport =
                        new ServiceAndStoreReport();

                storeReport.setBarReports(report);
                storeReport.setStoreOpen(storeOpen);
                storeReport.setSharedAmount(report.getBarServiceEntity().getPrice()/storeOpened);
                serviceAndStoreReports.add(storeReport);
            }
        }

        if (serviceAndStoreReports.isEmpty()) {

            throw new BusinessException("No Service And Store Report To Save");
        }

        List<ServiceAndStoreReport> savedReports =
                serviceAndStoreReportRepository.saveAll(
                        serviceAndStoreReports
                );

        if (savedReports == null || savedReports.isEmpty()) {

            throw new BusinessException("Error in saving Service and Store Report");
        }



        return new ResponseList<>(savedReports);
    }

    // Open stores for every service in a batch of reports, grouped by service UID.
    private Map<String, List<StoreOpen>> getOpenStoresByService(List<BarReports> reports) {
        List<String> serviceUids = reports.stream()
                .map(BarReports::getBarServiceEntity)
                .filter(Objects::nonNull)
                .map(BarServiceEntity::getUid)
                .distinct()
                .toList();
        if (serviceUids.isEmpty()) {
            return Map.of();
        }
        Map<String, List<StoreOpen>> byService = new HashMap<>();
        for (StoreOpen storeOpen : openStoreRepository.findOpenStoreListByServices(
                LoggerUser.getBranchUID(),
                serviceUids
        )) {
            BarServiceEntity service = storeOpen.getStore() != null
                    ? storeOpen.getStore().getBarServiceEntity()
                    : null;
            if (service != null) {
                byService.computeIfAbsent(service.getUid(), uid -> new ArrayList<>()).add(storeOpen);
            }
        }
        return byService;
    }

    private Integer calculatePercentage(Integer percentage, Integer amount) {
        if (percentage == null || amount == null) {
            return 0;
        }
        return (percentage * amount) / 100;
    }
    public Response<BarReports> findBarReportByUID(String barReportUID){
        log.info(LoggerUser.getEmail() + "Is Accessing Bar Reports");
        Optional<BarReports> optionalBarReports = barReportsRepository.findBarReportByUID(barReportUID, LoggerUser.getBranchUID());
        return optionalBarReports.map(Response::new).orElseGet(()->new Response<>("Report Not Found"));
    }
    public ResponsePage<BarProjection> findBarReportsPage(Integer page, Integer size, LocalDate date){
        log.info(LoggerUser.getEmail() + "Is Accessing Reports");
        Pageable pageable = PageRequest.of(page, size);
        return new ResponsePage<>(barReportsRepository.findBarReportsPage(pageable, LoggerUser.getBranchUID(), date));
    }
    public Response<BarProjection> findBarRevenueReport(LocalDate date){
        log.info(LoggerUser.getEmail() + "is accessing Commissions ");
        Optional<BarProjection> optionalBarProjection=barReportsRepository.findBarRevenueReport(LoggerUser.getBranchUID(), date);
        return optionalBarProjection.map(Response::new).orElseGet(() -> new Response<>("No Report Found"));
    }
    public Response<BarProjection> findCurrentBarRevenueReport(String filter) {

        LocalDate today = LocalDate.now();

        LocalDate startDate;
        LocalDate endDate;

        if (filter == null || filter.isBlank()) {

            // Default = leo
            startDate = today;
            endDate = today.plusDays(1);

        } else {

            switch (filter.toUpperCase()) {

                case "DAY":
                    // Leo
                    startDate = today;
                    endDate = today.plusDays(1);
                    break;
                case "YESTERDAY":
                    // Jana
                    startDate = today.minusDays(1);
                    endDate = today;
                    break;

                case "WEEK":
                    // Wiki hii
                    startDate = today.with(
                            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                    );
                    endDate = startDate.plusWeeks(1);
                    break;

                case "LAST_WEEK":
                    // Wiki iliyopita
                    startDate = today.with(
                            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                    ).minusWeeks(1);

                    endDate = startDate.plusWeeks(1);
                    break;

                case "MONTH":
                    // Mwezi huu
                    startDate = today.withDayOfMonth(1);
                    endDate = startDate.plusMonths(1);
                    break;

                case "LAST_MONTH":
                    // Mwezi uliopita
                    startDate = today.withDayOfMonth(1).minusMonths(1);
                    endDate = startDate.plusMonths(1);
                    break;

                case "THIS_YEAR":
                    // Mwaka huu
                    startDate = LocalDate.of(today.getYear(), 1, 1);
                    endDate = startDate.plusYears(1);
                    break;
                case "LAST_YEAR":
                    // Mwaka uliopita
                    startDate = today
                            .with(TemporalAdjusters.firstDayOfYear())
                            .minusYears(1);

                    endDate = today
                            .with(TemporalAdjusters.firstDayOfYear());

                    break;

                default:
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                        startDate = LocalDate.parse(filter, formatter);
                        endDate = startDate.plusDays(1);
                    } catch (DateTimeParseException e) {

                        throw new IllegalArgumentException(
                                "Filter must be DAY, WEEK, LAST_WEEK, MONTH, " +
                                        "LAST_MONTH, THIS_YEAR or date in format dd-MM-yyyy"
                        );
                    }
            }
        }Optional<BarProjection> optionalBarProjection =
                barReportsRepository.findCurrentBarRevenueReport(
                        LoggerUser.getBranchUID(),
                        startDate,
                        endDate
                );

        return optionalBarProjection
                .map(Response::new)
                .orElseGet(() -> new Response<>("Not found"));

    }
    public ResponseList<BarServiceRevenueProjection> findBarRevenueByService(LocalDate date) {

        List<BarServiceRevenueProjection> data = barReportsRepository.findBarRevenueByService(LoggerUser.getBranchUID(), date);
        ResponseList<BarServiceRevenueProjection> response = new ResponseList<>();
        response.setData(data);
        return response;
    }
    public ResponsePage<BarProjection> findCurrentBarReportsPage(int page, int size, String filter) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        if (filter == null || filter.isBlank()) {
            startDate = today;
            endDate = today.plusDays(1);
        } else {
            switch (filter.toUpperCase()) {
                case "DAY":
                    startDate = today;
                    endDate = today.plusDays(1);
                    break;
                case "YESTERDAY":
                    // Jana
                    startDate = today.minusDays(1);
                    endDate = today;
                    break;
                case "WEEK":
                    startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    endDate = startDate.plusWeeks(1);
                    break;
                case "LAST_WEEK":
                    startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
                    endDate = startDate.plusWeeks(1);
                    break;
                case "MONTH":
                    startDate = today.withDayOfMonth(1);
                    endDate = startDate.plusMonths(1);
                    break;
                case "LAST_MONTH":
                    startDate = today.withDayOfMonth(1).minusMonths(1);
                    endDate = startDate.plusMonths(1);
                    break;
                case "THIS_YEAR":
                    startDate = LocalDate.of(today.getYear(), 1, 1);
                    endDate = startDate.plusYears(1);
                    break;
                case "LAST_YEAR":
                    // Mwaka uliopita
                    startDate = today
                            .with(TemporalAdjusters.firstDayOfYear())
                            .minusYears(1);

                    endDate = today
                            .with(TemporalAdjusters.firstDayOfYear());

                    break;
                default:
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                        startDate = LocalDate.parse(filter, formatter);
                        endDate = startDate.plusDays(1);
                    } catch (DateTimeParseException e) {
                        throw new IllegalArgumentException("Filter must be DAY, WEEK, LAST_WEEK, MONTH, " +
                                "LAST_MONTH, THIS_YEAR or date in format dd-MM-yyyy");
                    }
            }
        }
        return new ResponsePage<>(barReportsRepository.findCurrentBarReportsPage(pageable, LoggerUser.getBranchUID(), startDate, endDate));
    }
    public ResponseList<BarServiceRevenueProjection> findCurrentBarRevenueByService(String filter) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        if (filter == null || filter.isBlank()) {
            startDate = today;
            endDate = today.plusDays(1);
        } else {
            switch (filter.toUpperCase()) {
                case "DAY":
                    startDate = today;
                    endDate = today.plusDays(1);
                    break;
                case "YESTERDAY":
                    // Jana
                    startDate = today.minusDays(1);
                    endDate = today;
                    break;

                case "WEEK":
                    // Wiki hii
                    startDate = today.with(
                            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                    );
                    endDate = startDate.plusWeeks(1);
                    break;

                case "LAST_WEEK":
                    // Wiki iliyopita
                    startDate = today.with(
                            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                    ).minusWeeks(1);

                    endDate = startDate.plusWeeks(1);
                    break;

                case "MONTH":
                    // Mwezi huu
                    startDate = today.withDayOfMonth(1);
                    endDate = startDate.plusMonths(1);
                    break;

                case "LAST_MONTH":
                    // Mwezi uliopita
                    startDate = today.withDayOfMonth(1).minusMonths(1);
                    endDate = startDate.plusMonths(1);
                    break;

                case "THIS_YEAR":
                    // Mwaka huu
                    startDate = LocalDate.of(today.getYear(), 1, 1);
                    endDate = startDate.plusYears(1);
                    break;
                case "LAST_YEAR":
                    // Mwaka uliopita
                    startDate = today
                            .with(TemporalAdjusters.firstDayOfYear())
                            .minusYears(1);

                    endDate = today
                            .with(TemporalAdjusters.firstDayOfYear());

                    break;

                default:
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                        startDate = LocalDate.parse(filter, formatter);
                        endDate = startDate.plusDays(1);
                    } catch (DateTimeParseException e) {

                        throw new IllegalArgumentException(
                                "Filter must be DAY, WEEK, LAST_WEEK, MONTH, " +
                                        "LAST_MONTH, THIS_YEAR or date in format dd-MM-yyyy"
                        );
                    }
            }
        }

        return new ResponseList<>(barReportsRepository.findCurrentBarRevenueByService(LoggerUser.getBranchUID(), startDate, endDate));
    }
    /***
     STAFF_COMMISSION_METHODS
     */
    @Transactional
    private StaffCommissions addStaffCommission(String staffUid, String branchUid, LocalDate date, List<Integer> values) {
        int value = values.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        Optional<StaffCommissions> existing = staffCommissionsRepository.findCommission(staffUid, branchUid, date);
        StaffCommissions commission;
        if (existing.isPresent()) {
            commission = existing.get();
            int currentTotal = commission.getTotalAmount() == null ? 0 : commission.getTotalAmount();
            int currentRemaining = commission.getRemainingAmount() == null ? 0 : commission.getRemainingAmount();
            commission.setTotalAmount(currentTotal + value);
            commission.setRemainingAmount(currentRemaining + value);
        } else {
            commission = new StaffCommissions();
            BarStaff staff = new BarStaff();
            staff.setUid(staffUid);
            commission.setBarStaff(staff);
            commission.setBranchUid(branchUid);
            commission.setCreatedAt(date);
            commission.setPaymentStatus("NOT PAID");
            commission.setPayedAmount(0);
            commission.setTotalAmount(value);
            commission.setRemainingAmount(value);
        }
        return staffCommissionsRepository.save(commission);
    }
    public ResponsePage<BarProjection> findStaffCommissionPage(String range, Integer page, Integer size) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        Pageable pageable = PageRequest.of(page, size);
        // Specific date: 05-8-2026, 5-8-2026, 05-08-2026, etc.
        if (range.matches("\\d{1,2}-\\d{1,2}-\\d{4}")) {
            try {
                LocalDate date = LocalDate.parse(
                        range,
                        DateTimeFormatter.ofPattern("d-M-yyyy")
                );
                startDate = date;
                endDate = date;

            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid date format: " + range +
                                ". Expected format: dd-M-yyyy"
                );
            }

        }  else {

            switch (range.toUpperCase()) {
                case "TODAY" -> {
                    startDate = today;
                    endDate = today;
                }

                case "YESTERDAY" -> {
                    startDate = today.minusDays(1);
                    endDate = today.minusDays(1);
                }
                case "THIS_WEEK" -> {
                    startDate = today.with(DayOfWeek.MONDAY);
                    endDate = startDate.plusDays(6);
                }
                case "LAST_WEEK" -> {
                    startDate = today
                            .with(DayOfWeek.MONDAY)
                            .minusWeeks(1);
                    endDate = startDate.plusDays(6);
                }
                case "THIS_MONTH" -> {
                    startDate = today.withDayOfMonth(1);
                    endDate = today.withDayOfMonth(today.lengthOfMonth());
                }

                case "LAST_MONTH" -> {
                    LocalDate lastMonth = today.minusMonths(1);
                    startDate = lastMonth.withDayOfMonth(1);
                    endDate = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
                }

                case "THIS_YEAR" -> {
                    startDate = today.withDayOfYear(1);
                    endDate = today.withDayOfYear(today.lengthOfYear());
                }

                case "LAST_YEAR" -> {
                    LocalDate lastYear = today.minusYears(1);
                    startDate = lastYear.withDayOfYear(1);
                    endDate = lastYear.withDayOfYear(lastYear.lengthOfYear());
                }

                default -> throw new IllegalArgumentException("Invalid date range: " + range);
            }
        }

        return new ResponsePage<>(staffCommissionsRepository.findStaffCommissionPage(
                pageable,
                LoggerUser.getBranchUID(),
                startDate,
                endDate
        ));
    }
    public Response<StaffCommissions> payStaffCommission(StaffCommissionDTO staffCommissionDTO){
        log.info(LoggerUser.getEmail() + "Is Paying Staff Commission");
        if(staffCommissionDTO == null)
            return new Response<>("Weka Details za Malipo");
        StaffCommissions staffCommissions =null;
        if(staffCommissionDTO.getUid() ==null)
            return new Response<>("Weka Commission REF");
        Optional<StaffCommissions> optionalStaffCommissions = staffCommissionsRepository.findById(staffCommissionDTO.getUid());
        if(optionalStaffCommissions.isEmpty())
            return new Response<>("Commission ya staff Haipo");
        staffCommissions = optionalStaffCommissions.get();
        staffCommissions.update();
        if(staffCommissionDTO.getAmount() == null)
            return new Response<>("Weka Kiasi kinacholipwa");
        staffCommissions.setPayedAmount(staffCommissionDTO.getAmount() + staffCommissions.getPayedAmount());
        staffCommissions.setRemainingAmount(staffCommissions.getRemainingAmount() - staffCommissionDTO.getAmount());
        List<IncomeExpenses> incomeExpenses = getIncomeExpensesFilter(staffCommissionDTO.getFilter(), staffCommissionDTO.getWeekDate());
        StaffCommissions savedStaffCommission = null;
        try{
            savedStaffCommission = staffCommissionsRepository.save(staffCommissions);
            incomeExpenses = getIncomeExpensesFilter(staffCommissionDTO.getFilter(), staffCommissionDTO.getWeekDate());
        } catch (Exception e) {
            e.printStackTrace();
            return new Response<>("Error wakati wa kulipa");
        }
        if(incomeExpenses.isEmpty())
            throw new BusinessException("Income Expenses Not Found");
        int number = incomeExpenses.size();
        List<IncomeExpenses> saveIncomeAndExpenses= new ArrayList<>();
        List<IncomeExpensesDescription> incomeExpensesDescriptions = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.valueOf(
                staffCommissionDTO.getAmount()
        );

        BigDecimal numberOfRecords = BigDecimal.valueOf(number);
        BigDecimal baseAmount = totalAmount.divide(
                numberOfRecords,
                0,
                RoundingMode.DOWN
        );

        BigDecimal remainder = totalAmount.remainder(numberOfRecords);
        for (int i = 0; i < incomeExpenses.size(); i++) {
            IncomeExpenses expenses = incomeExpenses.get(i);
            IncomeExpensesDescription incomeExpensesDescription = new IncomeExpensesDescription();
            expenses.setDescriptions("Staff Commission");
            BigDecimal amountPerExpense = baseAmount;
            if (i == incomeExpenses.size() - 1) {
                amountPerExpense = baseAmount.add(remainder);
            }
            BigDecimal currentExpenses = expenses.getExpenses();
            if (currentExpenses == null) {
                currentExpenses = BigDecimal.ZERO;
            }
            expenses.setExpenses(currentExpenses.add(amountPerExpense));
            saveIncomeAndExpenses.add(expenses);
            incomeExpensesDescription.setSpendAmount(amountPerExpense);
            incomeExpensesDescription.setStaffName(savedStaffCommission.getBarStaff().getFirstName() + "  " +savedStaffCommission.getBarStaff().getLastName());
            incomeExpensesDescription.setDescription(staffCommissionDTO.getDescriptions());

                    incomeExpensesDescription.setDescriptionDate(LocalDate.now());
            incomeExpensesDescription.setIncomeExpenses(expenses);
            incomeExpensesDescriptions.add(incomeExpensesDescription);
        }
        try{
            incomeExpensesRepository.saveAll(saveIncomeAndExpenses);
            incomeExpensesDescriptionRepository.saveAll(incomeExpensesDescriptions);
        }catch(Exception e){
            e.printStackTrace();
            throw new BusinessException(e.getMessage());
        }
        return new Response<>(savedStaffCommission);
    }
    public List<IncomeExpenses> getIncomeExpensesFilter(String filter, LocalDate weekDate) {
        log.info("FILTER" + filter);
        LocalDate today = LocalDate.now();

        LocalDate startDate;
        LocalDate endDate;

        switch (filter) {

            case "TODAY" -> {
                startDate = getWeekStart(today);
                endDate = startDate.plusDays(6);
            }


            case "YESTERDAY" -> {
                startDate = today.minusDays(1);
                endDate = today.minusDays(1);
            }

            case "THIS_WEEK" -> {
                startDate = getWeekStart(today);
                endDate = startDate.plusDays(6);
            }

            case "LAST_WEEK" -> {
                startDate = getWeekStart(today).minusWeeks(1);
                endDate = startDate.plusDays(6);
            }

            case "THIS_MONTH" -> {
                startDate = today.withDayOfMonth(1);
                endDate = today.withDayOfMonth(
                        today.lengthOfMonth()
                );
            }

            case "LAST_MONTH" -> {
                LocalDate lastMonth = today.minusMonths(1);

                startDate = lastMonth.withDayOfMonth(1);
                endDate = lastMonth.withDayOfMonth(
                        lastMonth.lengthOfMonth()
                );
            }

            case "THIS_YEAR" -> {
                startDate = today.withDayOfYear(1);
                endDate = today.withDayOfYear(
                        today.lengthOfYear()
                );
            }

            case "LAST_YEAR" -> {
                LocalDate lastYear = today.minusYears(1);

                startDate = lastYear.withDayOfYear(1);
                endDate = lastYear.withDayOfYear(
                        lastYear.lengthOfYear()
                );
            }

            default -> throw new BusinessException(
                    "Invalid filter: " + filter
            );
        }

        return incomeExpensesRepository.findByBranchAndWeekStartDateBetween(
                LoggerUser.getBranchUID(),
                weekDate
        );
    }
    private LocalDate getWeekStart(LocalDate date) {

        int daysFromSaturday =
                (date.getDayOfWeek().getValue() + 1) % 7;

        return date.minusDays(daysFromSaturday);
    }
    private LocalDate[] getDateRange(String filterDate) {

        LocalDate today = LocalDate.now();

        LocalDate startDate;
        LocalDate endDate;

        switch (filterDate) {

            case "TODAY":
                startDate = today;
                endDate = today.plusDays(1);
                break;

            case "YESTERDAY":
                startDate = today.minusDays(1);
                endDate = today;
                break;

            case "THIS_WEEK":
                startDate = today.with(
                        TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                );
                endDate = startDate.plusWeeks(1);
                break;

            case "LAST_WEEK":
                startDate = today
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .minusWeeks(1);

                endDate = startDate.plusWeeks(1);
                break;

            default:
                startDate = today;
                endDate = today.plusDays(1);
                break;
        }

        return new LocalDate[]{startDate, endDate};
    }
    /***
     BAR_STORE_METHODS
     */
    public Response<Store> saveStore(StoreDTO storeDTO){
        log.info(LoggerUser.getEmail() + "Is Saving Store Items");
        if(storeDTO == null)
            return new Response<>("Provide Store Details");
        Store store = null;
        if(storeDTO.getUid() !=null){
            Optional<Store> optionalStore = storeRepository.findById(storeDTO.getUid());
            if(optionalStore.isEmpty())
                return new Response<>("Store Not Found");
            store = optionalStore.get();
            store.update();
        }else{
            store = new Store();
        }
        store.setCodeOfStore(generateCode(storeDTO.getNameOfStore()));
        if(storeDTO.getNameOfStore() == null)
            return new Response<>("Provide Store Item Name");
        store.setNameOfStore(storeDTO.getNameOfStore());
        if(storeDTO.getDescription() == null)
            return new Response<>("Provide Descriptions For Store Item");
        store.setDescription(storeDTO.getDescription());
        if(storeDTO.getQuantity() == null)
            return new Response<>("Provide Quantity For Store");
        store.setQuantity(storeDTO.getQuantity());
        if(storeDTO.getBarServiceEntityUID() == null)
            return new Response<>("provide Service");
        Optional<BarServiceEntity> optionalBarServiceEntity = barServiceRepository.findById(storeDTO.getBarServiceEntityUID());
        if(optionalBarServiceEntity.isEmpty())
            return new Response<>("Service Not Found");
        store.setBarServiceEntity(optionalBarServiceEntity.get());
        if(storeDTO.getBuyingPrice() == null)
            return new Response<>("Provide Buying Price For Store");
        store.setBuyingPrice(storeDTO.getBuyingPrice());
        store.setTotalQuantityPrice(storeDTO.getBuyingPrice() * storeDTO.getQuantity());
        store.setNotUsedQuantity(storeDTO.getQuantity());

        try{
            return new Response<>(storeRepository.save(store));
        } catch (Exception e) {
            e.printStackTrace();
            return new Response<>("Error in saving Store");
        }
    }
    public ResponseList<BarProjection> findStoreList(){
        log.info(LoggerUser.getEmail() + "Access bar store");
        return new ResponseList<>(storeRepository.findStoreList(LoggerUser.getBranchUID()));
    }
    public ResponsePage<BarProjection> findStorePage(Integer page, Integer size){
        log.info(LoggerUser.getEmail() + "Is accessing Bar Store");
        Pageable pageable = PageRequest.of(page, size);
        return new ResponsePage<>(storeRepository.findStorePage(pageable, LoggerUser.getBranchUID()));
    }
    public Response<Store> deleteBarStore(String storeUID){
        log.info(LoggerUser.getEmail() + "is deleting bar store item");
        if(storeUID == null)
            return new Response<>("Provide Store Item REF");
        Optional<Store> optionalStore = storeRepository.findById(storeUID);
        if(optionalStore.isEmpty())
            return new Response<>("Store Not Found");
        Store store = optionalStore.get();
        storeRepository.delete(store);
        return new Response<>(optionalStore.get());
    }
    private String generateCode(String nameOfStore) {
        if (nameOfStore == null || nameOfStore.trim().isEmpty()) {
            throw new IllegalArgumentException("Store name cannot be empty");
        }
        String cleanName = nameOfStore
                .replaceAll("[^a-zA-Z]", "")
                .toUpperCase();
        return cleanName.substring(0, Math.min(3, cleanName.length()));
    }
    public Response<Store> addQuantityToStore(StoreDTO storeDTO){
        log.info(LoggerUser.getEmail() + "Is adding quantity to the store items");
        if(storeDTO == null)
            return new Response<>("Provide Data For Adding Quantity");
        Store store= null;
        if(storeDTO.getUid() != null){
            Optional<Store> optionalStore = storeRepository.findById(storeDTO.getUid());
            if(optionalStore.isEmpty())
                return new Response<>("Store Not Found");
            store = optionalStore.get();
        }else{
            store = new Store();
        }
        store.setQuantity(store.getQuantity() + storeDTO.getQuantity());
        store.setNotUsedQuantity(store.getNotUsedQuantity() + storeDTO.getQuantity());
        store.setTotalQuantityPrice(store.getTotalQuantityPrice() + (storeDTO.getQuantity())*store.getBuyingPrice());
        try{
            return new Response<>(storeRepository.save(store));
        }catch (Exception e){
            e.printStackTrace();
            return new Response<>("Error in Adding Quantity");
        }
    }
    public Response<Store> openStore(StoreDTO storeDTO){
        log.info(LoggerUser.getEmail() + "Is Opening Store for use");
        if(storeDTO.getUid() == null)
            return new Response<>("Provide Store REF");
        Optional<Store> optionalStore = storeRepository.findById(storeDTO.getUid());
        if(optionalStore.isEmpty())
            return new Response<>("Store Not Found");
        StoreOpen storeOpen = new StoreOpen();
        storeOpen.setStore(optionalStore.get());
        storeOpen.setOpenQuantity(1);
        storeOpen.setOpenStoreCode(generateOpenStoreCode(optionalStore.get().getCodeOfStore(),
                        openStoreRepository.countByStoreNameAndBranchUid(
                                optionalStore.get().getNameOfStore(),
                                LoggerUser.getBranchUID()
                        )
                )
        );
        try{
            openStoreRepository.save(storeOpen);
            Store store = optionalStore.get();
            store.setQuantity(optionalStore.get().getQuantity());
            store.setNotUsedQuantity(optionalStore.get().getNotUsedQuantity()-1);
            store.setUsedQuantity(optionalStore.get().getUsedQuantity() + 1);
            return new Response<>(storeRepository.save(store));
        }catch (Exception e){
            e.printStackTrace();
            return new Response<>("Error in Opening Store");
        }
    }
    private String generateOpenStoreCode(String code, Long number) {
        LocalDate now = LocalDate.now();

        String month = String.format("%02d", now.getMonthValue());
        String year = String.valueOf(now.getYear());

        long nextNumber = (number == null || number <= 0)
                ? 1L
                : number + 1;

        return String.format(
                "%s-%s-%s-%03d",
                code.toUpperCase(),
                month,
                year,
                nextNumber
        );
    }
    public ResponsePage<BarProjection> findOpenStorePage(Integer page, Integer size, String searchKey){
        log.info(LoggerUser.getEmail() + "is Accessing Stores Open");
        if(page == null || size == null)
            return new ResponsePage<>("Either Page or Size must no be Null");
        Pageable pageable = PageRequest.of(page, size);
        return new ResponsePage<>(openStoreRepository.findOpenStorePage(pageable, LoggerUser.getBranchUID(), searchKey));
    }
    public Response<StoreOpen> closeOpenStore(StoreDTO storeDTO){
        log.info(LoggerUser.getEmail() + "Is closing open store");
        if(storeDTO == null)
            return new Response<>("Weka Taarifa za kufunga Store");
        if(storeDTO.getOpenStoreUID() == null)
            return new Response<>("Provide Open Store REF");
        Optional<StoreOpen> optionalStoreOpen = openStoreRepository.findById(storeDTO.getOpenStoreUID());
        if(optionalStoreOpen.isEmpty())
            return new Response<>("Open Store Not Found");
        StoreOpen storeOpen = optionalStoreOpen.get();
        storeOpen.setUpdatedAt(LocalDate.now());
        storeOpen.setStatus("CLOSED");
        try {
            return new Response<>(openStoreRepository.save(storeOpen));
        }catch (Exception e){
            e.printStackTrace();
            return new Response<>("Error in Closing Open Store");
        }
    }
    public ResponseList<BarProjection> findServiceEntityUIDList(String status){
        log.info(LoggerUser.getEmail() + "is Accessing Service Entity");
        if(status == null)
            return new ResponseList<>("Provide Status");
        return new ResponseList<>(openStoreRepository.findServiceEntityUIDList(LoggerUser.getBranchUID(), status));
    }
    public ResponsePage<StoreReportSummaryProjection> findServiceAndStoreReportPage(Integer page, Integer size, String filter) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        if (filter == null || filter.isBlank()) {
            startDate = today;
            endDate = today.plusDays(1);
        } else {
            switch (filter.toUpperCase()) {
                case "DAY":
                    startDate = today;
                    endDate = today.plusDays(1);
                    break;
                case "YESTERDAY":
                    startDate = today.minusDays(1);
                    endDate = today;
                    break;
                case "WEEK":
                    startDate = today.with(
                            TemporalAdjusters.previousOrSame(
                                    DayOfWeek.MONDAY
                            )
                    );
                    endDate = startDate.plusWeeks(1);
                    break;
                case "LAST_WEEK":
                    startDate = today.with(
                            TemporalAdjusters.previousOrSame(
                                    DayOfWeek.MONDAY
                            )
                    ).minusWeeks(1);
                    endDate = startDate.plusWeeks(1);
                    break;
                case "MONTH":
                    startDate = today.withDayOfMonth(1);
                    endDate = startDate.plusMonths(1);
                    break;
                case "LAST_MONTH":
                    startDate = today
                            .withDayOfMonth(1)
                            .minusMonths(1);
                    endDate = startDate.plusMonths(1);
                    break;
                case "THIS_YEAR":
                    startDate = LocalDate.of(
                            today.getYear(),
                            1,
                            1
                    );
                    endDate = startDate.plusYears(1);
                    break;
                case "LAST_YEAR":
                    startDate = LocalDate.of(
                            today.getYear() - 1,
                            1,
                            1
                    );
                    endDate = startDate.plusYears(1);
                    break;

                default:
                    try {
                        DateTimeFormatter formatter =
                                DateTimeFormatter.ofPattern("yyyy-MM-dd");
                        startDate = LocalDate.parse(
                                filter,
                                formatter
                        );
                        endDate = startDate.plusDays(1);
                    } catch (DateTimeParseException e) {
                        throw new BusinessException(
                                "Filter must be DAY, YESTERDAY, WEEK, " +
                                        "LAST_WEEK, MONTH, LAST_MONTH, " +
                                        "THIS_YEAR or date in format yyyy-MM-dd"
                        );
                    }
            }
        }

        String branchUID = LoggerUser.getBranchUID();

        Page<StoreReportSummaryProjection> result =
                serviceAndStoreReportRepository
                        .findStoreReportSummaryPage(
                                pageable,
                                branchUID,
                                startDate,
                                endDate
                        );

        return new ResponsePage<>(result);
    }
    /***
     BAR_STOCK_AND_PURCHASE_METHODS
     */

    public Optional<StockAndPurchase> getCurrentWeekByService(String serviceUid) {
        LocalDate weekDate = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return stockAndPurchaseRepository.findCurrentWeekByService(
                LoggerUser.getBranchUID(),
                serviceUid,
                weekDate
        );
    }

    // Same week-row lookup as above, but for every service on a sale at once.
    // Rows come back newest first, so the first one seen per service wins.
    private Map<String, StockAndPurchase> getCurrentWeekByServices(List<BarServiceEntity> services) {
        List<String> serviceUids = services.stream()
                .filter(Objects::nonNull)
                .map(BarServiceEntity::getUid)
                .toList();
        if (serviceUids.isEmpty()) {
            return Map.of();
        }
        LocalDate weekDate = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<String, StockAndPurchase> byService = new HashMap<>();
        for (StockAndPurchase purchase : stockAndPurchaseRepository.findCurrentWeekByServices(
                LoggerUser.getBranchUID(),
                serviceUids,
                weekDate
        )) {
            if (purchase.getBarService() != null) {
                byService.putIfAbsent(purchase.getBarService().getUid(), purchase);
            }
        }
        return byService;
    }
    public ResponseList<StockAndPurchase> saveStockAndPurchase(List<BarServiceEntity> barServiceEntities,
                                                               Map<String, Commission> commissionsByService) {
        log.info(LoggerUser.getEmail() + " Is saving Stock And Purchase");
        if (barServiceEntities.isEmpty()) {
            throw new BusinessException("Bar Service is Empty");
        }

        Map<String, StockAndPurchase> currentWeekByService =
                getCurrentWeekByServices(barServiceEntities);

        List<StockAndPurchase> stockAndPurchases = new ArrayList<>();
        for (BarServiceEntity entity : barServiceEntities) {
            Commission commission = commissionsByService.get(entity.getUid());
            if (commission == null) {
                throw new BusinessException("Commission Not Found For: " + entity.getServiceName());
            }

            StockAndPurchase stockAndPurchase =
                    currentWeekByService.get(entity.getUid());

            int stockPurchaseAmount =
                    entity.getPrice()
                            * commission.getStockPurchasePercent()
                            / 100;

            StockAndPurchase purchase;

            if (stockAndPurchase == null) {

                purchase = new StockAndPurchase();

                purchase.setBarService(entity);
                purchase.setCommission(commission);

                purchase.setTotalAmount(stockPurchaseAmount);
                purchase.setRemainingAmount(stockPurchaseAmount);

            } else {

                purchase = stockAndPurchase;

                purchase.setTotalAmount(
                        purchase.getTotalAmount() + stockPurchaseAmount
                );

                purchase.setRemainingAmount(
                        purchase.getRemainingAmount() + stockPurchaseAmount
                );
            }

            stockAndPurchases.add(purchase);
        }

        try {

            return new ResponseList<>(
                    stockAndPurchaseRepository.saveAll(stockAndPurchases)
            );

        } catch (Exception e) {

          e.printStackTrace();
            throw new BusinessException(
                    "Error In Saving Stock And Purchase: " + e.getMessage()
            );
        }
    }
    public ResponseList<StockAndPurchaseProjection> getStockAndPurchaseByFilter(String filter) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        switch (filter.toUpperCase()) {
            case "THIS_WEEK" -> {startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                endDate = startDate;
            }

            case "LAST_WEEK" -> {

                startDate = today.with(
                        TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                ).minusWeeks(1);

                endDate = startDate;
            }

            case "THIS_MONTH" -> {

                startDate = today.with(
                        TemporalAdjusters.firstDayOfMonth()
                );

                endDate = today.with(
                        TemporalAdjusters.lastDayOfMonth()
                );
            }

            case "LAST_MONTH" -> {

                LocalDate lastMonth = today.minusMonths(1);

                startDate = lastMonth.with(
                        TemporalAdjusters.firstDayOfMonth()
                );

                endDate = lastMonth.with(
                        TemporalAdjusters.lastDayOfMonth()
                );
            }

            case "THIS_YEAR" -> {

                startDate = today.with(
                        TemporalAdjusters.firstDayOfYear()
                );

                endDate = today.with(
                        TemporalAdjusters.lastDayOfYear()
                );
            }

            case "LAST_YEAR" -> {

                LocalDate lastYear = today.minusYears(1);

                startDate = lastYear.with(
                        TemporalAdjusters.firstDayOfYear()
                );

                endDate = lastYear.with(
                        TemporalAdjusters.lastDayOfYear()
                );
            }

            default -> throw new BusinessException(
                    "Invalid filter: " + filter
            );
        }


        List<StockAndPurchaseProjection> stockAndPurchases =
                stockAndPurchaseRepository.findByWeekDateRange(
                        LoggerUser.getBranchUID(),
                        startDate,
                        endDate
                );

        return new ResponseList<>(stockAndPurchases);
    }
    @Transactional
    public Response<StockAndPurchase> payStockAndPurchase(PayStockAndPurchaseDTO dto) {

        log.info(LoggerUser.getEmail() + " Is Paying Stock and Purchase");


        // ==============================
        // VALIDATION
        // ==============================

        if (dto == null) {
            return new Response<>("Provide Data For Pay Stock");
        }

        if (dto.getUid() == null || dto.getUid().isBlank()) {
            return new Response<>("Provide Stock And Pay REF");
        }

        if (dto.getAmount() == null || dto.getAmount() <= 0) {
            return new Response<>("Provide Valid Payment Amount");
        }


        // ==============================
        // FIND STOCK
        // ==============================

        Optional<StockAndPurchase> optionalStockAndPurchase =
                stockAndPurchaseRepository.findById(dto.getUid());

        if (optionalStockAndPurchase.isEmpty()) {
            return new Response<>("Pay And Stock Not Found");
        }

        log.info(" Service Imepatikana " + optionalStockAndPurchase.get().getBarService().getServiceName());

        StockAndPurchase stockAndPurchase =
                optionalStockAndPurchase.get();


        // ==============================
        // FIND INCOME EXPENSE
        // ==============================

        Optional<IncomeExpenses> optionalIncomeExpenses =
                incomeExpensesRepository.findIncomeAndExpensesByWeekDate(
                        LoggerUser.getBranchUID(),
                        dto.getWeekDate()
                );

        if (optionalIncomeExpenses.isEmpty()) {
            return new Response<>("No Income Recorded This Week");
        }

        log.info(" Income And Expenses  :" + optionalIncomeExpenses.get().getName());

        IncomeExpenses incomeExpenses = optionalIncomeExpenses.get();
        // ==============================
        // PAYMENT AMOUNT
        // ==============================

        BigDecimal amount =
                BigDecimal.valueOf(dto.getAmount());


        // ==============================
        // CHECK REMAINING
        // ==============================

        if (dto.getAmount() >
                stockAndPurchase.getRemainingAmount()) {

            return new Response<>(
                    "Payment Amount Is Greater Than Remaining Amount"
            );
        }


        // ==============================
        // UPDATE INCOME EXPENSE
        // ==============================

        BigDecimal currentExpenses =
                incomeExpenses.getExpenses() != null
                        ? incomeExpenses.getExpenses()
                        : BigDecimal.ZERO;

        incomeExpenses.setExpenses(
                currentExpenses.add(amount)
        );

        incomeExpensesRepository.save(incomeExpenses);


        // ==============================
        // UPDATE STOCK
        // ==============================

        Integer currentPaid =
                stockAndPurchase.getPayedAmount() != null
                        ? stockAndPurchase.getPayedAmount()
                        : 0;

        Integer currentRemaining =
                stockAndPurchase.getRemainingAmount() != null
                        ? stockAndPurchase.getRemainingAmount()
                        : 0;


        Integer paymentAmount =
                dto.getAmount();


        Integer newPaid =
                currentPaid + paymentAmount;

        Integer newRemaining =
                currentRemaining - paymentAmount;


        stockAndPurchase.setPayedAmount(newPaid);

        stockAndPurchase.setRemainingAmount(newRemaining);


        // ==============================
        // PAYMENT STATUS
        // ==============================

        if (newRemaining <= 0) {

            stockAndPurchase.setRemainingAmount(0);

            stockAndPurchase.setPaymentStatus("PAID");

        } else {

            stockAndPurchase.setPaymentStatus("PARTIAL");

        }


        // ==============================
        // SAVE STOCK
        // ==============================

        StockAndPurchase savedStock =
                stockAndPurchaseRepository.save(
                        stockAndPurchase
                );


        // ==============================
        // SAVE PAYMENT DESCRIPTION
        // ==============================

        IncomeExpensesDescription incomeExpensesDescription=new IncomeExpensesDescription();

        log.info(" Stock And Purchase NAME  :" + savedStock.getBarService().getServiceName());

        incomeExpensesDescription.setIncomeExpenses(incomeExpenses);

        incomeExpensesDescription.setDescription(
                dto.getDescription()
        );

        incomeExpensesDescription.setSpendAmount(
                BigDecimal.valueOf(dto.getAmount())
        );

        incomeExpensesDescription.setDescriptionDate(
                LocalDate.now()
        );


        IncomeExpensesDescription incomeExpensesDescription1 =
                incomeExpensesDescriptionRepository.save(
                        incomeExpensesDescription
                );

        log.info(" Stock And Purchase Descriptions UID :" + incomeExpensesDescription1.getUid());
        return new Response<>(savedStock);
    }
    public ResponseList<StockAndPurchaseDescriptions> findStockPurchaseByUid(String stockUID){
        log.info(LoggerUser.getEmail() + "Is Accessing Stock And Purchase     " + stockUID);
        log.info(LoggerUser.getEmail() + "Is Accessing Stock And Purchase     " + LoggerUser.getBranchUID());
        if(stockUID == null)
            return new ResponseList<>("Provide Stock And Purchase REF");
        return new ResponseList<>(stockAndPurchaseDescriptionsRepository.findByStockPurchaseUid(LoggerUser.getBranchUID(), stockUID));
    }



    /***
     POS_HOME_DASHBOARD
     */

    /** The four headline numbers for the signed-in user's own branch. */
    public Response<DashboardSummaryDTO> findBranchDashboard() {
        String branchUID = LoggerUser.getBranchUID();
        LocalDate today = LocalDate.now();

        DashboardSummaryDTO summary = new DashboardSummaryDTO();
        // Already on the principal - the auth filter fetches the branch with
        // the user - so naming it here costs no extra query.
        User currentUser = LoggerUser.getUser();
        if (currentUser != null && currentUser.getBranch() != null) {
            summary.setBranchName(currentUser.getBranch().getBranchName());
        }
        summary.setTodayRevenue(barReportsRepository.totalRevenueOn(branchUID, today));
        summary.setYesterdayRevenue(barReportsRepository.totalRevenueOn(branchUID, today.minusDays(1)));
        summary.setServicesSoldToday(barReportsRepository.countServicesSoldOn(branchUID, today));
        summary.setPendingBillsCount(salesOpenedRepository.countPendingBills(branchUID));
        summary.setPendingBillsAmount(salesOpenedRepository.pendingBillsAmount(branchUID));
        summary.setOpenStores(openStoreRepository.countOpenStores(branchUID));
        summary.setTotalStores(storeRepository.countStores(branchUID));
        return new Response<>(summary);
    }

    /**
     * Revenue per day for the home page's trend line. Days the branch took
     * nothing have no report rows at all, so they are filled in as zero here -
     * otherwise the line would join Friday straight to Monday and read as if
     * the weekend never happened.
     */
    public ResponseList<DailyRevenueDTO> findRevenueTrend(int days) {
        int span = days < 1 ? 30 : Math.min(days, 365);
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(span - 1L);

        Map<LocalDate, Long> byDate = new HashMap<>();
        for (RevenueTrendProjection point : barReportsRepository.revenueTrend(LoggerUser.getBranchUID(), start)) {
            byDate.put(point.getDate(), point.getAmount() == null ? 0L : point.getAmount());
        }

        List<DailyRevenueDTO> trend = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            trend.add(new DailyRevenueDTO(date, byDate.getOrDefault(date, 0L)));
        }
        return new ResponseList<>(trend);
    }


    /** Staff ranked by what they earned the branch this month. */
    public ResponseList<StaffEarningsProjection> findStaffEarnings() {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        return new ResponseList<>(
                barReportsRepository.staffEarningsSince(LoggerUser.getBranchUID(), monthStart)
        );
    }

}
