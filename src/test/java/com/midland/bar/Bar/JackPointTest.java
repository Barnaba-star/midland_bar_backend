package com.midland.bar.Bar;

import com.midland.bar.Bar.Dto.BarStaffDTO;
import com.midland.bar.Bar.JackPoint.*;
import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Model.StaffCategory;
import com.midland.bar.Bar.Model.StaffCommissions;
import com.midland.bar.Bar.Repository.StaffCommissionsRepository;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Jack Point finds weeks-in-a-row winners once, and approval pays the bonus - local database, rolled back. */
@SpringBootTest
@org.junit.jupiter.api.Tag("local")
@Transactional
class JackPointTest {

    @Autowired UserRepository userRepository;
    @Autowired BarService barService;
    @Autowired JackPointService jackPointService;
    @Autowired JackPointAwardRepository awardRepository;
    @Autowired StaffCommissionsRepository staffCommissionsRepository;

    private BarStaff staff;
    private final LocalDate thisWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsernameForAuthentication("root@root.com");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        BarStaffDTO s = new BarStaffDTO();
        s.setFirstName("Jack");
        s.setLastName("Point");
        s.setPhoneNumber("0700000021");
        s.setBarCategory(StaffCategory.values()[0].name());
        s.setStaffCode("");
        staff = barService.saveBarStaff(s).getData();
    }

    /** Commission earned on the Tuesday of the week [weeksAgo] back. */
    private void earned(int weeksAgo, int amount) {
        StaffCommissions c = new StaffCommissions();
        c.setBarStaff(staff);
        c.setCreatedAt(thisWeek.minusWeeks(weeksAgo).plusDays(1));
        c.setTotalAmount(amount);
        c.setRemainingAmount(amount);
        staffCommissionsRepository.save(c);
    }

    private JackPoint game(int leaveDays, int bonus) {
        JackPointDTO dto = new JackPointDTO();
        dto.setName("Wiki mbili za moto");
        dto.setWeeklyTarget(50_000);
        dto.setWeeks(2);
        dto.setStartDate(thisWeek.minusWeeks(4));
        dto.setLeaveDays(leaveDays);
        dto.setBonusAmount(bonus);
        dto.setAutoBonus(false);
        return jackPointService.save(dto).getData();
    }

    private List<JackPointAward> awardsOfStaff() {
        return awardRepository.findOfStaff(staff.getUid(), LoggerUser.getBranchUIDOrMain());
    }

    @Test
    void twoWeeksInARowOverTheTargetWinsOnceAndTheRunStartsAgain() {
        earned(4, 30_000);   // under: no run
        earned(3, 60_000);
        earned(2, 55_000);   // 2 in a row: won
        earned(1, 70_000);   // 1 into the next run
        earned(0, 90_000);   // this week: not over, never counts yet
        JackPoint jp = game(2, 0);

        assertEquals(1, jackPointService.evaluate(LoggerUser.getBranchUIDOrMain()));
        assertEquals(0, jackPointService.evaluate(LoggerUser.getBranchUIDOrMain()), "found once");
        JackPointAward a = awardsOfStaff().get(0);
        assertEquals(thisWeek.minusWeeks(3), a.getWeekFrom());
        assertEquals(thisWeek.minusWeeks(2), a.getWeekTo());
        assertEquals(115_000, a.getCommissionTotal());
        assertEquals(JackPointAward.QUALIFIED, a.getStatus());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) jackPointService.overview().getData().get("jackPoints");
        Map<String, Object> mine = rows.stream().filter(r -> ((JackPoint) r.get("jackPoint")).getUid().equals(jp.getUid())).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> p = ((List<Map<String, Object>>) mine.get("progress")).stream()
                .filter(x -> staff.getUid().equals(x.get("staffUid"))).findFirst().orElseThrow();
        assertEquals(1, p.get("streak"));
        assertEquals(90_000, p.get("thisWeek"));
    }

    @Test
    void approvingNeedsTheFirstDayOffAndPaysTheBonusWhichNeverCountsAsCommission() {
        earned(3, 60_000);
        earned(2, 60_000);
        game(1, 20_000);
        jackPointService.evaluate(LoggerUser.getBranchUIDOrMain());
        JackPointAward a = awardsOfStaff().get(0);

        AwardDecisionDTO no = new AwardDecisionDTO();
        no.setApprove(false);
        assertNull(jackPointService.decide(a.getUid(), no).getData(), "turning down needs a reason");

        AwardDecisionDTO yes = new AwardDecisionDTO();
        yes.setApprove(true);
        assertNull(jackPointService.decide(a.getUid(), yes).getData(), "needs the first paid day off");
        yes.setLeaveFrom(LocalDate.now().plusDays(2));
        assertEquals(JackPointAward.APPROVED, jackPointService.decide(a.getUid(), yes).getData().getStatus());

        StaffCommissions today = staffCommissionsRepository.findCommission(staff.getUid(), LoggerUser.getBranchUIDOrMain(), LocalDate.now()).orElseThrow();
        assertEquals(20_000, today.getBonusAmount());
        assertEquals(20_000, today.getRemainingAmount(), "owed to the staff member");
        assertTrue(staffCommissionsRepository.earnedByDay(LoggerUser.getBranchUIDOrMain(), LocalDate.now(), LocalDate.now()).stream()
                .filter(r -> staff.getUid().equals(r[0])).allMatch(r -> ((Number) r[2]).intValue() == 0), "the bonus is not commission earned");
    }

    @Test
    void aGameNeedsAReward() {
        JackPointDTO dto = new JackPointDTO();
        dto.setName("Bila zawadi");
        dto.setWeeklyTarget(50_000);
        dto.setWeeks(2);
        assertNull(jackPointService.save(dto).getData());
    }

    private void earnedOn(LocalDate day, int amount) {
        StaffCommissions c = new StaffCommissions();
        c.setBarStaff(staff);
        c.setCreatedAt(day);
        c.setTotalAmount(amount);
        c.setRemainingAmount(amount);
        staffCommissionsRepository.save(c);
    }

    private JackPoint game(String period, int rounds, LocalDate start) {
        JackPointDTO dto = new JackPointDTO();
        dto.setName("Jack " + period);
        dto.setPeriod(period);
        dto.setWeeklyTarget(10_000);
        dto.setWeeks(rounds);
        dto.setStartDate(start);
        dto.setBonusAmount(5_000);
        dto.setAutoBonus(false);
        return jackPointService.save(dto).getData();
    }

    @Test
    void aDailyGameCountsDaysInARowAndTodayNeverCountsYet() {
        LocalDate today = LocalDate.now();
        earnedOn(today.minusDays(4), 5_000);   // under
        earnedOn(today.minusDays(3), 12_000);
        earnedOn(today.minusDays(2), 15_000);
        earnedOn(today.minusDays(1), 11_000);  // 3 days in a row: won
        earnedOn(today, 50_000);               // today is not over
        JackPoint jp = game("DAY", 3, today.minusDays(5));
        assertEquals("DAY", jp.getPeriod());
        assertEquals(today.minusDays(5), jp.getStartDate(), "a daily game starts on the day chosen");

        jackPointService.evaluate(LoggerUser.getBranchUIDOrMain());
        assertEquals(1, awardsOfStaff().stream().filter(x -> x.getJackPointUid().equals(jp.getUid())).count(), "one win for this staff member");
        JackPointAward a = awardsOfStaff().stream().filter(x -> x.getJackPointUid().equals(jp.getUid())).findFirst().orElseThrow();
        assertEquals(today.minusDays(3), a.getWeekFrom());
        assertEquals(today.minusDays(1), a.getWeekTo());
        assertEquals(today.minusDays(1), a.getRunEnd());
        assertEquals(38_000, a.getCommissionTotal());
        assertEquals("DAY", a.getPeriod());
    }

    @Test
    void aMonthlyGameCountsCalendarMonthsAndTheListSaysWhatIsRunning() {
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        earnedOn(month.minusMonths(2).plusDays(3), 20_000);
        earnedOn(month.minusMonths(1).plusDays(10), 30_000); // 2 months in a row: won
        JackPoint jp = game("MONTH", 2, month.minusMonths(2).plusDays(15));
        assertEquals(month.minusMonths(2), jp.getStartDate(), "a monthly game starts on the 1st");
        JackPoint later = game("WEEK", 2, LocalDate.now().plusWeeks(2));

        jackPointService.evaluate(LoggerUser.getBranchUIDOrMain());
        assertEquals(1, awardsOfStaff().stream().filter(x -> x.getJackPointUid().equals(jp.getUid())).count(), "one win for this staff member");
        JackPointAward a = awardsOfStaff().stream().filter(x -> x.getJackPointUid().equals(jp.getUid())).findFirst().orElseThrow();
        assertEquals(month.minusMonths(2), a.getWeekFrom());
        assertEquals(month.minusDays(1), a.getRunEnd(), "the run ends on the last day of last month");

        Map<String, Object> data = jackPointService.overview().getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("jackPoints");
        Map<String, Object> m = rows.stream().filter(r -> ((JackPoint) r.get("jackPoint")).getUid().equals(jp.getUid())).findFirst().orElseThrow();
        assertEquals("RUNNING", m.get("state"));
        assertTrue((Long) m.get("waiting") >= 1, "our win waits for approval");
        Map<String, Object> u = rows.stream().filter(r -> ((JackPoint) r.get("jackPoint")).getUid().equals(later.getUid())).findFirst().orElseThrow();
        assertEquals("UPCOMING", u.get("state"));
    }

    @Test
    void anAutomaticBonusGoesStraightOntoTheCommissionAndIsListed() {
        earned(3, 60_000);
        earned(2, 60_000);
        JackPointDTO dto = new JackPointDTO();
        dto.setName("Bonus moja kwa moja");
        dto.setWeeklyTarget(50_000);
        dto.setWeeks(2);
        dto.setStartDate(thisWeek.minusWeeks(4));
        dto.setBonusAmount(15_000);
        dto.setDescription("Wiki mbili za 50,000+ upate 15,000 kwenye commission");
        JackPoint jp = jackPointService.save(dto).getData();
        assertTrue(jp.getAutoBonus(), "new games pay the bonus straight away");
        assertEquals("Wiki mbili za 50,000+ upate 15,000 kwenye commission", jp.getDescription());

        jackPointService.evaluate(LoggerUser.getBranchUIDOrMain());
        JackPointAward a = awardsOfStaff().stream().filter(x -> x.getJackPointUid().equals(jp.getUid())).findFirst().orElseThrow();
        assertEquals(JackPointAward.APPROVED, a.getStatus(), "money only: nothing to approve");
        assertNotNull(a.getBonusPaidAt());
        StaffCommissions today = staffCommissionsRepository.findCommission(staff.getUid(), LoggerUser.getBranchUIDOrMain(), LocalDate.now()).orElseThrow();
        assertEquals(15_000, today.getBonusAmount());

        @SuppressWarnings("unchecked")
        List<JackPointAward> bonuses = (List<JackPointAward>) jackPointService.overview().getData().get("bonuses");
        assertTrue(bonuses.stream().anyMatch(b -> b.getUid().equals(a.getUid())), "listed among those given a bonus");
    }
}
