package com.multi.finance.controller;

import com.multi.finance.dto.request.OpenRunRequest;
import com.multi.finance.dto.response.DeliveryRunResponse;
import com.multi.finance.entity.RouteArea;
import com.multi.finance.enums.DeliveryRunStatus;
import com.multi.finance.service.impl.DeliveryRunServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Lorry rounds and the routes they run to.
 *
 * <p>The open run is what stops the accountant answering "which round?" twenty times
 * in a row: they open one, every bill entered joins it, and the create-bill screen
 * shows which one throughout.
 */
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryRunController {

    private final DeliveryRunServiceImpl service;

    // ── Routes ───────────────────────────────────────────────────────

    @GetMapping("/areas")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER','MAIN_ACCOUNTANT','ACCOUNTANT','SHOP_ACCOUNTANT')")
    public ResponseEntity<List<RouteArea>> areas(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(service.areas(includeInactive));
    }

    /** Admin only: the list is what every round is counted against. */
    @PostMapping("/areas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RouteArea> saveArea(@RequestBody RouteArea area) {
        return ResponseEntity.ok(service.saveArea(area));
    }

    // ── Runs ─────────────────────────────────────────────────────────

    @PostMapping("/runs")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_ACCOUNTANT','ACCOUNTANT')")
    public ResponseEntity<DeliveryRunResponse> open(@RequestBody OpenRunRequest req,
                                                     Principal principal) {
        return ResponseEntity.ok(service.open(req, principal.getName()));
    }

    /** The run this user is currently entering bills into, or null. */
    @GetMapping("/runs/current")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_ACCOUNTANT','ACCOUNTANT','SHOP_ACCOUNTANT')")
    public ResponseEntity<DeliveryRunResponse> current(Principal principal) {
        return ResponseEntity.ok(service.currentFor(principal.getName()));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER','MAIN_ACCOUNTANT','ACCOUNTANT')")
    public ResponseEntity<List<DeliveryRunResponse>> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) LocalDate month) {
        return ResponseEntity.ok(service.list(from, to, month));
    }

    /**
     * A month by business: billed, collected, still out.
     *
     * <p>Admin and owner only — it is a view of how the month is going, not something
     * an accountant needs while entering bills.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<List<com.multi.finance.dto.response.MonthBusinessSummary>> summary(
            @RequestParam(required = false) LocalDate month,
            @RequestParam(required = false) com.multi.finance.enums.DeliveryMode mode) {
        return ResponseEntity.ok(service.monthSummary(month, mode));
    }

    /** Everything the round carried — what the admin checks the lorry against. */
    @GetMapping("/runs/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER','MAIN_ACCOUNTANT','ACCOUNTANT')")
    public ResponseEntity<DeliveryRunResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(service.detail(id));
    }

    @PatchMapping("/runs/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_ACCOUNTANT','ACCOUNTANT')")
    public ResponseEntity<DeliveryRunResponse> setStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Principal principal) {
        DeliveryRunStatus status = DeliveryRunStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(service.setStatus(id, status, principal.getName()));
    }

    /** Bills that could still join this round — on no run, and dated near it. */
    @GetMapping("/runs/{id}/candidates")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_ACCOUNTANT','ACCOUNTANT')")
    public ResponseEntity<List<com.multi.finance.dto.response.BillResponse>> candidates(
            @PathVariable Long id) {
        return ResponseEntity.ok(service.candidatesFor(id));
    }

    /** Bills entered before the round was decided, or one that joined the wrong round. */
    @PostMapping("/runs/{id}/bills")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_ACCOUNTANT','ACCOUNTANT')")
    public ResponseEntity<Map<String, Integer>> assign(
            @PathVariable Long id,
            @RequestBody Map<String, List<Long>> body) {
        return ResponseEntity.ok(Map.of("assigned", service.assignBills(id, body.get("billIds"))));
    }

    @DeleteMapping("/runs/bills/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_ACCOUNTANT','ACCOUNTANT')")
    public ResponseEntity<Void> removeBill(@PathVariable Long billId) {
        service.removeBill(billId);
        return ResponseEntity.noContent().build();
    }
}
