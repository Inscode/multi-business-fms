package com.multi.finance.controller;

import com.multi.finance.service.impl.ImageKitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Photographs attached to payments.
 *
 * <p>Separate from the task image endpoint so the files land in their own ImageKit
 * folder. A payment receipt is evidence for a figure that was collected and is worth
 * keeping; a task photo is not, and is worth clearing out periodically. Sharing one
 * folder would make either policy impossible to apply without sorting by hand.
 */
@RestController
@RequestMapping("/api/payments/upload-image")
@RequiredArgsConstructor
public class PaymentImageController {

    private static final String FOLDER = "payments";

    private final ImageKitService imageKitService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(Map.of("url", imageKitService.upload(file, FOLDER)));
    }
}
