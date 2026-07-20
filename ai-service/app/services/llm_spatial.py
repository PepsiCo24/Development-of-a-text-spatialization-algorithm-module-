import json
from typing import Any

import httpx
from pydantic import ValidationError

from app.core.config import Settings, get_settings
from app.models.spatial import GeoJsonGeometry, SpatialChunk, SpatialObject
from app.services.geocoding import PlaceGeocoder
from app.services.llm_entities import GeologicalEntityExtractor, LlmExtractionError, ProviderConfig

OBJECT_TYPES={"PLACE","COORDINATE","MINERAL_POINT","BOREHOLE","FAULT","SURVEY_AREA"}
SYSTEM_PROMPT="""你是地质文本空间化专家。只根据原文抽取空间对象，不得编造坐标。
对象类型仅允许 PLACE(地名)、COORDINATE(坐标)、MINERAL_POINT(矿点)、BOREHOLE(钻孔)、FAULT(断裂)、SURVEY_AREA(调查区域)。
geometry 必须是 WGS84 GeoJSON，只允许 Point、LineString、Polygon；经度在前、纬度在后。只有原文提供明确坐标或足够的坐标序列时才能输出 geometry。地名没有坐标时 geometry 为 null，并提供 locationQuery 供地名服务解析。断裂至少两个点，区域多边形首尾必须闭合。
严格输出 JSON：{"objects":[{"name":"对象名","objectType":"FAULT","entityId":1,"geometry":{"type":"LineString","coordinates":[[114.1,30.1],[114.2,30.2]]},"locationQuery":null,"confidence":0.9,"sourceText":"证据句","page":1}]}。没有结果返回空数组，不得返回 Markdown。"""


class GeologicalSpatialExtractor:
    def __init__(self, settings:Settings|None=None, client:httpx.Client|None=None, geocoder:PlaceGeocoder|None=None)->None:
        self.settings=settings or get_settings();self.client=client or httpx.Client(timeout=self.settings.llm_timeout_seconds)
        self.resolver=GeologicalEntityExtractor(self.settings,self.client);self.geocoder=geocoder or PlaceGeocoder(self.settings)
    def extract(self,chunks:list[SpatialChunk],provider_name:str|None=None,region_hint:str|None=None)->tuple[ProviderConfig,list[SpatialObject],list[str]]:
        provider=self.resolver.resolve_provider(provider_name or self.settings.llm_default_provider);objects=[];warnings=[]
        for chunk in chunks:
            payload=self._call(provider,chunk,region_hint);found,notes=self._normalize(payload,chunk,region_hint);objects.extend(found);warnings.extend(notes)
        unique={(o.name,o.object_type,json.dumps(o.geometry.model_dump(),sort_keys=True)):o for o in objects}
        return provider,list(unique.values()),warnings
    def _call(self,provider:ProviderConfig,chunk:SpatialChunk,region_hint:str|None)->Any:
        entity_json=json.dumps([e.model_dump() for e in chunk.entities],ensure_ascii=False)
        request={"model":provider.model,"temperature":provider.temperature,"max_tokens":self.settings.llm_max_tokens,"response_format":{"type":"json_object"},"messages":[{"role":"system","content":self.resolver._system_prompt(provider,SYSTEM_PROMPT)},{"role":"user","content":f"区域提示：{region_hint or '无'}\n页码范围：{chunk.page_start}-{chunk.page_end}\n实体清单：{entity_json}\n原文：\n{chunk.content}"}]}
        try:
            response=self.client.post(f"{provider.base_url}/chat/completions",headers={"Authorization":f"Bearer {provider.api_key}"},json=request);response.raise_for_status()
            return self.resolver.decode_json(response.json()["choices"][0]["message"]["content"])
        except(httpx.HTTPError,KeyError,IndexError,TypeError,json.JSONDecodeError)as exc:raise LlmExtractionError(f"{provider.name} 空间信息抽取调用失败: {exc}")from exc
    def _normalize(self,payload:Any,chunk:SpatialChunk,region_hint:str|None)->tuple[list[SpatialObject],list[str]]:
        valid_ids={e.entity_id for e in chunk.entities};objects=[];warnings=[]
        for row in payload.get("objects",[])if isinstance(payload,dict)else[]:
            name=str(row.get("name","")).strip();object_type=str(row.get("objectType","")).upper();source=str(row.get("sourceText","")).strip();entity_id=row.get("entityId")
            if not name or object_type not in OBJECT_TYPES or not source or(entity_id is not None and int(entity_id)not in valid_ids):continue
            geometry_data=row.get("geometry");geocoding_source=None
            if geometry_data is None and object_type in{"PLACE","MINERAL_POINT","BOREHOLE"}:
                query=str(row.get("locationQuery")or name).strip();query=f"{query}, {region_hint}" if region_hint and region_hint not in query else query
                geometry=self.geocoder.geocode(query);geocoding_source="Nominatim" if geometry else None
                if geometry is None:warnings.append(f"未能为“{name}”解析坐标");continue
            else:
                try:geometry=GeoJsonGeometry.model_validate(geometry_data)
                except ValidationError:warnings.append(f"“{name}”的几何坐标无效，已跳过");continue
            page=min(max(int(row.get("page")or chunk.page_start),chunk.page_start),chunk.page_end);confidence=min(max(float(row.get("confidence",.5)),0),1)
            objects.append(SpatialObject(name=name,object_type=object_type,entity_id=int(entity_id)if entity_id is not None else None,chunk_id=chunk.chunk_id,geometry=geometry,confidence=confidence,source_text=source,page=page,geocoding_source=geocoding_source))
        return objects,warnings
