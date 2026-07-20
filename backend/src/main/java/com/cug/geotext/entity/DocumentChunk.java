package com.cug.geotext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("document_chunk")
public class DocumentChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String chapterTitle;
    private String content;
    private Integer pageStart;
    private Integer pageEnd;
    private Integer charCount;
    private OffsetDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getPageStart() { return pageStart; }
    public void setPageStart(Integer pageStart) { this.pageStart = pageStart; }
    public Integer getPageEnd() { return pageEnd; }
    public void setPageEnd(Integer pageEnd) { this.pageEnd = pageEnd; }
    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }
    public OffsetDateTime getCreateTime() { return createTime; }
    public void setCreateTime(OffsetDateTime createTime) { this.createTime = createTime; }
}

