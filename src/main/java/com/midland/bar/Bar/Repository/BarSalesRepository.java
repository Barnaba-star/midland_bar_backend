package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BarSales;
import com.midland.bar.Bar.Projection.BarProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BarSalesRepository extends JpaRepository<BarSales, String> {

    @Query("SELECT s FROM BarSales s WHERE s.uid=:uid AND s.branchUid=:branchUID")
    Optional<BarSales> findSalesByUID(String uid, String branchUID);

    @Query("""
            SELECT s.uid as uid,  s.createdAt as salesTime, so.paymentMethod as paymentName,
            so.salesCode as salesCode, so.status as status,
            st.firstName as firstName, st.middleName as middleName, st.lastName as lastName,
            ss.serviceName as serviceName, ss.serviceCode as serviceCode, ss.price as price
            FROM BarSales s LEFT JOIN s.barStaff st LEFT JOIN s.barServiceEntity ss
            LEFT JOIN SalesOpened so WHERE s.uid=:barSalesUID AND s.branchUid=:branchUID AND s.isActive=true
            """)
    Optional<BarProjection> findBarSalesByUID(String barSalesUID, String branchUID);

    @Query("""
    SELECT
        s.uid as uid,
        s.createdAt as salesTime,
        so.paymentMethod as paymentName,
        so.salesCode as salesCode,
        so.status as status,
        st.firstName as firstName,
        st.middleName as middleName,
        st.lastName as lastName,
        ss.serviceName as serviceName,
        ss.serviceCode as serviceCode,
        ss.price as price
    FROM BarSales s
    JOIN s.salesOpened so
    LEFT JOIN s.barStaff st
    LEFT JOIN s.barServiceEntity ss
    WHERE so.uid = :barOpenUID
      AND s.branchUid = :branchUID
      AND s.isActive = true
""")
    List<BarProjection> findBarSalesList(String branchUID, String barOpenUID);

    @Query("""
    SELECT
        s.uid as uid,
        s.createdAt as salesTime,
        so.paymentMethod as paymentName,
        so.salesCode as salesCode,
        so.status as status,
        st.firstName as firstName,
        st.middleName as middleName,
        st.lastName as lastName,
        ss.serviceName as serviceName,
        ss.serviceCode as serviceCode,
        ss.price as price
    FROM BarSales s
    JOIN s.salesOpened so
    LEFT JOIN s.barStaff st
    LEFT JOIN s.barServiceEntity ss
    WHERE s.branchUid = :branchUID
      AND s.isActive = true
      AND s.status = 'ACTIVE'
      AND s.createdAt = :localDate
""")
    List<BarProjection> findBarSalesListActiveTrue(String branchUID, LocalDate localDate);

    @Query("""
           SELECT s.uid as uid,  s.createdAt as salesTime, so.paymentMethod as paymentName,
            so.salesCode as salesCode, so.status as status,
            st.firstName as firstName, st.middleName as middleName, st.lastName as lastName,
            ss.serviceName as serviceName, ss.serviceCode as serviceCode, ss.price as price
            FROM BarSales s LEFT JOIN s.barStaff st LEFT JOIN s.barServiceEntity ss
            LEFT JOIN SalesOpened so WHERE  s.branchUid=:branchUID AND s.isActive=true
           """)
    Page<BarProjection> findBarSalesPage(Pageable pageable, String branchUID);
}
