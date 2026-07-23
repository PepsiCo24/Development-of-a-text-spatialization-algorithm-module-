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
    private static final String CONTENT = """
        大冶矿区铜绿山矿段综合地质记录

        调查区域位于湖北省大冶市铜绿山矿段，范围角点依次为东经114.9280°、北纬30.0770°，东经114.9500°、北纬30.0770°，东经114.9500°、北纬30.0910°，东经114.9280°、北纬30.0910°（WGS84），面积约3.20 km²。

        钻孔ZK001孔口坐标为东经114.9384°、北纬30.0840°（WGS84），孔深286.50 m。0-38.20 m为第四系覆盖层；38.20-126.40 m为下三叠统大冶组灰岩，地质年代为早三叠世，主要岩性为中厚层状灰岩。

        126.40-188.70 m见闪长玢岩，闪长玢岩侵入大冶组灰岩，并与矽卡岩化带呈接触关系。

        188.70-214.30 m为Ⅰ号铜铁矿体，厚度25.60 m，延伸规模约420 m，主要矿物为黄铜矿、磁铁矿，铜平均品位1.26%，铁平均品位38.40%。Ⅰ号铜铁矿体赋存于矽卡岩化带并包含黄铜矿和磁铁矿。

        F1断裂从东经114.9310°、北纬30.0790°延伸至东经114.9470°、北纬30.0890°，走向北东，倾向南东，倾角68°。F1断裂控制Ⅰ号铜铁矿体及矽卡岩型矿化。
        """;

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
        this.documents = documents; this.chunks = chunks; this.entities = entities; this.knowledge = knowledge;
        this.dictionary = dictionary; this.spatial = spatial; this.graph = graph; this.documentMapper = documentMapper; this.json = json;
    }

    public GeologicalDocument restore(String username) {
        GeologicalDocument document = documents.paste(new PastedDocumentRequest(
            "大冶矿区综合地质记录（标准演示）", "大冶矿区", 2025,
            "铜铁矿,钻孔,ZK001,F1断裂,矽卡岩,点线面",
            "覆盖全部核心实体、五类属性、六类关系以及点线面空间对象的确定性标准演示资料。", CONTENT), username);

        chunks.replace(document.getId(), List.of(new AiParseResponse.AiChunk(0, "大冶矿区综合地质记录", CONTENT, 1, 1, CONTENT.length())));
        DocumentChunk chunk = chunks.list(document.getId()).get(0);
        Map<String, GeologicalEntity> e = new LinkedHashMap<>();
        add(e, document.getId(), chunk, "大冶矿区", "PLACE", "0.98");
        add(e, document.getId(), chunk, "铜绿山矿段", "PLACE", "0.97");
        add(e, document.getId(), chunk, "ZK001", "COORDINATE", "0.99");
        add(e, document.getId(), chunk, "第四系覆盖层", "STRATUM", "0.94");
        add(e, document.getId(), chunk, "下三叠统大冶组", "STRATUM", "0.98");
        add(e, document.getId(), chunk, "早三叠世", "GEOLOGICAL_AGE", "0.96");
        add(e, document.getId(), chunk, "灰岩", "LITHOLOGY", "0.95");
        add(e, document.getId(), chunk, "闪长玢岩", "ROCK_BODY", "0.97");
        add(e, document.getId(), chunk, "矽卡岩化带", "MINERALIZATION_ZONE", "0.96");
        add(e, document.getId(), chunk, "Ⅰ号铜铁矿体", "ORE_BODY", "0.99");
        add(e, document.getId(), chunk, "黄铜矿", "MINERAL", "0.98");
        add(e, document.getId(), chunk, "磁铁矿", "MINERAL", "0.98");
        add(e, document.getId(), chunk, "1.26%", "GRADE", "0.99");
        add(e, document.getId(), chunk, "38.40%", "GRADE", "0.99");
        add(e, document.getId(), chunk, "25.60 m", "THICKNESS", "0.99");
        add(e, document.getId(), chunk, "F1断裂", "FAULT", "0.99");
        add(e, document.getId(), chunk, "南东", "DIP_DIRECTION", "0.96");
        add(e, document.getId(), chunk, "68°", "DIP_ANGLE", "0.99");
        int normalized = dictionary.normalize(new ArrayList<>(e.values()));

        attribute(document.getId(), e.get("下三叠统大冶组"), "AGE", "早三叠世", "0.96", "地质年代为早三叠世");
        attribute(document.getId(), e.get("Ⅰ号铜铁矿体"), "THICKNESS", "25.60 m", "0.99", "Ⅰ号铜铁矿体，厚度25.60 m");
        attribute(document.getId(), e.get("Ⅰ号铜铁矿体"), "SCALE", "延伸约420 m", "0.93", "延伸规模约420 m");
        attribute(document.getId(), e.get("Ⅰ号铜铁矿体"), "GRADE", "Cu 1.26%；Fe 38.40%", "0.98", "铜平均品位1.26%，铁平均品位38.40%");
        attribute(document.getId(), e.get("下三叠统大冶组"), "LITHOLOGY", "中厚层状灰岩", "0.95", "主要岩性为中厚层状灰岩");

        relation(document.getId(), e.get("ZK001"), e.get("铜绿山矿段"), "LOCATED_IN", "0.98", "钻孔ZK001位于铜绿山矿段");
        relation(document.getId(), e.get("Ⅰ号铜铁矿体"), e.get("矽卡岩化带"), "OCCURS_IN", "0.98", "Ⅰ号铜铁矿体赋存于矽卡岩化带");
        relation(document.getId(), e.get("闪长玢岩"), e.get("下三叠统大冶组"), "INTRUDES", "0.97", "闪长玢岩侵入大冶组灰岩");
        relation(document.getId(), e.get("闪长玢岩"), e.get("矽卡岩化带"), "CONTACTS", "0.94", "闪长玢岩与矽卡岩化带呈接触关系");
        relation(document.getId(), e.get("F1断裂"), e.get("Ⅰ号铜铁矿体"), "CONTROLS", "0.99", "F1断裂控制Ⅰ号铜铁矿体");
        relation(document.getId(), e.get("Ⅰ号铜铁矿体"), e.get("黄铜矿"), "CONTAINS", "0.98", "Ⅰ号铜铁矿体包含黄铜矿和磁铁矿");

        List<AiSpatialResponse.AiSpatialObject> objects = List.of(
            object("ZK001钻孔", "BOREHOLE", e.get("ZK001"), chunk, point(114.9384, 30.0840), "0.99", "钻孔ZK001孔口坐标为东经114.9384°、北纬30.0840°", "原文坐标"),
            object("Ⅰ号铜铁矿体", "MINERAL_POINT", e.get("Ⅰ号铜铁矿体"), chunk, point(114.9392, 30.0836), "0.93", "188.70-214.30 m为Ⅰ号铜铁矿体", "钻孔位置推定"),
            object("F1断裂", "FAULT", e.get("F1断裂"), chunk, line(new double[][]{{114.9310,30.0790},{114.9385,30.0838},{114.9470,30.0890}}), "0.99", "F1断裂从东经114.9310°、北纬30.0790°延伸至东经114.9470°、北纬30.0890°", "原文端点坐标"),
            object("铜绿山调查区域", "SURVEY_AREA", e.get("铜绿山矿段"), chunk, polygon(new double[][]{{114.9280,30.0770},{114.9500,30.0770},{114.9500,30.0910},{114.9280,30.0910},{114.9280,30.0770}}), "0.99", "调查区域范围角点依次为四组WGS84坐标", "原文范围角点")
        );
        spatial.replace(document.getId(), new AiSpatialResponse("demo", "verified-standard-demo", objects, List.of()));

        OffsetDateTime now = OffsetDateTime.now();
        document.setStatus("PARSED"); document.setParseProgress(100); document.setPageCount(1); document.setChunkCount(1); document.setParsedAt(now);
        document.setEntityStatus("COMPLETED"); document.setEntityProgress(100); document.setEntityCount(e.size()); document.setEntityExtractedAt(now);
        document.setKnowledgeStatus("COMPLETED"); document.setKnowledgeProgress(100); document.setAttributeCount(5); document.setRelationCount(6); document.setNormalizedCount(normalized); document.setKnowledgeExtractedAt(now);
        document.setSpatialStatus("COMPLETED"); document.setSpatialProgress(100); document.setSpatialObjectCount(objects.size()); document.setSpatialWarnings(""); document.setSpatialExtractedAt(now);
        document.setGraphStatus("PENDING"); document.setGraphProgress(0); document.setUpdateTime(now); documentMapper.updateById(document);
        graph.start(document.getId());
        return documentMapper.selectById(document.getId());
    }

    private void add(Map<String, GeologicalEntity> map, long documentId, DocumentChunk chunk, String name, String type, String confidence) {
        int start = CONTENT.indexOf(name); String evidence = sentence(name);
        GeologicalEntity item = entities.create(documentId, new ManualEntityRequest(chunk.getId(), name, type, new BigDecimal(confidence), evidence, 1, start, start < 0 ? null : start + name.length(), "CONFIRMED"));
        map.put(name, item);
    }
    private void attribute(long documentId, GeologicalEntity entity, String type, String value, String confidence, String evidence) { knowledge.createAttribute(documentId, new ManualAttributeRequest(entity.getId(), type, value, new BigDecimal(confidence), evidence, 1, "CONFIRMED")); }
    private void relation(long documentId, GeologicalEntity source, GeologicalEntity target, String type, String confidence, String evidence) { knowledge.createRelation(documentId, new ManualRelationRequest(source.getId(), target.getId(), type, new BigDecimal(confidence), evidence, 1, "CONFIRMED")); }
    private AiSpatialResponse.AiSpatialObject object(String name, String type, GeologicalEntity entity, DocumentChunk chunk, JsonNode geometry, String confidence, String evidence, String source) { return new AiSpatialResponse.AiSpatialObject(name, type, entity.getId(), chunk.getId(), geometry, new BigDecimal(confidence), evidence, 1, source); }
    private JsonNode point(double x, double y) { return json.valueToTree(Map.of("type", "Point", "coordinates", List.of(x, y))); }
    private JsonNode line(double[][] values) { return json.valueToTree(Map.of("type", "LineString", "coordinates", coordinates(values))); }
    private JsonNode polygon(double[][] values) { return json.valueToTree(Map.of("type", "Polygon", "coordinates", List.of(coordinates(values)))); }
    private List<List<Double>> coordinates(double[][] values) { List<List<Double>> result = new ArrayList<>(); for (double[] value : values) result.add(List.of(value[0], value[1])); return result; }
    private String sentence(String name) { int at = CONTENT.indexOf(name); if (at < 0) return name; int left = Math.max(CONTENT.lastIndexOf('。', at) + 1, CONTENT.lastIndexOf('\n', at) + 1); int right = CONTENT.indexOf('。', at); if (right < 0) right = Math.min(CONTENT.length(), at + 160); return CONTENT.substring(left, Math.min(CONTENT.length(), right + 1)).trim(); }
}
