package com.midland.bar.Bar.JackPoint;

import com.midland.bar.Bar.Alert.OwnerAlertService;
import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Projection.BarProjection;
import com.midland.bar.Bar.Repository.BarStaffRepository;
import com.midland.bar.Bar.Repository.StaffCommissionsRepository;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Bar.Service.CashUpService;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Config.Security.StaffSession;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Jack Point: the waiters' game. A staff member whose commission reaches the
 * weekly target for so many weeks in a row wins the reward. Finished weeks
 * are checked whenever the Jack Point page or a staff member's progress is
 * opened, and every Monday morning (JackPointJob); a win waits for the CEO,
 * manager or accountant to approve it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JackPointService {

    private final JackPointRepository jackPointRepository;
    private final JackPointAwardRepository awardRepository;
    private final StaffCommissionsRepository staffCommissionsRepository;
    private final BarStaffRepository barStaffRepository;
    private final OwnerAlertService ownerAlertService;
    private final BarService barService;

    static LocalDate monday(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static String branch() {
        return LoggerUser.getBranchUIDOrMain();
    }

    // ---------------------------------------------------------------- setting up

    @Transactional
    public Response<JackPoint> save(JackPointDTO dto) {
        JackPoint jp;
        if (dto.getUid() != null && !dto.getUid().isBlank()) {
            jp = jackPointRepository.findOne(dto.getUid(), branch()).orElse(null);
            if (jp == null)
                return new Response<>("Jack Point not found");
            jp.update();
        } else {
            jp = new JackPoint();
            jp.setCreatedBy(LoggerUser.getEmail());
        }
        String period = JackPeriod.of(dto.getPeriod());
        if (dto.getWeeks() > JackPeriod.maxRounds(period))
            return new Response<>("At most " + JackPeriod.maxRounds(period) + " in a row");
        int leave = dto.getLeaveDays() == null ? 0 : Math.max(0, dto.getLeaveDays());
        int bonus = dto.getBonusAmount() == null ? 0 : Math.max(0, dto.getBonusAmount());
        String note = dto.getRewardNote() == null ? "" : dto.getRewardNote().trim();
        if (leave == 0 && bonus == 0 && note.isEmpty())
            return new Response<>("Choose the reward: paid days off, a bonus or a written reward");
        if (leave > 30)
            return new Response<>("At most 30 paid days off");
        jp.setName(dto.getName().trim());
        jp.setWeeklyTarget(dto.getWeeklyTarget());
        jp.setWeeks(dto.getWeeks());
        jp.setPeriod(period);
        jp.setStartDate(JackPeriod.start(dto.getStartDate() == null ? LocalDate.now() : dto.getStartDate(), period));
        jp.setLeaveDays(leave);
        jp.setBonusAmount(bonus);
        jp.setRewardNote(note.isEmpty() ? null : (note.length() > 200 ? note.substring(0, 200) : note));
        jp.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        return new Response<>(jackPointRepository.save(jp));
    }

    @Transactional
    public Response<Boolean> delete(String uid) {
        JackPoint jp = jackPointRepository.findOne(uid, branch()).orElse(null);
        if (jp == null)
            return new Response<>("Jack Point not found");
        jp.delete();
        jackPointRepository.save(jp);
        return new Response<>(true);
    }

    // ---------------------------------------------------------------- the page

    /** The branch's Jack Points, each staff member's run on them, and the winners. */
    @Transactional
    public Response<Map<String, Object>> overview() {
        String branch = branch();
        evaluate(branch);
        List<JackPoint> points = jackPointRepository.findOfBranch(branch);
        List<BarProjection> staff = barStaffRepository.findBarStaffList(branch);
        Map<String, Map<String, NavigableMap<LocalDate, Integer>>> earnedBy = earnedByPeriod(branch, points);
        Map<String, List<JackPointAward>> awardsByPoint = new HashMap<>();
        List<JackPointAward> awards = awardRepository.findOfBranch(branch);
        for (JackPointAward a : awards)
            awardsByPoint.computeIfAbsent(a.getJackPointUid(), k -> new ArrayList<>()).add(a);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (JackPoint jp : points) {
            Map<String, NavigableMap<LocalDate, Integer>> weeks = earnedBy.getOrDefault(JackPeriod.of(jp.getPeriod()), Map.of());
            List<Map<String, Object>> progress = new ArrayList<>();
            if (Boolean.TRUE.equals(jp.getEnabled())) {
                for (BarProjection s : staff) {
                    Map<String, Object> p = progressOf(jp, s.getUid(), weeks.getOrDefault(s.getUid(), new TreeMap<>()),
                            awardsByPoint.getOrDefault(jp.getUid(), List.of()));
                    p.put("staffUid", s.getUid());
                    p.put("staffName", name(s.getFirstName(), s.getLastName()));
                    progress.add(p);
                }
                progress.sort(Comparator.comparing((Map<String, Object> p) -> -((Integer) p.get("streak")))
                        .thenComparing(p -> -((Integer) p.get("thisWeek"))));
            }
            List<JackPointAward> won = awardsByPoint.getOrDefault(jp.getUid(), List.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jackPoint", jp);
            row.put("progress", progress);
            row.put("state", stateOf(jp));
            row.put("winners", won.stream().filter(a -> !JackPointAward.REJECTED.equals(a.getStatus())).count());
            row.put("waiting", won.stream().filter(a -> JackPointAward.QUALIFIED.equals(a.getStatus())).count());
            row.put("currentFrom", JackPeriod.start(LocalDate.now(), jp.getPeriod()));
            row.put("currentTo", JackPeriod.end(JackPeriod.start(LocalDate.now(), jp.getPeriod()), jp.getPeriod()));
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jackPoints", rows);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String st : List.of("RUNNING", "UPCOMING", "OFF"))
            counts.put(st, rows.stream().filter(r -> st.equals(r.get("state"))).count());
        counts.put("ALL", (long) rows.size());
        out.put("counts", counts);
        out.put("awards", awards);
        return new Response<>(out);
    }

    /** A staff member's own runs (Staff Sell): how far they are on each game, and what they have won. */
    @Transactional
    public Response<Map<String, Object>> mine(String staffCode) {
        StaffSession.requireOwnCode(staffCode);
        String branch = branch();
        BarStaff staff = barStaffRepository.findByStaffCode(staffCode == null ? "" : staffCode.trim(), branch).orElse(null);
        if (staff == null)
            return new Response<>("No staff member has that code");
        evaluate(branch);
        List<JackPoint> points = jackPointRepository.findOfBranch(branch).stream()
                .filter(j -> Boolean.TRUE.equals(j.getEnabled())).toList();
        Map<String, Map<String, NavigableMap<LocalDate, Integer>>> earnedBy = earnedByPeriod(branch, points);
        List<JackPointAward> awards = awardRepository.findOfStaff(staff.getUid(), branch);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JackPoint jp : points) {
            Map<String, NavigableMap<LocalDate, Integer>> weeks = earnedBy.getOrDefault(JackPeriod.of(jp.getPeriod()), Map.of());
            Map<String, Object> p = progressOf(jp, staff.getUid(), weeks.getOrDefault(staff.getUid(), new TreeMap<>()),
                    awards.stream().filter(a -> a.getJackPointUid().equals(jp.getUid())).toList());
            p.put("name", jp.getName());
            p.put("leaveDays", jp.getLeaveDays());
            p.put("bonusAmount", jp.getBonusAmount());
            p.put("rewardNote", jp.getRewardNote());
            rows.add(p);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("games", rows);
        out.put("awards", awards.stream().limit(10).toList());
        return new Response<>(out);
    }

    @Transactional
    public Response<JackPointAward> decide(String awardUid, AwardDecisionDTO dto) {
        JackPointAward a = awardRepository.findOne(awardUid, branch()).orElse(null);
        if (a == null)
            return new Response<>("Not found");
        if (!JackPointAward.QUALIFIED.equals(a.getStatus()))
            return new Response<>("Already decided");
        String note = dto.getNote() == null ? "" : dto.getNote().trim();
        if (dto.isApprove()) {
            if (a.getLeaveDays() != null && a.getLeaveDays() > 0) {
                if (dto.getLeaveFrom() == null)
                    return new Response<>("Choose the first paid day off");
                a.setLeaveFrom(dto.getLeaveFrom());
            }
            if (a.getBonusAmount() != null && a.getBonusAmount() > 0)
                barService.addJackPointBonus(a.getStaffUid(), a.getBranchUid(), LocalDate.now(), a.getBonusAmount());
            a.setStatus(JackPointAward.APPROVED);
        } else {
            if (note.isEmpty())
                return new Response<>("Write why it is turned down");
            a.setStatus(JackPointAward.REJECTED);
        }
        a.setDecisionNote(note.isEmpty() ? null : (note.length() > 300 ? note.substring(0, 300) : note));
        a.setDecidedBy(LoggerUser.getEmail());
        a.setDecidedByName(CashUpService.nameOf(LoggerUser.getUser()));
        a.setDecidedAt(LocalDateTime.now());
        a.update();
        log.info("{} {} Jack Point {} of staff {}", LoggerUser.getEmail(), a.getStatus(), a.getJackPointName(), a.getStaffUid());
        return new Response<>(awardRepository.save(a));
    }

    // ---------------------------------------------------------------- counting

    /**
     * Finds new winners in the branch: for each running Jack Point and staff
     * member, the finished weeks since their last win on it (or the start),
     * looking for [weeks] in a row at or over the target. Each run found is a
     * QUALIFIED award and the owners hear of it. Returns how many.
     */
    @Transactional
    public int evaluate(String branch) {
        List<JackPoint> points = jackPointRepository.findOfBranch(branch).stream()
                .filter(j -> Boolean.TRUE.equals(j.getEnabled())).toList();
        if (points.isEmpty())
            return 0;
        Map<String, Map<String, NavigableMap<LocalDate, Integer>>> earnedBy = earnedByPeriod(branch, points);
        List<BarProjection> staff = barStaffRepository.findBarStaffList(branch);
        int found = 0;
        for (JackPoint jp : points) {
            String period = JackPeriod.of(jp.getPeriod());
            // Only rounds that are over count: yesterday, last week, last month.
            LocalDate lastFinished = JackPeriod.plus(JackPeriod.start(LocalDate.now(), period), period, -1);
            Map<String, NavigableMap<LocalDate, Integer>> weeks = earnedBy.getOrDefault(period, Map.of());
            List<JackPointAward> past = awardRepository.findOfJackPoint(jp.getUid());
            for (BarProjection s : staff) {
                NavigableMap<LocalDate, Integer> earned = weeks.getOrDefault(s.getUid(), new TreeMap<>());
                LocalDate week = firstCountedWeek(jp, s.getUid(), past);
                int streak = 0;
                int total = 0;
                for (; !week.isAfter(lastFinished); week = JackPeriod.plus(week, period, 1)) {
                    int got = earned.getOrDefault(week, 0);
                    if (got >= jp.getWeeklyTarget()) {
                        streak++;
                        total += got;
                    } else {
                        streak = 0;
                        total = 0;
                    }
                    if (streak == jp.getWeeks()) {
                        JackPointAward a = new JackPointAward();
                        a.setJackPointUid(jp.getUid());
                        a.setJackPointName(jp.getName());
                        a.setStaffUid(s.getUid());
                        a.setStaffName(name(s.getFirstName(), s.getLastName()));
                        a.setWeekFrom(JackPeriod.plus(week, period, 1L - jp.getWeeks()));
                        a.setWeekTo(week);
                        a.setPeriod(period);
                        a.setCommissionTotal(total);
                        a.setLeaveDays(jp.getLeaveDays());
                        a.setBonusAmount(jp.getBonusAmount());
                        a.setRewardNote(jp.getRewardNote());
                        a.setQualifiedAt(LocalDateTime.now());
                        awardRepository.save(a);
                        ownerAlertService.jackPointWon(branch, a.getStaffName(), jp.getName());
                        found++;
                        streak = 0;
                        total = 0;
                    }
                }
            }
        }
        if (found > 0)
            log.info("Jack Point: {} new winner(s) in branch {}", found, branch);
        return found;
    }

    /** The first week that still counts: the game's start, or the week after this staff member's last win on it. */
    private static LocalDate firstCountedWeek(JackPoint jp, String staffUid, List<JackPointAward> past) {
        LocalDate from = jp.getStartDate();
        for (JackPointAward a : past)
            if (a.getStaffUid().equals(staffUid) && !a.getWeekTo().isBefore(from))
                from = JackPeriod.plus(a.getWeekTo(), jp.getPeriod(), 1);
        return from;
    }

    /** Where a staff member stands: weeks in a row so far (finished weeks), this week's commission, and the target. */
    private static Map<String, Object> progressOf(JackPoint jp, String staffUid, NavigableMap<LocalDate, Integer> earned, List<JackPointAward> past) {
        String period = JackPeriod.of(jp.getPeriod());
        LocalDate thisWeek = JackPeriod.start(LocalDate.now(), period);
        int streak = 0;
        for (LocalDate w = firstCountedWeek(jp, staffUid, past); w.isBefore(thisWeek); w = JackPeriod.plus(w, period, 1))
            streak = earned.getOrDefault(w, 0) >= jp.getWeeklyTarget() ? streak + 1 : 0;
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("jackPointUid", jp.getUid());
        p.put("streak", streak);
        p.put("weeks", jp.getWeeks());
        p.put("weeklyTarget", jp.getWeeklyTarget());
        p.put("period", period);
        p.put("thisWeek", jp.getStartDate().isAfter(thisWeek) ? 0 : earned.getOrDefault(thisWeek, 0));
        p.put("started", !jp.getStartDate().isAfter(thisWeek));
        return p;
    }

    /** RUNNING (on and started), UPCOMING (on, starts later) or OFF. */
    static String stateOf(JackPoint jp) {
        if (!Boolean.TRUE.equals(jp.getEnabled()))
            return "OFF";
        return jp.getStartDate().isAfter(LocalDate.now()) ? "UPCOMING" : "RUNNING";
    }

    /**
     * Commission earned on sales per staff member per round, for each kind of
     * round the games use: {period: {staffUid: {round start: amount}}}. Read
     * once by day, from the earliest game start up to today.
     */
    private Map<String, Map<String, NavigableMap<LocalDate, Integer>>> earnedByPeriod(String branch, List<JackPoint> points) {
        LocalDate from = points.stream().map(JackPoint::getStartDate).min(Comparator.naturalOrder()).orElse(LocalDate.now());
        Set<String> periods = new HashSet<>();
        for (JackPoint jp : points)
            periods.add(JackPeriod.of(jp.getPeriod()));
        Map<String, Map<String, NavigableMap<LocalDate, Integer>>> out = new HashMap<>();
        for (Object[] row : staffCommissionsRepository.earnedByDay(branch, from, LocalDate.now())) {
            if (row[0] == null || row[1] == null)
                continue;
            int amount = row[2] instanceof Number n ? n.intValue() : 0;
            for (String period : periods)
                out.computeIfAbsent(period, k -> new HashMap<>())
                        .computeIfAbsent((String) row[0], k -> new TreeMap<>())
                        .merge(JackPeriod.start((LocalDate) row[1], period), amount, Integer::sum);
        }
        return out;
    }

    private static String name(String first, String last) {
        return ((first == null ? "" : first.trim()) + " " + (last == null ? "" : last.trim())).trim();
    }
}
