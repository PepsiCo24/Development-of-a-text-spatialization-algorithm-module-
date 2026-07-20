package com.cug.geotext.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cug.geotext.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {
    @TempDir Path tempDirectory;

    @Test
    void storesLoadsAndDeletesAllowedDocument() throws Exception {
        FileStorageService service = new FileStorageService(tempDirectory.toString());
        MockMultipartFile upload = new MockMultipartFile("file", "field-report.txt", "text/plain", "granite".getBytes(StandardCharsets.UTF_8));

        FileStorageService.StoredFile stored = service.store(upload);

        assertThat(stored.type()).isEqualTo("TXT");
        assertThat(stored.originalName()).isEqualTo("field-report.txt");
        assertThat(stored.contentType()).startsWith("text/plain");
        assertThat(stored.size()).isEqualTo(7);
        assertThat(service.load(stored.relativePath()).getContentAsString(StandardCharsets.UTF_8)).isEqualTo("granite");

        service.delete(stored.relativePath());
        assertThatThrownBy(() -> service.load(stored.relativePath()))
            .isInstanceOf(BusinessException.class)
            .hasMessage("资料文件不存在");
    }

    @Test
    void rejectsUnsupportedFilesAndTraversalPaths() {
        FileStorageService service = new FileStorageService(tempDirectory.toString());
        MockMultipartFile executable = new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> service.store(executable))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅支持");
        assertThatThrownBy(() -> service.load("../../outside.txt"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("文件路径不合法");
    }
}
