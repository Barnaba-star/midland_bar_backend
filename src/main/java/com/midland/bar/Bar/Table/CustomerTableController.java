package com.midland.bar.Bar.Table;

import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * The customers' window behind a table's QR card. Open to anyone (no JWT -
 * see WebSecurityConfiguration "/public/**"); the table token is the key.
 */
@RestController
@RequestMapping("/public/table")
@RequiredArgsConstructor
public class CustomerTableController {

    private final BarTableService tableService;
    private final TableActionsService actions;

    @GetMapping("/{token}")
    public Response<Map<String, Object>> view(@PathVariable String token) {
        return tableService.customerView(token);
    }

    /** {kind: WAITER | BILL} - the bar sees it at once. */
    @PostMapping("/{token}/call")
    public Response<Map<String, Object>> call(@PathVariable String token, @RequestBody(required = false) Map<String, String> body) {
        return actions.call(token, body == null ? null : body.get("kind"));
    }

    /** {kind, message, rating?, name?, phone?} - to the branch's CEO / manager. */
    /** A bill's own window (its QR): the copy of that bill; ask=true tells its waiter. */
    @GetMapping("/{token}/myBill")
    public Response<Map<String, Object>> myBill(@PathVariable String token, @RequestParam(defaultValue = "false") boolean ask) {
        return actions.myBill(token, ask);
    }

    /** A copy of the customer's bill by its number (MK-1). */
    @PostMapping("/{token}/bill")
    public Response<Map<String, Object>> bill(@PathVariable String token, @RequestBody Map<String, String> body) {
        return actions.billCopy(token, body == null ? null : body.get("code"));
    }

    @PostMapping("/{token}/feedback")
    public Response<Map<String, Object>> feedback(@PathVariable String token, @RequestBody Map<String, Object> body) {
        return actions.feedback(token, body);
    }

    /** {phone, pin} - the customer's ofa and visits (Ofa za Wateja). */
    @PostMapping("/{token}/card")
    public Response<Map<String, Object>> card(@PathVariable String token, @RequestBody Map<String, String> body) {
        return actions.card(token, body);
    }

    /** {items: [{uid, qty}], note?} - waits for someone at the bar to take it. */
    @PostMapping("/{token}/order")
    public Response<Map<String, Object>> order(@PathVariable String token, @RequestBody Map<String, Object> body) {
        return actions.placeOrder(token, body);
    }

    /** The table's orders tonight and its waiter. */
    @GetMapping("/{token}/orders")
    public Response<Map<String, Object>> orders(@PathVariable String token) {
        return actions.tableOrders(token);
    }

    /** {method, name, ref, amount} - paid the bar's own number; the cashier checks. */
    @PostMapping("/{token}/paid")
    public Response<Map<String, Object>> paid(@PathVariable String token, @RequestBody Map<String, Object> body) {
        return actions.paid(token, body);
    }
}
