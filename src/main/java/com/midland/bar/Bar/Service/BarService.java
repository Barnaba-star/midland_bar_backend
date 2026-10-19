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
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Bar.Dto.StaffRowDTO;
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
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;


@Service
@Log
@RequiredArgsConstructor
public class BarService {
    private final com.midland.bar.Utils.MoneyLock moneyLock;

    /** Selling and paying out need the login's shift open. */
    private final WorkShiftService workShiftService;
    private final PriceRuleService priceRuleService;
    private final com.midland.bar.Bar.Alert.OwnerAlertService ownerAlertService;
    private final com.midland.bar.Bar.Repository.WorkShiftRepository workShiftRepository;
    private final com.midland.bar.Bar.Repository.BillDiscountRepository billDiscountRepository;
    private final com.midland.bar.Setting.Repository.SystemSettingRepository systemSettingRepository;
    private final com.midland.bar.Config.Security.AuthChecker authChecker;
  /** The payment methods a payout may go by - the same as a bill's. */
  private static final java.util.Set<String> PAYOUT_METHODS = java.util.Set.of("cash", "mpesa", "tigopesa", "airtelmoney", "halopesa", "bank");

  /** A payout's method, lower-case; cash when none (or an unknown one) was given. */
  private static String payoutMethod(String method) {
      String m = method == null ? "" : method.trim().toLowerCase();
      return PAYOUT_METHODS.contains(m) ? m : "cash";
  }
  private final BarServiceRepository barServiceRepository;
  private final OtherCommissionService otherCommissionService;
  private final UserRepository userRepository;
  private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder;
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
  private final com.midland.bar.Bar.Repository.OtherCommissionEntryRepository otherCommissionEntryRepository;
  private final IncomeExpensesDescriptionRepository incomeExpensesDescriptionRepository;
  private final StockAndPurchaseRepository stockAndPurchaseRepository;
  private final StockAndPurchaseDescriptionsRepository stockAndPurchaseDescriptionsRepository;
  private final com.midland.bar.Bar.Supplier.SupplierDeliveryItemRepository supplierDeliveryItemRepository;
  private final NotificationService notificationService;
  private final PlatformSettingService platformSettingService;
  private final BillCodeService billCodeService;
  private final StaffCodeService staffCodeService;
  private final StaffOrderRepository staffOrderRepository;
  private final BillLineVoidRepository billLineVoidRepository;
  private final com.midland.bar.Bar.Repository.BarSaleStockRepository barSaleStockRepository;
  private final StaffLossRepository staffLossRepository;
  private final com.midland.bar.Utils.Offline.OfflineOps offlineOps;

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
          serviceEntity.setExtraIngredients(null);
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
              serviceEntity.setExtraIngredients(null);
          } else if ("NONE".equals(source)) {
              serviceEntity.setTrackStock(false);
              serviceEntity.setStockSourceUid(null);
              serviceEntity.setUnitsPerSale(null);
              serviceEntity.setExtraIngredients(null);
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

              // The other stock items one sale takes too: the rice beside the fish.
              List<Ingredients.Ingredient> extras = new ArrayList<>();
              java.util.Set<String> seen = new java.util.HashSet<>(List.of(stockItem.get().getUid()));
              if (barServiceDTO.getExtraIngredients() != null) {
                  for (BarServiceDTO.IngredientDTO in : barServiceDTO.getExtraIngredients()) {
                      if (in == null || in.getStockItemUid() == null || in.getStockItemUid().isBlank())
                          continue;
                      BarServiceEntity item = barServiceRepository.findBarServiceByUID(in.getStockItemUid(), LoggerUser.getBranchUID())
                              .filter(x -> ServiceKind.of(x.getKind()) == ServiceKind.STOCK_ITEM)
                              .orElse(null);
                      if (item == null)
                          return new Response<>("Choose each other ingredient from the store's stock items");
                      if (!seen.add(item.getUid()))
                          return new Response<>(item.getServiceName() + " is in the recipe twice - put it once with the right amount");
                      String unit = in.getSaleUnitName();
                      int count = in.getSaleUnitCount() == null || in.getSaleUnitCount() < 1 ? 1 : in.getSaleUnitCount();
                      int units;
                      if (unit != null && !unit.isBlank()) {
                          Optional<Integer> base = UnitLadders.baseOf(item.getUnitLadder(), unit);
                          if (base.isEmpty())
                              return new Response<>(item.getServiceName() + " has no measure called " + unit);
                          units = base.get() * count;
                      } else if (in.getUnitsPerSale() != null && in.getUnitsPerSale() > 0) {
                          units = in.getUnitsPerSale();
                          unit = null;
                      } else {
                          return new Response<>("Say how much of " + item.getServiceName() + " one sale takes");
                      }
                      extras.add(new Ingredients.Ingredient(item.getUid(), unit, unit == null ? null : count, units));
                  }
              }
              // Not sent at all (an older app's form): keep what it has.
              if (barServiceDTO.getExtraIngredients() != null)
                  serviceEntity.setExtraIngredients(Ingredients.toJson(extras));
          }
      }
      serviceEntity.setUsageType(barServiceDTO.getUsageType());
      try {
          BarServiceEntity saved = barServiceRepository.save(serviceEntity);
          if (ladderChanged && saved.getUid() != null) {
              // A rung resized (a Portion is now 12 Mshikaki) changes what
              // every service made from this item takes per sale.
              for (BarServiceEntity drawing : barServiceRepository.findDrawingOn(saved.getUid(), LoggerUser.getBranchUID())) {
                  if (saved.getUid().equals(drawing.getStockSourceUid()) && drawing.getSaleUnitName() != null)
                      UnitLadders.baseOf(saved.getUnitLadder(), drawing.getSaleUnitName()).ifPresent(base ->
                              drawing.setUnitsPerSale(base * (drawing.getSaleUnitCount() == null ? 1 : drawing.getSaleUnitCount())));
                  // ...and the same for it as another ingredient (the rice of a wali samaki).
                  List<Ingredients.Ingredient> extras = new ArrayList<>();
                  for (Ingredients.Ingredient in : Ingredients.parse(drawing.getExtraIngredients())) {
                      if (saved.getUid().equals(in.uid()) && in.unit() != null) {
                          Optional<Integer> base = UnitLadders.baseOf(saved.getUnitLadder(), in.unit());
                          extras.add(base.isPresent()
                                  ? new Ingredients.Ingredient(in.uid(), in.unit(), in.count(), base.get() * (in.count() == null ? 1 : in.count()))
                                  : in);
                      } else {
                          extras.add(in);
                      }
                  }
                  drawing.setExtraIngredients(Ingredients.toJson(extras));
                  barServiceRepository.save(drawing);
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
   * "SERVICES" leaves the stock items (malighafi) out - they are picked as a
   * service's source, not sold - and "STOCK_ITEMS" lists only them.
   */
  public ResponsePage<BarProjection> findBarServicePage(Integer page, Integer size, String search, String filter){
        log.info(LoggerUser.getEmail() + " Is Accessing Bar Services");
        Pageable pageable = PageRequest.of(page, size);
        try{
            Page<BarProjection> response = barServiceRepository.findBarServicePage(pageable, LoggerUser.getBranchUID(), lowStockLevel(), likePattern(search), "COUNTED".equals(filter),
                    "SERVICES".equals(filter) ? "SERVICE" : "STOCK_ITEMS".equals(filter) ? "STOCK_ITEM" : "ALL");
            return new ResponsePage<>(response);
        }catch(Exception e){
            e.printStackTrace();
            return new ResponsePage<>("Error in Accessing Bar Service");
        }
  }
  public ResponsePage<User>  findUserPageByBranch(Integer page, Integer size, String search){
      log.info(LoggerUser.getEmail() + " Is Accessing User by using Branch");
      Pageable pageable = PageRequest.of(page, size);
      return new ResponsePage<>(userRepository.findUserPageByBranch(pageable, LoggerUser.getBranchUID(), likePattern(search)));
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
    /**
     * Setting > Commission: every service on sale, the ones still without a
     * split first (they cannot be sold until they have one); filter "MISSING"
     * lists only those.
     */
    public ResponsePage<ServiceCommissionProjection> findServiceCommissionPage(Integer page, Integer size, String search, String filter){
        org.springframework.data.domain.Page<ServiceCommissionProjection> rows = commissionRepository.findServiceCommissionPage(
                LoggerUser.getBranchUID(), likePattern(search), "MISSING".equals(filter), PageRequest.of(page, size));
        // A recipe (wali samaki = fish + rice) costs every stock item in it.
        java.util.Set<String> extraUids = new java.util.HashSet<>();
        rows.forEach(r -> Ingredients.parse(r.getExtraIngredients()).forEach(in -> extraUids.add(in.uid())));
        if (extraUids.isEmpty())
            return new ResponsePage<>(rows);
        Map<String, BarServiceEntity> items = new HashMap<>();
        barServiceRepository.findAllById(extraUids).forEach(i -> items.put(i.getUid(), i));
        return new ResponsePage<>(rows.map(r -> {
            List<Ingredients.Ingredient> extras = Ingredients.parse(r.getExtraIngredients());
            if (extras.isEmpty())
                return r;
            int cost = 0;
            StringBuilder names = new StringBuilder(r.getStockSourceName() == null ? "" : r.getStockSourceName());
            for (Ingredients.Ingredient in : extras) {
                BarServiceEntity item = items.get(in.uid());
                if (item == null)
                    continue;
                int perPack = item.getUnitsPerPack() == null || item.getUnitsPerPack() < 1 ? 1 : item.getUnitsPerPack();
                if (item.getBuyingPrice() != null)
                    cost += Math.round((float) item.getBuyingPrice() * Math.max(1, in.units()) / perPack);
                names.append(" + ").append(item.getServiceName());
            }
            return new com.midland.bar.Bar.Projection.RecipeCommissionRow(r, cost, names.toString());
        }));
    }

    public Response<Long> countServicesWithoutCommission() {
        return new Response<>(commissionRepository.countWithoutCommission(LoggerUser.getBranchUID()));
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

        // A new staff member chooses their own code - the one they will tap on
        // the Staff Sell keypad, so exactly three digits - and it must be free
        // in the branch (codes of staff who left are never handed out again).
        // Editing leaves the code alone unless a new one is sent.
        String code = StaffCodeService.normalise(barStaffDTO.getStaffCode());
        boolean isNew = barStaff.getStaffCode() == null;
        // Added without a code or PIN: the system gives a free code and a
        // temporary PIN, shown once to the manager; at their first sign-in
        // the staff member picks their own code and PIN.
        boolean chosenBySystem = false;
        if (code.isEmpty() && isNew) {
            code = staffCodeService.nextCode(LoggerUser.getBranchUIDOrMain());
            chosenBySystem = true;
        }
        if (!code.isEmpty()) {
            // A code sent (editing) must be a new-style one: 4 digits, unique
            // across every branch. Keeping one's own older code is fine.
            boolean unchanged = !isNew && code.equals(StaffCodeService.normalise(barStaff.getStaffCode()));
            if (!unchanged && !StaffCodeService.isNewCode(code))
                return new Response<>("The staff code must be exactly 4 digits, e.g. 2458");
            // The code is secret: say it is taken, never by whom.
            if (!unchanged && barStaffRepository.countCodeHoldersAnyBranch(code, isNew ? null : barStaff.getUid()) > 0)
                return new Response<>("That code is already taken - choose another");
            barStaff.setStaffCode(code);
        }

        // Their PIN for signing in with the code. New staff must have one; an
        // edit changes it only when a new one is sent (and clears any lock).
        String pin = barStaffDTO.getPin() == null ? "" : barStaffDTO.getPin().trim();
        String issuedPin = null;
        if (pin.isEmpty() && isNew) {
            pin = StaffCodeService.randomPin();
            issuedPin = pin;
            chosenBySystem = true;
        }
        if (isNew)
            barStaff.setMustSetCode(chosenBySystem);
        if (!pin.isEmpty()) {
            String problem = StaffPinRules.problem(pin);
            if (problem != null)
                return new Response<>(problem);
            barStaff.setPinHash(passwordEncoder.encode(pin));
            barStaff.setPinFailedAttempts(0);
            barStaff.setPinLockedUntil(null);
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
            BarStaff saved = barStaffRepository.save(barStaff);
            // Shown once in the "code and PIN" dialog; never stored in the clear.
            saved.setIssuedPin(issuedPin);
            return new Response<>(saved);
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
    public ResponsePage<StaffRowDTO> findBarStaffPage(Integer page, Integer size, String search, String category){
        Pageable pageable = PageRequest.of(page, size);
        String categoryFilter = StaffCategory.isValid(category) ? category : "";
        String branchUID = LoggerUser.getBranchUID();
        Page<BarProjection> staff = barStaffRepository.findBarStaffPage(pageable, branchUID, likePattern(search), categoryFilter);
        LoginMatcher logins = new LoginMatcher(branchUID);
        return new ResponsePage<>(staff.map(s -> StaffRowDTO.of(s,
                logins.rolesOf(s.getUserUid(), s.getFirstName(), s.getLastName()))));
    }

    /**
     * Finds the login behind a staff member: the one their row is linked to,
     * or else the only user of the branch with the same first and last name
     * (staff typed in by hand before they were given a login are not linked).
     */
    private class LoginMatcher {
        private final Map<String, User> byUid = new HashMap<>();
        private final Map<String, User> byName = new HashMap<>();

        LoginMatcher(String branchUID) {
            Map<String, Integer> nameCount = new HashMap<>();
            List<User> users = userRepository.findAllUsersWithBranchAndRoles(branchUID);
            for (User user : users) {
                byUid.put(user.getUid(), user);
                nameCount.merge(nameKey(user.getFirstName(), user.getLastName()), 1, Integer::sum);
            }
            for (User user : users) {
                String key = nameKey(user.getFirstName(), user.getLastName());
                if (nameCount.get(key) == 1)
                    byName.put(key, user);
            }
        }

        List<String> rolesOf(String userUid, String firstName, String lastName) {
            User user = null;
            if (userUid != null)
                user = byUid.containsKey(userUid) ? byUid.get(userUid) : userRepository.findById(userUid).orElse(null);
            if (user == null)
                user = byName.get(nameKey(firstName, lastName));
            if (user == null || user.getRoles() == null || !Boolean.TRUE.equals(user.getIsActive()))
                return List.of();
            return user.getRoles().stream().map(Role::getCode).filter(Objects::nonNull).sorted().toList();
        }

        private String nameKey(String firstName, String lastName) {
            return (firstName == null ? "" : firstName.trim().toLowerCase()) + " "
                    + (lastName == null ? "" : lastName.trim().toLowerCase());
        }
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
        // Never a real delete: their sales, commissions and bills point at
        // this row. They are made inactive instead, which takes them off
        // Manage Staff, the sales staff list and Staff Sell, and leaves the
        // history as it was.
        // A bill still open in their name could not be paid once they are
        // inactive, so those have to be closed first.
        if (barStaffRepository.countOpenWork(barStaff.getUid()) > 0)
            return new Response<>("Staff has unpaid bills or orders not yet received - close them first");
        // Someone with a login role (CASHIER, SUPERVISOR...) would just sign
        // in again; the CEO takes the role away first.
        List<String> roles = new LoginMatcher(LoggerUser.getBranchUID())
                .rolesOf(barStaff.getUserUid(), barStaff.getFirstName(), barStaff.getLastName());
        if (!roles.isEmpty())
            return new Response<>("Staff has a login role (" + String.join(", ", roles) + ") - the CEO must remove it first");
        try{
            barStaff.delete();
            barStaffRepository.save(barStaff);
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
        moneyLock.hold(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain()); // before any row lock - see MoneyLock
        workShiftService.requireOpen();
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
            sale.setSoldAt(java.time.LocalDateTime.now());
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
        // A queued add sent twice (the connection dropped on the answer) is applied once.
        if (offlineOps.alreadyApplied().isPresent())
            return salesOpenedRepository.findById(dto.getSalesOpenedUID())
                    .map(b -> new Response<>((SalesOpened) org.hibernate.Hibernate.unproxy(b)))
                    .orElseGet(() -> new Response<>("Open Sale Not Found"));
        workShiftService.requireOpen();
        Response<SalesOpened> result = addItemsToBill(dto);
        if (result.getData() != null)
            offlineOps.claim("ADD_ITEMS", dto.getSalesOpenedUID());
        return result;
    }

    /**
     * addSaleItems without the shift check, for the supervisor receiving a
     * Staff Sell order: the order was written inside the seller's shift, and
     * the supervisor takes no money.
     */
    @Transactional
    public Response<SalesOpened> addItemsToBill(SaleItemsDTO dto) {
        moneyLock.hold(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain()); // before any row lock - see MoneyLock
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
            // The other stock items it is made from (the rice of a wali samaki), locked the same way.
            if (service.getStockSourceUid() != null) {
                for (Ingredients.Ingredient in : Ingredients.parse(service.getExtraIngredients())) {
                    if (storeRows.containsKey(in.uid()))
                        continue;
                    BarServiceEntity item = barServiceRepository.findForUpdate(in.uid(), branchUID)
                            .orElseThrow(() -> new BusinessException("A stock item " + service.getServiceName() + " is made from is missing"));
                    storeRows.put(item.getUid(), item);
                }
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
        // When the sale happened - the device's time for one made offline.
        java.time.LocalDateTime soldAt = com.midland.bar.Utils.Offline.OfflineContext.now();
        LocalDate today = soldAt.toLocalDate();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<BarSales> lines = new ArrayList<>();
        List<BarSaleStock> saleStock = new ArrayList<>();
        List<BarReports> reports = new ArrayList<>();
        List<Integer> sellerCuts = new ArrayList<>();
        List<IncomeExpenses> pots = new ArrayList<>();
        Map<String, StockAndPurchase> purchasePots = getWeekByServices(services, weekStart);
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
                // A sale made offline already happened - the drinks are gone and
                // paid for. It is taken even past zero; the negative shows at Stock-up.
                if (onHand < stockUnits && !com.midland.bar.Utils.Offline.OfflineContext.isReplay())
                    throw new BusinessException(storeRow == service
                            ? "Only " + onHand + " " + service.getServiceName() + " left in the store"
                            : "Only " + onHand + " units of " + storeRow.getServiceName() + " left - "
                              + quantity + " " + service.getServiceName() + " need " + stockUnits);
                storeRow.setStockQuantity(onHand - stockUnits);
                barServiceRepository.save(storeRow);
            }

            // A happy hour or offer running now prices it (the split follows what is charged).
            PriceRuleService.Priced priced = priceRuleService.price(service, quantity, soldAt, bill.getUid());
            int unitPrice = priced.unitPrice();
            int lineTotal = priced.lineTotal();
            // Cost of one sold unit, from whatever row the stock came out of:
            // a mshikaki costs its share of the beef.
            BarServiceEntity costRow = storeRow != null ? storeRow : service;
            int perPack = costRow.getUnitsPerPack() == null || costRow.getUnitsPerPack() < 1 ? 1 : costRow.getUnitsPerPack();
            int unitCost = costRow.getBuyingPrice() == null ? 0
                    : Math.round((float) costRow.getBuyingPrice() * perSale / perPack);

            BarSales line = new BarSales();
            // The other stock items it is made from: out of the store too, and
            // their cost on the line (a wali samaki costs the fish and the rice).
            if (service.getStockSourceUid() != null) {
                for (Ingredients.Ingredient in : Ingredients.parse(service.getExtraIngredients())) {
                    BarServiceEntity item = storeRows.get(in.uid());
                    int units = quantity * Math.max(1, in.units());
                    int onHand = item.getStockQuantity() == null ? 0 : item.getStockQuantity();
                    if (onHand < units && !com.midland.bar.Utils.Offline.OfflineContext.isReplay())
                        throw new BusinessException("Only " + onHand + " units of " + item.getServiceName() + " left - "
                                + quantity + " " + service.getServiceName() + " need " + units);
                    item.setStockQuantity(onHand - units);
                    barServiceRepository.save(item);
                    int itemPack = item.getUnitsPerPack() == null || item.getUnitsPerPack() < 1 ? 1 : item.getUnitsPerPack();
                    int oneCost = item.getBuyingPrice() == null ? 0 : Math.round((float) item.getBuyingPrice() * Math.max(1, in.units()) / itemPack);
                    unitCost += oneCost;
                    BarSaleStock taken = new BarSaleStock();
                    taken.setBarSalesUid(line.getUid());
                    taken.setStockItemUid(item.getUid());
                    taken.setPrimaryStockItemUid(storeRow == null ? null : storeRow.getUid());
                    taken.setUnits(units);
                    taken.setCost(oneCost * quantity);
                    taken.setSoldOn(today);
                    saleStock.add(taken);
                }
            }
            line.setSoldAt(soldAt);
            line.setCreatedAt(today);
            line.setAddOpId(com.midland.bar.Utils.Offline.OfflineContext.opId());
            line.setBarStaff(seller);
            line.setBarServiceEntity(service);
            line.setSalesOpened(bill);
            line.setQuantity(quantity);
            line.setUnitPrice(unitPrice);
            line.setLineTotal(lineTotal);
            line.setListPrice(priced.listPrice());
            line.setPriceRuleName(priced.ruleName());
            line.setFreeUnits(priced.freeUnits() > 0 ? priced.freeUnits() : null);
            line.setUnitCost(unitCost);
            line.setStockItemUid(storeRow == null ? null : storeRow.getUid());
            line.setStockUnits(storeRow == null ? null : stockUnits);
            line.setSoldBy(soldBy);
            lines.add(line);

            BarReports report = new BarReports();
            report.setCreatedAt(today);
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
            addOtherIncome(amount, commission.getOtherPercent(), branchUID, weekStart, pots, today);
            addIncomeExpense("LUKU", amount, commission.getLukuPercent(), branchUID, weekStart, pots);
            addIncomeExpense("Water", amount, commission.getWaterPercent(), branchUID, weekStart, pots);
            addIncomeExpense("Rent", amount, commission.getRentPercent(), branchUID, weekStart, pots);
            addIncomeExpense("Loan", amount, commission.getLoanPercent(), branchUID, weekStart, pots);
            addIncomeExpense("Stock Purchase", amount, commission.getStockPurchasePercent(), branchUID, weekStart, pots);

            int toPurchasePot = calculatePercentage(commission.getStockPurchasePercent(), lineTotal);
            StockAndPurchase pot = purchasePots.get(service.getUid());
            if (pot == null) {
                pot = new StockAndPurchase();
                pot.setWeekDate(weekStart);
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
        barSaleStockRepository.saveAll(saleStock);
        barReportsRepository.saveAll(reports);
        addStaffCommission(seller.getUid(), branchUID, today, sellerCuts);
        stockAndPurchaseRepository.saveAll(purchasePots.values());

        log.info(soldBy + " added " + lines.size() + " line(s) worth " + added + " to bill " + bill.getSalesCode());
        return new Response<>(bill);
    }

    /**
     * Takes some or all of one line off an unpaid bill - the Castle that should
     * have been a Serengeti. Everything the sale wrote is undone for what comes
     * off: the units go back to the store, the line's split report shrinks (or
     * goes), the week's pots and the Other split, the seller's commission for
     * that day, the week's stock-purchase pot, and the bill's total. The pots
     * and commission are those of the day and week the line was sold. A
     * BillLineVoid keeps who took what off, when and why.
     */
    @Transactional
    public Response<SalesOpened> removeBillLine(RemoveBillLineDTO dto) {
        moneyLock.hold(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain()); // before any row lock - see MoneyLock
        // Sent twice from the offline queue: applied once.
        Optional<String> done = offlineOps.alreadyApplied();
        if (done.isPresent())
            return salesOpenedRepository.findById(done.get())
                    .map(b -> new Response<>((SalesOpened) org.hibernate.Hibernate.unproxy(b)))
                    .orElseGet(() -> new Response<>("Open Sale Not Found"));
        workShiftService.requireOpen();
        return takeOffBillLine(dto, false);
    }

    /**
     * A station receiving a change (Badilisho): the returned item comes off
     * the bill the same way, without the station's own shift - receiving an
     * order never needed one.
     */
    @Transactional
    public Response<SalesOpened> takeOffForExchange(String barSalesUID, int quantity, String reason) {
        moneyLock.hold(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain()); // before any row lock - see MoneyLock
        RemoveBillLineDTO dto = new RemoveBillLineDTO();
        dto.setBarSalesUID(barSalesUID);
        dto.setQuantity(quantity);
        dto.setReason(reason);
        return takeOffBillLine(dto, false);
    }

    /**
     * An approved refund (RefundService): the same undoing on a paid bill -
     * stock back, the split, pots and commission taken off. The money going
     * back is the refund's business, not this.
     */
    @Transactional
    public Response<SalesOpened> takeOffForRefund(String barSalesUID, int quantity, String reason) {
        moneyLock.hold(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain()); // before any row lock - see MoneyLock
        RemoveBillLineDTO dto = new RemoveBillLineDTO();
        dto.setBarSalesUID(barSalesUID);
        dto.setQuantity(quantity);
        dto.setReason(reason);
        return takeOffBillLine(dto, true);
    }

    private Response<SalesOpened> takeOffBillLine(RemoveBillLineDTO dto, boolean refund) {
        moneyLock.hold(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain()); // before any row lock - see MoneyLock
        String branchUID = LoggerUser.getBranchUID();
        if (dto != null && dto.getBarSalesUID() == null && dto.getAddOpId() != null)
            dto.setBarSalesUID(barSalesRepository.findAddedBy(dto.getAddOpId(), dto.getBarServiceUID(), dto.getSalesOpenedUID()).orElse(null));
        if (dto == null || dto.getBarSalesUID() == null)
            return new Response<>("Choose the item to take off");
        String reason = dto.getReason() == null ? "" : dto.getReason().trim();
        if (reason.isEmpty())
            return new Response<>("Write why it is being taken off");

        BarSales line = barSalesRepository.findById(dto.getBarSalesUID())
                .filter(l -> Optional.ofNullable(branchUID).orElse("MAIN_OFFICE").equals(l.getBranchUid()))
                .orElse(null);
        if (line == null || line.getSalesOpened() == null)
            return new Response<>("Item Not Found");
        SalesOpened bill = salesOpenedRepository.findById(line.getSalesOpened().getUid()).orElse(null);
        if (bill == null)
            return new Response<>("Open Sale Not Found");
        if ("PAID".equals(bill.getPaymentStatus()) != refund)
            return new Response<>(refund ? "Bill " + bill.getSalesCode() + " is not paid - take the item off instead"
                    : "Bill " + bill.getSalesCode() + " is already paid - it cannot be changed");

        int lineQty = line.getQuantity() == null || line.getQuantity() < 1 ? 1 : line.getQuantity();
        int qty = dto.getQuantity() == null ? lineQty : dto.getQuantity();
        if (qty < 1 || qty > lineQty)
            return new Response<>("You can take off between 1 and " + lineQty);
        boolean whole = qty == lineQty;

        BarServiceEntity service = line.getBarServiceEntity();
        int unitPrice = line.getUnitPrice() != null ? line.getUnitPrice()
                : (line.getLineTotal() != null ? line.getLineTotal() / lineQty : 0);
        int amount = whole && line.getLineTotal() != null ? line.getLineTotal() : unitPrice * qty;

        // The units go back where they came from.
        if (line.getStockItemUid() != null && line.getStockUnits() != null) {
            int units = whole ? line.getStockUnits() : (line.getStockUnits() / lineQty) * qty;
            BarServiceEntity storeRow = barServiceRepository.findForUpdate(line.getStockItemUid(), branchUID).orElse(null);
            if (storeRow != null && units > 0) {
                storeRow.setStockQuantity((storeRow.getStockQuantity() == null ? 0 : storeRow.getStockQuantity()) + units);
                barServiceRepository.save(storeRow);
            }
        }

        // ...and the other stock items it was made from (the rice of a wali samaki).
        for (BarSaleStock taken : barSaleStockRepository.findByLine(line.getUid())) {
            int units = whole ? taken.getUnits() : (taken.getUnits() / lineQty) * qty;
            BarServiceEntity item = barServiceRepository.findForUpdate(taken.getStockItemUid(), branchUID).orElse(null);
            if (item != null && units > 0) {
                item.setStockQuantity((item.getStockQuantity() == null ? 0 : item.getStockQuantity()) + units);
                barServiceRepository.save(item);
            }
            if (whole) {
                barSaleStockRepository.delete(taken);
            } else {
                int cost = taken.getCost() == null ? 0 : taken.getCost();
                taken.setUnits(taken.getUnits() - units);
                taken.setCost(cost - (cost / lineQty) * qty);
                barSaleStockRepository.save(taken);
            }
        }

        java.time.LocalDateTime soldAt = line.getSoldAt() != null ? line.getSoldAt()
                : (line.getCreatedAt() != null ? line.getCreatedAt().atStartOfDay() : java.time.LocalDateTime.now());
        List<BarReports> reports = barReportsRepository.findBySalesLine(line.getUid());
        unsplit(line, service, amount, whole, reports, branchUID);

        BillLineVoid record = new BillLineVoid();
        record.setBillUid(bill.getUid());
        record.setSalesCode(bill.getSalesCode());
        record.setServiceName(service == null ? null : service.getServiceName());
        record.setQuantity(qty);
        record.setUnitPrice(unitPrice);
        record.setAmount(amount);
        BarStaff seller = line.getBarStaff();
        record.setStaffName(seller == null ? null
                : ((seller.getFirstName() == null ? "" : seller.getFirstName()) + " " + (seller.getLastName() == null ? "" : seller.getLastName())).trim());
        record.setSoldAt(soldAt);
        record.setReason(reason.length() > 300 ? reason.substring(0, 300) : reason);
        record.setVoidedBy(LoggerUser.getEmail());
        record.setVoidedByName(CashUpService.nameOf(LoggerUser.getUser()));
        record.setVoidedAt(com.midland.bar.Utils.Offline.OfflineContext.now());
        billLineVoidRepository.save(record);
        // Many items taken off in one shift: the owner hears of it.
        if (!refund) {
            workShiftRepository.findUnfinished(branchUID, LoggerUser.getEmail()).stream().findFirst().ifPresent(shift ->
                    ownerAlertService.voidsReached(branchUID, record.getVoidedByName() != null ? record.getVoidedByName() : record.getVoidedBy(),
                            (int) billLineVoidRepository.countBySince(branchUID, LoggerUser.getEmail(), shift.getOpenedAt())));
        }

        if (whole) {
            barReportsRepository.deleteAll(reports);
            barSalesRepository.delete(line);
        } else {
            barReportsRepository.saveAll(reports);
            line.setQuantity(lineQty - qty);
            line.setLineTotal((line.getLineTotal() == null ? unitPrice * lineQty : line.getLineTotal()) - amount);
            if (line.getStockUnits() != null)
                line.setStockUnits(line.getStockUnits() - (line.getStockUnits() / lineQty) * qty);
            barSalesRepository.save(line);
        }

        bill.setBill(Math.max(0, (bill.getBill() == null ? 0 : bill.getBill()) - amount));
        bill.update();
        salesOpenedRepository.save(bill);
        log.info(LoggerUser.getEmail() + " took " + qty + " x " + record.getServiceName() + " off bill " + bill.getSalesCode() + ": " + reason);
        offlineOps.claim("REMOVE_LINE", bill.getUid());
        // The bill was reached through the line, so it is a lazy proxy - Jackson can't write one.
        return new Response<>((SalesOpened) org.hibernate.Hibernate.unproxy(bill));
    }

    /**
     * A discount on an open bill (owner, 2026-10-18): spread over its lines by
     * their share, each line's split taken down by its part (no stock moves),
     * and recorded with the reason. A cashier may give up to the branch's
     * cashier limit (POS Setting > Tawi) of the bill in total; more needs
     * DISCOUNT_ANY (CEO, MANAGER).
     */
    @Transactional
    public Response<SalesOpened> discountBill(String billUid, Integer amount, Integer percent, String reason) {
        moneyLock.hold(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain()); // before any row lock - see MoneyLock
        if (com.midland.bar.Config.Security.StaffSession.active())
            throw new BusinessException("Only the cashier can give a discount");
        workShiftService.requireOpen();
        String branchUID = LoggerUser.getBranchUIDOrMain();
        SalesOpened bill = salesOpenedRepository.findForUpdate(billUid, branchUID).orElse(null);
        if (bill == null || !"PENDING".equals(bill.getPaymentStatus()))
            return new Response<>("That bill is not open any more");
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 3)
            return new Response<>("Write why the discount is given");
        int total = (int) barSalesRepository.billTotal(bill.getUid());
        if (total <= 0)
            return new Response<>("Bill " + bill.getSalesCode() + " has nothing on it");
        int amt = percent != null && percent > 0 ? Math.round(total * Math.min(100, percent) / 100f) : (amount == null ? 0 : amount);
        if (amt < 1 || amt > total)
            return new Response<>("The discount is between 1 and " + total);
        long given = billDiscountRepository.totalOnBill(bill.getUid());
        int gross = total + (int) given;
        int limit = systemSettingRepository.findByBranchUid(branchUID)
                .map(com.midland.bar.Setting.Model.SystemSetting::cashierDiscountPercentOrDefault).orElse(10);
        boolean any = Boolean.TRUE.equals(authChecker.hasPermissionOrRoot("DISCOUNT_ANY"));
        if (!any && given + amt > Math.floor(gross * limit / 100.0))
            return new Response<>("A cashier may discount up to " + limit + "% of the bill (" + (int) Math.floor(gross * limit / 100.0)
                    + ") - a manager or CEO gives more");

        int left = amt - spreadOff(bill, amt, total, branchUID, false);
        bill.setBill(Math.max(0, (bill.getBill() == null ? total : bill.getBill()) - amt));
        bill.update();
        salesOpenedRepository.save(bill);

        com.midland.bar.Bar.Model.BillDiscount d = new com.midland.bar.Bar.Model.BillDiscount();
        d.setBillUid(bill.getUid());
        d.setSalesCode(bill.getSalesCode());
        d.setBillTotal(total);
        d.setAmount(amt - left);
        d.setReason(why.length() > 300 ? why.substring(0, 300) : why);
        d.setGivenBy(LoggerUser.getEmail());
        d.setGivenByName(CashUpService.nameOf(LoggerUser.getUser()));
        d.setGivenAt(java.time.LocalDateTime.now());
        billDiscountRepository.save(d);
        log.info(LoggerUser.getEmail() + " gave " + d.getAmount() + " off bill " + bill.getSalesCode() + ": " + why);
        return new Response<>(bill);
    }

    /**
     * Takes [amt] off a bill's lines by their share of [total]: each line's
     * total and split come down by its part (no stock moves) and it records
     * the discount on the line. Returns what was taken off. A discount and a
     * bill given as an offer (ofa) both go through here.
     */
    public int spreadOff(SalesOpened bill, int amt, int total, String branchUID, boolean offer) {
        moneyLock.hold(branchUID); // before any row lock - see MoneyLock
        List<BarSales> lines = barSalesRepository.findLinesOfBill(bill.getUid());
        int left = amt;
        for (int i = 0; i < lines.size(); i++) {
            BarSales line = lines.get(i);
            int lt = line.getLineTotal() == null ? 0 : line.getLineTotal();
            int share = i == lines.size() - 1 ? left : (int) Math.floor((double) amt * lt / total);
            share = Math.min(share, lt);
            if (share <= 0)
                continue;
            left -= share;
            List<BarReports> reports = barReportsRepository.findBySalesLine(line.getUid());
            unsplit(line, line.getBarServiceEntity(), share, false, reports, branchUID);
            barReportsRepository.saveAll(reports);
            line.setLineTotal(lt - share);
            if (offer)
                line.setOfferAmount((line.getOfferAmount() == null ? 0 : line.getOfferAmount()) + share);
            else
                line.setDiscountAmount((line.getDiscountAmount() == null ? 0 : line.getDiscountAmount()) + share);
            barSalesRepository.save(line);
        }
        return amt - left;
    }

    public com.midland.bar.Utils.Responses.ResponseList<com.midland.bar.Bar.Model.BillDiscount> discounts(String filter) {
        java.time.LocalDateTime[] range = ReportRange.of(filter);
        return new com.midland.bar.Utils.Responses.ResponseList<>(billDiscountRepository.between(LoggerUser.getBranchUIDOrMain(), range[0], range[1]));
    }

    /**
     * Takes [amount] of a sold line back out of everything the sale wrote: its
     * split report (all of it when [whole]), the week's pots and the Other
     * split, the week's stock-purchase pot and the seller's commission - on
     * the day and week it was sold. Used by take-off, refund and discount.
     */
    private void unsplit(BarSales line, BarServiceEntity service, int amount, boolean whole, List<BarReports> reports, String branchUID) {
        java.time.LocalDateTime soldAt = line.getSoldAt() != null ? line.getSoldAt()
                : (line.getCreatedAt() != null ? line.getCreatedAt().atStartOfDay() : java.time.LocalDateTime.now());
        LocalDate soldDay = soldAt.toLocalDate();
        LocalDate soldWeek = soldDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Commission commission = service == null ? null : loadCommissionsByService(List.of(service)).get(service.getUid());

        int sellerCut = 0;
        for (BarReports report : reports) {
            if (whole) {
                sellerCut += report.getStaffAmount() == null ? 0 : report.getStaffAmount();
            } else if (commission != null) {
                int cut = calculatePercentage(commission.getStaffPercent(), amount);
                sellerCut += cut;
                report.setStaffAmount(minus(report.getStaffAmount(), cut));
                report.setOwnerAmount(minus(report.getOwnerAmount(), calculatePercentage(commission.getOwnerPercent(), amount)));
                report.setTraAmount(minus(report.getTraAmount(), calculatePercentage(commission.getTraPercent(), amount)));
                report.setMaintenanceAmount(minus(report.getMaintenanceAmount(), calculatePercentage(commission.getMaintenancePercent(), amount)));
                report.setEmergencyAmount(minus(report.getEmergencyAmount(), calculatePercentage(commission.getEmergencyPercent(), amount)));
                report.setOthersAmount(minus(report.getOthersAmount(), calculatePercentage(commission.getOtherPercent(), amount)));
                report.setRentAmount(minus(report.getRentAmount(), calculatePercentage(commission.getRentPercent(), amount)));
                report.setLoanAmount(minus(report.getLoanAmount(), calculatePercentage(commission.getLoanPercent(), amount)));
                report.setLukuAmount(minus(report.getLukuAmount(), calculatePercentage(commission.getLukuPercent(), amount)));
                report.setWaterAmount(minus(report.getWaterAmount(), calculatePercentage(commission.getWaterPercent(), amount)));
                report.setStockPurchaseAmount(minus(report.getStockPurchaseAmount(), calculatePercentage(commission.getStockPurchasePercent(), amount)));
            }
        }

        if (commission != null) {
            BigDecimal back = BigDecimal.valueOf(-amount);
            List<IncomeExpenses> pots = new ArrayList<>();
            addIncomeExpense("Staff", back, commission.getStaffPercent(), branchUID, soldWeek, pots);
            addIncomeExpense("Owner", back, commission.getOwnerPercent(), branchUID, soldWeek, pots);
            addIncomeExpense("TRA", back, commission.getTraPercent(), branchUID, soldWeek, pots);
            addIncomeExpense("Emergency", back, commission.getEmergencyPercent(), branchUID, soldWeek, pots);
            addIncomeExpense("Maintenance", back, commission.getMaintenancePercent(), branchUID, soldWeek, pots);
            addOtherIncome(back, commission.getOtherPercent(), branchUID, soldWeek, pots, soldDay);
            addIncomeExpense("LUKU", back, commission.getLukuPercent(), branchUID, soldWeek, pots);
            addIncomeExpense("Water", back, commission.getWaterPercent(), branchUID, soldWeek, pots);
            addIncomeExpense("Rent", back, commission.getRentPercent(), branchUID, soldWeek, pots);
            addIncomeExpense("Loan", back, commission.getLoanPercent(), branchUID, soldWeek, pots);
            addIncomeExpense("Stock Purchase", back, commission.getStockPurchasePercent(), branchUID, soldWeek, pots);

            int fromPurchasePot = calculatePercentage(commission.getStockPurchasePercent(), amount);
            for (StockAndPurchase pot : stockAndPurchaseRepository.findCurrentWeekByServices(branchUID, List.of(service.getUid()), soldWeek)) {
                pot.setTotalAmount(minus(pot.getTotalAmount(), fromPurchasePot));
                pot.setRemainingAmount(minus(pot.getRemainingAmount(), fromPurchasePot));
                stockAndPurchaseRepository.save(pot);
                break;
            }
            if (!whole && sellerCut == 0)
                sellerCut = calculatePercentage(commission.getStaffPercent(), amount);
        }
        if (line.getBarStaff() != null && sellerCut != 0)
            addStaffCommission(line.getBarStaff().getUid(), branchUID, soldDay, List.of(-sellerCut));

    }

    private static Integer minus(Integer value, int by) {
        return (value == null ? 0 : value) - by;
    }

    /**
     * A staff member hands in less than their bills came to. The shortage comes
     * off today's commission for them (lossAmount, and remainingAmount - which
     * can go below zero, meaning they owe) and, through the StaffLoss row, off
     * the expected cash of whoever took the handover.
     */
    @Transactional
    public Response<StaffLoss> recordStaffLoss(StaffLossDTO dto) {
        Optional<String> done = offlineOps.alreadyApplied();
        if (done.isPresent())
            return staffLossRepository.findById(done.get()).map(Response::new).orElseGet(() -> new Response<>("Shortage not found"));
        workShiftService.requireOpen();
        String branchUID = LoggerUser.getBranchUID();
        if (dto == null || dto.getStaffCode() == null || dto.getStaffCode().isBlank())
            return new Response<>("Choose the staff member");
        int expected = dto.getExpectedAmount() == null ? 0 : dto.getExpectedAmount();
        int handed = dto.getHandedAmount() == null ? 0 : dto.getHandedAmount();
        if (expected < 0 || handed < 0)
            return new Response<>("Amounts cannot be below zero");
        int loss = expected - handed;
        if (loss <= 0)
            return new Response<>("Nothing is short - what was handed in covers the bills");
        BarStaff staff = barStaffRepository.findByStaffCode(dto.getStaffCode().trim(), branchUID)
                .orElse(null);
        if (staff == null)
            return new Response<>("Staff Not Found");

        // The day it happened - the device's, for one recorded offline.
        StaffCommissions commission = addStaffCommission(staff.getUid(), branchUID, com.midland.bar.Utils.Offline.OfflineContext.today(), List.of(0));
        commission.setLossAmount((commission.getLossAmount() == null ? 0 : commission.getLossAmount()) + loss);
        commission.setRemainingAmount((commission.getRemainingAmount() == null ? 0 : commission.getRemainingAmount()) - loss);
        commission.update();
        commission = staffCommissionsRepository.save(commission);

        StaffLoss record = new StaffLoss();
        record.setStaffUid(staff.getUid());
        record.setStaffCode(staff.getStaffCode());
        record.setStaffName(((staff.getFirstName() == null ? "" : staff.getFirstName()) + " "
                + (staff.getLastName() == null ? "" : staff.getLastName())).trim());
        record.setExpectedAmount(expected);
        record.setHandedAmount(handed);
        record.setAmount(loss);
        String note = dto.getNote() == null ? null : dto.getNote().trim();
        record.setNote(note == null || note.isEmpty() ? null : (note.length() > 300 ? note.substring(0, 300) : note));
        String method = dto.getMethod() == null || dto.getMethod().isBlank() ? "cash" : dto.getMethod().trim().toLowerCase();
        record.setMethod(BillPaymentService.METHODS.contains(method) ? method : "cash");
        record.setCommissionUid(commission.getUid());
        record.setRecordedBy(LoggerUser.getEmail());
        record.setRecordedByName(CashUpService.nameOf(LoggerUser.getUser()));
        record.setRecordedAt(com.midland.bar.Utils.Offline.OfflineContext.now());
        log.info(LoggerUser.getEmail() + " recorded a " + loss + " shortage for " + record.getStaffName());
        StaffLoss saved = staffLossRepository.save(record);
        offlineOps.claim("STAFF_LOSS", saved.getUid());
        return new Response<>(saved);
    }

    /** What was taken off bills in a Reports period: everyone's for CEO/manager, a cashier's own otherwise. */
    public ResponseList<BillLineVoid> findBillLineVoids(String filter) {
        LocalDateTime[] range = ReportRange.of(filter);
        String email = CashUpService.seesAll() ? null : LoggerUser.getEmail();
        return new ResponseList<>(billLineVoidRepository.findIn(LoggerUser.getBranchUID(), range[0], range[1], email));
    }

    /**
     * The staff row the logged-in user sells as. Made from their own account
     * the first time they sell, so every seller has somewhere for their
     * commission to land without anyone setting it up by hand.
     */
    /** The staff row the signed-in user sells as (made on first use) - for their own Staff Sell bills. */
    public BarStaff myStaff() {
        return sellerStaff();
    }

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
        // A staff code session sees the lines of its own bills only.
        if (com.midland.bar.Config.Security.StaffSession.active())
            com.midland.bar.Config.Security.StaffSession.requireOwnBill(salesOpenedRepository.findById(barOpenUID).orElse(null));
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
    /**
     * Deleting a sale line is taking it off its bill, whole: the same undoing
     * as removeBillLine (stock back, split, pots, commission, a void record),
     * never a bare delete that left the stock short. A paid bill's line goes
     * back through a refund instead.
     */
    @Transactional
    public Response<BarSales> deleteBarSales(String barSalesUID){
        moneyLock.hold(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain()); // before any row lock - see MoneyLock
        Optional<BarSales> line = barSalesRepository.findSalesByUID(barSalesUID, LoggerUser.getBranchUID());
        if (line.isEmpty())
            return new Response<>("Sales Not Found");
        workShiftService.requireOpen();
        RemoveBillLineDTO dto = new RemoveBillLineDTO();
        dto.setBarSalesUID(barSalesUID);
        dto.setReason("Deleted");
        Response<SalesOpened> done = takeOffBillLine(dto, false);
        if (done.getData() == null)
            return new Response<>(done.getMessage());
        log.info(LoggerUser.getEmail() + " deleted sale line " + barSalesUID);
        return new Response<>((BarSales) org.hibernate.Hibernate.unproxy(line.get()));
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
        com.midland.bar.Config.Security.StaffSession.requireOwnBill(bill);
        if (barSalesRepository.countLines(billUid) > 0 || (bill.getBill() != null && bill.getBill() > 0))
            return new Response<>("Only an empty bill can be deleted - bill " + bill.getSalesCode() + " has items on it");
        if (staffOrderRepository.countUndecided(billUid) > 0)
            return new Response<>("A waiter has an order waiting to go on bill " + bill.getSalesCode());
        bill.setPaymentStatus("CANCELLED");
        bill.delete();
        return new Response<>(salesOpenedRepository.save(bill));
    }

    public Response<SalesOpened> saveOpenSale(SaleOpenedDTO saleOpenedDTO) {
        workShiftService.requireOpen();
        log.info(LoggerUser.getEmail() + " is Opening Sale");
        if (saleOpenedDTO == null) {
            return new Response<>("Provide Data For Opening new sale");
        }
        SalesOpened salesOpened;
        boolean wasAlreadyPaid = false;
        // A bill opened offline arrives with the uid the device gave it (its
        // queued items and payment point at that uid). Sent twice, it is the same bill.
        if (saleOpenedDTO.getUid() == null && saleOpenedDTO.getClientUid() != null) {
            Optional<SalesOpened> existing = salesOpenedRepository.findById(saleOpenedDTO.getClientUid());
            if (existing.isPresent())
                return new Response<>((SalesOpened) org.hibernate.Hibernate.unproxy(existing.get()));
        }
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
            if (!billCodeService.isAvailable(saleOpenedDTO.getSalesCode())) {
                if (!com.midland.bar.Utils.Offline.OfflineContext.isReplay())
                    return new Response<>("Code " + saleOpenedDTO.getSalesCode() + " is not available - choose a free code");
                // Opened offline on a code another till took meanwhile: the bill
                // is real (it has drinks on it), so it takes a free code instead.
                List<String> free = billCodeService.findAvailable().getData();
                String uidPart = saleOpenedDTO.getClientUid() == null ? "X" : saleOpenedDTO.getClientUid().substring(0, 4).toUpperCase();
                saleOpenedDTO.setSalesCode(free != null && !free.isEmpty() ? free.get(0) : saleOpenedDTO.getSalesCode() + "-" + uidPart);
            }
            salesOpened = new SalesOpened();
            if (saleOpenedDTO.getClientUid() != null)
                salesOpened.setUid(saleOpenedDTO.getClientUid());
            // Whose bill this is when no staff member is on it: the login at the till.
            String opener = LoggerUser.getEmail();
            String openerName = opener == null ? null : userRepository.findFullNameByLogin(opener);
            salesOpened.setOpenedBy(opener);
            salesOpened.setOpenedByName(openerName == null || openerName.isBlank() ? opener : openerName);
            salesOpened.setCreatedAt(com.midland.bar.Utils.Offline.OfflineContext.today());
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
            addOtherIncome(amount, commission.getOtherPercent(), branchUID, weekStartDate, result, today);

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


    /**
     * The Other bucket. With the branch's own list set (POS Setting > Other),
     * its amount is shared across those items, each into a pot of its own;
     * without one it stays the single "Other" pot it always was.
     */
    private void addOtherIncome(BigDecimal amount, Integer percent, String branchUID, LocalDate weekStartDate, List<IncomeExpenses> result, LocalDate day) {
        if (percent == null || percent <= 0) {
            return;
        }
        BigDecimal other = amount.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100));
        java.util.Map<String, BigDecimal> parts = otherCommissionService.split(other, branchUID);
        if (parts.isEmpty()) {
            addIncomeExpense("Other", amount, percent, branchUID, weekStartDate, result);
            return;
        }
        // Each share is already in shillings - taken at 100% it lands as it is.
        parts.forEach((pot, share) -> addIncomeExpense(pot, share, 100, branchUID, weekStartDate, result));
        otherCommissionService.record(parts, day);
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
        Map<String, IncomeExpenses> pots = weekPots(branchUID, weekStartDate);
        Optional<IncomeExpenses> existing = Optional.ofNullable(pots.get(name));

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
            pots.put(name, saved);

            /*
             * Add kwenye response
             */
            result.add(saved);
        }
    }

    /**
     * The week's pots by name, read once per transaction: a sale splits every
     * line into 11+ pots, and looking each one up was 11+ queries a line. The
     * map holds the managed rows, so later lines of the same sale add to them,
     * and a pot created here is put in it (see addIncomeExpense).
     */
    private Map<String, IncomeExpenses> weekPots(String branchUID, LocalDate week) {
        moneyLock.hold(branchUID); // read the pots only under the branch's money lock
        return com.midland.bar.Utils.TxMemo.get("weekPots:" + branchUID + ":" + week, () -> {
            Map<String, IncomeExpenses> pots = new HashMap<>();
            for (IncomeExpenses pot : incomeExpensesRepository.findWeekPots(branchUID, week))
                pots.putIfAbsent(pot.getName(), pot);
            return pots;
        });
    }
    public ResponseList<IncomeExpenses> getIncomeExpenses(String filter) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        // Pots are kept per week; a day is worked out from that day's sales.
        if ("TODAY".equalsIgnoreCase(filter) || "DAY".equalsIgnoreCase(filter))
            return incomeExpensesOfDay(today);
        if ("YESTERDAY".equalsIgnoreCase(filter))
            return incomeExpensesOfDay(today.minusDays(1));
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

        return incomeExpensesByWhenPaid(incomeExpensesRepository.findByBranchAndWeekRange(
                LoggerUser.getBranchUID(),
                startDate,
                endDate));
    }

    /**
     * The week pots, each showing what was paid out in its week (owner,
     * 2026-10-19). A payout comes from the pot whose money it used - Stock
     * Purchase from the oldest week's savings first - so a delivery received
     * today may be paid from weeks ago; here it shows in the week it
     * happened. Copies, so the pots themselves are not changed.
     */
    private ResponseList<IncomeExpenses> incomeExpensesByWhenPaid(List<IncomeExpenses> pots) {
        if (pots.isEmpty())
            return new ResponseList<>(pots);
        LocalDate from = pots.stream().map(IncomeExpenses::getWeekStartDate).min(LocalDate::compareTo).get();
        LocalDate to = pots.stream().map(IncomeExpenses::getWeekStartDate).max(LocalDate::compareTo).get().plusDays(7);
        Map<String, IncomeExpenses> rows = new LinkedHashMap<>();
        List<IncomeExpenses> out = new ArrayList<>();
        for (IncomeExpenses pot : pots) {
            IncomeExpenses row = new IncomeExpenses();
            row.setUid(pot.getUid());
            row.setName(pot.getName());
            row.setWeekStartDate(pot.getWeekStartDate());
            row.setPaymentMethod(pot.getPaymentMethod());
            row.setIncome(pot.getIncome());
            row.setSpends(new ArrayList<>());
            row.setExpenses(java.math.BigDecimal.ZERO);
            rows.put(pot.getName() + "|" + pot.getWeekStartDate(), row);
            out.add(row);
        }
        for (IncomeExpensesDescription d : incomeExpensesDescriptionRepository.paidBetween(LoggerUser.getBranchUID(),
                from.atStartOfDay(), to.atStartOfDay(), from, to)) {
            LocalDate on = d.getPaidAt() != null ? d.getPaidAt().toLocalDate() : d.getDescriptionDate();
            LocalDate week = on.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            IncomeExpenses row = rows.get(d.getIncomeExpenses().getName() + "|" + week);
            if (row == null)
                continue;
            row.getSpends().add(d);
            row.setExpenses(row.getExpenses().add(d.getSpendAmount() == null ? java.math.BigDecimal.ZERO : d.getSpendAmount()));
        }
        return new ResponseList<>(out);
    }
    /**
     * Mapato na Matumizi for one day (owner, 2026-10-19). The pots are weekly,
     * so the day is built from what happened on it: each pot's income from
     * that day's sale splits (bar_reports, already net of refunds, discounts
     * and offers; Other per item from its entries), its expenses from what
     * was paid out of it that day. Each row keeps its week pot's uid, so Pay
     * and Details still act on the real pot. Nothing here is saved.
     */
    private ResponseList<IncomeExpenses> incomeExpensesOfDay(LocalDate day) {
        String branchUID = LoggerUser.getBranchUID();
        LocalDate week = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<String, java.math.BigDecimal> income = new LinkedHashMap<>();
        for (Object[] r : barReportsRepository.splitOfDay(branchUID, day)) {
            String[] names = {"Staff", "Owner", "TRA", "Emergency", "Maintenance", "Other", "LUKU", "Water", "Rent", "Loan", "Stock Purchase"};
            for (int i = 0; i < names.length; i++)
                income.put(names[i], java.math.BigDecimal.valueOf(r[i] == null ? 0 : ((Number) r[i]).longValue()));
        }
        java.math.BigDecimal otherItems = java.math.BigDecimal.ZERO;
        for (Object[] e : otherCommissionEntryRepository.byItemOn(branchUID, day)) {
            java.math.BigDecimal amount = e[1] == null ? java.math.BigDecimal.ZERO : (java.math.BigDecimal) e[1];
            income.merge(OtherCommissionService.POT_PREFIX + e[0], amount, java.math.BigDecimal::add);
            otherItems = otherItems.add(amount);
        }
        List<IncomeExpensesDescription> paidThatDay = incomeExpensesDescriptionRepository.paidBetween(branchUID,
                day.atStartOfDay(), day.plusDays(1).atStartOfDay(), day, day.plusDays(1));
        List<IncomeExpenses> out = new ArrayList<>();
        for (IncomeExpenses pot : incomeExpensesRepository.findByBranchAndWeekRange(branchUID, week, week)) {
            IncomeExpenses row = new IncomeExpenses();
            row.setUid(pot.getUid());
            row.setName(pot.getName());
            row.setWeekStartDate(pot.getWeekStartDate());
            row.setPaymentMethod(pot.getPaymentMethod());
            java.math.BigDecimal in = income.getOrDefault(pot.getName(), java.math.BigDecimal.ZERO);
            if ("Other".equals(pot.getName()))
                in = in.subtract(otherItems);
            row.setIncome(in);
            // Whatever was paid out that day, from this pot or an older week's of the same name.
            List<IncomeExpensesDescription> spends = new ArrayList<>();
            java.math.BigDecimal spent = java.math.BigDecimal.ZERO;
            for (IncomeExpensesDescription d : paidThatDay) {
                if (pot.getName().equals(d.getIncomeExpenses().getName())) {
                    spends.add(d);
                    spent = spent.add(d.getSpendAmount() == null ? java.math.BigDecimal.ZERO : d.getSpendAmount());
                }
            }
            row.setSpends(spends);
            row.setExpenses(spent);
            out.add(row);
        }
        return new ResponseList<>(out);
    }

    @Transactional
    public Response<IncomeExpenses> addSpend(SpendDTO spendDTO) {
        moneyLock.hold(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain()); // before any row lock - see MoneyLock
        workShiftService.requireOpenForPayout();

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

        if (spendDTO.getMethod() != null && !spendDTO.getMethod().isBlank() && !PAYOUT_METHODS.contains(spendDTO.getMethod().trim().toLowerCase())) {
            return new Response<>("Choose how it was paid");
        }


        // ==============================
        // FIND INCOME EXPENSE
        // ==============================

        // Only a pot of the user's own branch - a uid from another branch is not found.
        Optional<IncomeExpenses> optionalIncomeExpenses =
                incomeExpensesRepository.findById(spendDTO.getUid())
                        .filter(pot -> java.util.Objects.equals(pot.getBranchUid(), LoggerUser.getBranchUID()));

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
        spend.setPaidBy(LoggerUser.getEmail());
        spend.setPaidAt(java.time.LocalDateTime.now());
        spend.setMethod(payoutMethod(spendDTO.getMethod()));

        spend.setDescription(
                spendDTO.getDescription().trim()
        );

        spend.setDescriptionDate(
                LocalDate.now()
        );

        spend.setSpendAmount(
                spendAmount
        );

        // Who recorded the payment, for the spending history.
        com.midland.bar.Uaa.Model.User payer = LoggerUser.getUser();
        if (payer != null) {
            String fullName = ((payer.getFirstName() == null ? "" : payer.getFirstName()) + " "
                    + (payer.getLastName() == null ? "" : payer.getLastName())).trim();
            spend.setStaffName(fullName.isEmpty() ? payer.getUsername() : fullName);
        }

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
    /** The days a Staff report filter covers: a date (d-M-yyyy) or TODAY, THIS_WEEK, LAST_MONTH... */
    private LocalDate[] staffCommissionRange(String range) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
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
        return new LocalDate[]{startDate, endDate};
    }

    public ResponsePage<BarProjection> findStaffCommissionPage(String range, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDate[] days = staffCommissionRange(range);
        LocalDate startDate = days[0];
        LocalDate endDate = days[1];
        return new ResponsePage<>(staffCommissionsRepository.findStaffCommissionPage(
                pageable,
                LoggerUser.getBranchUID(),
                startDate,
                endDate
        ));
    }
    /** Staff report over a week, month or year: one row per staff member, the days added together. */
    public ResponseList<com.midland.bar.Bar.Projection.StaffCommissionTotalProjection> findStaffCommissionTotals(String range) {
        LocalDate[] days = staffCommissionRange(range);
        return new ResponseList<>(staffCommissionsRepository.findStaffCommissionTotals(LoggerUser.getBranchUID(), days[0], days[1]));
    }

    /**
     * Pays a staff member's total over the filter's range: the days still
     * owed are paid oldest first, each through payStaffCommission so the
     * pots move exactly as a day's payment does. All or none.
     */
    @Transactional
    public Response<Integer> payStaffCommissionTotal(StaffCommissionDTO dto) {
        if (dto == null || dto.getStaffUid() == null)
            return new Response<>("Chagua mhudumu");
        if (dto.getAmount() == null || dto.getAmount() <= 0)
            return new Response<>("Weka Kiasi kinacholipwa");
        LocalDate[] days = staffCommissionRange(dto.getFilter());
        String branchUid = LoggerUser.getBranchUID();
        // Net of the range: a day they owe on comes off what the other days are owed.
        long net = staffCommissionsRepository.findStaffCommissionTotals(branchUid, days[0], days[1]).stream()
                .filter(t -> dto.getStaffUid().equals(t.getStaffUid()))
                .mapToLong(t -> t.getRemainingAmount() == null ? 0 : t.getRemainingAmount())
                .sum();
        if (dto.getAmount() > net)
            return new Response<>(net <= 0 ? "Nothing to pay - after the shortage this staff member is owed nothing"
                    : "You can pay at most " + net);
        int left = dto.getAmount();
        for (StaffCommissions day : staffCommissionsRepository.findOwedDays(dto.getStaffUid(), branchUid, days[0], days[1])) {
            if (left <= 0)
                break;
            int part = Math.min(left, day.getRemainingAmount());
            StaffCommissionDTO pay = new StaffCommissionDTO();
            pay.setUid(day.getUid());
            pay.setAmount(part);
            pay.setFilter(dto.getFilter());
            pay.setWeekDate(day.getWeekDate());
            pay.setDescriptions(dto.getDescriptions());
            pay.setMethod(dto.getMethod());
            Response<StaffCommissions> paid = payStaffCommission(pay);
            if (paid.getData() == null)
                throw new BusinessException(paid.getMessage());
            left -= part;
        }
        if (left > 0)
            throw new BusinessException("You can pay at most " + (dto.getAmount() - left));
        return new Response<>(dto.getAmount());
    }

    // One transaction: the commission row is saved before the pots are found,
    // so a failure there used to leave it marked paid with no money moved.
    @Transactional
    public Response<StaffCommissions> payStaffCommission(StaffCommissionDTO staffCommissionDTO){
        moneyLock.hold(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain()); // before any row lock - see MoneyLock
        workShiftService.requireOpenForPayout();
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
        // What they are owed is after any handover shortage - never pay past it.
        int owed = staffCommissions.getRemainingAmount() == null ? 0 : staffCommissions.getRemainingAmount();
        if (staffCommissionDTO.getAmount() <= 0 || staffCommissionDTO.getAmount() > owed)
            return new Response<>(owed <= 0 ? "Nothing to pay - after the shortage this staff member is owed nothing"
                    : "You can pay at most " + owed);
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
            incomeExpensesDescription.setPaidBy(LoggerUser.getEmail());
            incomeExpensesDescription.setPaidAt(java.time.LocalDateTime.now());
            incomeExpensesDescription.setMethod(payoutMethod(staffCommissionDTO.getMethod()));
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
        return getWeekByServices(services, LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    /** The stock-purchase pots of the week starting weekDate, by service. */
    private Map<String, StockAndPurchase> getWeekByServices(List<BarServiceEntity> services, LocalDate weekDate) {
        List<String> serviceUids = services.stream()
                .filter(Objects::nonNull)
                .map(BarServiceEntity::getUid)
                .toList();
        if (serviceUids.isEmpty()) {
            return Map.of();
        }
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

        return new ResponseList<>(withIngredientShares(stockAndPurchases));
    }

    /**
     * A recipe's pot (wali samaki = fish + rice), split by what each of its
     * stock items costs today, so each one's share of the buying money shows.
     */
    private List<StockAndPurchaseProjection> withIngredientShares(List<StockAndPurchaseProjection> rows) {
        Set<String> serviceUids = new HashSet<>();
        rows.forEach(r -> { if (r.getServiceUid() != null) serviceUids.add(r.getServiceUid()); });
        if (serviceUids.isEmpty())
            return rows;
        Map<String, BarServiceEntity> recipes = new HashMap<>();
        Set<String> itemUids = new HashSet<>();
        for (BarServiceEntity sv : barServiceRepository.findAllById(serviceUids)) {
            List<Ingredients.Ingredient> extras = Ingredients.parse(sv.getExtraIngredients());
            if (sv.getStockSourceUid() == null || extras.isEmpty())
                continue;
            recipes.put(sv.getUid(), sv);
            itemUids.add(sv.getStockSourceUid());
            extras.forEach(in -> itemUids.add(in.uid()));
        }
        if (recipes.isEmpty())
            return rows;
        Map<String, BarServiceEntity> items = new HashMap<>();
        barServiceRepository.findAllById(itemUids).forEach(i -> items.put(i.getUid(), i));

        List<StockAndPurchaseProjection> out = new ArrayList<>();
        for (StockAndPurchaseProjection r : rows) {
            BarServiceEntity sv = recipes.get(r.getServiceUid());
            if (sv == null) {
                out.add(r);
                continue;
            }
            // Each stock item's cost in one sale.
            List<String> uids = new ArrayList<>();
            List<Integer> costs = new ArrayList<>();
            uids.add(sv.getStockSourceUid());
            costs.add(costOf(items.get(sv.getStockSourceUid()), sv.getUnitsPerSale()));
            for (Ingredients.Ingredient in : Ingredients.parse(sv.getExtraIngredients())) {
                uids.add(in.uid());
                costs.add(costOf(items.get(in.uid()), in.units()));
            }
            int whole = costs.stream().mapToInt(Integer::intValue).sum();
            int total = r.getTotalAmount() == null ? 0 : r.getTotalAmount();
            int remaining = r.getRemainingAmount() == null ? 0 : r.getRemainingAmount();
            List<com.midland.bar.Bar.Projection.PurchaseRowWithShares.Share> shares = new ArrayList<>();
            int givenTotal = 0, givenLeft = 0, givenPct = 0;
            for (int i = 0; i < uids.size(); i++) {
                BarServiceEntity item = items.get(uids.get(i));
                boolean last = i == uids.size() - 1;
                // No buying prices at all: equal parts.
                double part = whole > 0 ? (double) costs.get(i) / whole : 1.0 / uids.size();
                // The last takes what rounding left, so the parts add up to the pot.
                int t = last ? total - givenTotal : (int) Math.round(total * part);
                int left = last ? remaining - givenLeft : (int) Math.round(remaining * part);
                int pct = last ? 100 - givenPct : (int) Math.round(part * 100);
                givenTotal += t; givenLeft += left; givenPct += pct;
                shares.add(new com.midland.bar.Bar.Projection.PurchaseRowWithShares.Share(
                        uids.get(i), item == null ? "?" : item.getServiceName(), pct, t, left));
            }
            out.add(new com.midland.bar.Bar.Projection.PurchaseRowWithShares(r, shares));
        }
        return out;
    }

    /** What {@code units} of a stock item's smallest measure cost at its buying price. */
    private static int costOf(BarServiceEntity item, Integer units) {
        if (item == null || item.getBuyingPrice() == null)
            return 0;
        int perPack = item.getUnitsPerPack() == null || item.getUnitsPerPack() < 1 ? 1 : item.getUnitsPerPack();
        int n = units == null || units < 1 ? 1 : units;
        return Math.round((float) item.getBuyingPrice() * n / perPack);
    }
    @Transactional
    public Response<StockAndPurchase> payStockAndPurchase(PayStockAndPurchaseDTO dto) {
        moneyLock.hold(com.midland.bar.Config.Security.LoggerUser.getBranchUIDOrMain()); // before any row lock - see MoneyLock
        workShiftService.requireOpenForPayout();

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
        incomeExpensesDescription.setPaidBy(LoggerUser.getEmail());
        incomeExpensesDescription.setPaidAt(java.time.LocalDateTime.now());
        incomeExpensesDescription.setMethod(payoutMethod(dto.getMethod()));

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
    /**
     * Manunuzi > what was bought in the period (owner, 2026-10-19): every
     * payment out of Manunuzi on the days it was made - a delivery today
     * shows today, though its money came from several weeks' savings.
     */
    public ResponseList<Map<String, Object>> stockPurchaseSpends(String filter) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate from, to;
        switch (filter.toUpperCase()) {
            case "TODAY", "DAY" -> { from = today; to = today; }
            case "YESTERDAY" -> { from = today.minusDays(1); to = from; }
            case "THIS_WEEK" -> { from = monday; to = monday.plusDays(6); }
            case "LAST_WEEK" -> { from = monday.minusWeeks(1); to = from.plusDays(6); }
            case "THIS_MONTH" -> { from = today.withDayOfMonth(1); to = today.with(TemporalAdjusters.lastDayOfMonth()); }
            case "LAST_MONTH" -> { from = today.minusMonths(1).withDayOfMonth(1); to = from.with(TemporalAdjusters.lastDayOfMonth()); }
            case "THIS_YEAR" -> { from = today.withDayOfYear(1); to = today.with(TemporalAdjusters.lastDayOfYear()); }
            case "LAST_YEAR" -> { from = today.minusYears(1).withDayOfYear(1); to = from.with(TemporalAdjusters.lastDayOfYear()); }
            default -> throw new BusinessException("Invalid filter: " + filter);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : stockAndPurchaseDescriptionsRepository.spentBetween(LoggerUser.getBranchUID(), from, to)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", r[0]);
            m.put("description", r[1]);
            m.put("serviceName", r[2]);
            m.put("amount", r[3] == null ? 0 : ((Number) r[3]).longValue());
            m.put("outside", false);
            out.add(m);
        }
        // What a delivery cost beyond its product's savings - paid, but not out of Manunuzi.
        for (Object[] r : supplierDeliveryItemRepository.outsideBetween(LoggerUser.getBranchUID(), from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", ((LocalDateTime) r[0]).toLocalDate());
            m.put("description", "Mzigo " + (r[1] != null ? r[1] + " · " : "") + r[2] + " · " + r[3]);
            m.put("serviceName", r[3]);
            m.put("amount", r[4] == null ? 0 : ((Number) r[4]).longValue());
            m.put("outside", true);
            out.add(m);
        }
        out.sort((a, b) -> ((LocalDate) b.get("date")).compareTo((LocalDate) a.get("date")));
        return new ResponseList<>(out);
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
