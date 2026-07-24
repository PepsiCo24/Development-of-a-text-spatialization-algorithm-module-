package com.cug.geotext.service;

import com.cug.geotext.client.AiParseResponse;
import com.cug.geotext.client.AiSpatialResponse;
import com.cug.geotext.dto.ManualAttributeRequest;
import com.cug.geotext.dto.ManualEntityRequest;
import com.cug.geotext.dto.ManualRelationRequest;
import com.cug.geotext.dto.PastedDocumentRequest;
import com.cug.geotext.entity.DocumentChunk;
import com.cug.geotext.entity.GeologicalDocument;
import com.cug.geotext.entity.GeologicalEntity;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DemoDataService {
    private static final List<String> SECTION_TITLES = List.of(
        "第一章 调查区概况",
        "第二章 地层、岩性与岩体",
        "第三章 构造特征",
        "第四章 钻孔记录",
        "第五章 矿体与矿化"
    );

    private static final List<String> SECTIONS = List.of(
        """
        第一章 调查区概况
        铜绿山矿段位于湖北省大冶市。调查范围采用WGS84坐标系，四个角点依次为东经114.9280°、北纬30.0770°，东经114.9500°、北纬30.0770°，东经114.9500°、北纬30.0910°，东经114.9280°、北纬30.0910°，面积约3.20 km²。
        """.trim(),
        """
        第二章 地层、岩性与岩体
        矿段主要出露下三叠统大冶组，地质年代为早三叠世，主要岩性为中厚层状灰岩。燕山期闪长玢岩侵入下三叠统大冶组，并与接触带形成的矽卡岩化带呈接触关系。
        """.trim(),
        """
        第三章 构造特征
        F1断裂从东经114.9310°、北纬30.0790°延伸至东经114.9470°、北纬30.0890°，走向北东，倾向南东，倾角68°。F1断裂控制Ⅰ号铜铁矿体及矽卡岩型矿化。
        """.trim(),
        """
        第四章 钻孔记录
        ZK001钻孔位于铜绿山矿段中部，孔口坐标为东经114.9384°、北纬30.0840°（WGS84），孔深286.50 m。0-38.20 m为第四系覆盖层；38.20-126.40 m为下三叠统大冶组灰岩；126.40-188.70 m为闪长玢岩；188.70-214.30 m揭露Ⅰ号铜铁矿体。
        """.trim(),
        """
        第五章 矿体与矿化
        Ⅰ号铜铁矿体位于铜绿山矿段，赋存于矽卡岩化带，厚度25.60 m，走向延伸约420 m。矿体铜平均品位1.26%，铁平均品位38.40%，主要包含黄铜矿和磁铁矿。
        """.trim()
    );

    private static final String CONTENT = "铜绿山矿段综合地质调查报告（演示数据）\n\n" + String.join("\n\n", SECTIONS);

    private final DocumentService documents;
    private final DocumentChunkService chunks;
    private final GeologicalEntityService entities;
    private final KnowledgePersistenceService knowledge;
    private final DictionaryService dictionary;
    private final SpatialObjectService spatial;
    private final GraphSyncService graph;
    private final GeologicalDocumentMapper documentMapper;
    private final ObjectMapper json;

    public DemoDataService(DocumentService documents, DocumentChunkService chunks, GeologicalEntityService entities,
                           KnowledgePersistenceService knowledge, DictionaryService dictionary,
                           SpatialObjectService spatial, GraphSyncService graph,
                           GeologicalDocumentMapper documentMapper, ObjectMapper json) {
        this.documents = documents;
        this.chunks = chunks;
        this.entities = entities;
        this.knowledge = knowledge;
        this.dictionary = dictionary;
        this.spatial = spatial;
        this.graph = graph;
        this.documentMapper = documentMapper;
        this.json = json;
    }

    public GeologicalDocument restore(String username) {
        GeologicalDocument document = documents.paste(new PastedDocumentRequest(
            "铜绿山矿段综合地质调查（标准演示）", "湖北省大冶市", 2026,
            "铜绿山,铜铁矿,ZK001,F1断裂,矽卡岩,点线面",
            "覆盖重要实体、五类核心属性、六类地质关系、真实坐标点线面和逐页证据定位的标准演示资料。", CONTENT), username);

        List<AiParseResponse.AiChunk> parsedChunks = new ArrayList<>();
        for (int index = 0; index < SECTIONS.size(); index++) {
            parsedChunks.add(new AiParseResponse.AiChunk(index, SECTION_TITLES.get(index), SECTIONS.get(index),
                index + 1, index + 1, SECTIONS.get(index).length()));
        }
        chunks.replace(document.getId(), parsedChunks);
        List<DocumentChunk> c = chunks.list(document.getId());

        Map<String, GeologicalEntity> e = new LinkedHashMap<>();
        add(e, document.getId(), c.get(0), "铜绿山矿段", "PLACE", "0.99");
        add(e, document.getId(), c.get(0), "湖北省大冶市", "PLACE", "0.98");
        add(e, document.getId(), c.get(1), "下三叠统大冶组", "STRATUM", "0.99");
        add(e, document.getId(), c.get(1), "早三叠世", "GEOLOGICAL_AGE", "0.98");
        add(e, document.getId(), c.get(1), "中厚层状灰岩", "LITHOLOGY", "0.97");
        add(e, document.getId(), c.get(1), "闪长玢岩", "ROCK_BODY", "0.98");
        add(e, document.getId(), c.get(1), "矽卡岩化带", "MINERALIZATION_ZONE", "0.98");
        add(e, document.getId(), c.get(2), "F1断裂", "FAULT", "0.99");
        add(e, document.getId(), c.get(2), "南东", "DIP_DIRECTION", "0.97");
        add(e, document.getId(), c.get(2), "68°", "DIP_ANGLE", "0.99");
        add(e, document.getId(), c.get(3), "东经114.9384°、北纬30.0840°", "COORDINATE", "0.99");
        add(e, document.getId(), c.get(3), "第四系覆盖层", "STRATUM", "0.96");
        add(e, document.getId(), c.get(4), "Ⅰ号铜铁矿体", "ORE_BODY", "0.99");
        add(e, document.getId(), c.get(4), "25.60 m", "THICKNESS", "0.99");
        add(e, document.getId(), c.get(4), "1.26%", "GRADE", "0.99");
        add(e, document.getId(), c.get(4), "38.40%", "GRADE", "0.99");
        add(e, document.getId(), c.get(4), "黄铜矿", "MINERAL", "0.98");
        add(e, document.getId(), c.get(4), "磁铁矿", "MINERAL", "0.98");
        int normalized = dictionary.normalize(new ArrayList<>(e.values()));

        attribute(document.getId(), e.get("下三叠统大冶组"), "AGE", "早三叠世", "0.98", "地质年代为早三叠世", 2);
        attribute(document.getId(), e.get("下三叠统大冶组"), "LITHOLOGY", "中厚层状灰岩", "0.97", "主要岩性为中厚层状灰岩", 2);
        attribute(document.getId(), e.get("Ⅰ号铜铁矿体"), "THICKNESS", "25.60 m", "0.99", "Ⅰ号铜铁矿体厚度25.60 m", 5);
        attribute(document.getId(), e.get("Ⅰ号铜铁矿体"), "SCALE", "走向延伸约420 m", "0.95", "走向延伸约420 m", 5);
        attribute(document.getId(), e.get("Ⅰ号铜铁矿体"), "GRADE", "Cu 1.26%；Fe 38.40%", "0.99", "铜平均品位1.26%，铁平均品位38.40%", 5);
        attribute(document.getId(), e.get("F1断裂"), "DIP_DIRECTION", "南东", "0.97", "倾向南东", 3);
        attribute(document.getId(), e.get("F1断裂"), "DIP_ANGLE", "68°", "0.99", "倾角68°", 3);

        relation(document.getId(), e.get("Ⅰ号铜铁矿体"), e.get("铜绿山矿段"), "LOCATED_IN", "0.99", "Ⅰ号铜铁矿体位于铜绿山矿段", 5);
        relation(document.getId(), e.get("Ⅰ号铜铁矿体"), e.get("矽卡岩化带"), "OCCURS_IN", "0.99", "Ⅰ号铜铁矿体赋存于矽卡岩化带", 5);
        relation(document.getId(), e.get("闪长玢岩"), e.get("下三叠统大冶组"), "INTRUDES", "0.98", "闪长玢岩侵入下三叠统大冶组", 2);
        relation(document.getId(), e.get("闪长玢岩"), e.get("矽卡岩化带"), "CONTACTS", "0.96", "闪长玢岩与矽卡岩化带呈接触关系", 2);
        relation(document.getId(), e.get("F1断裂"), e.get("Ⅰ号铜铁矿体"), "CONTROLS", "0.99", "F1断裂控制Ⅰ号铜铁矿体", 3);
        relation(document.getId(), e.get("Ⅰ号铜铁矿体"), e.get("黄铜矿"), "CONTAINS", "0.98", "矿体主要包含黄铜矿和磁铁矿", 5);

        List<AiSpatialResponse.AiSpatialObject> objects = List.of(
            object("ZK001钻孔", "BOREHOLE", e.get("东经114.9384°、北纬30.0840°"), c.get(3),
                point(114.9384, 30.0840), "0.99", "孔口坐标为东经114.9384°、北纬30.0840°（WGS84）", 4, "原文WGS84坐标"),
            object("Ⅰ号铜铁矿体", "MINERAL_POINT", e.get("Ⅰ号铜铁矿体"), c.get(4),
                point(114.9392, 30.0836), "0.93", "188.70-214.30 m揭露Ⅰ号铜铁矿体", 4, "钻孔位置约束推定"),
            object("F1断裂", "FAULT", e.get("F1断裂"), c.get(2),
                line(new double[][]{{114.9310, 30.0790}, {114.9385, 30.0838}, {114.9470, 30.0890}}), "0.99",
                "F1断裂从东经114.9310°、北纬30.0790°延伸至东经114.9470°、北纬30.0890°", 3, "原文端点坐标"),
            object("铜绿山调查区域", "SURVEY_AREA", e.get("铜绿山矿段"), c.get(0),
                polygon(new double[][]{{114.9280, 30.0770}, {114.9500, 30.0770}, {114.9500, 30.0910}, {114.9280, 30.0910}, {114.9280, 30.0770}}), "0.99",
                "调查范围由四个WGS84角点围定", 1, "原文范围角点")
        );
        spatial.replace(document.getId(), new AiSpatialResponse("demo", "verified-standard-demo-v2", objects, List.of()));

        OffsetDateTime now = OffsetDateTime.now();
        document.setStatus("PARSED");
        document.setParseProgress(100);
        document.setPageCount(SECTIONS.size());
        document.setChunkCount(SECTIONS.size());
        document.setParsedAt(now);
        document.setEntityStatus("COMPLETED");
        document.setEntityProgress(100);
        document.setEntityCount(e.size());
        document.setEntityExtractedAt(now);
        document.setKnowledgeStatus("COMPLETED");
        document.setKnowledgeProgress(100);
        document.setAttributeCount(7);
        document.setRelationCount(6);
        document.setNormalizedCount(normalized);
        document.setKnowledgeExtractedAt(now);
        document.setSpatialStatus("COMPLETED");
        document.setSpatialProgress(100);
        document.setSpatialObjectCount(objects.size());
        document.setSpatialWarnings("");
        document.setSpatialExtractedAt(now);
        document.setGraphStatus("PENDING");
        document.setGraphProgress(0);
        document.setUpdateTime(now);
        documentMapper.updateById(document);
        graph.start(document.getId());
        return documentMapper.selectById(document.getId());
    }

    private void add(Map<String, GeologicalEntity> map, long documentId, DocumentChunk chunk,
                     String name, String type, String confidence) {
        int start = chunk.getContent().indexOf(name);
        String evidence = sentence(chunk.getContent(), name);
        GeologicalEntity item = entities.create(documentId, new ManualEntityRequest(
            chunk.getId(), name, type, new BigDecimal(confidence), evidence, chunk.getPageStart(), start,
            start < 0 ? null : start + name.length(), "CONFIRMED"));
        map.put(name, item);
    }

    private void attribute(long documentId, GeologicalEntity entity, String type, String value,
                           String confidence, String evidence, int page) {
        knowledge.createAttribute(documentId, new ManualAttributeRequest(
            entity.getId(), type, value, new BigDecimal(confidence), evidence, page, "CONFIRMED"));
    }

    private void relation(long documentId, GeologicalEntity source, GeologicalEntity target, String type,
                          String confidence, String evidence, int page) {
        knowledge.createRelation(documentId, new ManualRelationRequest(
            source.getId(), target.getId(), type, new BigDecimal(confidence), evidence, page, "CONFIRMED"));
    }

    private AiSpatialResponse.AiSpatialObject object(String name, String type, GeologicalEntity entity,
                                                     DocumentChunk chunk, JsonNode geometry, String confidence,
                                                     String evidence, int page, String source) {
        return new AiSpatialResponse.AiSpatialObject(name, type, entity.getId(), chunk.getId(), geometry,
            new BigDecimal(confidence), evidence, page, source);
    }

    private JsonNode point(double x, double y) {
        return json.valueToTree(Map.of("type", "Point", "coordinates", List.of(x, y)));
    }

    private JsonNode line(double[][] values) {
        return json.valueToTree(Map.of("type", "LineString", "coordinates", coordinates(values)));
    }

    private JsonNode polygon(double[][] values) {
        return json.valueToTree(Map.of("type", "Polygon", "coordinates", List.of(coordinates(values))));
    }

    private List<List<Double>> coordinates(double[][] values) {
        List<List<Double>> result = new ArrayList<>();
        for (double[] value : values) {
            result.add(List.of(value[0], value[1]));
        }
        return result;
    }

    private String sentence(String content, String name) {
        int at = content.indexOf(name);
        if (at < 0) {
            return name;
        }
        int left = Math.max(content.lastIndexOf('。', at) + 1, content.lastIndexOf('\n', at) + 1);
        int right = content.indexOf('。', at);
        if (right < 0) {
            right = Math.min(content.length(), at + 160);
        }
        return content.substring(left, Math.min(content.length(), right + 1)).trim();
    }
}
