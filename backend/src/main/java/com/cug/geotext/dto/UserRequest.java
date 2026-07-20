package com.cug.geotext.dto;
import jakarta.validation.constraints.NotBlank;import jakarta.validation.constraints.Pattern;import jakarta.validation.constraints.Size;
public record UserRequest(@NotBlank@Size(max=64)String username,@Size(max=100)String password,@NotBlank@Size(max=100)String displayName,@Pattern(regexp="ADMIN|USER")String role,Boolean enabled){}
