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
    @PostMapping("/{token}/feedback")
    public Response<Map<String, Object>> feedback(@PathVariable String token, @RequestBody Map<String, Object> body) {
        return actions.feedback(token, body);
    }

    /** {phone, pin} - the customer's ofa and visits (Ofa za Wateja). */
    @PostMapping("/{token}/card")
    public Response<Map<String, Object>> card(@PathVariable String token, @RequestBody Map<String, String> body) {
        return actions.card(token, body);
    }
}
