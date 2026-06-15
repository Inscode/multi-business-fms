package com.multi.finance.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageKitService {

    private static final String UPLOAD_URL = "https://upload.imagekit.io/api/v1/files/upload";

    @Value("${imagekit.private-key}")
    private String privateKey;

    @Value("${imagekit.folder:tasks}")
    private String folder;

    private final RestTemplate restTemplate;

    public String upload(MultipartFile file) {
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            // ImageKit auth: Basic base64(privateKey + ":")
            String encoded = Base64.getEncoder().encodeToString((privateKey + ":").getBytes());
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("fileName", fileName);
            body.add("folder", "/" + folder);
            body.add("useUniqueFileName", "true");
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() { return fileName; }
            });

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(UPLOAD_URL, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("url");
            }
            throw new RuntimeException("ImageKit upload failed: " + response.getStatusCode());

        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }
}
