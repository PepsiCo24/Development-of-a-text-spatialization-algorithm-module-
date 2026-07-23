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

import com.cug.geotext.client.AiKnowledgeResponse;
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

class KnowledgeExtractionServiceTest {
    @Test
    void extractsPersistsAndNormalizesKnowledge() {
        GeologicalDocumentMapper documentMapper=mock(GeologicalDocumentMapper.class); DocumentService documentService=mock(DocumentService.class);
        DocumentChunkService chunkService=mock(DocumentChunkService.class); GeologicalEntityService entityService=mock(GeologicalEntityService.class);
        KnowledgePersistenceService persistence=mock(KnowledgePersistenceService.class); DictionaryService dictionary=mock(DictionaryService.class);
        LlmConfigService llmConfigService=mock(LlmConfigService.class);
        GeologicalDocument document=new GeologicalDocument(); document.setId(9L); document.setEntityStatus("COMPLETED"); document.setKnowledgeStatus("PENDING");
        DocumentChunk chunk=new DocumentChunk();chunk.setId(20L);chunk.setContent("燕山期花岗岩体侵入寒武系灰岩。");chunk.setPageStart(2);chunk.setPageEnd(2);
        GeologicalEntity rock=entity(30L,20L,"燕山期花岗岩体","ROCK_BODY"),stratum=entity(31L,20L,"寒武系","STRATUM");
        when(documentService.get(9L)).thenReturn(document);when(chunkService.list(9L)).thenReturn(List.of(chunk));when(entityService.list(9L)).thenReturn(List.of(rock,stratum));when(dictionary.normalize(any())).thenReturn(1);
        RestClient.Builder builder=RestClient.builder().baseUrl("http://localhost:8000");MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8000/api/v1/knowledge/extract")).andExpect(content().json("""
          {"document_id":9,"provider":"deepseek","chunks":[{"chunk_id":20,"content":"燕山期花岗岩体侵入寒武系灰岩。","page_start":2,"page_end":2,"entities":[{"entity_id":30,"entity_name":"燕山期花岗岩体","entity_type":"ROCK_BODY"},{"entity_id":31,"entity_name":"寒武系","entity_type":"STRATUM"}]}]}
          """)).andRespond(withSuccess("""
          {"provider":"deepseek","model":"deepseek-chat","attributes":[{"entity_id":30,"attribute_type":"AGE","original_value":"燕山期","confidence":0.94,"source_text":"燕山期花岗岩体侵入寒武系灰岩。","page":2}],"relations":[{"source_entity_id":30,"target_entity_id":31,"relation_type":"INTRUDES","confidence":0.97,"source_text":"燕山期花岗岩体侵入寒武系灰岩。","page":2}]}
          """,MediaType.APPLICATION_JSON));
        Executor direct=Runnable::run;KnowledgeExtractionService service=new KnowledgeExtractionService(documentMapper,documentService,chunkService,entityService,persistence,dictionary,llmConfigService,builder.build(),direct);

        KnowledgeExtractionService.KnowledgeStatus status=service.start(9L,"deepseek");

        server.verify();assertThat(status.status()).isEqualTo("COMPLETED");assertThat(status.attributeCount()).isEqualTo(1);assertThat(status.relationCount()).isEqualTo(1);assertThat(status.normalizedCount()).isEqualTo(1);
        verify(persistence).replace(eq(9L),any(AiKnowledgeResponse.class));verify(documentMapper,atLeastOnce()).updateById(document);
    }
    private GeologicalEntity entity(long id,long chunkId,String name,String type){GeologicalEntity e=new GeologicalEntity();e.setId(id);e.setChunkId(chunkId);e.setEntityName(name);e.setEntityType(type);return e;}
}
