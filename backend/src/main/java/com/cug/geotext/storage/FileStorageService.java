package com.cug.geotext.storage;

import com.cug.geotext.common.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "png", "jpg", "jpeg", "tif", "tiff");
    private static final Map<String, String> TYPES = Map.ofEntries(
        Map.entry("pdf", "PDF"), Map.entry("doc", "WORD"), Map.entry("docx", "WORD"), Map.entry("txt", "TXT"),
        Map.entry("png", "IMAGE"), Map.entry("jpg", "IMAGE"), Map.entry("jpeg", "IMAGE"), Map.entry("tif", "IMAGE"), Map.entry("tiff", "IMAGE")
    );
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
        Map.entry("pdf", "application/pdf"), Map.entry("doc", "application/msword"),
        Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("txt", "text/plain;charset=UTF-8"), Map.entry("png", "image/png"),
        Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
        Map.entry("tif", "image/tiff"), Map.entry("tiff", "image/tiff")
    );
    private final Path root;

    public FileStorageService(@Value("${app.storage.root:uploads/documents}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "请选择需要上传的文件");
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
        if (originalName.contains("..")) throw new BusinessException(400, "文件名不合法");
        String extension = extension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) throw new BusinessException(415, "仅支持 PDF、Word、TXT 和常见图片格式");

        LocalDate today = LocalDate.now();
        Path relative = Path.of(String.valueOf(today.getYear()), String.format("%02d", today.getMonthValue()), UUID.randomUUID() + "." + extension);
        Path target = resolve(relative.toString());
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(relative.toString().replace('\\', '/'), originalName, TYPES.get(extension), CONTENT_TYPES.get(extension), file.getSize());
        } catch (IOException exception) {
            throw new BusinessException(5001, "文件保存失败");
        }
    }

    public StoredFile storeText(String name, String content) {
        String originalName = StringUtils.cleanPath(name.endsWith(".txt") ? name : name + ".txt");
        if (originalName.contains("..")) throw new BusinessException(400, "文件名不合法");
        LocalDate today = LocalDate.now();
        Path relative = Path.of(String.valueOf(today.getYear()), String.format("%02d", today.getMonthValue()), UUID.randomUUID() + ".txt");
        Path target = resolve(relative.toString());
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return new StoredFile(relative.toString().replace('\\', '/'), originalName, "TXT", "text/plain;charset=UTF-8", Files.size(target));
        } catch (IOException exception) {
            throw new BusinessException(5001, "文本资料保存失败");
        }
    }

    public Resource load(String relativePath) {
        Path file = resolve(relativePath);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) throw new BusinessException(404, "资料文件不存在");
            return resource;
        } catch (IOException exception) {
            throw new BusinessException(404, "资料文件无法读取");
        }
    }

    public void delete(String relativePath) {
        try { Files.deleteIfExists(resolve(relativePath)); }
        catch (IOException exception) { throw new BusinessException(5002, "资料文件删除失败"); }
    }

    private Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) throw new BusinessException(400, "文件路径不合法");
        return resolved;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredFile(String relativePath, String originalName, String type, String contentType, long size) {}
}
