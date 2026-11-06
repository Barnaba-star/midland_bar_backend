package com.midland.bar.Bar.JackPoint;

import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Uaa.Model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Every morning, once yesterday (and last week / last month) is over: every branch with a Jack Point
 * running is checked for new winners, so the owner hears without opening
 * the page. Each branch is checked as a stand-in login of that branch,
 * because new rows take their branch from the login (TenantEntity).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JackPointJob {

    private final JackPointRepository jackPointRepository;
    private final JackPointService jackPointService;

    @Scheduled(cron = "0 30 6 * * *")
    public void weekly() {
        for (String branchUid : jackPointRepository.branchesWithGames()) {
            try {
                Branch branch = new Branch();
                branch.setUid(branchUid);
                User system = new User();
                system.setUsername("jack-point");
                system.setBranch(branch);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(system, null, List.of()));
                jackPointService.evaluate(branchUid);
            } catch (Exception e) {
                log.warn("Jack Point check failed for branch {}: {}", branchUid, e.getMessage());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
