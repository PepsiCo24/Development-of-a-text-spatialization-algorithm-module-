package com.cug.geotext.mapper;

import com.cug.geotext.entity.SpatialObject;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SpatialObjectMapper {
    @Insert("""
        INSERT INTO spatial_object(document_id,entity_id,chunk_id,name,object_type,geometry_type,geojson,geometry,confidence,source_text,page,geocoding_source,provider,model,create_time)
        VALUES(#{documentId},#{entityId},#{chunkId},#{name},#{objectType},#{geometryType},CAST(#{geojson} AS jsonb),ST_SetSRID(ST_GeomFromGeoJSON(#{geojson}),4326),#{confidence},#{sourceText},#{page},#{geocodingSource},#{provider},#{model},#{createTime})
        """) @Options(useGeneratedKeys=true,keyProperty="id") int insert(SpatialObject object);
    @Delete("DELETE FROM spatial_object WHERE document_id=#{documentId}") int deleteByDocument(long documentId);
    @Select("""
        <script>SELECT s.id,s.document_id,d.name AS document_name,s.entity_id,s.chunk_id,s.name,s.object_type,s.geometry_type,
        ST_AsGeoJSON(s.geometry) AS geojson,ST_X(ST_Centroid(s.geometry)) AS center_longitude,ST_Y(ST_Centroid(s.geometry)) AS center_latitude,
        s.confidence,s.source_text,s.page,s.geocoding_source,s.provider,s.model,s.create_time
        FROM spatial_object s JOIN document d ON d.id=s.document_id
        <if test='documentId != null'>WHERE s.document_id=#{documentId}</if>
        ORDER BY s.document_id,s.page,s.id</script>
        """) List<SpatialObject> selectAll(@Param("documentId")Long documentId);
    @Select("""
        SELECT s.id,s.document_id,d.name AS document_name,s.entity_id,s.chunk_id,s.name,s.object_type,s.geometry_type,
        ST_AsGeoJSON(s.geometry) AS geojson,ST_X(ST_Centroid(s.geometry)) AS center_longitude,ST_Y(ST_Centroid(s.geometry)) AS center_latitude,
        s.confidence,s.source_text,s.page,s.geocoding_source,s.provider,s.model,s.create_time
        FROM spatial_object s JOIN document d ON d.id=s.document_id WHERE s.id=#{id}
        """) SpatialObject selectOne(long id);
}
