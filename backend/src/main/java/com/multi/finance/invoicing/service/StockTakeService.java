package com.multi.finance.invoicing.service;

import com.multi.finance.invoicing.dto.request.StockTakeRequest;
import com.multi.finance.invoicing.dto.response.StockTakePreview;
import com.multi.finance.invoicing.entity.Item;
import com.multi.finance.invoicing.entity.StockMovement;
import com.multi.finance.invoicing.enums.StockMovementType;
import com.multi.finance.invoicing.repository.ItemRepository;
import com.multi.finance.invoicing.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writing a physical count over the system's figure.
 *
 * <p>Used where system stock was never kept and the shelves are the only truth there is.
 * Each item gets one adjustment straight to the counted figure — never a zeroing followed
 * by a re-entry. Zeroing first would leave every item at nil for as long as the entry
 * takes, and invoicing hard-blocks on insufficient stock, so anyone billing in that
 * window is stopped for a reason that is not real.
 *
 * <p>Applied as one transaction. A count is a single event: everything was on the shelf
 * at the same moment, and a half-applied count describes a warehouse that never existed.
 */
@Service
@RequiredArgsConstructor
public class StockTakeService {

    private final ItemRepository itemRepo;
    private final StockMovementRepository movementRepo;

    /**
     * A swing this large is flagged for a second look.
     *
     * <p>Not blocked — a first count after months of drift genuinely does move by this
     * much. But a 5 typed where 50 was meant looks exactly like a real correction once
     * it is written, and this is the last point at which anyone can tell.
     */
    private static final int LARGE_SWING = 500;

    /** What the count would do, without doing any of it. */
    @Transactional(readOnly = true)
    public StockTakePreview preview(StockTakeRequest req) {
        return compute(req);
    }

    /**
     * Writes the count.
     *
     * @param by who counted, recorded on every movement
     * @throws IllegalArgumentException if any line names an item that does not exist —
     *         a count sheet with an unrecognised code is a sheet nobody has reconciled,
     *         and applying the rest of it silently would hide that
     */
    @Transactional
    public StockTakePreview apply(StockTakeRequest req, String by) {
        StockTakePreview preview = compute(req);

        if (preview.getNotFoundCount() > 0) {
            String codes = preview.getRows().stream()
                    .filter(r -> "NOT_FOUND".equals(r.getStatus()))
                    .map(StockTakePreview.Row::getItemCode)
                    .limit(10)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            throw new IllegalArgumentException(
                    "These codes match no item: " + codes
                  + ". Fix or remove them before applying — a count that quietly skips "
                  + "lines is worse than one that stops.");
        }

        LocalDate countedOn = req.getCountedOn() != null ? req.getCountedOn() : LocalDate.now();
        String note = req.getReference().trim() + " · counted " + countedOn
                    + (by == null ? "" : " · " + by);

        List<StockTakePreview.Row> toWrite = new ArrayList<>(preview.getRows());
        if (req.isZeroUncounted()) toWrite.addAll(preview.getUncounted());

        for (StockTakePreview.Row row : toWrite) {
            if (row.getItemId() == null) continue;

            if (row.getDelta() != null && row.getDelta() != 0) {
                itemRepo.adjustStock(row.getItemId(), row.getDelta());
                movementRepo.save(movement(row.getItemId(), row.getDelta(), note));
            }
            // Damage moves on its own line so the two buckets stay separable in the log.
            if (row.getDamageDelta() != null && row.getDamageDelta() != 0) {
                itemRepo.adjustDamageStock(row.getItemId(), row.getDamageDelta());
                movementRepo.save(movement(row.getItemId(), row.getDamageDelta(),
                                           note + " · damage"));
            }
        }
        return preview;
    }

    private StockMovement movement(Long itemId, int qty, String note) {
        Item ref = itemRepo.getReferenceById(itemId);
        StockMovement mv = new StockMovement();
        mv.setItem(ref);
        mv.setType(StockMovementType.MANUAL_ADJUSTMENT);
        mv.setQuantity(qty);
        mv.setReferenceType("STOCK_TAKE");
        mv.setNotes(note);
        return mv;
    }

    // ── Working out ─────────────────────────────────────────────────────────

    private StockTakePreview compute(StockTakeRequest req) {
        List<Item> all = itemRepo.findAllWithBrand();
        Map<Long, Item> byId = new HashMap<>();
        Map<String, Item> byCode = new HashMap<>();
        for (Item i : all) {
            byId.put(i.getId(), i);
            if (i.getItemCode() != null) byCode.put(norm(i.getItemCode()), i);
        }

        List<StockTakePreview.Row> rows = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Set<Long> touched = new HashSet<>();

        int changed = 0, matched = 0, notFound = 0, net = 0;

        for (StockTakeRequest.Line line : req.getLines() == null ? List.<StockTakeRequest.Line>of()
                                                                : req.getLines()) {
            Item item = line.getItemId() != null ? byId.get(line.getItemId())
                      : (line.getItemCode() == null ? null : byCode.get(norm(line.getItemCode())));

            if (item == null) {
                notFound++;
                rows.add(StockTakePreview.Row.builder()
                        .itemCode(line.getItemCode())
                        .countedQty(line.getCountedQty())
                        .status("NOT_FOUND")
                        .warning("No item with this code.")
                        .build());
                continue;
            }

            // The same code twice usually means two shelves of one item that were meant
            // to be added together. Kept visible rather than silently taking the last.
            boolean duplicate = !seen.add(item.getId());

            int system = item.getStockQty() == null ? 0 : item.getStockQty();
            Integer countedQty = line.getCountedQty();
            Integer delta = countedQty == null ? null : countedQty - system;

            int systemDamage = item.getDamageQty() == null ? 0 : item.getDamageQty();
            Integer countedDamage = line.getCountedDamageQty();
            Integer damageDelta = countedDamage == null ? null : countedDamage - systemDamage;

            String status;
            if (duplicate) {
                status = "DUPLICATE";
            } else if ((delta == null || delta == 0) && (damageDelta == null || damageDelta == 0)) {
                status = "MATCHED";
                matched++;
            } else if (delta != null && delta > 0) {
                status = "INCREASE";
                changed++;
            } else if (delta != null && delta < 0) {
                status = "DECREASE";
                changed++;
            } else {
                status = "INCREASE";
                changed++;
            }
            if (delta != null && !duplicate) net += delta;

            String warning = null;
            if (duplicate) {
                warning = "This code appears more than once. Only the first line is applied — "
                        + "add the shelves together instead.";
            } else if (delta != null && Math.abs(delta) >= LARGE_SWING) {
                warning = "Moves by " + Math.abs(delta) + " units. Worth checking.";
            } else if (countedQty != null && countedQty < 0) {
                warning = "Negative count.";
            }

            if (!duplicate) touched.add(item.getId());

            rows.add(row(item, system, countedQty, duplicate ? null : delta,
                         systemDamage, countedDamage, duplicate ? null : damageDelta,
                         status, warning));
        }

        // Everything the sheet never mentioned, with what the system still holds.
        List<StockTakePreview.Row> uncounted = all.stream()
                .filter(i -> !touched.contains(i.getId()))
                .filter(Item::isActive)
                .map(i -> {
                    int system = i.getStockQty() == null ? 0 : i.getStockQty();
                    return row(i, system, 0, -system,
                               i.getDamageQty() == null ? 0 : i.getDamageQty(), null, null,
                               system == 0 ? "MATCHED" : "DECREASE",
                               system == 0 ? null : "Not on the count sheet.");
                })
                .toList();

        return StockTakePreview.builder()
                .reference(req.getReference())
                .lineCount(rows.size())
                .changedCount(changed)
                .matchedCount(matched)
                .notFoundCount(notFound)
                .netUnitChange(net)
                .rows(rows)
                .uncounted(uncounted)
                .build();
    }

    private static StockTakePreview.Row row(Item item, Integer system, Integer counted,
                                            Integer delta, Integer systemDamage,
                                            Integer countedDamage, Integer damageDelta,
                                            String status, String warning) {
        return StockTakePreview.Row.builder()
                .itemId(item.getId())
                .itemCode(item.getItemCode())
                .description(item.getDescription())
                .brand(item.getBrand() != null ? item.getBrand().getName() : null)
                .category(item.getCategory() != null ? item.getCategory().name() : null)
                .systemQty(system)
                .countedQty(counted)
                .delta(delta)
                .systemDamageQty(systemDamage)
                .countedDamageQty(countedDamage)
                .damageDelta(damageDelta)
                .status(status)
                .warning(warning)
                .build();
    }

    private static String norm(String code) {
        return code == null ? "" : code.trim().toUpperCase().replace(" ", "");
    }
}
