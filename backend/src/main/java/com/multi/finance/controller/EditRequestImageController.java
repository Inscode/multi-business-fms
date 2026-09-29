package com.multi.finance.controller;

import com.multi.finance.service.impl.ImageKitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Photographs attached to edit requests.
 *
 * <p>Its own ImageKit folder, like payments and returns before it. These are evidence
 * for a figure somebody asked to change and an admin agreed to — they are worth keeping
 * as long as the bill is, and worth being able to find without picking through the
 * task photos that are cleared out every few weeks.
 */
@RestController
@RequestMapping("/api/edit-requests/upload-image")
@RequiredArgsConstructor
public class EditRequestImageController {

    private static final String FOLDER = "edit-requests";

    private final ImageKitService imageKitService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(Map.of("url", imageKitService.upload(file, FOLDER)));
    }
}
