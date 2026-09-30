package com.midland.bar.Bar.Projection;

import com.midland.bar.Bar.Model.BarSales;
import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Model.BarStaff;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface BarProjection {

    /*
     * ==========================================================
     * SERVICE PROJECTION
     * Used to retrieve Bar Service information.
     * ==========================================================
     */

    String getServiceName();

    String getServiceCode();

    String getDescription();

    Integer getPrice();

    String getCategory();

    String getUnit();

    String getPackUnit();

    Integer getUnitsPerPack();

    // getBuyingPrice() is declared once, under the store section below, and
    // serves both.

    Integer getStockQuantity();

    /** SERVICE or STOCK_ITEM (null on older rows = SERVICE). */
    String getKind();

    /** For a service made from a stock item: that item, and how much one sale takes. */
    String getStockSourceUid();
    String getStockSourceName();
    Integer getUnitsPerSale();

    /** A stock item's measures as JSON, smallest first (see BarServiceEntity.unitLadder). */
    String getUnitLadder();

    /** For a service from a stock item: the rung one sale is, how many of it, and the item's ladder. */
    String getSaleUnitName();
    Integer getSaleUnitCount();
    String getSourceUnitLadder();

    /** The stock item's units on hand, so the till can say how many can still be sold. */
    Integer getSourceStockQuantity();

    /** Counted, and at or below the Settings > Config low-stock level (in packs). */
    Boolean getLowStock();

    Boolean getTrackStock();

    Integer getCommissionValue();

    String getCommissionType();

    String getStatus();

    String getUid();
    String getUsageType();

    LocalDate getSalesTime();

    LocalDate getWeekDate();

    /*
     * ==========================================================
     * PAYMENT METHOD PROJECTION
     * Used to retrieve Payment Method information.
     * ==========================================================
     */
    String getPaymentName();

    Integer getPaymentCode();


    /*
     * ==========================================================
     * STAFF METHOD PROJECTION
     * Used to retrieve Staff Method information.
     * ==========================================================
     */

    String getFirstName();
    String getStaffCode();
    String getMiddleName();
    String getLastName();
    LocalDate getDateOfBirth();
    String getPhoneNumber();
    String getGender();
    String getBarCategory();
    Boolean getActive();

    /*
     * ==========================================================
     * SERVICES BOOKED METHOD PROJECTION
     * Used to retrieve Staff Method information.
     * ==========================================================
     */

    String getCustomerName();
    BigDecimal getTotalAmount();
    BigDecimal getPaidAmount();
    BigDecimal getRemainingAmount();
    LocalDate getBookingDate();
    String getServiceUID();


    /*
     * ==========================================================
     * REPORTS METHOD PROJECTION
     * Used to retrieve Staff Method information.
     * ==========================================================
     */
    Integer getTraAmount();
    Integer getEmergencyAmount();
    Integer getOthersAmount();
    Integer getOwnerAmount();
    Integer getStaffAmount();
    Integer getMaintenanceAmount();
    String getPaymentMethod();
    Integer getLoanAmount();
    Integer getRentAmount();
    Integer getWaterAmount();
    Integer getLukuAmount();
    Integer getStockPurchaseAmount();
    Integer getSharedAmount();

    /*
     * ==========================================================
     * SALES METHOD PROJECTION
     * Used to retrieve Payment Method information.
     * ==========================================================
     */
     String getSalesCode();


     String getNameOfStore();

     String getCodeOfStore();

     Integer getQuantity();

    /** price x quantity for a sale line. */
    Integer getLineTotal();

     String getBarServiceEntityUID();

    /*
     * ==========================================================
     * STORE METHOD PROJECTION
     * Used to retrieve Store Method information.
     * ==========================================================
     */
    LocalDate getClosedDate();
    LocalDate getOpenedDate();
    Integer getBuyingPrice();
    Integer getTotalQuantityPrice();
    Integer getUsedQuantity();
    Integer getNotUsedQuantity();
    String getOpenStoreCode();
    Integer getOpenQuantity();


    String getStaffCommissionUID();
    LocalDate getDate();

}
