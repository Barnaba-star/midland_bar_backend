package com.midland.bar.Bar;

import com.midland.bar.Bar.Dto.*;
import com.midland.bar.Bar.Model.*;
import com.midland.bar.Bar.Repository.BarSalesRepository;
import com.midland.bar.Bar.Repository.BarServiceRepository;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Bar.Service.Ingredients;
import com.midland.bar.Bar.Service.StaffSellService;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A service made from more than one stock item (wali samaki: fish and rice),
 * against the local database, rolled back: a sale takes from every one of
 * them, and taking the line off puts every one back.
 */
@SpringBootTest
@Transactional
class RecipeStockTest {

    private static final String LOGIN = "root@root.com";
    private static final String MSHIKAKI = "7b57124d-a41d-40e2-b5e5-54fce93123f8";

    @Autowired UserRepository userRepository;
    @Autowired BarService barService;
    @Autowired StaffSellService staffSellService;
    @Autowired BarServiceRepository barServiceRepository;
    @Autowired BarSalesRepository barSalesRepository;
    @Autowired com.midland.bar.Bar.Service.WorkShiftService workShiftService;

    private SalesOpened bill;
    private BarServiceEntity rice;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        workShiftService.open();

        BarStaffDTO s = new BarStaffDTO();
        s.setFirstName("Recipe");
        s.setLastName("Test");
        s.setPhoneNumber("0700000000");
        s.setBarCategory(StaffCategory.values()[0].name());
        s.setStaffCode("");
        BarStaff staff = barService.saveBarStaff(s).getData();
        StaffBillDTO b = new StaffBillDTO();
        b.setStaffCode(staff.getStaffCode());
        bill = staffSellService.openBill(b).getData();
        assertNotNull(bill);

        BarServiceEntity mshikaki = barServiceRepository.findById(MSHIKAKI).orElseThrow();
        rice = new BarServiceEntity();
        rice.setServiceName("Mchele test");
        rice.setKind(ServiceKind.STOCK_ITEM.name());
        rice.setCategory("FOOD");
        rice.setTrackStock(true);
        rice.setStockQuantity(100);
        rice.setUnitsPerPack(10);
        rice.setBuyingPrice(5000); // 500 a unit
        rice.setBranchUid(mshikaki.getBranchUid());
        rice = barServiceRepository.save(rice);
        // One mshikaki now also takes 2 units of rice.
        mshikaki.setExtraIngredients(Ingredients.toJson(List.of(new Ingredients.Ingredient(rice.getUid(), null, null, 2))));
        barServiceRepository.save(mshikaki);
    }

    private int onHand(String uid) {
        return barServiceRepository.findById(uid).orElseThrow().getStockQuantity();
    }

    private String beef() {
        return barServiceRepository.findById(MSHIKAKI).orElseThrow().getStockSourceUid();
    }

    @Test
    void aSaleTakesFromEveryIngredientAndTakingItOffPutsThemBack() {
        int beef = onHand(beef());
        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(bill.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(3);
        sale.setItems(List.of(item));
        assertNotNull(barService.addItemsToBill(sale).getData());

        assertEquals(100 - 6, onHand(rice.getUid()), "3 mshikaki x 2 rice");
        assertTrue(onHand(beef()) < beef, "the beef too");
        BarSales line = barSalesRepository.findAll().stream()
                .filter(l -> l.getSalesOpened() != null && bill.getUid().equals(l.getSalesOpened().getUid()))
                .findFirst().orElseThrow();
        assertTrue(line.getUnitCost() >= 1000, "the rice's cost is on the line: " + line.getUnitCost());

        RemoveBillLineDTO off = new RemoveBillLineDTO();
        off.setBarSalesUID(line.getUid());
        off.setQuantity(1);
        off.setReason("test");
        assertNotNull(barService.removeBillLine(off).getData());
        assertEquals(100 - 4, onHand(rice.getUid()), "one back: its rice back");

        off.setQuantity(null);
        assertNotNull(barService.removeBillLine(off).getData());
        assertEquals(100, onHand(rice.getUid()), "all back");
        assertEquals(beef, onHand(beef()));
    }

    private com.midland.bar.Bar.Projection.ServiceCommissionProjection commissionRow() {
        String name = barServiceRepository.findById(MSHIKAKI).orElseThrow().getServiceName();
        return barService.findServiceCommissionPage(0, 50, name, null).getData().stream()
                .filter(r -> MSHIKAKI.equals(r.getServiceUid())).findFirst().orElseThrow();
    }

    @Test
    void commissionCostsTheWholeRecipe() {
        var withRice = commissionRow();
        BarServiceEntity mshikaki = barServiceRepository.findById(MSHIKAKI).orElseThrow();
        mshikaki.setExtraIngredients(null);
        barServiceRepository.save(mshikaki);
        var beefOnly = commissionRow();
        int beef = beefOnly.getUnitCost() == null ? 0 : beefOnly.getUnitCost();
        assertEquals(beef + 1000, withRice.getUnitCost(), "2 units of rice at 500 on top of the beef");
        assertTrue(withRice.getStockSourceName().contains("Mchele test"), withRice.getStockSourceName());
    }

    @Test
    void thePurchasePotOfARecipeIsSplitByIngredient() {
        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(bill.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(2);
        sale.setItems(List.of(item));
        assertNotNull(barService.addItemsToBill(sale).getData());

        var row = barService.getStockAndPurchaseByFilter("THIS_WEEK").getData().stream()
                .filter(r -> MSHIKAKI.equals(r.getServiceUid())).findFirst().orElseThrow();
        assertInstanceOf(com.midland.bar.Bar.Projection.PurchaseRowWithShares.class, row);
        var shares = ((com.midland.bar.Bar.Projection.PurchaseRowWithShares) row).getIngredientShares();
        assertEquals(2, shares.size(), "the beef and the rice");
        assertEquals(row.getTotalAmount(), shares.stream().mapToInt(x -> x.totalAmount()).sum());
        assertEquals(100, shares.stream().mapToInt(x -> x.percent()).sum());
        assertTrue(shares.stream().anyMatch(x -> x.name().equals("Mchele test")));
    }

    @Test
    void notEnoughOfAnyIngredientRefusesTheSale() {
        rice.setStockQuantity(1);
        barServiceRepository.save(rice);
        SaleItemsDTO sale = new SaleItemsDTO();
        sale.setSalesOpenedUID(bill.getUid());
        SaleItemsDTO.Item item = new SaleItemsDTO.Item();
        item.setBarServiceUID(MSHIKAKI);
        item.setQuantity(1);
        sale.setItems(List.of(item));
        var ex = assertThrows(com.midland.bar.Utils.Exceptions.BusinessException.class, () -> barService.addItemsToBill(sale));
        assertTrue(ex.getMessage().contains("Mchele test"), ex.getMessage());
    }

    @Test
    void aStockItemInARecipeCannotBeDeleted() {
        var res = barService.deleteServiceBarByUID(rice.getUid());
        assertNull(res.getData());
        assertTrue(res.getMessage().contains("made from"), res.getMessage());
    }
}
