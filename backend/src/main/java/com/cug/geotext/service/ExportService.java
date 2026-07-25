package com.cug.geotext.service;

import com.cug.geotext.common.BusinessException;
import com.cug.geotext.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
    private final DocumentService documents;
    private final GeologicalEntityService entities;
    private final KnowledgePersistenceService knowledge;
    private final SpatialObjectService spatial;
    private final ObjectMapper json;

    public ExportService(
            DocumentService documents,
            GeologicalEntityService entities,
            KnowledgePersistenceService knowledge,
            SpatialObjectService spatial,
            ObjectMapper json
    ) {
        this.documents = documents;
        this.entities = entities;
        this.knowledge = knowledge;
        this.spatial = spatial;
        this.json = json;
    }

    public ExportFile export(long documentId, String format, String dataset) {
        GeologicalDocument document = documents.get(documentId);
        List<GeologicalEntity> entityRows = entities.list(documentId);
        List<EntityAttribute> attributes = knowledge.attributes(documentId);
        List<EntityRelation> relations = knowledge.relations(documentId);
        List<SpatialObject> objects = spatial.list(documentId);
        String normalized = format == null ? "" : format.toLowerCase();
        ensureExportable(normalized, dataset, entityRows, attributes, relations, objects);
        String base = safe(document.getName());
        return switch (normalized) {
            case "xlsx" -> new ExportFile(
                    base + "-成果.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    excel(entityRows, attributes, relations, objects));
            case "json" -> new ExportFile(
                    base + "-成果.json",
                    "application/json",
                    json(document, entityRows, attributes, relations, objects));
            case "geojson" -> new ExportFile(
                    base + "-空间对象.geojson",
                    "application/geo+json",
                    geojson(objects));
            case "csv" -> new ExportFile(
                    base + "-" + (dataset == null ? "entities" : dataset) + ".csv",
                    "text/csv",
                    csv(dataset, entityRows, attributes, relations, objects));
            default -> throw new BusinessException(400, "导出格式仅支持 xlsx、csv、json、geojson");
        };
    }

    private void ensureExportable(
            String format,
            String dataset,
            List<GeologicalEntity> entities,
            List<EntityAttribute> attributes,
            List<EntityRelation> relations,
            List<SpatialObject> objects
    ) {
        boolean hasAny = !entities.isEmpty() || !attributes.isEmpty() || !relations.isEmpty() || !objects.isEmpty();
        if ("geojson".equals(format)) {
            if (objects.isEmpty()) {
                throw new BusinessException(409, "当前资料没有可导出的空间对象，请先完成空间化后再导出 GeoJSON。");
            }
            return;
        }
        if ("csv".equals(format)) {
            String target = dataset == null ? "entities" : dataset;
            boolean empty = switch (target) {
                case "attributes" -> attributes.isEmpty();
                case "relations" -> relations.isEmpty();
                case "spatial" -> objects.isEmpty();
                default -> entities.isEmpty();
            };
            if (empty) {
                throw new BusinessException(409, "当前资料的 " + csvDatasetLabel(target) + " 为空，请先完成识别/抽取后再导出。");
            }
            return;
        }
        if (!hasAny) {
            throw new BusinessException(409, "当前资料尚无可导出成果（实体/属性/关系/空间对象均为空）。请先完成实体识别、知识抽取与空间化，或改选“铜绿山矿段综合地质调查（标准演示）”等已有成果的资料。");
        }
    }

    private static String csvDatasetLabel(String dataset) {
        return switch (dataset) {
            case "attributes" -> "属性";
            case "relations" -> "关系";
            case "spatial" -> "空间对象";
            default -> "实体";
        };
    }

    private byte[] excel(
            List<GeologicalEntity> entities,
            List<EntityAttribute> attributes,
            List<EntityRelation> relations,
            List<SpatialObject> objects
    ) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            sheet(workbook, "实体", List.of("ID", "名称", "标准名称", "类型", "置信度", "页码", "原文"),
                    entities.stream().map(item -> cells(
                            item.getId(), item.getEntityName(), value(item.getStandardName()), item.getEntityType(),
                            item.getConfidence(), item.getPage(), item.getSourceText())).toList());
            sheet(workbook, "属性", List.of("ID", "实体ID", "属性类型", "值", "置信度", "页码", "原文"),
                    attributes.stream().map(item -> cells(
                            item.getId(), item.getEntityId(), item.getAttributeType(), item.getOriginalValue(),
                            item.getConfidence(), item.getPage(), item.getSourceText())).toList());
            sheet(workbook, "关系", List.of("ID", "源实体ID", "目标实体ID", "关系类型", "置信度", "页码", "原文"),
                    relations.stream().map(item -> cells(
                            item.getId(), item.getSourceEntityId(), item.getTargetEntityId(), item.getRelationType(),
                            item.getConfidence(), item.getPage(), item.getSourceText())).toList());
            sheet(workbook, "空间对象", List.of("ID", "名称", "类型", "几何", "经度", "纬度", "页码", "原文"),
                    objects.stream().map(item -> cells(
                            item.getId(), item.getName(), item.getObjectType(), item.getGeojson(),
                            item.getCenterLongitude(), item.getCenterLatitude(), item.getPage(), item.getSourceText())).toList());
            workbook.write(out);
            return out.toByteArray();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(500, "Excel 导出失败: " + exception.getMessage());
        }
    }

    private void sheet(Workbook workbook, String name, List<String> headers, List<List<Object>> rows) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        for (int index = 0; index < headers.size(); index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(headers.get(index));
            cell.setCellStyle(style);
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            List<Object> values = rows.get(rowIndex);
            for (int column = 0; column < values.size(); column++) {
                row.createCell(column).setCellValue(stringify(values.get(column)));
            }
        }
        for (int index = 0; index < headers.size(); index++) {
            sheet.setColumnWidth(index, Math.min(index == headers.size() - 1 ? 16000 : 6000, 255 * 256));
        }
    }

    private byte[] json(
            GeologicalDocument document,
            List<GeologicalEntity> entities,
            List<EntityAttribute> attributes,
            List<EntityRelation> relations,
            List<SpatialObject> objects
    ) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of(
                    "document", document,
                    "entities", entities,
                    "attributes", attributes,
                    "relations", relations,
                    "spatialObjects", objects));
        } catch (Exception exception) {
            throw new BusinessException(500, "JSON 导出失败");
        }
    }

    private byte[] geojson(List<SpatialObject> objects) {
        try {
            ObjectNode root = json.createObjectNode();
            root.put("type", "FeatureCollection");
            ArrayNode features = root.putArray("features");
            for (SpatialObject object : objects) {
                ObjectNode feature = features.addObject();
                feature.put("type", "Feature");
                feature.set("geometry", json.readTree(object.getGeojson()));
                ObjectNode properties = feature.putObject("properties");
                properties.put("id", object.getId());
                properties.put("name", object.getName());
                properties.put("objectType", object.getObjectType());
                properties.put("documentName", object.getDocumentName());
                properties.put("page", object.getPage());
                properties.put("sourceText", object.getSourceText());
                properties.put("confidence", object.getConfidence());
            }
            return json.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        } catch (Exception exception) {
            throw new BusinessException(500, "GeoJSON 导出失败");
        }
    }

    private byte[] csv(
            String dataset,
            List<GeologicalEntity> entities,
            List<EntityAttribute> attributes,
            List<EntityRelation> relations,
            List<SpatialObject> objects
    ) {
        List<String> header;
        List<List<Object>> rows;
        switch (dataset == null ? "entities" : dataset) {
            case "attributes" -> {
                header = List.of("id", "entityId", "attributeType", "value", "confidence", "page", "sourceText");
                rows = attributes.stream().map(item -> cells(
                        item.getId(), item.getEntityId(), item.getAttributeType(), item.getOriginalValue(),
                        item.getConfidence(), item.getPage(), item.getSourceText())).toList();
            }
            case "relations" -> {
                header = List.of("id", "sourceEntityId", "targetEntityId", "relationType", "confidence", "page", "sourceText");
                rows = relations.stream().map(item -> cells(
                        item.getId(), item.getSourceEntityId(), item.getTargetEntityId(), item.getRelationType(),
                        item.getConfidence(), item.getPage(), item.getSourceText())).toList();
            }
            case "spatial" -> {
                header = List.of("id", "name", "objectType", "geojson", "longitude", "latitude", "page", "sourceText");
                rows = objects.stream().map(item -> cells(
                        item.getId(), item.getName(), item.getObjectType(), item.getGeojson(),
                        item.getCenterLongitude(), item.getCenterLatitude(), item.getPage(), item.getSourceText())).toList();
            }
            default -> {
                header = List.of("id", "name", "standardName", "entityType", "confidence", "page", "sourceText");
                rows = entities.stream().map(item -> cells(
                        item.getId(), item.getEntityName(), value(item.getStandardName()), item.getEntityType(),
                        item.getConfidence(), item.getPage(), item.getSourceText())).toList();
            }
        }
        StringBuilder out = new StringBuilder("\ufeff");
        append(out, header);
        for (List<Object> row : rows) {
            append(out, row);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void append(StringBuilder out, List<?> row) {
        List<String> values = new ArrayList<>(row.size());
        for (Object value : row) {
            values.add("\"" + stringify(value).replace("\"", "\"\"") + "\"");
        }
        out.append(String.join(",", values)).append("\r\n");
    }

    private static List<Object> cells(Object... values) {
        List<Object> row = new ArrayList<>(values.length);
        for (Object value : values) {
            row.add(value);
        }
        return row;
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String safe(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public record ExportFile(String filename, String contentType, byte[] content) {}
}
