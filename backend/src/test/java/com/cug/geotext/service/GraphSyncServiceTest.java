package com.cug.geotext.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cug.geotext.entity.*;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GraphSyncServiceTest {
    @Test
    void mapsGeologicalKnowledgeToNeo4jAndVectorPayload(){
        GeologicalDocumentMapper mapper=mock(GeologicalDocumentMapper.class);DocumentService documents=mock(DocumentService.class);DocumentChunkService chunks=mock(DocumentChunkService.class);GeologicalEntityService entities=mock(GeologicalEntityService.class);KnowledgePersistenceService knowledge=mock(KnowledgePersistenceService.class);SpatialObjectService spatial=mock(SpatialObjectService.class);
        GeologicalDocument document=new GeologicalDocument();document.setId(4L);document.setName("鄂东南调查报告");document.setSpatialStatus("COMPLETED");document.setGraphStatus("PENDING");when(documents.get(4L)).thenReturn(document);
        DocumentChunk chunk=new DocumentChunk();chunk.setId(7L);chunk.setContent("一号矿体受北东向断裂控制。");chunk.setPageStart(2);chunk.setPageEnd(2);when(chunks.list(4L)).thenReturn(List.of(chunk));
        GeologicalEntity ore=entity(11,"一号矿体","ORE_BODY"),fault=entity(12,"北东向断裂","FAULT");when(entities.list(4L)).thenReturn(List.of(ore,fault));
        EntityRelation relation=new EntityRelation();relation.setSourceEntityId(12L);relation.setTargetEntityId(11L);relation.setRelationType("CONTROLS");relation.setConfidence(new BigDecimal("0.96"));relation.setSourceText("一号矿体受北东向断裂控制。");relation.setPage(2);when(knowledge.relations(4L)).thenReturn(List.of(relation));
        SpatialObject location=new SpatialObject();location.setEntityId(11L);location.setCenterLongitude(new BigDecimal("114.9"));location.setCenterLatitude(new BigDecimal("30.1"));when(spatial.list(4L)).thenReturn(List.of(location));
        RestClient.Builder builder=RestClient.builder().baseUrl("http://localhost:8000");MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();server.expect(requestTo("http://localhost:8000/api/v1/graph/sync")).andExpect(jsonPath("$.nodes[0].node_type").value("ORE_BODY")).andExpect(jsonPath("$.nodes[0].longitude").value(114.9)).andExpect(jsonPath("$.relations[0].relation_type").value("CONTROLS")).andExpect(jsonPath("$.chunks[0].content").value("一号矿体受北东向断裂控制。")).andRespond(withSuccess("{\"node_count\":2,\"relation_count\":1,\"vector_count\":1}",MediaType.APPLICATION_JSON));
        GraphSyncService service=new GraphSyncService(mapper,documents,chunks,entities,knowledge,spatial,builder.build(),Runnable::run);

        GraphSyncService.GraphStatus result=service.start(4L);

        server.verify();assertThat(result.status()).isEqualTo("COMPLETED");assertThat(result.nodeCount()).isEqualTo(2);assertThat(result.vectorCount()).isEqualTo(1);verify(mapper,atLeastOnce()).updateById(document);
    }

    private GeologicalEntity entity(long id,String name,String type){GeologicalEntity entity=new GeologicalEntity();entity.setId(id);entity.setEntityName(name);entity.setEntityType(type);entity.setSourceText(name);entity.setPage(2);return entity;}
}
