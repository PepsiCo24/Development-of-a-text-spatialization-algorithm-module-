import json
import re
from concurrent.futures import ThreadPoolExecutor
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
        self.settings=settings or get_settings();self.client=client or httpx.Client(timeout=min(self.settings.llm_timeout_seconds,15.0),trust_env=self.settings.llm_trust_env_proxy)
        self.resolver=GeologicalEntityExtractor(self.settings,self.client);self.geocoder=geocoder or PlaceGeocoder(self.settings)
    def extract(self,chunks:list[SpatialChunk],provider_name:str|None=None,region_hint:str|None=None)->tuple[ProviderConfig,list[SpatialObject],list[str]]:
        provider=self.resolver.resolve_provider(provider_name or self.settings.llm_default_provider);objects=[];warnings=[]
        def extract_chunk(chunk:SpatialChunk):
            try:
                payload=self._call(provider,chunk,region_hint)
                return self._normalize(payload,chunk,region_hint)
            except LlmExtractionError:
                return self._evidence_fallback(chunk)
        with ThreadPoolExecutor(max_workers=min(4,len(chunks)))as pool:
            chunk_results=list(pool.map(extract_chunk,chunks))
        for found,notes in chunk_results:objects.extend(found);warnings.extend(notes)
        unique={(o.name,o.object_type,json.dumps(o.geometry.model_dump(),sort_keys=True)):o for o in objects}
        return provider,list(unique.values()),warnings
    def _call(self,provider:ProviderConfig,chunk:SpatialChunk,region_hint:str|None)->Any:
        entity_json=json.dumps([e.model_dump() for e in chunk.entities],ensure_ascii=False)
        request={"model":provider.model,"temperature":provider.temperature,"max_tokens":self.settings.llm_max_tokens,"response_format":{"type":"json_object"},"messages":[{"role":"system","content":self.resolver._system_prompt(provider,SYSTEM_PROMPT)},{"role":"user","content":f"区域提示：{region_hint or '无'}\n页码范围：{chunk.page_start}-{chunk.page_end}\n实体清单：{entity_json}\n原文：\n{chunk.content}"}]}
        if self.resolver._is_siliconflow_qwen3(provider):request["enable_thinking"]=False
        try:
            response=self.client.post(self.resolver._chat_completions_url(provider.base_url),headers={"Authorization":f"Bearer {provider.api_key}"},json=request);response.raise_for_status()
            return self.resolver.decode_json(response.json()["choices"][0]["message"]["content"])
        except(httpx.HTTPError,KeyError,IndexError,TypeError,json.JSONDecodeError)as exc:raise LlmExtractionError(f"{provider.name} 空间信息抽取调用失败: {exc}")from exc
    def _normalize(self,payload:Any,chunk:SpatialChunk,region_hint:str|None)->tuple[list[SpatialObject],list[str]]:
        valid_ids={e.entity_id for e in chunk.entities};objects=[];warnings=[]
        for row in payload.get("objects",[])if isinstance(payload,dict)else[]:
            name=str(row.get("name","")).strip();object_type=str(row.get("objectType","")).upper();source=str(row.get("sourceText","")).strip();entity_id=row.get("entityId")
            if not name or object_type not in OBJECT_TYPES or not source or(entity_id is not None and int(entity_id)not in valid_ids):continue
            if self.resolver._normalized_text(source) not in self.resolver._normalized_text(chunk.content):
                warnings.append(f"“{name}”的证据句无法在原文中定位，已跳过")
                continue
            geometry_data=row.get("geometry");geocoding_source=None
            if geometry_data is None and object_type in{"PLACE","MINERAL_POINT","BOREHOLE"}:
                geometry=self._linked_place_anchor(chunk,name) if object_type=="PLACE" else None
                if geometry is not None:
                    geocoding_source="原文钻孔关联位置（锚点）"
                    warnings.append(f"“{name}”采用原文钻孔坐标作为关联锚点，不代表矿段边界")
                else:
                    query=str(row.get("locationQuery")or name).strip();query=f"{query}, {region_hint}" if region_hint and region_hint not in query else query
                    geometry=self.geocoder.geocode(query);geocoding_source="Nominatim" if geometry else None
                if geometry is None:warnings.append(f"未能为“{name}”解析坐标");continue
            else:
                if geometry_data is None and object_type=="FAULT":warnings.append(f"“{name}”仅有走向、倾向或倾角，缺少起止坐标，无法生成断裂线");continue
                try:geometry=GeoJsonGeometry.model_validate(geometry_data)
                except ValidationError:warnings.append(f"“{name}”的几何坐标无效，已跳过");continue
            page=min(max(int(row.get("page")or chunk.page_start),chunk.page_start),chunk.page_end);confidence=min(max(float(row.get("confidence",.5)),0),1)
            if geometry.type=="Point" and re.search(r"(?:东经|西经|北纬|南纬).+\d",source):confidence=max(confidence,.98)
            objects.append(SpatialObject(name=name,object_type=object_type,entity_id=int(entity_id)if entity_id is not None else None,chunk_id=chunk.chunk_id,geometry=geometry,confidence=confidence,source_text=source,page=page,geocoding_source=geocoding_source))
        return objects,warnings
    def _evidence_fallback(self,chunk:SpatialChunk)->tuple[list[SpatialObject],list[str]]:
        longitude=re.search(r"(?:东经|E)\s*(\d{1,3}(?:\.\d+)?)\s*°?",chunk.content,re.IGNORECASE)
        latitude=re.search(r"(?:北纬|N)\s*(\d{1,2}(?:\.\d+)?)\s*°?",chunk.content,re.IGNORECASE)
        if not longitude or not latitude:
            faults=[entity.entity_name for entity in chunk.entities if entity.entity_type=="FAULT"]
            if faults:return [],[f"“{'、'.join(faults)}”仅有走向、倾向或倾角，缺少起止坐标，无法生成断裂线"]
            return [],["上游模型不可用，且原文没有可直接转换的经纬度坐标"]
        lon=float(longitude.group(1));lat=float(latitude.group(1))
        if not(-180<=lon<=180 and -90<=lat<=90):return [],["原文经纬度超出有效范围"]
        borehole=next((e for e in chunk.entities if e.entity_type in{"BOREHOLE","ROCK_BODY"} and re.search(r"(?:ZK|钻孔)",e.entity_name,re.IGNORECASE)),None)
        coordinate=next((e for e in chunk.entities if e.entity_type=="COORDINATE"),None)
        linked=borehole or coordinate
        name=borehole.entity_name if borehole else f"坐标点 {lon:.4f},{lat:.4f}"
        source=self._evidence_sentence(chunk.content,longitude.start())
        geometry=GeoJsonGeometry(type="Point",coordinates=[lon,lat])
        objects=[SpatialObject(name=name,object_type="BOREHOLE" if borehole else "COORDINATE",entity_id=linked.entity_id if linked else None,chunk_id=chunk.chunk_id,geometry=geometry,confidence=.98,source_text=source,page=chunk.page_start,geocoding_source="原文经纬度")]
        warnings=["已直接依据原文明确经纬度完成空间化"]
        for entity in chunk.entities:
            if entity.entity_type=="PLACE" and self._linked_place_anchor(chunk,entity.entity_name) is not None:
                place_source=self._evidence_sentence(chunk.content,chunk.content.find(entity.entity_name))
                objects.append(SpatialObject(name=entity.entity_name,object_type="PLACE",entity_id=entity.entity_id,chunk_id=chunk.chunk_id,geometry=geometry,confidence=.90,source_text=place_source,page=chunk.page_start,geocoding_source="原文钻孔关联位置（锚点）"))
                warnings.append(f"“{entity.entity_name}”采用原文钻孔坐标作为关联锚点，不代表矿段边界")
        return objects,warnings
    @staticmethod
    def _linked_place_anchor(chunk:SpatialChunk,name:str)->GeoJsonGeometry|None:
        if not re.search(rf"位于\s*{re.escape(name)}",chunk.content):return None
        longitude=re.search(r"(?:东经|E)\s*(\d{1,3}(?:\.\d+)?)\s*°?",chunk.content,re.IGNORECASE)
        latitude=re.search(r"(?:北纬|N)\s*(\d{1,2}(?:\.\d+)?)\s*°?",chunk.content,re.IGNORECASE)
        if not longitude or not latitude:return None
        lon=float(longitude.group(1));lat=float(latitude.group(1))
        return GeoJsonGeometry(type="Point",coordinates=[lon,lat])if-180<=lon<=180 and-90<=lat<=90 else None
    @staticmethod
    def _evidence_sentence(content:str,position:int)->str:
        start=max(content.rfind(mark,0,position) for mark in("。","；",";","\n"))+1
        ends=[index for mark in("。","；",";","\n") if(index:=content.find(mark,position))>=0]
        end=min(ends)if ends else len(content)
        return content[start:end].strip()
