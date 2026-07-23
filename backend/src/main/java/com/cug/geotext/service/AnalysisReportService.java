package com.cug.geotext.service;

import com.cug.geotext.common.BusinessException;
import com.cug.geotext.entity.EntityAttribute;
import com.cug.geotext.entity.EntityRelation;
import com.cug.geotext.entity.GeologicalDocument;
import com.cug.geotext.entity.GeologicalEntity;
import com.cug.geotext.entity.SpatialObject;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalysisReportService {
    private final DocumentService documents;
    private final GeologicalEntityService entities;
    private final KnowledgePersistenceService knowledge;
    private final SpatialObjectService spatial;

    public AnalysisReportService(DocumentService documents, GeologicalEntityService entities,
                                 KnowledgePersistenceService knowledge, SpatialObjectService spatial) {
        this.documents = documents; this.entities = entities; this.knowledge = knowledge; this.spatial = spatial;
    }

    public ReportView report(long documentId) {
        GeologicalDocument document = documents.get(documentId);
        List<GeologicalEntity> entityRows = entities.list(documentId);
        List<EntityAttribute> attributes = knowledge.attributes(documentId);
        List<EntityRelation> relations = knowledge.relations(documentId);
        List<SpatialObject> objects = spatial.list(documentId);
        return new ReportView(document, new Summary(entityRows.size(), attributes.size(), relations.size(), objects.size()),
            entityRows, attributes, relations, objects, OffsetDateTime.now());
    }

    public ReportFile pdf(long documentId) {
        ReportView view = report(documentId);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document pdf = new Document(PageSize.A4, 42, 42, 46, 42);
            PdfWriter.getInstance(pdf, output);
            pdf.open();
            Font title = font(20, Font.BOLD, new Color(22, 67, 58));
            Font heading = font(13, Font.BOLD, new Color(181, 99, 45));
            Font body = font(9, Font.NORMAL, Color.DARK_GRAY);
            Font small = font(8, Font.NORMAL, new Color(90, 105, 99));

            Paragraph name = new Paragraph("基于填图对象智能识别的文本空间化算法模块", title);
            name.setAlignment(Element.ALIGN_CENTER); pdf.add(name);
            Paragraph subtitle = new Paragraph("地质文本空间化分析报告", font(15, Font.BOLD, Color.BLACK));
            subtitle.setAlignment(Element.ALIGN_CENTER); subtitle.setSpacingAfter(18); pdf.add(subtitle);
            addMeta(pdf, view, body);
            addHeading(pdf, "一、成果概览", heading);
            PdfPTable metrics = new PdfPTable(4); metrics.setWidthPercentage(100);
            metric(metrics, "实体", view.summary().entityCount(), body);
            metric(metrics, "属性", view.summary().attributeCount(), body);
            metric(metrics, "关系", view.summary().relationCount(), body);
            metric(metrics, "空间对象", view.summary().spatialObjectCount(), body);
            pdf.add(metrics);

            addHeading(pdf, "二、地质实体识别结果", heading);
            PdfPTable entityTable = table(new String[]{"名称", "类型", "置信度", "页码", "来源原文"}, body);
            for (GeologicalEntity item : view.entities()) row(entityTable, body, value(item.getStandardName(), item.getEntityName()), item.getEntityType(), percent(item.getConfidence()), String.valueOf(item.getPage()), item.getSourceText());
            pdf.add(entityTable);

            addHeading(pdf, "三、属性与关系抽取", heading);
            for (EntityAttribute item : view.attributes()) pdf.add(new Paragraph("属性  " + attributeLabel(item.getAttributeType()) + " = " + item.getOriginalValue() + "（第 " + item.getPage() + " 页）", body));
            Map<Long, String> names = view.entities().stream().collect(Collectors.toMap(GeologicalEntity::getId, item -> value(item.getStandardName(), item.getEntityName())));
            for (EntityRelation item : view.relations()) pdf.add(new Paragraph("关系  " + names.getOrDefault(item.getSourceEntityId(), String.valueOf(item.getSourceEntityId())) + " — " + relationLabel(item.getRelationType()) + " → " + names.getOrDefault(item.getTargetEntityId(), String.valueOf(item.getTargetEntityId())) + "（第 " + item.getPage() + " 页）", body));

            addHeading(pdf, "四、空间化成果", heading);
            PdfPTable spatialTable = table(new String[]{"对象", "类型", "几何", "中心坐标", "来源页码"}, body);
            for (SpatialObject item : view.spatialObjects()) row(spatialTable, body, item.getName(), item.getObjectType(), item.getGeometryType(), item.getCenterLongitude() + ", " + item.getCenterLatitude(), String.valueOf(item.getPage()));
            pdf.add(spatialTable);

            addHeading(pdf, "五、结论与可追溯性说明", heading);
            pdf.add(new Paragraph("本报告由系统根据已导入资料及人工校核结果生成。每项实体、属性、关系和空间对象均保留来源页码与原文证据，可在系统中定位复核。", body));
            Paragraph footer = new Paragraph("生成时间：" + view.generatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")), small);
            footer.setSpacingBefore(16); pdf.add(footer);
            pdf.close();
            String filename = safe(view.document().getName()) + "-空间化分析报告.pdf";
            return new ReportFile(filename, "application/pdf", output.toByteArray());
        } catch (Exception exception) {
            throw new BusinessException(500, "分析报告生成失败: " + exception.getMessage());
        }
    }

    private void addMeta(Document pdf, ReportView view, Font font) throws Exception {
        GeologicalDocument d = view.document();
        PdfPTable table = new PdfPTable(2); table.setWidthPercentage(100); table.setWidths(new float[]{1, 3});
        row(table, font, "资料名称", d.getName()); row(table, font, "资料类型", d.getType());
        row(table, font, "所属区域", value(d.getRegion(), "未填写")); row(table, font, "编制年份", d.getYear() == null ? "未填写" : String.valueOf(d.getYear()));
        row(table, font, "关键词", value(d.getKeyword(), "未填写")); row(table, font, "资料摘要", value(d.getSummary(), "未填写"));
        pdf.add(table);
    }
    private void addHeading(Document pdf, String text, Font font) throws Exception { Paragraph p = new Paragraph(text, font); p.setSpacingBefore(15); p.setSpacingAfter(7); pdf.add(p); }
    private PdfPTable table(String[] headers, Font font) { PdfPTable table = new PdfPTable(headers.length); table.setWidthPercentage(100); for (String h : headers) { PdfPCell c = new PdfPCell(new Phrase(h, font)); c.setBackgroundColor(new Color(224, 232, 226)); c.setPadding(6); table.addCell(c); } table.setHeaderRows(1); return table; }
    private void row(PdfPTable table, Font font, String... values) { for (String value : values) { PdfPCell c = new PdfPCell(new Phrase(value == null ? "" : value, font)); c.setPadding(5); c.setVerticalAlignment(Element.ALIGN_MIDDLE); table.addCell(c); } }
    private void metric(PdfPTable table, String label, int count, Font font) { PdfPCell c = new PdfPCell(new Phrase(label + "\n" + count, font)); c.setHorizontalAlignment(Element.ALIGN_CENTER); c.setPadding(10); table.addCell(c); }
    private Font font(float size, int style, Color color) throws Exception { Path path = Path.of("C:/Windows/Fonts/simhei.ttf"); BaseFont base = Files.exists(path) ? BaseFont.createFont(path.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED) : BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false); return new Font(base, size, style, color); }
    private String percent(java.math.BigDecimal value) { return value == null ? "—" : value.multiply(java.math.BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%"; }
    private String value(String primary, String fallback) { return primary == null || primary.isBlank() ? fallback : primary; }
    private String safe(String name) { return name.replaceAll("[\\\\/:*?\"<>|]", "_"); }
    private String attributeLabel(String type) { return switch (type) { case "AGE" -> "年代"; case "THICKNESS" -> "厚度"; case "SCALE" -> "规模"; case "GRADE" -> "品位"; case "LITHOLOGY" -> "岩性"; default -> type; }; }
    private String relationLabel(String type) { return switch (type) { case "LOCATED_IN" -> "位于"; case "OCCURS_IN" -> "赋存于"; case "INTRUDES" -> "侵入"; case "CONTACTS" -> "接触"; case "CONTROLS" -> "控制"; case "CONTAINS" -> "包含"; default -> type; }; }

    public record Summary(int entityCount, int attributeCount, int relationCount, int spatialObjectCount) {}
    public record ReportView(GeologicalDocument document, Summary summary, List<GeologicalEntity> entities,
                             List<EntityAttribute> attributes, List<EntityRelation> relations,
                             List<SpatialObject> spatialObjects, OffsetDateTime generatedAt) {}
    public record ReportFile(String filename, String contentType, byte[] content) {}
}
