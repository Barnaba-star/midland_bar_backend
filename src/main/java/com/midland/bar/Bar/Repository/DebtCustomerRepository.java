package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.DebtCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DebtCustomerRepository extends JpaRepository<DebtCustomer, String> {

    @Query("SELECT c FROM DebtCustomer c WHERE c.branchUid = :branchUID ORDER BY c.name")
    List<DebtCustomer> findInBranch(@Param("branchUID") String branchUID);

    @Query("SELECT c FROM DebtCustomer c WHERE c.uid = :uid AND c.branchUid = :branchUID")
    Optional<DebtCustomer> findInBranch(@Param("uid") String uid, @Param("branchUID") String branchUID);

    /** The customer, locked until the transaction ends - two tills cannot both take them past their limit. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DebtCustomer c WHERE c.uid = :uid AND c.branchUid = :branchUID")
    Optional<DebtCustomer> findForUpdate(@Param("uid") String uid, @Param("branchUID") String branchUID);

    @Query("SELECT c FROM DebtCustomer c WHERE c.phone = :phone AND c.branchUid = :branchUID")
    Optional<DebtCustomer> findByPhone(@Param("phone") String phone, @Param("branchUID") String branchUID);

    /** Every customer in every branch - the weekly reminder. */
    @Query("SELECT c FROM DebtCustomer c ORDER BY c.branchUid")
    List<DebtCustomer> findAllActive();
}
