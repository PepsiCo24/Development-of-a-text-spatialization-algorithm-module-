package com.cug.geotext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.dto.DocumentMetadata;
import com.cug.geotext.dto.DocumentQuery;
import com.cug.geotext.dto.PageResult;
import com.cug.geotext.dto.PastedDocumentRequest;
import com.cug.geotext.entity.AppUser;
import com.cug.geotext.entity.GeologicalDocument;
import com.cug.geotext.mapper.AppUserMapper;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import com.cug.geotext.storage.FileStorageService;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private static final Set<String> STATUSES = Set.of("UPLOADED", "PARSING", "PARSED", "FAILED", "ARCHIVED");
    private final GeologicalDocumentMapper documentMapper;
    private final AppUserMapper userMapper;
    private final FileStorageService storage;

    public DocumentService(GeologicalDocumentMapper documentMapper, AppUserMapper userMapper, FileStorageService storage) {
        this.documentMapper = documentMapper;
        this.userMapper = userMapper;
        this.storage = storage;
    }

    @Transactional
    public GeologicalDocument upload(MultipartFile file, DocumentMetadata metadata, String username) {
        FileStorageService.StoredFile stored = storage.store(file);
        try {
            GeologicalDocument document = new GeologicalDocument();
            document.setName(StringUtils.hasText(metadata.name()) ? metadata.name().trim() : stored.originalName());
            document.setType(stored.type());
            document.setRegion(trimToNull(metadata.region()));
            document.setYear(metadata.year());
            document.setKeyword(trimToNull(metadata.keyword()));
            document.setSummary(trimToNull(metadata.summary()));
            document.setFilePath(stored.relativePath());
            document.setOriginalName(stored.originalName());
            document.setContentType(stored.contentType());
            document.setFileSize(stored.size());
            document.setStatus("UPLOADED");
            AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, username));
            if (user != null) document.setCreatedBy(user.getId());
            document.setCreateTime(OffsetDateTime.now());
            document.setUpdateTime(OffsetDateTime.now());
            documentMapper.insert(document);
            return document;
        } catch (RuntimeException exception) {
            storage.delete(stored.relativePath());
            throw exception;
        }
    }

    @Transactional
    public GeologicalDocument paste(PastedDocumentRequest request, String username) {
        FileStorageService.StoredFile stored = storage.storeText(request.name().trim(), request.content().trim());
        try {
            GeologicalDocument document = new GeologicalDocument();
            document.setName(request.name().trim());
            document.setType(stored.type());
            document.setRegion(trimToNull(request.region()));
            document.setYear(request.year());
            document.setKeyword(trimToNull(request.keyword()));
            document.setSummary(trimToNull(request.summary()));
            document.setFilePath(stored.relativePath());
            document.setOriginalName(stored.originalName());
            document.setContentType(stored.contentType());
            document.setFileSize(stored.size());
            document.setStatus("UPLOADED");
            AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, username));
            if (user != null) document.setCreatedBy(user.getId());
            document.setCreateTime(OffsetDateTime.now());
            document.setUpdateTime(OffsetDateTime.now());
            documentMapper.insert(document);
            return document;
        } catch (RuntimeException exception) {
            storage.delete(stored.relativePath());
            throw exception;
        }
    }

    public PageResult<GeologicalDocument> list(DocumentQuery query) {
        LambdaQueryWrapper<GeologicalDocument> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.query())) {
            String term = query.query().trim();
            wrapper.and(scope -> scope.like(GeologicalDocument::getName, term)
                .or().like(GeologicalDocument::getKeyword, term)
                .or().like(GeologicalDocument::getSummary, term));
        }
        wrapper.eq(StringUtils.hasText(query.type()), GeologicalDocument::getType, normalize(query.type()));
        wrapper.like(StringUtils.hasText(query.region()), GeologicalDocument::getRegion, trimToNull(query.region()));
        wrapper.eq(query.year() != null, GeologicalDocument::getYear, query.year());
        wrapper.eq(StringUtils.hasText(query.status()), GeologicalDocument::getStatus, normalize(query.status()));
        wrapper.orderByDesc(GeologicalDocument::getCreateTime);
        return PageResult.from(documentMapper.selectPage(new Page<>(query.pageOrDefault(), query.sizeOrDefault()), wrapper));
    }

    public GeologicalDocument get(long id) {
        GeologicalDocument document = documentMapper.selectById(id);
        if (document == null) throw new BusinessException(404, "资料不存在");
        return document;
    }

    public Resource preview(long id) { return storage.load(get(id).getFilePath()); }

    @Transactional
    public GeologicalDocument update(long id, DocumentMetadata metadata) {
        GeologicalDocument document = get(id);
        if (StringUtils.hasText(metadata.name())) document.setName(metadata.name().trim());
        document.setRegion(trimToNull(metadata.region()));
        document.setYear(metadata.year());
        document.setKeyword(trimToNull(metadata.keyword()));
        document.setSummary(trimToNull(metadata.summary()));
        document.setUpdateTime(OffsetDateTime.now());
        documentMapper.updateById(document);
        return document;
    }

    @Transactional
    public GeologicalDocument updateStatus(long id, String status) {
        String normalized = normalize(status);
        if (!STATUSES.contains(normalized)) throw new BusinessException(400, "资料状态不合法");
        GeologicalDocument document = get(id);
        document.setStatus(normalized);
        document.setUpdateTime(OffsetDateTime.now());
        documentMapper.updateById(document);
        return document;
    }

    @Transactional
    public void delete(long id) {
        GeologicalDocument document = get(id);
        storage.delete(document.getFilePath());
        documentMapper.deleteById(id);
    }

    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String normalize(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
}
