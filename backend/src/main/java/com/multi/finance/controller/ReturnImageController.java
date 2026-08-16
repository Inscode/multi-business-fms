package com.multi.finance.controller;

import com.multi.finance.enums.ReturnType;
import com.multi.finance.service.impl.ImageKitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Photographs behind returns.
 *
 * <p>Damage and salable go to separate folders because they are separate books, kept
 * for different reasons and for different lengths of time: a damage photo supports a
 * claim against the agent, a salable one supports a credit to the customer. One folder
 * would make either retention rule impossible to apply without sorting by hand.
 */
@RestController
@RequestMapping("/api/returns/upload-image")
@RequiredArgsConstructor
public class ReturnImageController {

    private final ImageKitService imageKitService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER','MAIN_ACCOUNTANT','ACCOUNTANT','SHOP_ACCOUNTANT')")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("returnType") ReturnType returnType) {

        if (file.isEmpty()) return ResponseEntity.badRequest().build();

        String folder = "returns/" + returnType.name().toLowerCase();
        return ResponseEntity.ok(Map.of("url", imageKitService.upload(file, folder)));
    }
}
