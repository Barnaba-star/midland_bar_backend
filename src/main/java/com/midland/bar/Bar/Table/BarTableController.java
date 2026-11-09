package com.midland.bar.Bar.Table;

import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** POS Setting > Meza & QR - the same permissions as bill codes on that screen. */
@RestController
@RequestMapping("/bar/tables")
@RequiredArgsConstructor
public class BarTableController {

    private final BarTableService tableService;

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SERVICE')")
    @GetMapping
    public ResponseList<BarTable> list() {
        return tableService.list();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SERVICE')")
    @PostMapping("/count")
    public ResponseList<BarTable> setCount(@RequestBody Map<String, Integer> body) {
        return tableService.setCount(body.get("count"));
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SERVICE')")
    @PostMapping("/{uid}/rename")
    public Response<BarTable> rename(@PathVariable String uid, @RequestBody Map<String, String> body) {
        return tableService.rename(uid, body.get("name"));
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SERVICE')")
    @PostMapping("/{uid}/newCard")
    public Response<BarTable> newCard(@PathVariable String uid) {
        return tableService.newCard(uid);
    }
}
