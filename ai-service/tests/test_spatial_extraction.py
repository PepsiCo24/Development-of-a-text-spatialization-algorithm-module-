import json

import httpx
import pytest
from pydantic import ValidationError

from app.core.config import Settings
from app.models.spatial import GeoJsonGeometry, SpatialChunk, SpatialEntity
from app.services.geocoding import PlaceGeocoder
from app.services.llm_spatial import GeologicalSpatialExtractor


class StubGeocoder:
    def geocode(self, query: str):
        assert "大冶铁矿" in query
        return GeoJsonGeometry(type="Point", coordinates=[114.92, 30.10])


def handler(request: httpx.Request) -> httpx.Response:
    assert request.url.path.endswith("/chat/completions")
    return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps({"objects": [
        {"name": "ZK12", "objectType": "BOREHOLE", "entityId": 1, "geometry": {"type": "Point", "coordinates": [114.91, 30.08]}, "confidence": .98, "sourceText": "ZK12孔位于东经114.91度、北纬30.08度。", "page": 4},
        {"name": "北东向断裂", "objectType": "FAULT", "entityId": 2, "geometry": {"type": "LineString", "coordinates": [[114.88, 30.04], [114.96, 30.13]]}, "confidence": .91, "sourceText": "断裂自西南向东北延伸。", "page": 4},
        {"name": "大冶铁矿", "objectType": "MINERAL_POINT", "entityId": 3, "geometry": None, "locationQuery": "大冶铁矿", "confidence": .86, "sourceText": "调查范围包括大冶铁矿。", "page": 4},
        {"name": "错误区域", "objectType": "SURVEY_AREA", "geometry": {"type": "Polygon", "coordinates": [[[181, 30], [182, 30], [182, 31], [181, 30]]]}, "confidence": .9, "sourceText": "错误坐标。", "page": 4}
    ]}, ensure_ascii=False)}}]})


def test_extracts_points_lines_and_geocoded_places():
    settings=Settings(deepseek_api_key="test-key",deepseek_base_url="https://deepseek.test/v1")
    client=httpx.Client(transport=httpx.MockTransport(handler))
    chunk=SpatialChunk(chunk_id=7,content="ZK12孔位于东经114.91度、北纬30.08度。断裂自西南向东北延伸。调查范围包括大冶铁矿。错误坐标。",page_start=4,page_end=4,entities=[
        SpatialEntity(entity_id=1,entity_name="ZK12",entity_type="BOREHOLE"),SpatialEntity(entity_id=2,entity_name="北东向断裂",entity_type="FAULT"),SpatialEntity(entity_id=3,entity_name="大冶铁矿",entity_type="PLACE")])
    _,objects,warnings=GeologicalSpatialExtractor(settings,client,StubGeocoder()).extract([chunk],"deepseek","湖北省黄石市")
    assert [item.geometry.type for item in objects]==["Point","LineString","Point"]
    assert objects[2].geocoding_source=="Nominatim"
    assert warnings==["“错误区域”的几何坐标无效，已跳过"]


def test_rejects_unclosed_polygon():
    with pytest.raises(ValidationError):
        GeoJsonGeometry(type="Polygon",coordinates=[[[114,30],[115,30],[115,31],[114,31]]])


def test_geocoder_caches_repeated_place_queries():
    requests = []

    def geocode_handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json=[{"lon": "114.92", "lat": "30.10"}])

    settings = Settings(geocoding_min_interval_seconds=0)
    client = httpx.Client(transport=httpx.MockTransport(geocode_handler))
    geocoder = PlaceGeocoder(settings, client)
    first = geocoder.geocode("大冶铁矿")
    second = geocoder.geocode("大冶铁矿")

    assert first == second
    assert first.coordinates == [114.92, 30.10]
    assert len(requests) == 1


def test_falls_back_to_explicit_coordinates_when_remote_model_is_unavailable():
    def unavailable(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectTimeout("offline", request=request)

    settings=Settings(qwen_api_key="test-key",qwen_base_url="https://api.siliconflow.cn/v1")
    chunk=SpatialChunk(chunk_id=7,content="钻孔 ZK001 位于东经114.9300°、北纬30.1100°。",page_start=1,page_end=1,entities=[
        SpatialEntity(entity_id=1,entity_name="ZK001",entity_type="ROCK_BODY")])

    _,objects,warnings=GeologicalSpatialExtractor(settings,httpx.Client(transport=httpx.MockTransport(unavailable))).extract([chunk],"qwen")

    assert len(objects)==1
    assert objects[0].object_type=="BOREHOLE"
    assert objects[0].geometry.coordinates==[114.93,30.11]
    assert warnings==["已直接依据原文明确经纬度完成空间化"]


def test_uses_borehole_coordinate_as_explicit_place_anchor_and_explains_unlocated_fault():
    def linked_handler(request: httpx.Request) -> httpx.Response:
        content={"objects":[
            {"name":"ZK001","objectType":"BOREHOLE","entityId":1,"geometry":{"type":"Point","coordinates":[114.93,30.11]},"confidence":.96,"sourceText":"钻孔位于铜绿山矿段，孔口坐标为东经114.9300°、北纬30.1100°","page":1},
            {"name":"铜绿山矿段","objectType":"PLACE","entityId":2,"geometry":None,"locationQuery":"铜绿山矿段","confidence":.88,"sourceText":"钻孔位于铜绿山矿段","page":1},
            {"name":"F1","objectType":"FAULT","entityId":3,"geometry":None,"confidence":.8,"sourceText":"F1断裂走向北东，倾向南东，倾角68°","page":1},
        ]}
        return httpx.Response(200,json={"choices":[{"message":{"content":json.dumps(content,ensure_ascii=False)}}]})

    settings=Settings(deepseek_api_key="test-key",deepseek_base_url="https://deepseek.test/v1")
    chunk=SpatialChunk(chunk_id=7,content="钻孔位于铜绿山矿段，孔口坐标为东经114.9300°、北纬30.1100°。F1断裂走向北东，倾向南东，倾角68°。",page_start=1,page_end=1,entities=[
        SpatialEntity(entity_id=1,entity_name="ZK001",entity_type="ROCK_BODY"),
        SpatialEntity(entity_id=2,entity_name="铜绿山矿段",entity_type="PLACE"),
        SpatialEntity(entity_id=3,entity_name="F1",entity_type="FAULT"),
    ])

    _,objects,warnings=GeologicalSpatialExtractor(settings,httpx.Client(transport=httpx.MockTransport(linked_handler))).extract([chunk],"deepseek")

    assert [item.object_type for item in objects]==["BOREHOLE","PLACE"]
    assert objects[1].geometry.coordinates==[114.93,30.11]
    assert objects[1].geocoding_source=="原文钻孔关联位置（锚点）"
    assert any("缺少起止坐标" in warning for warning in warnings)
