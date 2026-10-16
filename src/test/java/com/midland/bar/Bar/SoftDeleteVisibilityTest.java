package com.midland.bar.Bar;

import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Repository.BarServiceRepository;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Monitoring.Model.ErrorLog;
import com.midland.bar.Monitoring.Repository.ErrorLogRepository;
import com.midland.bar.Monitoring.Service.ErrorLogService;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What is deleted stays out of the lists, against the local database, rolled back. */
@SpringBootTest
@Transactional
class SoftDeleteVisibilityTest {

    private static final String LOGIN = "root@root.com";
    private static final String MSHIKAKI = "7b57124d-a41d-40e2-b5e5-54fce93123f8";

    @Autowired UserRepository userRepository;
    @Autowired BarService barService;
    @Autowired BarServiceRepository barServiceRepository;
    @Autowired ErrorLogRepository errorLogRepository;
    @Autowired ErrorLogService errorLogService;
    @Autowired EntityManager em;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void deletedServiceLeavesTheServicesList() {
        BarServiceEntity s = new BarServiceEntity();
        s.setServiceName("Futa test zzq");
        s.setCategory("FOOD");
        s.setBranchUid(barServiceRepository.findById(MSHIKAKI).orElseThrow().getBranchUid());
        s = barServiceRepository.save(s);
        em.flush();
        assertEquals(1L, barService.findBarServicePage(0, 10, "Futa test zzq", "SERVICES").getTotalElements());

        assertNotNull(barService.deleteServiceBarByUID(s.getUid()).getData());
        em.flush();
        em.clear();
        assertEquals(0L, barService.findBarServicePage(0, 10, "Futa test zzq", "SERVICES").getTotalElements());
        assertEquals(0L, barService.findBarServicePage(0, 10, "Futa test zzq", "ALL").getTotalElements());
    }

    @Test
    void clearedErrorsLeaveTheErrorsList() {
        ErrorLog e = new ErrorLog();
        e.setSource("BACKEND");
        e.setLevel("ERROR");
        e.setMessage("futa test zzq");
        errorLogRepository.save(e);
        em.flush();
        long before = errorLogRepository.findErrorPage(null, null, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
        assertTrue(before >= 1);

        errorLogService.clearAll();
        em.flush();
        em.clear();
        assertEquals(0L, errorLogRepository.findErrorPage(null, null, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements());
    }

    @Test
    void usersSearchNarrowsTheList() {
        long all = barService.findUserPageByBranch(0, 50, null).getTotalElements();
        assertTrue(all >= 1);
        assertEquals(1L, barService.findUserPageByBranch(0, 50, "ROOT@ROOT.COM").getTotalElements());
        assertEquals(0L, barService.findUserPageByBranch(0, 50, "hakuna-mtu-zzq").getTotalElements());
    }
}
