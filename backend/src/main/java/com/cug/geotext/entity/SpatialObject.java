package com.cug.geotext.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class SpatialObject {
    private Long id; private Long documentId; private String documentName; private Long entityId; private Long chunkId;
    private String name; private String objectType; private String geometryType; private String geojson;
    private BigDecimal centerLongitude; private BigDecimal centerLatitude; private BigDecimal confidence;
    private String sourceText; private Integer page; private String geocodingSource; private String provider; private String model; private OffsetDateTime createTime;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getDocumentId(){return documentId;} public void setDocumentId(Long v){documentId=v;}
    public String getDocumentName(){return documentName;} public void setDocumentName(String v){documentName=v;} public Long getEntityId(){return entityId;} public void setEntityId(Long v){entityId=v;}
    public Long getChunkId(){return chunkId;} public void setChunkId(Long v){chunkId=v;} public String getName(){return name;} public void setName(String v){name=v;}
    public String getObjectType(){return objectType;} public void setObjectType(String v){objectType=v;} public String getGeometryType(){return geometryType;} public void setGeometryType(String v){geometryType=v;}
    public String getGeojson(){return geojson;} public void setGeojson(String v){geojson=v;} public BigDecimal getCenterLongitude(){return centerLongitude;} public void setCenterLongitude(BigDecimal v){centerLongitude=v;}
    public BigDecimal getCenterLatitude(){return centerLatitude;} public void setCenterLatitude(BigDecimal v){centerLatitude=v;} public BigDecimal getConfidence(){return confidence;} public void setConfidence(BigDecimal v){confidence=v;}
    public String getSourceText(){return sourceText;} public void setSourceText(String v){sourceText=v;} public Integer getPage(){return page;} public void setPage(Integer v){page=v;}
    public String getGeocodingSource(){return geocodingSource;} public void setGeocodingSource(String v){geocodingSource=v;} public String getProvider(){return provider;} public void setProvider(String v){provider=v;}
    public String getModel(){return model;} public void setModel(String v){model=v;} public OffsetDateTime getCreateTime(){return createTime;} public void setCreateTime(OffsetDateTime v){createTime=v;}
}
