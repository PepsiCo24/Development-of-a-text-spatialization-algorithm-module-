package com.cug.geotext.service;

import com.cug.geotext.dto.PastedDocumentRequest;
import com.cug.geotext.entity.GeologicalDocument;
import org.springframework.stereotype.Service;

@Service
public class DemoDataService {
    private final DocumentService documents;
    private final DocumentParsingService parsing;

    public DemoDataService(DocumentService documents, DocumentParsingService parsing) { this.documents = documents; this.parsing = parsing; }

    public GeologicalDocument restore(String username) {
        PastedDocumentRequest request = new PastedDocumentRequest("大冶矿区钻孔记录（标准演示）", "大冶矿区", 2025,
            "铜铁矿,钻孔,ZK001,断裂", "用于完整演示文本解析、实体识别、知识抽取和空间化的标准资料。",
            "大冶矿区钻孔 ZK001 地质记录\n\n钻孔位于铜绿山矿段，孔口坐标为东经114.9300°、北纬30.1100°，孔深286.50 m。\n\n0—38.20 m 为第四系覆盖层；38.20—126.40 m 为下三叠统大冶组灰岩。126.40—188.70 m 见闪长玢岩，侵入大冶组灰岩。\n\n188.70—214.30 m 为铜铁矿体，厚度25.60 m，主要矿物为黄铜矿、磁铁矿，铜平均品位1.26%，铁平均品位38.40%。矿体受北东向F1断裂控制，并赋存于矽卡岩化带。\n\nF1断裂走向北东，倾向南东，倾角68°，与闪长玢岩接触带共同控制矿化。");
        GeologicalDocument document = documents.paste(request, username);
        parsing.start(document.getId());
        return document;
    }
}
