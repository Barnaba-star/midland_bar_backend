package com.midland.bar.Config.Security;

import com.midland.bar.Bar.Model.SalesOpened;
import com.midland.bar.Utils.Exceptions.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A staff member signed in with their code and PIN (not a user account).
 *
 * Such a session is boxed in three ways:
 *  - the filter lets it reach only the Staff Sell paths below (403 elsewhere);
 *  - its token carries only VIEW_SALES / SAVE_SALES / VIEW_SERVICE;
 *  - the services check every code and bill it touches is the staff member's own.
 */
public final class StaffSession {

    private StaffSession() {}

    /** Put on the Authentication as its details. */
    public record Info(String staffUid, String staffCode) {}

    private record Route(String method, Pattern path) {}

    private static final List<Route> ALLOWED = List.of(
            new Route("GET", Pattern.compile("^/bar/staffSell/[^/]+$")),
            new Route("POST", Pattern.compile("^/bar/staffSell/openBill$")),
            new Route("GET", Pattern.compile("^/bar/staffSell/handover/[^/]+$")),
            new Route("POST", Pattern.compile("^/bar/staffOrders/addItem$")),
            new Route("POST", Pattern.compile("^/bar/staffOrders/[^/]+/removeLine/[^/]+$")),
            new Route("POST", Pattern.compile("^/bar/staffOrders/send/[^/]+$")),
            new Route("POST", Pattern.compile("^/bar/staffOrders/offline$")),
            new Route("GET", Pattern.compile("^/bar/findBarSalesList/[^/]+$")),
            new Route("POST", Pattern.compile("^/bar/deleteEmptyBill/[^/]+$")),
            new Route("GET", Pattern.compile("^/bar/findBarServiceList$")),
            new Route("GET", Pattern.compile("^/bar/shift/current$")),
            // Printing a bill: the receipt (own bills only - see BillPaymentService.receipt) and the branch logo on it.
            new Route("GET", Pattern.compile("^/bar/findBillReceipt/[^/]+$")),
            new Route("GET", Pattern.compile("^/systemSetting/logo$")),
            // "Paid by phone, from this name" on their own bill.
            new Route("POST", Pattern.compile("^/bar/bills/[^/]+/paymentNote$")),
            new Route("GET", Pattern.compile("^/uploads/.+$"))
    );

    /** Whether a staff session may make this request at all. */
    public static boolean allows(String method, String path) {
        if (path == null)
            return false;
        if ("OPTIONS".equalsIgnoreCase(method) || path.startsWith("/authentication/") || path.equals("/error"))
            return true;
        return ALLOWED.stream().anyMatch(r -> r.method().equalsIgnoreCase(method) && r.path().matcher(path).matches());
    }

    private static Info info() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof Info i ? i : null;
    }

    /** True while the request comes from a staff code sign-in. */
    public static boolean active() {
        return info() != null;
    }

    /** A staff session may only use its own code. No-op for ordinary logins. */
    public static void requireOwnCode(String code) {
        Info i = info();
        if (i == null)
            return;
        if (code == null || !sameCode(code, i.staffCode()))
            throw new BusinessException("You can only work with your own bills");
    }

    /** A staff session may only touch its own bills. No-op for ordinary logins. */
    public static void requireOwnBill(SalesOpened bill) {
        requireOwnStaff(bill == null ? null : bill.getStaffUid());
    }

    public static void requireOwnStaff(String staffUid) {
        Info i = info();
        if (i == null)
            return;
        if (staffUid == null || !staffUid.equals(i.staffUid()))
            throw new BusinessException("You can only work with your own bills");
    }

    /** "7" and "007" are the same code (the keypad's leading zeros). */
    private static boolean sameCode(String a, String b) {
        String x = a.trim().toUpperCase(), y = b.trim().toUpperCase();
        if (x.equals(y))
            return true;
        try {
            return x.matches("\\d+") && y.matches("\\d+") && Integer.parseInt(x) == Integer.parseInt(y);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
