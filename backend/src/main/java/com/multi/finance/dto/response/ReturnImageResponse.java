package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** One photograph behind a return. */
@Data
@Builder
public class ReturnImageResponse {
    private Long id;
    private String imageUrl;
    private Integer pageNo;
    private String returnType;
    private String uploadedBy;
    private LocalDateTime uploadedAt;

    /**
     * True when the photo belongs to the lorry round rather than this one return —
     * a book page covering every shop on the round. Worth saying, so nobody reads it
     * as a photo of this shop's goods alone.
     */
    private boolean fromRun;
    private String runLabel;
}
