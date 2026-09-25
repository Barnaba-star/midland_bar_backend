package com.midland.bar.Setting.Service;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Payment.Client.SnippeClient;
import com.midland.bar.Payment.Dto.SnippePaymentResult;
import com.midland.bar.Setting.Dto.BranchDTO;
import com.midland.bar.Setting.Dto.SubscriptionPaymentDTO;
import com.midland.bar.Setting.Dto.TableSizeDto;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Setting.Repository.DatabaseMonitoringRepository;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Setting.Dto.ExpiredSubscriptionPaymentDTO;
import com.midland.bar.Uaa.Repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@Log
public class SettingService {

    private final DatabaseMonitoringRepository repository;
    private final BranchRepository branchRepository;
    private final BranchService branchService;
    private final SnippeClient snippeClient;
    private final PlatformSettingService platformSettingService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SettingService(DatabaseMonitoringRepository repository, BranchRepository branchRepository, SnippeClient snippeClient, BranchService branchService, PlatformSettingService platformSettingService, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.branchRepository = branchRepository;
        this.snippeClient = snippeClient;
        this.branchService = branchService;
        this.platformSettingService = platformSettingService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public ResponseList<TableSizeDto> getTableSizes() {
        return new ResponseList<>(repository.findTableSizes());
    }

    public Response<Branch> saveBranchSubscription(BranchDTO branchDTO){
        log.info(LoggerUser.getEmail() + "Is Saving Branch Subscription");
        if(branchDTO == null)
            return new Response<>("Provide Data For Branch Subscription");
        if(branchDTO.getCloseSubscription() == null)
            return new Response<>("Provide Subscription End Date");
        if(branchDTO.getSubscriptionAmount() == null)
            return new Response<>("Provide Amount For Subscription");
        if(branchDTO.getUid() == null)
            return new Response<>("Provide Branch REF");
        Optional<Branch> optionalBranch = branchRepository.findById(branchDTO.getUid());
        if(optionalBranch.isEmpty())
            return new Response<>("Branch Not Found");
        // STAFF may only touch branches they registered themselves.
        if(!branchService.canTouch(optionalBranch.get()))
            return new Response<>("Branch Not Found");
        Branch branch = optionalBranch.get();
        branch.setCloseSubscription(branchDTO.getCloseSubscription());
        branch.setOpenSubscription(LocalDate.now());
        branch.setSubscriptionAmount(branchDTO.getSubscriptionAmount());
        branch.setSubscriptionStatus("FREE");
        try{
            return new Response<>(branchRepository.save(branch));
        }catch (Exception e){
            e.printStackTrace();
            return new Response<>("Error When Saving Branch Subscription");
        }
    }
    /**
     * Branch user requests a subscription payment: we work out how much they
     * owe from the branch's configured subscriptionAmount (set by an admin
     * via saveBranchSubscription) times the number of months they picked,
     * then hand off to Snippe to trigger a mobile money USSD push.
     *
     * This only ever returns a "pending" outcome - Snippe confirms the real
     * result (payment.completed / payment.failed) via webhook, which is
     * where the subscription actually gets extended
     * (see SnippeWebhookController). We never mark the branch as paid here,
     * since the customer hasn't authorised anything yet at this point.
     */
    // Mirrored in subscribe-dialog-component.ts, which checks the same total
    // before sending so the provider's own message never reaches a customer.

    public Response<Branch> updateSubscription(SubscriptionPaymentDTO subscriptionPaymentDTO){
        log.info(LoggerUser.getEmail() + "Is Requesting Subscription Payment");

        if(subscriptionPaymentDTO == null)
            return new Response<>("Provide Subscription Payment Details");
        if(subscriptionPaymentDTO.getMonths() == null || subscriptionPaymentDTO.getMonths() <= 0)
            return new Response<>("Provide A Valid Number Of Months");
        if(LoggerUser.getBranchUID() == null)
            return new Response<>("User Logged In Yet have No Branch");

        Optional<Branch> optionalBranch = branchRepository.findById(Objects.requireNonNull(LoggerUser.getBranchUID()));
        if(optionalBranch.isEmpty())
            return new Response<>("Branch Not Found");

        Branch branch = optionalBranch.get();

        if(branch.getSubscriptionAmount() == null || branch.getSubscriptionAmount() <= 0)
            return new Response<>("This Branch Has No Subscription Plan Configured Yet, Contact Admin");

        int amount = branch.getSubscriptionAmount() * subscriptionPaymentDTO.getMonths();

        // Snippe refuses anything under this and answers with its own raw
        // English text. The dialog already blocks it client-side (it knows the
        // branch's monthly amount), so this only catches a request made
        // outside the UI - but the provider's wording must never be what a
        // caller sees, so stop here rather than forwarding it.
        int minimumPaymentAmount = platformSettingService.current().getMinimumPaymentAmount();
        if (amount < minimumPaymentAmount)
            return new Response<>("The Total Is Below The Minimum Of " + minimumPaymentAmount + " TZS, Choose More Months");

        return startSubscriptionPayment(branch, LoggerUser.getUser(), subscriptionPaymentDTO.getMonths(), subscriptionPaymentDTO.getPhoneNumber());
    }

    /**
     * Paying for a branch whose subscription has already lapsed, from the
     * login screen. There is no token at that point - the customer cannot get
     * one until they have paid - so the credentials are checked again here,
     * exactly as /login checks them.
     *
     * Deliberately narrow: it works only while the branch really is expired,
     * and it never issues a token. The most it can do is start a payment for
     * a branch whose password the caller already knows.
     */
    public Response<Branch> payExpiredSubscription(ExpiredSubscriptionPaymentDTO dto) {
        if (dto == null)
            return new Response<>("Provide Subscription Payment Details");
        if (dto.getMonths() == null || dto.getMonths() <= 0)
            return new Response<>("Provide A Valid Number Of Months");

        User user = userRepository.findByUsernameForAuthentication(dto.getUsername());
        // One message for "no such user" and "wrong password" alike, so this
        // cannot be used to find out which branches exist.
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword()))
            return new Response<>("Invalid Username or Password");

        if (Boolean.TRUE.equals(user.getIsBlocked()))
            return new Response<>("Account Blocked");

        Branch branch = user.getBranch();
        if (branch == null)
            return new Response<>("User Has No Branch");

        // If they can still log in, they should - this route exists only for
        // the case where the normal one is closed to them.
        Integer graceDays = platformSettingService.current().getGracePeriodDays();
        LocalDate lockoutDate = LocalDate.now().minusDays(graceDays == null ? 0 : graceDays);
        boolean expired = branch.getCloseSubscription() != null
                && branch.getCloseSubscription().isBefore(lockoutDate);
        if (!expired)
            return new Response<>("This Branch Is Active, Please Log In");

        log.info(dto.getUsername() + " is paying for an expired subscription");
        return startSubscriptionPayment(branch, user, dto.getMonths(), dto.getPhoneNumber());
    }

    /** Shared by both routes: price it, hand off to Snippe, mark it pending. */
    private Response<Branch> startSubscriptionPayment(Branch branch, User user, int months, String phoneNumber) {

        if (branch.getSubscriptionAmount() == null || branch.getSubscriptionAmount() <= 0)
            return new Response<>("This Branch Has No Subscription Plan Configured Yet, Contact Admin");

        int amount = branch.getSubscriptionAmount() * months;

        int minimum = platformSettingService.current().getMinimumPaymentAmount();
        if (amount < minimum)
            return new Response<>("The Total Is Below The Minimum Of " + minimum + " TZS, Choose More Months");

        SnippePaymentResult result = snippeClient.createMobilePayment(
                amount,
                phoneNumber,
                user.getFirstName(),
                user.getLastName(),
                branch.getUid(),
                months
        );

        if(!result.isSuccess())
            return new Response<>(result.getMessage() != null ? result.getMessage() : "Failed To Initiate Payment");

        branch.setSubscriptionStatus("PENDING");
        branch.setSubscriptionPhoneNumber(phoneNumber);
        // Stored now, while we have it. If the webhook never lands this is
        // the only way back to what happened to the customer's money.
        branch.setPendingPaymentRef(result.getReference());
        branch.setPendingPaymentAt(LocalDateTime.now());
        branch.setLastPaymentFailure(null);

        try {
            branch = branchRepository.save(branch);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response<>("Payment Was Initiated But Failed To Update Branch, Contact Admin");
        }

        // No message on this success response on purpose - StatusInterceptor
        // (frontend) pops up an "info" dialog for any response carrying a
        // message, which duplicated SubscribeDialogComponent's own success
        // banner and looked like an error interrupting the flow. The dialog
        // already shows its own confirmation text.
        return new Response<>(branch);
    }
}
