package com.multi.finance.controller;

import com.multi.finance.service.impl.ImageKitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/expenses/upload-image")
@RequiredArgsConstructor
public class ExpenseImageController {

    private final ImageKitService imageKitService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")
                || file.getSize() > 10L * 1024 * 1024) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(Map.of("url", imageKitService.upload(file, "expense")));
    }
}
