package com.midland.bar.Uaa.Service;

import com.midland.bar.Config.Security.AuthChecker;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Setting.Repository.RoleRepository;
import com.midland.bar.Uaa.Dto.*;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Projection.UserProjection;
import com.midland.bar.Notification.Sms.Service.SmsService;
import com.midland.bar.Uaa.Support.ActivationCode;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Utils.Exceptions.BusinessException;
import com.midland.bar.Utils.PageableParam;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponsePage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final SmsService smsService;
    private final AuthChecker authChecker;

    // Matches the minimum the change-password dialog enforces.
    private static final int MIN_PASSWORD_LENGTH = 6;


    public Response<SavedUserDTO> saveUser(UserDTO userDTO){
        log.info(LoggerUser.getEmail() + "Is Saving User");
        if(userDTO == null)
            return new Response<>("Provide User Data");
        User user=null;
        // Credentials are only ever set when the account is created. They
        // used to be reassigned on every save, so editing a phone number
        // reset that person's password - and would now have texted it to them.
        boolean isNew = userDTO.getUid() == null;
        if(userDTO.getUid() !=null){
            Optional<User> optionalUser = userRepository.findById(userDTO.getUid());
            if(optionalUser.isEmpty())
                return new Response<>("User Not Found");
            user=optionalUser.get();
            user.update();
        }else {
            user = new User();
        }
        user.setGender(userDTO.getGender());
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        user.setLastName(userDTO.getLastName());
        user.setMiddleName(userDTO.getMiddleName());
        user.setFirstName(userDTO.getFirstName());
        user.setDateOfBirth(userDTO.getDob());
        user.setAddress(userDTO.getAddress());
        if (userDTO.getAccountNumber() != null) {
            user.setAccountNumber(userDTO.getAccountNumber().trim());
        }
        if (userDTO.getBankName() != null) {
            user.setBankName(userDTO.getBankName().trim());
        }
        if (userDTO.getAccountName() != null) {
            user.setAccountName(userDTO.getAccountName().trim());
        }
        Branch branch= null;
        if(userDTO.getBranch() !=null){
            Optional<Branch> optionalBranch = branchRepository.findById(userDTO.getBranch());
            if(optionalBranch.isEmpty())
                return new Response<>("Branch Not Found");
            branch = optionalBranch.get();
        }
        user.setBranch(branch);

        assert branch != null;

        // Held in plain only for the length of this call, to text it to them.
        // What is stored is the hash, as before.
        String activationCode = null;
        if (isNew) {
            user.setUsername(uniqueUsername(userDTO.getFirstName()));
            // Was the surname, which is not a secret. Now a one-time code that
            // is only good for setting a real password - see mustChangePassword.
            activationCode = issueActivationCode(user);

            // The role is chosen here rather than in a second, separate step.
            // Without one the account cannot open anything, so someone
            // registered at the counter could not start work until an admin
            // went back and assigned it.
            if (userDTO.getRole() != null && !userDTO.getRole().isBlank()) {
                Optional<Role> optionalRole = roleRepository.findById(userDTO.getRole());
                if (optionalRole.isEmpty())
                    return new Response<>("Role Not Found");
                Role role = optionalRole.get();
                // STAFF are only shown the branch-operational roles, so they
                // must not be able to grant one they cannot see. Same rule as
                // assignOrUnAssignUserRole.
                if (!seesAllRoles() && !STAFF_VISIBLE_ROLE_CODES.contains(role.getCode()))
                    return new Response<>("Role Not Allowed");
                user.setRoles(new ArrayList<>(List.of(role)));
            }
        }

        try{
            User saved = userRepository.save(user);

            if (isNew) {
                // After the save, and never blocking it: an account that
                // exists but whose text failed is recoverable - the code can
                // be re-sent; one that was not created because a text failed
                // is not.
                smsService.sendActivationCode(
                        saved.getUid(),
                        saved.getPhone(),
                        saved.getUsername(),
                        activationCode
                );
            }

            return new Response<>(new SavedUserDTO(
                    saved,
                    activationCode,
                    isNew ? ActivationCode.VALID_HOURS : null
            ));
        }catch (Exception e){
            e.printStackTrace();
            return new Response<>("Error in saving user");
        }
    }
    /**
     * The sign-in name, which has to be one person's and nobody else's.
     *
     * It used to be first name and year with nothing checking it, so two
     * people called Hela registered in the same year both became Hela@2026 -
     * and login, which looks a person up by this name, then found two rows
     * and threw. Neither of them could sign in.
     *
     * A second Hela is now Hela2@2026. The counter reads as an ordinal to
     * anyone who sees it, which a random suffix would not.
     */
    private String uniqueUsername(String firstName) {
        int year = LocalDate.now().getYear();
        String base = firstName + "@" + year;
        if (userRepository.countUsername(base) == 0) {
            return base;
        }
        for (int n = 2; n <= MAX_USERNAME_ATTEMPTS; n++) {
            String candidate = firstName + n + "@" + year;
            if (userRepository.countUsername(candidate) == 0) {
                return candidate;
            }
        }
        // A thousand people with one first name in one year is not a real
        // salon; falling back to the uid keeps the account creatable rather
        // than failing on a number nobody will reach.
        return firstName + "-" + java.util.UUID.randomUUID().toString().substring(0, 6) + "@" + year;
    }

    private static final int MAX_USERNAME_ATTEMPTS = 1000;

    /**
     * Puts a fresh code on the account and returns it in plain, once, for the
     * caller to text. Only the hash is kept, exactly as for a password.
     */
    private String issueActivationCode(User user) {
        String code = ActivationCode.generate();
        user.setPassword(passwordEncoder.encode(code));
        user.setMustChangePassword(true);
        user.setActivationExpiresAt(LocalDateTime.now().plusHours(ActivationCode.VALID_HOURS));
        user.setActivationAttempts(0);
        return code;
    }

    /**
     * A code that expired, was used up on wrong guesses, or never arrived.
     *
     * Deliberately refuses an account that has already set its own password:
     * that would be a password reset, which is a different thing with
     * different risks, not something an admin should be able to do from the
     * user list by accident.
     */
    public Response<String> resendActivationCode(String userUID) {
        log.info(LoggerUser.getEmail() + " is resending an activation code");
        if (userUID == null)
            return new Response<>("Provide user ref UID");
        Optional<User> optionalUser = userRepository.findById(userUID);
        if (optionalUser.isEmpty())
            return new Response<>("User Not Found");
        User user = optionalUser.get();
        if (!Boolean.TRUE.equals(user.getMustChangePassword()))
            return data("ALREADY_ACTIVATED");
        if (user.getPhone() == null || user.getPhone().isBlank())
            return data("NO_PHONE");

        String code = issueActivationCode(user);
        User saved = userRepository.save(user);
        smsService.sendActivationCode(saved.getUid(), saved.getPhone(), saved.getUsername(), code);
        return data("SENT");
    }

    /**
     * A Response whose payload really is the string.
     *
     * Response has both Response(T data) and Response(String message), and
     * for a Response<String> both match a string argument - Java picks the
     * message one. So `new Response<>("SENT")` quietly returned an empty
     * body with a message, and every caller reading res.data saw nothing.
     * Building it explicitly is the only way to say which was meant.
     */
    private static Response<String> data(String value) {
        Response<String> response = new Response<>();
        response.setData(value);
        response.setStatus(com.midland.bar.Utils.Responses.ResponseStatus.SUCCESS);
        return response;
    }

    /** The caller's own, so a profile can show them without VIEW_USER. */
    public Response<BankDetailsDTO> findMyBankDetails() {
        User caller = LoggerUser.getUser();
        Optional<User> user = userRepository.findById(caller.getUid());
        if (user.isEmpty()) {
            return new Response<>("User Not Found");
        }
        return new Response<>(new BankDetailsDTO(
                user.get().getAccountNumber(),
                user.get().getBankName(),
                user.get().getAccountName()
        ));
    }

    /**
     * Sets where somebody's payroll money is sent - account number and bank
     * together.
     *
     * Anyone may set their own; changing somebody else's needs SAVE_USER.
     * It is a narrow endpoint rather than part of saveUser because the
     * screens that call it hold one field, and a full save from a form that
     * does not carry the rest would quietly blank it.
     */
    public Response<String> saveBankDetails(String userUID, BankDetailsDTO details) {
        log.info(LoggerUser.getEmail() + " is setting bank details");

        User caller = LoggerUser.getUser();
        String target = userUID == null || userUID.isBlank() ? caller.getUid() : userUID;

        if (!target.equals(caller.getUid()) && !authChecker.hasPermissionOrRoot("SAVE_USER")) {
            return data("NOT_ALLOWED");
        }

        Optional<User> optionalUser = userRepository.findById(target);
        if (optionalUser.isEmpty()) {
            return new Response<>("User Not Found");
        }

        // Clearing them is a real choice - somebody who changed bank and has
        // not got the new number yet should not be left with the old one.
        String number = blankToNull(details == null ? null : details.getAccountNumber());
        String bank = blankToNull(details == null ? null : details.getBankName());
        String accountName = blankToNull(details == null ? null : details.getAccountName());

        if (number != null && number.length() > 50) {
            return data("TOO_LONG");
        }
        if (bank != null && bank.length() > 100) {
            return data("TOO_LONG");
        }
        if (accountName != null && accountName.length() > 150) {
            return data("TOO_LONG");
        }
        // Any one of the three on its own cannot be paid to, so a partial
        // record is not a saveable state - better refused here than found
        // out at the bank counter.
        boolean anySet = number != null || bank != null || accountName != null;
        boolean allSet = number != null && bank != null && accountName != null;
        if (anySet && !allSet) {
            return data("INCOMPLETE");
        }

        User user = optionalUser.get();
        user.setAccountNumber(number);
        user.setBankName(bank);
        user.setAccountName(accountName);
        userRepository.save(user);
        return data("SAVED");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public Response<User> deleteUser(String uid) {
        User user = userRepository.findById(uid).orElseThrow(() -> new BusinessException("User not found"));
        userRepository.deleteUserRoles(uid);
        userRepository.delete(user);
        return new Response<>(user);
    }

    public Response<UserProjection> findUserByUID(String userUID){
        log.info(LoggerUser.getEmail() + "is Accessing User");
        if(userUID == null)
            return new Response<>("Provide user ref UID");
        List<UserProjection> rows = userRepository.findUserWithRoles(userUID);
        return rows.isEmpty() ? new Response<>("User Not Found") : new Response<>(rows.get(0));
    }
    public ResponsePage<User> findUserPAGE(int page, int size){
        log.info(LoggerUser.getEmail() + "is Accessing User Page");
        Pageable pageable = PageRequest.of(page, size);
        return new ResponsePage<>(userRepository.findUserPage(pageable));
    }

    // Sort fields tunazokubali kutoka kwa mteja, kila moja na path yake kwenye query ya findUsers.
    private static final Map<String, String> USER_SORT_FIELDS = Map.ofEntries(
            Map.entry("username", "u.username"),
            Map.entry("firstName", "u.firstName"),
            Map.entry("middleName", "u.middleName"),
            Map.entry("lastName", "u.lastName"),
            Map.entry("email", "u.email"),
            Map.entry("phone", "u.phone"),
            Map.entry("gender", "u.gender"),
            Map.entry("dateOfBirth", "u.dateOfBirth"),
            Map.entry("address", "u.address"),
            Map.entry("isActive", "u.isActive"),
            Map.entry("isBlocked", "u.isBlocked"),
            Map.entry("isRoot", "u.isRoot"),
            Map.entry("createdAt", "u.createdAt"),
            Map.entry("updatedAt", "u.updatedAt"),
            Map.entry("branchName", "b.branchName"),
            Map.entry("branchCode", "b.branchCode")
    );

    public ResponsePage<java.util.Map<String, Object>> findUsers(PageableParam pageableParam){
        log.info(LoggerUser.getEmail() + "is Accessing Users");
        // field isiyojulikana ingeangusha query, kwa hiyo turudi kwenye createdAt
        pageableParam.setSortBy(USER_SORT_FIELDS.getOrDefault(pageableParam.getSortBy(), "u.createdAt"));
        // Null rather than "" so the query skips the LIKE branches entirely
        // when the search box is empty.
        String search = pageableParam.getSearchParam() == null || pageableParam.getSearchParam().isBlank()
                ? null
                : pageableParam.getSearchParam().trim().toLowerCase();
        org.springframework.data.domain.Page<UserProjection> page = userRepository.findUsers(search, pageableParam.pageable(true));
        // Each row also says what the user is - their role names - fetched for
        // the whole page in one query.
        java.util.Map<String, java.util.List<String>> roles = new java.util.HashMap<>();
        java.util.List<String> uids = page.getContent().stream().map(UserProjection::getUid).toList();
        if (!uids.isEmpty()) {
            for (Object[] row : userRepository.findRoleNamesOf(uids))
                roles.computeIfAbsent((String) row[0], k -> new java.util.ArrayList<>()).add((String) row[1]);
        }
        return new ResponsePage<>(page.map(p -> {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> row = USER_ROWS.convertValue(p, java.util.LinkedHashMap.class);
            row.put("roles", roles.getOrDefault(p.getUid(), java.util.List.of()));
            return row;
        }));
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper USER_ROWS = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public Response<User> assignOrUnAssignUserRole(AssignUserRoleDTO assignUserRoleDTO) {
        log.info(LoggerUser.getEmail() + " is accessing User");
        if (assignUserRoleDTO == null)
            return new Response<>("Provide Data for User");
        Optional<User> optionalUser = userRepository.findById(assignUserRoleDTO.getUserUID());
        if (optionalUser.isEmpty())
            return new Response<>("User Not Found");
        User user = optionalUser.get();
        List<Role> updatedRoles = new ArrayList<>(roleRepository.findAllById(assignUserRoleDTO.getRoleUIDS()));
        if (!seesAllRoles()) {
            // The caller (e.g. STAFF) is only shown CEO/MANAGER/CASHIER, so
            // they can neither hand out a role they can't see nor strip one
            // the user already holds - those are kept as they were.
            updatedRoles.removeIf(role -> !STAFF_VISIBLE_ROLE_CODES.contains(role.getCode()));
            if (user.getRoles() != null) {
                user.getRoles().stream()
                        .filter(role -> !STAFF_VISIBLE_ROLE_CODES.contains(role.getCode()))
                        .forEach(updatedRoles::add);
            }
        }
        user.setRoles(updatedRoles);
        return new Response<>(userRepository.save(user));
    }
    // Mirrors RoleService: ROOT and DIRECTOR manage every role, everyone
    // else only ever sees/handles the branch-operational ones.
    private static final List<String> STAFF_VISIBLE_ROLE_CODES = List.of("CEO", "MANAGER", "SUPERVISOR", "CASHIER");

    private boolean seesAllRoles() {
        User loggedUser = LoggerUser.getUser();
        List<String> roleCodes = loggedUser.getRoles() == null
                ? List.of()
                : loggedUser.getRoles().stream().map(Role::getCode).toList();
        return Boolean.TRUE.equals(loggedUser.getIsRoot())
                || roleCodes.contains("ROOT")
                || roleCodes.contains("DIRECTOR");
    }

    /**
     * Takes somebody's way into the system away, or gives it back.
     *
     * Not a delete. A person's uid is what branches.created_by, the
     * commission payouts and the subscription payments all point at - which
     * is to say it is what the STAFF commission report is built from.
     * Removing the row would orphan every one of those and quietly change
     * what people are owed. Blocking stops the login and leaves the history
     * standing, which is what "they no longer work here" actually means.
     *
     * The parameter used to be called `enable` while setting isBlocked, so
     * true meant the opposite of what it read as. It says what it does now.
     */
    public Response<String> setAccountBlocked(String userUID, Boolean blocked){
        log.info(LoggerUser.getEmail() + " is changing account access");
        if (userUID == null || blocked == null) {
            return blockCode("MISSING_DATA");
        }

        // Blocking yourself locks you out of the screen you did it from, and
        // possibly out of the only account that could undo it.
        if (userUID.equals(LoggerUser.getUser().getUid())) {
            return blockCode("NOT_YOURSELF");
        }

        Optional<User> optionalUser = userRepository.findById(userUID);
        if (optionalUser.isEmpty()) {
            return blockCode("NOT_FOUND");
        }

        User user = optionalUser.get();

        // The platform's own root account is how everything is put right when
        // something goes wrong; it is not something to switch off from a
        // branch screen.
        if (Boolean.TRUE.equals(blocked) && Boolean.TRUE.equals(user.getIsRoot())) {
            return blockCode("NOT_ROOT");
        }

        user.setIsBlocked(blocked);
        userRepository.save(user);
        return blockCode(blocked ? "BLOCKED" : "RESTORED");
    }

    /** See the note on data() - Response<String> needs it said explicitly. */
    private static Response<String> blockCode(String value) {
        Response<String> response = new Response<>();
        response.setData(value);
        response.setStatus(com.midland.bar.Utils.Responses.ResponseStatus.SUCCESS);
        return response;
    }
    public Response<User> changePassword(DataDTO dataDTO) {
        log.info(LoggerUser.getEmail() + " is Changing Password");
        if (dataDTO == null) {
            return new Response<>("Provide data to change password");
        }
        String username = LoggerUser.getUser().getUsername();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return new Response<>("User not found");
        }
        if (user.getIsBlocked()) {
            return new Response<>("You cannot change password, account is blocked");
        }
        if (!passwordEncoder.matches(dataDTO.getOldPassword(), user.getPassword())) {
            return new Response<>("Invalid password provided");
        }
        if (!dataDTO.getNewPassword().equals(dataDTO.getConfirmPassword())) {
            return new Response<>("Passwords do not match");
        }
        // The dialog checks this too, but the dialog is not the boundary -
        // this endpoint can be called directly.
        if (dataDTO.getNewPassword() == null || dataDTO.getNewPassword().length() < MIN_PASSWORD_LENGTH) {
            return new Response<>("Password is too short");
        }
        user.setPassword(passwordEncoder.encode(dataDTO.getNewPassword()));
        // Whatever they chose - even the password we texted them - they chose
        // it, so the account stops being one that can only change its password.
        user.setMustChangePassword(false);
        // The code is spent; nothing about it should outlive it.
        user.setActivationExpiresAt(null);
        user.setActivationAttempts(0);
        return new Response<>(userRepository.save(user));
    }




    // ============================================================
    // BRANCHES A USER MAY WORK IN
    // ============================================================

    public Response<java.util.Map<String, Object>> findUserBranches(String userUid) {
        java.util.Optional<User> found = userRepository.findById(userUid);
        if (found.isEmpty())
            return new Response<>("User Not Found");
        return new Response<>(branchesOf(found.get()));
    }

    /**
     * Replaces the user's other branches. Their home branch always stays -
     * it is where they were made - and is left out of the list if sent.
     */
    @org.springframework.transaction.annotation.Transactional
    public Response<java.util.Map<String, Object>> saveUserBranches(com.midland.bar.Uaa.Dto.UserBranchesDTO dto) {
        if (dto == null || dto.getUserUID() == null)
            return new Response<>("Choose the user");
        java.util.Optional<User> found = userRepository.findById(dto.getUserUID());
        if (found.isEmpty())
            return new Response<>("User Not Found");
        User user = found.get();
        if (Boolean.TRUE.equals(user.getIsRoot()))
            return new Response<>("A root user already reaches every branch");
        java.util.Set<Branch> extra = new java.util.HashSet<>();
        for (String uid : dto.getBranchUIDs() == null ? java.util.List.<String>of() : dto.getBranchUIDs()) {
            if (uid == null || (user.getHomeBranch() != null && uid.equals(user.getHomeBranch().getUid())))
                continue;
            Branch b = branchRepository.findById(uid).orElse(null);
            if (b == null || Boolean.FALSE.equals(b.getIsActive()))
                return new Response<>("Branch Not Found");
            if ("ROOT".equalsIgnoreCase(b.getBranchCode()))
                return new Response<>("The main office is not a working branch");
            extra.add(b);
        }
        user.getExtraBranches().clear();
        user.getExtraBranches().addAll(extra);
        userRepository.save(user);
        log.info(LoggerUser.getEmail() + " set " + user.getUsername() + " to work in " + (extra.size() + 1) + " branch(es)");
        return new Response<>(branchesOf(user));
    }

    private java.util.Map<String, Object> branchesOf(User user) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("userUID", user.getUid());
        out.put("username", user.getUsername());
        Branch home = user.getHomeBranch();
        out.put("home", home == null ? null : branchRef(home));
        out.put("extra", user.getWorkBranches().stream().filter(b -> b != home).map(this::branchRef).toList());
        return out;
    }

    private java.util.Map<String, Object> branchRef(Branch b) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("uid", b.getUid());
        m.put("branchName", b.getBranchName());
        m.put("branchCode", b.getBranchCode());
        return m;
    }
}
