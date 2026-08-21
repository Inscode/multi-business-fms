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

    /** Uploads to the default folder — task images. */
    public String upload(MultipartFile file) {
        return upload(file, folder);
    }

    /**
     * Uploads into a named folder.
     *
     * <p>Kept separate per kind of image: payment receipts and task photos have
     * different retention — a receipt is evidence for a figure and a task photo is
     * not — and mixing them in one folder makes clearing out old ones impossible
     * without picking through them by hand.
     *
     * @param targetFolder folder name without a leading slash, e.g. "payments"
     */
    public String upload(MultipartFile file, String targetFolder) {
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            // ImageKit auth: Basic base64(privateKey + ":")
            String encoded = Base64.getEncoder().encodeToString((privateKey + ":").getBytes());
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("fileName", fileName);
            body.add("folder", "/" + (targetFolder == null || targetFolder.isBlank()
                                      ? folder : targetFolder.trim()));
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

    private static final String LIST_URL = "https://api.imagekit.io/v1/files";

    /**
     * Removes a stored image, given the URL that was kept for it.
     *
     * <p>Two calls, because only the URL was ever stored and ImageKit deletes by file id:
     * the name is looked up first, then deleted. Storing the id at upload time would save
     * a call but would not help the images already uploaded, which are the ones being
     * cleared out.
     *
     * <p>Never throws. A file that has already gone, or a lookup that fails, must not
     * stop the record it belonged to from being deleted — the alternative is a payment
     * that cannot be removed because of a picture.
     *
     * @return true when something was actually deleted
     */
    public boolean deleteByUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            int q = fileName.indexOf('?');
            if (q >= 0) fileName = fileName.substring(0, q);
            if (fileName.isBlank()) return false;

            HttpHeaders headers = new HttpHeaders();
            String encoded = Base64.getEncoder().encodeToString((privateKey + ":").getBytes());
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);

            String search = LIST_URL + "?searchQuery=" + java.net.URLEncoder.encode(
                    "name=\"" + fileName + "\"", java.nio.charset.StandardCharsets.UTF_8);

            ResponseEntity<java.util.List> found = restTemplate.exchange(
                    search, org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers), java.util.List.class);

            if (found.getBody() == null || found.getBody().isEmpty()) return false;

            Object first = found.getBody().get(0);
            if (!(first instanceof Map<?, ?> m)) return false;
            Object fileId = m.get("fileId");
            if (fileId == null) return false;

            restTemplate.exchange(LIST_URL + "/" + fileId,
                    org.springframework.http.HttpMethod.DELETE,
                    new HttpEntity<>(headers), Void.class);
            return true;

        } catch (Exception e) {
            // Logged rather than raised: the record must still be removable.
            System.err.println("ImageKit delete failed for " + url + ": " + e.getMessage());
            return false;
        }
    }
}
