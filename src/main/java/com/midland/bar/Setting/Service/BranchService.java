package com.midland.bar.Setting.Service;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Dto.ExpiringBranchDTO;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.midland.bar.Setting.Dto.BranchDTO;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Model.BranchCodeHelper;
import com.midland.bar.Setting.Model.PlatformSetting;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Setting.Projection.BranchProjection;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import com.midland.bar.Utils.PageableParam;
import com.midland.bar.Utils.Responses.ResponsePage;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Log
public class BranchService {
    private final BranchRepository branchRepository;
    private final BranchCodeHelper branchCodeHelper;
    private final UserRepository userRepository;
    private final PlatformSettingService platformSettingService;
    public Response<Branch> saveBranch(BranchDTO branchDTO) {

        log.info(LoggerUser.getEmail() + " is saving Branch");

        if (branchDTO == null) {
            return new Response<>("Provide Branch Details");
        }

        if (branchDTO.getBranchName() == null ||
                branchDTO.getBranchName().isBlank()) {
            return new Response<>("Provide Branch Name");
        }

        if (branchDTO.getRegion() == null ||
                branchDTO.getRegion().isBlank()) {
            return new Response<>("Provide Region");
        }

        try {

            Branch branch;

            /*
             * ==========================
             * UPDATE
             * ==========================
             */
            if (branchDTO.getUid() != null) {

                Optional<Branch> optionalBranch =
                        branchRepository.findById(branchDTO.getUid());

                if (optionalBranch.isEmpty()) {
                    return new Response<>("Branch Not Found");
                }

                if (!canTouch(optionalBranch.get())) {
                    return new Response<>("Branch Not Found");
                }

                branch = optionalBranch.get();

            }

            /*
             * ==========================
             * CREATE
             * ==========================
             */
            else {

                branch = new Branch();
                branch.setCreatedBy(LoggerUser.getUser().getUid());

                // A new branch starts on the trial rather than with no end
                // date at all. An open-ended closeSubscription is treated as
                // unrestricted by the login gate, so without this a branch
                // nobody got round to putting on a plan used the system free
                // for good.
                PlatformSetting platformSetting = platformSettingService.current();
                Integer trialDays = platformSetting.getTrialDays();
                if (trialDays != null && trialDays > 0) {
                    branch.setOpenSubscription(LocalDate.now());
                    branch.setCloseSubscription(LocalDate.now().plusDays(trialDays));
                    branch.setSubscriptionStatus("TRIAL");
                }
                // Pre-filled so an admin is not typing the same plan onto
                // every branch; still editable per branch afterwards.
                if (platformSetting.getDefaultSubscriptionAmount() != null
                        && platformSetting.getDefaultSubscriptionAmount() > 0) {
                    branch.setSubscriptionAmount(platformSetting.getDefaultSubscriptionAmount());
                }
                branch.setSubscriptionDays(platformSetting.getDefaultSubscriptionDays());

                Integer lastSequence = branchRepository.findLastBranchSequenceByRegion(branchDTO.getRegion());
                long nextNumber = (lastSequence == null ? 0 : lastSequence) + 1;
                String branchCode = branchCodeHelper.generateBranchCode(branchDTO.getRegion(), nextNumber);
                branch.setBranchCode(branchCode);
            }


            /*
             * ==========================
             * SET BRANCH DETAILS
             * ==========================
             */

            branch.setBranchName(
                    branchDTO.getBranchName()
            );

            branch.setBranchCategory(
                    branchDTO.getBranchCategory()
            );



            branch.setDescription(
                    branchDTO.getDescription()
            );

            branch.setAddress(
                    branchDTO.getAddress()
            );

            branch.setPhone(
                    branchDTO.getPhone()
            );

            branch.setRegion(
                    branchDTO.getRegion()
            );

            // Blocking and unblocking go through blockBranch only: an edit of
            // the branch's details must never lift (or set) a block.
            if (!branch.isBlocked() && !com.midland.bar.Setting.Model.Branch.BLOCKED.equalsIgnoreCase(branchDTO.getStatus()))
                branch.setStatus(branchDTO.getStatus());


            /*
             * ==========================
             * SAVE
             * ==========================
             */

            Branch savedBranch =
                    branchRepository.save(branch);

            return new Response<>(savedBranch);

        } catch (NumberFormatException e) {

            e.printStackTrace();
            return new Response<>(
                    "Invalid Branch Code Format"
            );

        } catch (Exception e) {


            return new Response<>(
                    "Error in saving Branch"
            );
        }
    }




    public Response<Branch> findBranchByUID(String branchUID){
        log.info(LoggerUser.getEmail() + " is Accessing Branch");
        if(branchUID == null)
            return new Response<>("Branch UID is required");

        // VIEW_BRANCH is granted to every role (CEO/MANAGER/CASHIER) so the
        // header can show their own branch's name/subscription. Without this
        // check, that same permission would let them pass any branchUID and
        // read any OTHER company's branch too. Only ROOT can look up a
        // branch that isn't their own; findBranchList/findBranchPage (the
        // real cross-branch admin views) are separately gated behind
        // VIEW_ALL_BRANCHES, which non-ROOT roles never get.
        boolean isRoot = Boolean.TRUE.equals(LoggerUser.getUser().getIsRoot());
        if (!isRoot && !branchUID.equals(LoggerUser.getBranchUID())) {
            return new Response<>("Branch Not Found");
        }

        Optional<Branch> optionalBranch = branchRepository.findById(branchUID);
        return optionalBranch.map(Response::new).orElseGet(() -> new Response<>("Branch Not Found"));
    }
    // ROOT and DIRECTOR see every branch; anyone else (STAFF) only sees the
    // ones they registered themselves - one staffer's companies stay hidden
    // from another's.
    public boolean seesAllBranches() {
        User user = LoggerUser.getUser();
        List<String> roleCodes = user.getRoles() == null
                ? List.of()
                : user.getRoles().stream().map(Role::getCode).toList();
        return Boolean.TRUE.equals(user.getIsRoot())
                || roleCodes.contains("ROOT")
                || roleCodes.contains("DIRECTOR");
    }

    public ResponsePage<Branch> findBranchPage(PageableParam pageableParam){
        log.info(LoggerUser.getEmail() + "is accessing Branch");
        Pageable pageable = PageRequest.of(
                pageableParam.getPage() == null ? 0 : pageableParam.getPage(),
                pageableParam.getSize() == null || pageableParam.getSize() <= 0 ? 10 : pageableParam.getSize()
        );
        // Searching must never widen what a STAFF member can see, so the
        // creator narrowing is applied to the search query too rather than
        // being swapped out for it.
        String createdBy = seesAllBranches() ? null : LoggerUser.getUser().getUid();
        String search = searchTerm(pageableParam);
        return new ResponsePage<>(branchRepository.searchBranchPage(search, createdBy, pageable));
    }

    /** Null when nothing was typed, so the query skips the LIKE branches entirely. */
    private static String searchTerm(PageableParam pageableParam) {
        String raw = pageableParam.getSearchParam();
        return raw == null || raw.isBlank() ? null : raw.trim().toLowerCase();
    }
    public Response<Branch> deleteBranch(String branchUID){
        log.info(LoggerUser.getEmail() + "is deleting Branch");
        Optional<Branch> optionalBranch = branchRepository.findById(branchUID);
        if(optionalBranch.isEmpty())
            return new Response<>("Branch Not Found");
        if (!canTouch(optionalBranch.get()))
            return new Response<>("Branch Not Found");
        branchRepository.delete(optionalBranch.get());
        return new Response<>(optionalBranch.get());
    }
    public ResponseList<BranchProjection> findBranchList(){
        log.info(LoggerUser.getEmail() + "is accessing Branch");
        if (!seesAllBranches()) {
            return new ResponseList<>(branchRepository.findBranchListByCreator(LoggerUser.getUser().getUid()));
        }
        return new ResponseList<>(branchRepository.findBranchList());
    }

    // A branch the caller is allowed to act on: their own creation, unless
    // they see everything anyway.
    public boolean canTouch(Branch branch) {
        return seesAllBranches()
                || LoggerUser.getUser().getUid().equals(branch.getCreatedBy());
    }

    public ResponseList<User> findAllUsersWithBranchAndRoles(String branchUID){
        log.info(LoggerUser.getEmail() + "is accessing User and Branch");
        if(branchUID==null)
            return new ResponseList<>("Provide Branch REF");
        Optional<Branch> optionalBranch = branchRepository.findById(branchUID);
        if(optionalBranch.isEmpty() || !canTouch(optionalBranch.get()))
            return new ResponseList<>("Branch Not Found");
        return new ResponseList<>(userRepository.findAllUsersWithBranchAndRoles(branchUID));
    }

    /**
     * Branches running out within the next few days, and any already past
     * their date. Sorted soonest first, so the one to call about is the one
     * at the top.
     *
     * Scoped the same way the branch list is: a STAFF member sees only the
     * branches they registered.
     */
    public ResponseList<ExpiringBranchDTO> findExpiringBranches(Integer days) {
        int window = days == null || days < 0 ? 7 : Math.min(days, 365);
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(window);

        // Admin's Expiring view (VIEW_EXPIRING_BRANCHES) is platform-wide, like ROOT/DIRECTOR.
        boolean platformWide = seesAllBranches() || org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "VIEW_EXPIRING_BRANCHES".equals(a.getAuthority()));
        String createdBy = platformWide ? null : LoggerUser.getUser().getUid();
        List<Branch> branches = branchRepository.findExpiringBranches(horizon, createdBy);

        // Names of whoever registered them, in one query rather than one per row.
        Set<String> creatorUids = branches.stream()
                .map(Branch::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> creatorNames = new HashMap<>();
        if (!creatorUids.isEmpty()) {
            for (User creator : userRepository.findAllById(creatorUids)) {
                String name = String.format("%s %s",
                        creator.getFirstName() == null ? "" : creator.getFirstName(),
                        creator.getLastName() == null ? "" : creator.getLastName()).trim();
                creatorNames.put(creator.getUid(), name.isBlank() ? creator.getUsername() : name);
            }
        }

        List<ExpiringBranchDTO> rows = new ArrayList<>();
        for (Branch branch : branches) {
            ExpiringBranchDTO row = new ExpiringBranchDTO();
            row.setUid(branch.getUid());
            row.setBranchName(branch.getBranchName());
            row.setBranchCode(branch.getBranchCode());
            row.setRegion(branch.getRegion());
            row.setPhone(branch.getPhone());
            row.setCloseSubscription(branch.getCloseSubscription());
            // Negative once past: "3 days ago" rather than "-3 days left".
            row.setDaysLeft(ChronoUnit.DAYS.between(today, branch.getCloseSubscription()));
            row.setSubscriptionStatus(branch.getSubscriptionStatus());
            row.setSubscriptionAmount(branch.getSubscriptionAmount());
            row.setRegisteredBy(creatorNames.get(branch.getCreatedBy()));
            row.setLastPaymentFailure(branch.getLastPaymentFailure());
            rows.add(row);
        }
        return new ResponseList<>(rows);
    }


    /**
     * Blocks (or unblocks) a branch: while blocked nobody in it can sign in -
     * users or staff codes - and anyone signed in is turned away at their
     * next request. Its data stays; unblocking puts everything back. Never
     * the main (ROOT) branch.
     */
    @org.springframework.transaction.annotation.Transactional
    public com.midland.bar.Utils.Responses.Response<com.midland.bar.Setting.Model.Branch> blockBranch(String branchUID, boolean blocked, String reason) {
        com.midland.bar.Setting.Model.Branch branch = branchRepository.findById(branchUID).orElse(null);
        if (branch == null)
            return new com.midland.bar.Utils.Responses.Response<>("BRANCH_NOT_FOUND");
        if ("ROOT".equalsIgnoreCase(branch.getBranchCode()))
            return new com.midland.bar.Utils.Responses.Response<>("ROOT_BRANCH");
        String why = reason == null ? "" : reason.trim();
        if (blocked) {
            branch.setStatus(com.midland.bar.Setting.Model.Branch.BLOCKED);
            branch.setBlockedReason(why.isEmpty() ? null : (why.length() > 300 ? why.substring(0, 300) : why));
            branch.setBlockedAt(java.time.LocalDateTime.now());
            branch.setBlockedBy(com.midland.bar.Config.Security.LoggerUser.getEmail());
        } else {
            branch.setStatus("ACTIVE");
            branch.setBlockedReason(null);
            branch.setBlockedAt(null);
            branch.setBlockedBy(null);
        }
        log.info(com.midland.bar.Config.Security.LoggerUser.getEmail() + (blocked ? " blocked" : " unblocked") + " branch " + branch.getBranchCode());
        return new com.midland.bar.Utils.Responses.Response<>(branchRepository.save(branch));
    }

}
