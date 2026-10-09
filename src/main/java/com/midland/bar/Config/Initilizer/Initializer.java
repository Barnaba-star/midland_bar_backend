package com.midland.bar.Config.Initilizer;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Setting.Repository.RoleRepository;
import com.midland.bar.Setting.Model.BranchCodeHelper;
import com.midland.bar.Setting.Service.RegionService;
import com.midland.bar.Setting.Service.PlatformSettingService;
import com.midland.bar.Uaa.Model.Permission;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.PermissionRepository;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


@Component
@RequiredArgsConstructor
public class Initializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Initializer.class);
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final BranchRepository branchRepository;
    private final PlatformSettingService platformSettingService;
    private final RegionService regionService;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        seedPermissions();
        seedSuperRole();
        seedStandardRoles();
        // The branch first: the super user is put in it, and a user with no
        // branch cannot be issued a token - which meant a fresh install could
        // not log in at all, the one account it has having been created a
        // moment before the branch it belongs to.
        seedRootBranch();
        seedSuperUser();
        platformSettingService.seedIfMissing();
        regionService.seedIfMissing(BranchCodeHelper.SEED_REGION_CODES);
    }

    public void seedPermissions() throws Exception {
        log.info("************************Seed Permissions******************************");
        InputStream inputStream = null;
        try{
            inputStream = new ClassPathResource("seed/Permission.csv").getInputStream();

        }catch (IOException e){
            e.printStackTrace();
            return;
        }
        CsvParserSettings csvParserSettings = new CsvParserSettings();
        csvParserSettings.setHeaderExtractionEnabled(true);
        CsvParser parser = new CsvParser(csvParserSettings);
        List<Record> recordList = parser.parseAllRecords(inputStream);
        for(Record record:recordList){
            if(record.getString("name") != null){
                Optional<Permission> permission = permissionRepository.findByName(record.getString("name"));
                if(permission.isEmpty()){
                   Permission permission1 = new Permission();
                   permission1.setName(record.getString("name"));
                   permission1.setGroup(record.getString("group"));
                   permission1.setModule(record.getString("module"));
                   try {
                       permissionRepository.save(permission1);
                   }catch (Exception e){
                       e.printStackTrace();
                       throw new Exception("Error Occurred");
                   }
                }
            }
        }
    }



    public void seedSuperRole() throws Exception{
        log.info("***************************Seed Supper Role*****************************");
        Role roleName = roleRepository.findFirstByName("ROOT");
        if(roleName == null){
            Role role = new Role();
            role.setCode("ROOT");
            role.setName("ROOT");
            role.setDescription("SUPER ROLE");
            role.setPermission(Collections.emptyList());
            try{
                roleRepository.save(role);
            }catch (Exception e){
                e.printStackTrace();
                throw new Exception("Error in Seeding Super Role");
            }
        }
    }

    // Standard branch-scoped roles: CEO (full operational access to their
    // own branch), MANAGER (moderate - day-to-day operations, no deletes on
    // sensitive records), CASHIER (front-desk sales only). None of these get
    // SAVE_BRANCH/DELETE_BRANCH/VIEW_ALL_BRANCHES/SAVE_ROLE/DELETE_ROLE -
    // those stay ROOT-only since they're cross-branch/platform-wide, not
    // something a single branch's staff should touch. If a role already
    // exists, any of the permissions listed here that it's missing are added
    // back on every startup (so a role created before a permission was added
    // to this list still gets it). Nothing is ever removed - permissions
    // granted via the Role admin UI beyond this list are kept - but a listed
    // permission removed via the UI will come back on the next restart.
    public void seedStandardRoles() throws Exception {
        log.info("***************************Seed Standard Roles*****************************");

        seedRoleWithPermissions(
                "CEO",
                "Company owner - full operational access within their own branch",
                List.of(
                        "SAVE_USER", "VIEW_USER", "DELETE_USER", "ENABLE_OR_DISABLE_USER",
                        "DELETE_BAR_USER", "ASSIGN_USER_ROLE", "ENABLE_OR_DISABLE_ACCOUNT",
                        "VIEW_ROLE",
                        "VIEW_BRANCH",
                        // Changes the branch logo (ROOT could before; the owner can now too).
                        "MANAGE_SYSTEM_SETTINGS",
                        // Decides what the Other commission pays for (POS Setting > Other).
                        "MANAGE_OTHER_COMMISSION",
                        "SAVE_COMMISSION", "VIEW_COMMISSION", "DELETE_COMMISSION",
                        "VIEW_TABLE_SIZE",
                        "SAVE_SERVICE", "VIEW_SERVICE", "DELETE_SERVICE",
                        "VIEW_STAFF", "SAVE_STAFF", "DELETE_STAFF",
                        "SAVE_SALES", "VIEW_SALES", "DELETE_SALES", "RECEIVE_ORDERS",
                        "VIEW_REPORT", "PAY_STAFF", "SAVE_EXPENSES", "VIEW_EXPENSES",
                        "SAVE_STOCK_AND_PURCHASE", "VIEW_STOCK_AND_PURCHASE",
                        "SAVE_STORE", "VIEW_STORE", "DELETE_STORE"
                )
        );

        seedRoleWithPermissions(
                "MANAGER",
                "Branch manager - day-to-day operations, moderate access",
                List.of(
                        "VIEW_BRANCH",
                        "SAVE_USER", "VIEW_USER",
                        "SAVE_SALES", "VIEW_SALES", "RECEIVE_ORDERS",
                        "SAVE_SERVICE", "VIEW_SERVICE",
                        // DELETE_STAFF only makes a staff member inactive; their history stays.
                        "SAVE_STAFF", "VIEW_STAFF", "DELETE_STAFF",
                        "VIEW_REPORT", "VIEW_EXPENSES", "VIEW_STOCK_AND_PURCHASE",
                        "SAVE_STORE", "VIEW_STORE", "DELETE_STORE"
                )
        );

        seedRoleWithPermissions(
                "CASHIER",
                "Front-desk cashier - day-to-day sales only",
                List.of(
                        "VIEW_BRANCH",
                        "SAVE_SALES", "VIEW_SALES",
                        // New Sale picks the staff member serving the bill.
                        "VIEW_STAFF",
                        "VIEW_STORE",
                        "VIEW_SERVICE",
                        // "Expenses & Cash-up": the cashier hands out every
                        // payment (on the manager's word), so what leaves the
                        // drawer comes off their own cash-up.
                        "VIEW_REPORT",
                        "VIEW_EXPENSES", "SAVE_EXPENSES",
                        "PAY_STAFF",
                        "VIEW_STOCK_AND_PURCHASE", "SAVE_STOCK_AND_PURCHASE"
                )
        );

        // Receive what waiters write at Staff Sell before it leaves: drinks at
        // the COUNTER, food by the CHEF (kitchen). Each lands on the orders
        // screen and sees only their own station. (SUPERVISOR, which received
        // everything, is no longer seeded or offered; an existing one keeps
        // working and sees every station until the CEO gives a new role.)
        seedRoleWithPermissions(
                "COUNTER",
                "Counter - receives waiters' drink orders before they leave the counter, and sells to customers at the counter on their own bills",
                // COUNTER_SELL: their own bills, as a staff member would, handed to the cashier - never payment.
                List.of("VIEW_BRANCH", "VIEW_SALES", "RECEIVE_ORDERS", "COUNTER_SELL")
        );
        seedRoleWithPermissions(
                "CHEF",
                "Chef (kitchen) - receives waiters' food orders before they leave the kitchen",
                List.of("VIEW_BRANCH", "VIEW_SALES", "RECEIVE_ORDERS")
        );

        // Main office: answers the branches and publishes guidance to them -
        // the Admin area, across every branch - without DIRECTOR's hold on
        // users, roles and branches.
        seedRoleWithPermissions(
                "ADMIN",
                "Main office - answers branch messages and publishes guidance",
                List.of(
                        "VIEW_BRANCH",
                        "VIEW_BRANCH_MESSAGE", "REPLY_BRANCH_MESSAGE", "MANAGE_GUIDANCE",
                        // Admin > Errors, Audit and Expiring branches (moved from Settings).
                        "VIEW_ERROR_LOG", "DELETE_ERROR_LOG", "VIEW_AUDIT_LOG", "VIEW_EXPIRING_BRANCHES"
                )
        );

        // DIRECTOR and STAFF are system/settings-management roles (see Dashboard
        // + Settings, unlike CEO/MANAGER/CASHIER who land straight on POS).
        // Left with no permissions on purpose - assigned by hand via the Role
        // admin UI (Settings > Role > assign permissions) rather than seeded,
        // per explicit instruction.
        // DIRECTOR manages Role, Branch and Users across every branch -
        // Permission, Config and Storage stay ROOT-only.
        seedRoleWithPermissions(
                "DIRECTOR",
                "System/settings management role",
                List.of(
                        "VIEW_BRANCH", "VIEW_ALL_BRANCHES", "SAVE_BRANCH", "DELETE_BRANCH",
                        "VIEW_USER", "SAVE_USER", "DELETE_USER", "ASSIGN_USER_ROLE",
                        "ENABLE_OR_DISABLE_USER", "ENABLE_OR_DISABLE_ACCOUNT",
                        "VIEW_ROLE", "SAVE_ROLE", "DELETE_ROLE",
                        "VIEW_PERMISSION", "SAVE_PERMISSION",
                        "VIEW_COMMISSION_REPORT",
                        // Settings > Errors, so a DIRECTOR can see what broke
                        // without going through ROOT.
                        "VIEW_ERROR_LOG", "DELETE_ERROR_LOG", "VIEW_AUDIT_LOG",
                        "VIEW_PAYMENTS", "RECONCILE_PAYMENTS",
                        "SAVE_REGION", "DELETE_REGION",
                        // Admin: what branches are asking, and what we
                        // publish back to them.
                        "VIEW_BRANCH_MESSAGE", "REPLY_BRANCH_MESSAGE", "MANAGE_GUIDANCE"
                )
        );
        // STAFF registers branches (only ever seeing its own) and sets up
        // that branch's first users and their roles. Everything else in
        // Settings is DIRECTOR/ROOT.
        seedRoleWithPermissions(
                "STAFF",
                "System/settings management role",
                List.of(
                        "VIEW_BRANCH", "VIEW_ALL_BRANCHES", "SAVE_BRANCH", "DELETE_BRANCH",
                        "VIEW_USER", "SAVE_USER", "ASSIGN_USER_ROLE",
                        "VIEW_ROLE",
                        // Scoped server-side to their own earnings only -
                        // see CommissionService.seesAllStaff().
                        "VIEW_COMMISSION_REPORT"
                )
        );
    }

    private void seedRoleWithPermissions(String code, String description, List<String> permissionNames) throws Exception {
        Role existing = roleRepository.findByCode(code);
        if (existing != null) {
            addMissingPermissions(existing, permissionNames);
            return;
        }

        List<Permission> permissions = new ArrayList<>();
        for (String name : permissionNames) {
            permissionRepository.findByName(name).ifPresent(permissions::add);
        }

        Role role = new Role();
        role.setCode(code);
        role.setName(code);
        role.setDescription(description);
        role.setPermission(permissions);

        try {
            roleRepository.save(role);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Error in Seeding Role: " + code);
        }
    }

    private void addMissingPermissions(Role role, List<String> permissionNames) throws Exception {
        List<Permission> current = role.getPermission() == null
                ? new ArrayList<>()
                : new ArrayList<>(role.getPermission());
        List<String> added = new ArrayList<>();
        for (String name : permissionNames) {
            boolean alreadyHas = current.stream().anyMatch(p -> name.equals(p.getName()));
            if (alreadyHas) {
                continue;
            }
            Optional<Permission> permission = permissionRepository.findByName(name);
            if (permission.isPresent()) {
                current.add(permission.get());
                added.add(name);
            } else {
                log.warn("Permission " + name + " not found - not added to role " + role.getCode());
            }
        }
        if (added.isEmpty()) {
            return;
        }
        role.setPermission(current);
        try {
            roleRepository.save(role);
            log.info("Added missing permissions to role " + role.getCode() + ": " + added);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Error in updating Role: " + role.getCode());
        }
    }

    // The branch STAFF, DIRECTOR and ROOT all sit on - they run the platform
    // rather than a bar of their own. Its display name is free to change;
    // this code is not.
    private static final String ROOT_BRANCH_CODE = "ROOT";

    public void seedRootBranch() throws Exception{
        log.info("*****************************Seed Main Branch***************************");
        // Looked up by CODE, never by name. The code is the identifier the rest
        // of the system keys on (login's subscription exemption, the super
        // user's branch, the disabled edit/delete buttons); the name is only
        // ever display text and can be renamed freely. Matching on the name
        // would quietly seed a SECOND root branch the moment it was renamed.
        Branch existingRoot = branchRepository.findByBranchCode(ROOT_BRANCH_CODE);
        if(existingRoot == null){
            Branch branch = new Branch();
            branch.setBranchName("MIDLAND SOLUTIONS");
            branch.setBranchCategory("ALL CATEGORIES");
            branch.setBranchCode(ROOT_BRANCH_CODE);
            branch.setRegion("DODOMA");
            branch.setStatus("ACTIVE");
            branch.setAddress("DODOMA");
            branch.setPhone("0000-0000-0000");
            branch.setDescription("INASIMAMIA MATAWI NA WATUMIAJI WA MFUMO, SI BAR");
            try{
                branchRepository.save(branch);
            }catch (Exception e){
                e.printStackTrace();
                throw new Exception("Error in seeding Root Branch");
            }
        }
    }

    public void seedSuperUser() throws Exception{
        log.info("*****************************Seed Super User***************************");
        User user = userRepository.findFirstByUsername("root@root.com");
        Role role = roleRepository.findByCode("ROOT");
        Branch branch = branchRepository.findByBranchCode(ROOT_BRANCH_CODE);
        // An install seeded in the old order already has this user without a
        // branch, and reordering alone leaves it that way forever - the block
        // below only runs when the user is absent. So repair it in passing.
        if (user != null && branch != null && user.getBranch() == null) {
            log.info("Super user had no branch; attaching the root branch");
            user.setBranch(branch);
            userRepository.save(user);
        }

        if(user == null){
            User superUser = new User();
            superUser.setUsername("root@root.com");
            superUser.setPassword(passwordEncoder.encode("root"));
            superUser.setIsRoot(true);
            superUser.setEmail("root@root.com");
            superUser.setGender("MALE");
            superUser.setAddress("DODOMA");
            superUser.setFirstName("ROOT");
            superUser.setMiddleName("ROOT");
            superUser.setLastName("ROOT");
            superUser.setDateOfBirth(LocalDate.now());
            superUser.setPhone("1010101010");
            superUser.setRoles(List.of(role));
            superUser.setBranch(branch);
            try {
                userRepository.save(superUser);
            }catch (Exception e){
                e.printStackTrace();
                throw new Exception("Error in Saving Super User");
            }
        }
    }




}
