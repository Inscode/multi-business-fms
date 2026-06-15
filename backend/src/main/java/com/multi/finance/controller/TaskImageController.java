package com.multi.finance.controller;

import com.multi.finance.service.impl.ImageKitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks/upload-image")
@RequiredArgsConstructor
public class TaskImageController {

    private final ImageKitService imageKitService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT', 'WORKER')")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        String url = imageKitService.upload(file);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
