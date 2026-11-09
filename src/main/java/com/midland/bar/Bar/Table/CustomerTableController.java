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

    @GetMapping("/{token}")
    public Response<Map<String, Object>> view(@PathVariable String token) {
        return tableService.customerView(token);
    }
}
