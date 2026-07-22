package com.cug.geotext.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewStatusRequest(
        @NotBlank @Pattern(regexp = "PENDING|CONFIRMED|REJECTED") String reviewStatus
) {
}
