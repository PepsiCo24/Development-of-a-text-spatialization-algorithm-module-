package com.cug.geotext.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DictionaryRequest(
    @NotBlank @Size(max=64) String termType,
    @NotBlank @Size(max=255) String standardName,
    @Size(max=2000) String aliases,
    @Size(max=2000) String description,
    Boolean enabled
) {}
