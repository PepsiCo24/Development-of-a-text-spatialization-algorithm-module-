package com.cug.geotext.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cug.geotext.client.AiSpatialResponse;
import com.cug.geotext.entity.DocumentChunk;
import com.cug.geotext.entity.GeologicalDocument;
import com.cug.geotext.entity.GeologicalEntity;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SpatialExtractionServiceTest {
    @Test
    void sendsEvidenceToAiAndPersistsValidatedGeometry(){
        GeologicalDocumentMapper documentMapper=mock(GeologicalDocumentMapper.class);DocumentService documentService=mock(DocumentService.class);DocumentChunkService chunkService=mock(DocumentChunkService.class);GeologicalEntityService entityService=mock(GeologicalEntityService.class);SpatialObjectService objectService=mock(SpatialObjectService.class);
        GeologicalDocument document=new GeologicalDocument();document.setId(6L);document.setRegion("鄂东南");document.setKnowledgeStatus("COMPLETED");document.setSpatialStatus("PENDING");
        DocumentChunk chunk=new DocumentChunk();chunk.setId(12L);chunk.setContent("ZK12孔位于东经114.91度、北纬30.08度。");chunk.setPageStart(4);chunk.setPageEnd(4);
        GeologicalEntity entity=new GeologicalEntity();entity.setId(20L);entity.setChunkId(12L);entity.setEntityName("ZK12孔");entity.setStandardName("ZK12孔");entity.setEntityType("BOREHOLE");
        when(documentService.get(6L)).thenReturn(document);when(chunkService.list(6L)).thenReturn(List.of(chunk));when(entityService.list(6L)).thenReturn(List.of(entity));
        RestClient.Builder builder=RestClient.builder().baseUrl("http://localhost:8000");MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8000/api/v1/spatial/extract")).andExpect(content().json("""
          {"document_id":6,"provider":"qwen","region_hint":"鄂东南","chunks":[{"chunk_id":12,"content":"ZK12孔位于东经114.91度、北纬30.08度。","page_start":4,"page_end":4,"entities":[{"entity_id":20,"entity_name":"ZK12孔","entity_type":"BOREHOLE"}]}]}
          """)).andRespond(withSuccess("""
          {"provider":"qwen","model":"qwen-plus","warnings":[],"objects":[{"name":"ZK12孔","object_type":"BOREHOLE","entity_id":20,"chunk_id":12,"geometry":{"type":"Point","coordinates":[114.91,30.08]},"confidence":0.98,"source_text":"ZK12孔位于东经114.91度、北纬30.08度。","page":4}]}
          """,MediaType.APPLICATION_JSON));
        Executor direct=Runnable::run;SpatialExtractionService service=new SpatialExtractionService(documentMapper,documentService,chunkService,entityService,objectService,builder.build(),direct);
        SpatialExtractionService.SpatialStatus status=service.start(6L,"qwen");
        server.verify();assertThat(status.status()).isEqualTo("COMPLETED");assertThat(status.objectCount()).isEqualTo(1);verify(objectService).replace(eq(6L),any(AiSpatialResponse.class));verify(documentMapper,atLeastOnce()).updateById(document);
    }
}
