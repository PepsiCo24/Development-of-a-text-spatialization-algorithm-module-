package com.cug.geotext;

import static org.assertj.core.api.Assertions.assertThat;
import com.cug.geotext.common.ApiResponse;
import org.junit.jupiter.api.Test;

class ApiResponseTest {
    @Test void successResponseUsesStableContract() {
        ApiResponse<String> response = ApiResponse.ok("ready");
        assertThat(response.code()).isZero();
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("ready");
        assertThat(response.timestamp()).isNotNull();
    }
}

