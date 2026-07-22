package com.cug.geotext.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ManualRelationRequest(
        @NotNull Long sourceEntityId,
        @NotNull Long targetEntityId,
        @NotBlank @Size(max = 64) String relationType,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
        @NotBlank String sourceText,
        @NotNull Integer page,
        @Size(max = 32) String reviewStatus
) {
}
