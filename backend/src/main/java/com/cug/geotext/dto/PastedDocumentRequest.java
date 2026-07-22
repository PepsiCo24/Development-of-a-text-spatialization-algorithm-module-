package com.cug.geotext.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PastedDocumentRequest(
        @NotBlank(message = "Name is required") @Size(max = 255) String name,
        @Size(max = 128) String region,
        Integer year,
        @Size(max = 2000) String keyword,
        @Size(max = 5000) String summary,
        @NotBlank(message = "Content is required") @Size(max = 500000, message = "Content is too large") String content
) {
}
