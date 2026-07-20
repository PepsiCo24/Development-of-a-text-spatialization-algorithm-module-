package com.cug.geotext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cug.geotext.client.AiParseResponse;
import com.cug.geotext.entity.DocumentChunk;
import com.cug.geotext.mapper.DocumentChunkMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentChunkService {
    private final DocumentChunkMapper chunkMapper;
    public DocumentChunkService(DocumentChunkMapper chunkMapper) { this.chunkMapper = chunkMapper; }

    public List<DocumentChunk> list(long documentId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
            .eq(DocumentChunk::getDocumentId, documentId).orderByAsc(DocumentChunk::getChunkIndex));
    }

    @Transactional
    public void replace(long documentId, List<AiParseResponse.AiChunk> chunks) {
        chunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, documentId));
        OffsetDateTime now = OffsetDateTime.now();
        for (AiParseResponse.AiChunk source : chunks) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(source.chunkIndex());
            chunk.setChapterTitle(source.chapterTitle());
            chunk.setContent(source.content());
            chunk.setPageStart(source.pageStart());
            chunk.setPageEnd(source.pageEnd());
            chunk.setCharCount(source.charCount());
            chunk.setCreateTime(now);
            chunkMapper.insert(chunk);
        }
    }
}

