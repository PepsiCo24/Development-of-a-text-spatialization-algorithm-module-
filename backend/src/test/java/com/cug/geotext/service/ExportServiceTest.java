package com.cug.geotext.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.cug.geotext.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class ExportServiceTest {
    @Test
    void createsFourSheetWorkbookAndGeoJsonFeatureCollection()throws Exception{
        DocumentService documents=mock(DocumentService.class);GeologicalEntityService entities=mock(GeologicalEntityService.class);KnowledgePersistenceService knowledge=mock(KnowledgePersistenceService.class);SpatialObjectService spatial=mock(SpatialObjectService.class);
        GeologicalDocument document=new GeologicalDocument();document.setId(1L);document.setName("调查报告");when(documents.get(1L)).thenReturn(document);
        GeologicalEntity entity=new GeologicalEntity();entity.setId(2L);entity.setEntityName("北东向断裂");entity.setEntityType("FAULT");entity.setConfidence(new BigDecimal("0.95"));entity.setPage(2);entity.setSourceText("北东向断裂控制矿体。");when(entities.list(1L)).thenReturn(List.of(entity));when(knowledge.attributes(1L)).thenReturn(List.of());when(knowledge.relations(1L)).thenReturn(List.of());
        SpatialObject object=new SpatialObject();object.setId(3L);object.setName("北东向断裂");object.setObjectType("FAULT");object.setGeojson("{\"type\":\"LineString\",\"coordinates\":[[114.8,30.0],[115.0,30.2]]}");object.setCenterLongitude(new BigDecimal("114.9"));object.setCenterLatitude(new BigDecimal("30.1"));object.setConfidence(new BigDecimal("0.94"));object.setPage(2);object.setSourceText("断裂延伸。");object.setDocumentName("调查报告");when(spatial.list(1L)).thenReturn(List.of(object));
        ExportService service=new ExportService(documents,entities,knowledge,spatial,new ObjectMapper());

        ExportService.ExportFile workbook=service.export(1L,"xlsx","entities");ExportService.ExportFile geojson=service.export(1L,"geojson","spatial");

        try(var parsed=WorkbookFactory.create(new ByteArrayInputStream(workbook.content()))){assertThat(parsed.getNumberOfSheets()).isEqualTo(4);assertThat(parsed.getSheet("实体").getRow(1).getCell(1).getStringCellValue()).isEqualTo("北东向断裂");}
        assertThat(new String(geojson.content(),java.nio.charset.StandardCharsets.UTF_8)).contains("FeatureCollection","LineString","北东向断裂");
    }
}
