package com.cug.geotext.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionRequest(@NotBlank @Size(max=1000) String question, String provider, @Min(1) @Max(12) Integer limit) {}
