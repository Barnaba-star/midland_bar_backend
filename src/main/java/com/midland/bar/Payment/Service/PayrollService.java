package com.midland.bar.Payment.Service;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Payment.Dto.PayrollDTO;
import com.midland.bar.Payment.Dto.PayrollLineDTO;
import com.midland.bar.Payment.Dto.RevenueShareDTO;
import com.midland.bar.Payment.Dto.ShareRecipientDTO;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The month's share-out written as a payment schedule.
 *
 * It calculates nothing of its own. RevenueShareService already works out
 * what each person is owed, and the Payments screen already shows it - two
 * ways of arriving at the same figure would eventually disagree in front of
 * a bank teller. This reads that answer and lays it out to be printed.
 *
 * What it adds is the person-by-person view of that split, and the
 * difference between what the month earned somebody and what is left to
 * send them. Both are on the sheet: the first is what the month decided,
 * the second is what the bank is being asked to do about it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    /** Ordered the way the money splits, so the sheet reads top-down. */
    private static final List<String> ROLES = List.of("ROOT", "DIRECTOR", "STAFF");

    private final RevenueShareService revenueShareService;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    public Response<PayrollDTO> findPayroll(Integer year, Integer month) {
        log.info(LoggerUser.getEmail() + " is accessing the payroll");

        YearMonth period = (year == null || month == null) ? YearMonth.now() : YearMonth.of(year, month);

        List<PayrollLineDTO> lines = new ArrayList<>();

        for (String role : ROLES) {
            List<ShareRecipientDTO> recipients =
                    revenueShareService.findShareRecipients(role, period.getYear(), period.getMonthValue()).getData();
            if (recipients == null) {
                continue;
            }
            for (ShareRecipientDTO recipient : recipients) {
                // Everyone the month earned something goes on the sheet,
                // including those already settled. Listing only what is left
                // to send hid the very thing this page is for: with staff and
                // directors already paid, the month's split showed as a
                // single line and the breakdown was nowhere. Settled people
                // are shown with nothing left to send, which is an answer;
                // leaving them out was not.
                if (recipient.getAmount() <= 0) {
                    continue;
                }
                PayrollLineDTO line = new PayrollLineDTO();
                line.setUid(recipient.getUid());
                line.setRole(role);
                line.setName(recipient.getName());
                line.setEarned(recipient.getAmount());
                line.setPaid(recipient.getPaid());
                line.setToPay(recipient.getOutstanding());
                lines.add(line);
            }
        }

        attachContactDetails(lines);

        // Sorted within a role by what the month earned them, largest first.
        // Earned rather than outstanding, so the order does not rearrange
        // itself as people are paid.
        lines.sort((a, b) -> {
            int byRole = Integer.compare(ROLES.indexOf(a.getRole()), ROLES.indexOf(b.getRole()));
            return byRole != 0 ? byRole : Long.compare(b.getEarned(), a.getEarned());
        });

        RevenueShareDTO share = revenueShareService
                .findRevenueShare(period.getYear(), period.getMonthValue()).getData();

        PayrollDTO payroll = new PayrollDTO();
        payroll.setCompanyName(companyName());
        payroll.setYear(period.getYear());
        payroll.setMonth(period.getMonthValue());
        payroll.setGeneratedAt(LocalDateTime.now());
        payroll.setRevenue(share == null ? 0 : share.getRevenue());
        payroll.setShare(share);
        payroll.setLines(lines);
        payroll.setRecipients(lines.size());
        payroll.setTotalEarned(lines.stream().mapToLong(PayrollLineDTO::getEarned).sum());
        payroll.setTotalPaid(lines.stream().mapToLong(PayrollLineDTO::getPaid).sum());
        payroll.setTotalToPay(lines.stream().mapToLong(PayrollLineDTO::getToPay).sum());
        payroll.setSettled((int) lines.stream().filter(line -> line.getToPay() <= 0).count());
        payroll.setMissingPhone((int) lines.stream()
                .filter(line -> line.getPhone() == null || line.getPhone().isBlank())
                .count());
        // Counted only among people actually owed something: somebody
        // already settled has no missing detail worth chasing this month.
        // Either part missing makes the transfer un-instructable, so both
        // count the same way.
        payroll.setMissingAccount((int) lines.stream()
                .filter(line -> line.getToPay() > 0)
                .filter(line -> line.getAccountNumber() == null || line.getAccountNumber().isBlank()
                        || line.getBankName() == null || line.getBankName().isBlank()
                        || line.getAccountName() == null || line.getAccountName().isBlank())
                .count());

        return new Response<>(payroll);
    }

    /**
     * The platform's own branch - the one holding the ROOT code, which is
     * how the rest of the system already tells it from a customer's.
     */
    private String companyName() {
        try {
            Branch company = branchRepository.findByBranchCode("ROOT");
            return company == null ? null : company.getBranchName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The branch is an eager association, and some rows in this database
     * point at a branch that no longer exists - there is no foreign key on
     * the column to have stopped it. Reading it can therefore throw, and a
     * payroll that will not load is a worse answer than a blank cell.
     */
    private static String branchNameOf(User user) {
        try {
            return user.getBranch() == null ? null : user.getBranch().getBranchName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Phone numbers for everyone on the sheet, in one query rather than one
     * per line - a payroll is short, but this runs on a page that is opened
     * monthly by people who will not forgive a slow one.
     */
    private void attachContactDetails(List<PayrollLineDTO> lines) {
        if (lines.isEmpty()) {
            return;
        }
        List<String> uids = lines.stream().map(PayrollLineDTO::getUid).filter(java.util.Objects::nonNull).toList();
        Map<String, User> byUid = new HashMap<>();
        for (User user : userRepository.findAllById(uids)) {
            byUid.put(user.getUid(), user);
        }
        for (PayrollLineDTO line : lines) {
            User user = byUid.get(line.getUid());
            if (user != null) {
                line.setPhone(user.getPhone());
                line.setAccountNumber(user.getAccountNumber());
                line.setBankName(user.getBankName());
                line.setAccountName(user.getAccountName());
                line.setBranchName(branchNameOf(user));
            }
        }
    }
}
