package com.midland.bar.Config.Security;

import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Uaa.Model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which branches a login may work in - chosen at login, carried in the token.
 *
 *  - ROOT and DIRECTOR (main office): every branch, to give support anywhere.
 *  - STAFF (registers branches): MIDLAND plus the branches they registered,
 *    and in a customer's branch they may look but not change anything.
 *  - Everyone else: their home branch plus any the main office gave them.
 *
 * MIDLAND - the home branch of main-office logins - is always listed first,
 * so it is the default.
 */
@Component
@RequiredArgsConstructor
public class BranchAccess {

    private static final Set<String> EVERY_BRANCH = Set.of("ROOT", "DIRECTOR");
    private static final Set<String> MAIN_OFFICE = Set.of("ROOT", "DIRECTOR", "STAFF");

    private final BranchRepository branchRepository;

    public List<Branch> allowed(User user) {
        Branch home = user.getHomeBranch();
        Map<String, Branch> out = new LinkedHashMap<>();
        if (home != null)
            out.put(home.getUid(), home);
        List<Branch> others = new ArrayList<>();
        if (seesEveryBranch(user)) {
            others.addAll(branchRepository.findAll());
        } else {
            others.addAll(user.getWorkBranches());
            if (hasRole(user, "STAFF"))
                others.addAll(branchRepository.findAllByCreatedBy(user.getUid()));
        }
        others.stream()
                .filter(b -> b != null && !Boolean.FALSE.equals(b.getIsActive()))
                .sorted(Comparator.comparing(b -> b.getBranchName() == null ? "" : b.getBranchName().toLowerCase()))
                .forEach(b -> out.putIfAbsent(b.getUid(), b));
        return new ArrayList<>(out.values());
    }

    /** The branch the token names, if this login may still work there. */
    public Optional<Branch> find(User user, String branchUid) {
        if (branchUid == null)
            return Optional.empty();
        if (user.getHomeBranch() != null && branchUid.equals(user.getHomeBranch().getUid()))
            return Optional.of(user.getHomeBranch());
        if (seesEveryBranch(user))
            return branchRepository.findById(branchUid).filter(b -> !Boolean.FALSE.equals(b.getIsActive()));
        return allowed(user).stream().filter(b -> branchUid.equals(b.getUid())).findFirst();
    }

    /** Main office: never stopped by a customer branch's lapsed subscription - they come to help. */
    public boolean isMainOffice(User user) {
        return Boolean.TRUE.equals(user.getIsRoot()) || MAIN_OFFICE.stream().anyMatch(r -> hasRole(user, r));
    }

    /**
     * STAFF working in a branch that is not their own: they look, they don't
     * touch - the token carries only VIEW_* permissions. ROOT and DIRECTOR are
     * not limited this way.
     */
    public boolean isSupportViewOnly(User user) {
        if (seesEveryBranch(user) || !hasRole(user, "STAFF"))
            return false;
        Branch home = user.getHomeBranch();
        Branch working = user.getBranch();
        return home != null && working != null && !home.getUid().equals(working.getUid());
    }

    public boolean seesEveryBranch(User user) {
        return Boolean.TRUE.equals(user.getIsRoot()) || EVERY_BRANCH.stream().anyMatch(r -> hasRole(user, r));
    }

    private static boolean hasRole(User user, String code) {
        return user.getRoles() != null && user.getRoles().stream().map(Role::getCode).anyMatch(code::equals);
    }
}
