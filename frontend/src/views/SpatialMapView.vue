<script setup lang="ts">
import { computed,nextTick,onBeforeUnmount,onMounted,reactive,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Aim,Close,Delete,MagicStick,Position,Refresh } from '@element-plus/icons-vue'
import Map from 'ol/Map.js'
import View from 'ol/View.js'
import Feature from 'ol/Feature'
import GeoJSON from 'ol/format/GeoJSON'
import Draw from 'ol/interaction/Draw'
import TileLayer from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import OSM from 'ol/source/OSM'
import VectorSource from 'ol/source/Vector'
import XYZ from 'ol/source/XYZ'
import {Circle as CircleStyle,Fill,Stroke,Style} from 'ol/style'
import {ScaleLine,defaults as defaultControls} from 'ol/control'
import {fromLonLat,toLonLat} from 'ol/proj'
import {getArea,getLength} from 'ol/sphere'
import {isEmpty as isEmptyExtent} from 'ol/extent'
import type Geometry from 'ol/geom/Geometry'
import {getSpatialStatus,listDocuments,listSpatialObjects,startSpatialExtraction,type GeologicalDocument,type SpatialObject,type SpatialObjectType,type SpatialStatus} from '@/api/documents'
import 'ol/ol.css'

const route=useRoute(),router=useRouter()
const mapTarget=ref<HTMLElement>(),documents=ref<GeologicalDocument[]>([]),objects=ref<SpatialObject[]>([]),selectedDocumentId=ref<number>(),selected=ref<SpatialObject>(),provider=ref<'deepseek'|'qwen'>('deepseek'),status=ref<SpatialStatus>(),baseMap=ref<'topographic'|'imagery'>('topographic'),mouseCoordinate=ref('114.9000, 30.1000'),measureResult=ref(''),measureMode=ref<'distance'|'area'>(),loading=ref(true),starting=ref(false)
const typeMeta:Record<SpatialObjectType,{label:string;color:string}>={PLACE:{label:'地名',color:'#2f7881'},COORDINATE:{label:'坐标',color:'#397c9d'},MINERAL_POINT:{label:'矿点',color:'#d18432'},BOREHOLE:{label:'钻孔',color:'#745d92'},FAULT:{label:'断裂',color:'#b34f42'},SURVEY_AREA:{label:'调查区域',color:'#4f7f67'}}
const visibility=reactive<Record<SpatialObjectType,boolean>>({PLACE:true,COORDINATE:true,MINERAL_POINT:true,BOREHOLE:true,FAULT:true,SURVEY_AREA:true})
const spatialDocuments=computed(()=>documents.value.filter(d=>d.knowledgeStatus==='COMPLETED'))
const visibleObjects=computed(()=>objects.value.filter(o=>visibility[o.objectType]))
let map:Map|undefined,vectorSource:VectorSource,measureSource:VectorSource,vectorLayer:VectorLayer<VectorSource>,measureLayer:VectorLayer<VectorSource>,osmLayer:TileLayer<OSM>,imageryLayer:TileLayer<XYZ>,draw:Draw|undefined,pollTimer:number|undefined

function featureStyle(feature:Feature<Geometry>,highlight=false){const objectType=feature.get('objectType')as SpatialObjectType;if(!visibility[objectType])return undefined;const color=typeMeta[objectType].color;const geometry=feature.getGeometry()?.getType();return new Style({stroke:new Stroke({color:highlight?'#f3b45a':color,width:highlight?5:3,lineDash:geometry==='LineString'?[9,5]:undefined}),fill:new Fill({color:`${color}35`}),image:new CircleStyle({radius:highlight?7:5,fill:new Fill({color}),stroke:new Stroke({color:highlight?'#fff3da':'white',width:highlight?3:2})})})}
function initializeMap() {
  if (!mapTarget.value) return
  vectorSource = new VectorSource()
  measureSource = new VectorSource()
  osmLayer = new TileLayer({ source: new OSM(), visible: true })
  imageryLayer = new TileLayer({
    source: new XYZ({
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      attributions: 'Tiles © Esri',
    }),
    visible: false,
  })
  vectorLayer = new VectorLayer({ source: vectorSource, style: feature => featureStyle(feature as Feature<Geometry>) })
  measureLayer = new VectorLayer({
    source: measureSource,
    style: new Style({
      stroke: new Stroke({ color: '#f0a34a', width: 3, lineDash: [8, 5] }),
      fill: new Fill({ color: 'rgba(240,163,74,.18)' }),
      image: new CircleStyle({ radius: 5, fill: new Fill({ color: '#f0a34a' }) }),
    }),
  })
  map = new Map({
    target: mapTarget.value,
    layers: [osmLayer, imageryLayer, vectorLayer, measureLayer],
    view: new View({ center: fromLonLat([114.9384, 30.0840]), zoom: 12, minZoom: 3, maxZoom: 20 }),
    controls: defaultControls().extend([new ScaleLine({ units: 'metric', bar: true, steps: 3, text: true })]),
  })
  map.on('pointermove', event => {
    const [lon, lat] = toLonLat(event.coordinate)
    mouseCoordinate.value = `${lon.toFixed(5)}, ${lat.toFixed(5)}`
  })
  map.on('singleclick', event => {
    const feature = map?.forEachFeatureAtPixel(event.pixel, item => item, { layerFilter: layer => layer === vectorLayer }) as Feature<Geometry> | undefined
    if (feature) selectObject(feature.get('objectId'))
  })
}
async function load(){loading.value=true;try{const[page,spatial]=await Promise.all([listDocuments({page:1,size:100,status:'PARSED'}),listSpatialObjects()]);documents.value=page.records;objects.value=spatial;const linkedDocument=Number(route.query.documentId);if(Number.isFinite(linkedDocument)&&linkedDocument>0)selectedDocumentId.value=linkedDocument;if(!selectedDocumentId.value)selectedDocumentId.value=spatialDocuments.value[0]?.id;if(selectedDocumentId.value)status.value=await getSpatialStatus(selectedDocumentId.value);renderObjects();const linkedEntity=Number(route.query.entityId);const linkedObject=objects.value.find(item=>item.entityId===linkedEntity);if(linkedObject)nextTick(()=>selectObject(linkedObject.id))}catch(e){ElMessage.error(message(e))}finally{loading.value=false}}
function renderObjects(){if(!vectorSource)return;vectorSource.clear();const format=new GeoJSON();for(const object of objects.value){try{const feature=format.readFeature(JSON.parse(object.geojson),{dataProjection:'EPSG:4326',featureProjection:'EPSG:3857'})as Feature<Geometry>;feature.setProperties({objectId:object.id,objectType:object.objectType});vectorSource.addFeature(feature)}catch{continue}}fitAll()}
function mapPadding(detail=false):number[]{return window.innerWidth<=650?(detail?[100,35,160,35]:[90,35,90,35]):(detail?[130,390,230,340]:[110,340,210,330])}
function fitAll(){if(!map||!vectorSource||vectorSource.isEmpty())return;const extent=vectorSource.getExtent();if(extent&&!isEmptyExtent(extent))map.getView().fit(extent,{padding:mapPadding(),maxZoom:16,duration:450})}
function switchBase(value:'topographic'|'imagery'){baseMap.value=value;osmLayer?.setVisible(value==='topographic');imageryLayer?.setVisible(value==='imagery')}
function toggleType(type:SpatialObjectType){visibility[type]=!visibility[type];vectorLayer.changed()}
function selectObject(id:number){const object=objects.value.find(item=>item.id===id);if(!object)return;selected.value=object;const feature=vectorSource.getFeatures().find(f=>f.get('objectId')===id);if(feature&&map){map.getView().fit(feature.getGeometry()!.getExtent(),{padding:mapPadding(true),maxZoom:18,duration:450});vectorLayer.setStyle(f=>featureStyle(f as Feature<Geometry>,f.get('objectId')===id))}nextTick(()=>document.querySelector(`[data-spatial-id="${id}"]`)?.scrollIntoView({behavior:'smooth',block:'nearest',inline:'center'}))}
function clearSelection(){selected.value=undefined;vectorLayer.setStyle(f=>featureStyle(f as Feature<Geometry>))}
function openSource(){if(!selected.value)return;router.push({name:'document-entities',params:{id:selected.value.documentId},query:{entityId:selected.value.entityId,chunkId:selected.value.chunkId,page:selected.value.page,from:'map'}})}
function startMeasure(mode:'distance'|'area'){if(!map)return;if(draw)map.removeInteraction(draw);measureSource.clear();measureMode.value=mode;measureResult.value='绘制中…';draw=new Draw({source:measureSource,type:mode==='distance'?'LineString':'Polygon'});map.addInteraction(draw);draw.on('drawend',event=>{const geometry=event.feature.getGeometry()!;if(mode==='distance'){const length=getLength(geometry);measureResult.value=length>1000?`${(length/1000).toFixed(2)} km`:`${length.toFixed(1)} m`}else{const area=getArea(geometry);measureResult.value=area>1e6?`${(area/1e6).toFixed(2)} km²`:`${area.toFixed(1)} m²`}if(draw){map?.removeInteraction(draw);draw=undefined}})}
function clearMeasure(){if(draw)map?.removeInteraction(draw);draw=undefined;measureMode.value=undefined;measureResult.value='';measureSource.clear()}
async function changeDocument(){if(!selectedDocumentId.value)return;try{status.value=await getSpatialStatus(selectedDocumentId.value)}catch(e){ElMessage.error(message(e))}}
async function extract(){if(!selectedDocumentId.value)return;starting.value=true;try{status.value=await startSpatialExtraction(selectedDocumentId.value,provider.value);ElMessage.success('空间化任务已提交');schedulePoll(150)}catch(e){ElMessage.error(message(e))}finally{starting.value=false}}
function schedulePoll(delay=650){if(pollTimer)clearTimeout(pollTimer);pollTimer=window.setTimeout(pollStatus,delay)}
async function pollStatus(){if(!selectedDocumentId.value)return;try{const latest=await getSpatialStatus(selectedDocumentId.value);status.value=latest;if(latest.status==='EXTRACTING')return schedulePoll();if(latest.status==='COMPLETED'){objects.value=await listSpatialObjects();renderObjects();ElMessage.success(`空间化完成，共生成 ${latest.objectCount} 个对象`)}}catch(e){ElMessage.error(message(e));if(status.value?.status==='EXTRACTING')schedulePoll()}}
function message(e:unknown){return e instanceof Error?e.message:'操作失败'}
onMounted(async()=>{try{initializeMap()}catch(error){ElMessage.error(`地图初始化失败：${message(error)}`)}await load()});onBeforeUnmount(()=>{if(pollTimer)clearTimeout(pollTimer);if(draw)map?.removeInteraction(draw);map?.setTarget(undefined);map=undefined})
</script>

<template><div class="map-page" v-loading="loading"><div ref="mapTarget" class="map-canvas" aria-label="地质空间对象地图"></div><header class="map-title"><span>Phase 6 · Text to geography</span><h1>地质文本空间化</h1><p>{{objects.length}} 个可追溯空间对象</p></header><section class="map-control-panel"><div class="map-section"><small>空间化资料</small><el-select v-model="selectedDocumentId" placeholder="选择资料" @change="changeDocument"><el-option v-for="item in spatialDocuments" :key="item.id" :label="item.name" :value="item.id"/></el-select><div class="map-provider"><button type="button" :class="{active:provider==='deepseek'}" @click="provider='deepseek'">DeepSeek</button><button type="button" :class="{active:provider==='qwen'}" @click="provider='qwen'">Qwen</button></div><button class="map-extract" type="button" :disabled="status?.status==='EXTRACTING'||starting" @click="extract"><el-icon><Refresh v-if="status?.status==='COMPLETED'||status?.status==='FAILED'"/><MagicStick v-else/></el-icon>{{status?.status==='COMPLETED'?'重新空间化':status?.status==='EXTRACTING'?`空间化 ${status.progress}%`:'开始空间化'}}</button><p v-if="status?.errorMessage" class="map-error">{{status.errorMessage}}</p><p v-if="status?.warnings" class="map-warning">{{status.warnings}}</p></div><div class="map-section"><small>底图</small><div class="map-segment"><button type="button" :class="{active:baseMap==='topographic'}" @click="switchBase('topographic')">标准地图</button><button type="button" :class="{active:baseMap==='imagery'}" @click="switchBase('imagery')">卫星影像</button></div></div><div class="map-section"><small>对象图层</small><button v-for="(meta,type) in typeMeta" :key="type" type="button" class="layer-toggle" :class="{muted:!visibility[type as SpatialObjectType]}" @click="toggleType(type as SpatialObjectType)"><i :style="{background:meta.color}"></i><span>{{meta.label}}</span><b>{{objects.filter(o=>o.objectType===type).length}}</b></button></div></section><section class="map-toolbar"><button type="button" @click="fitAll"><el-icon><Aim/></el-icon>全图</button><button type="button" :class="{active:measureMode==='distance'}" @click="startMeasure('distance')">距离量算</button><button type="button" :class="{active:measureMode==='area'}" @click="startMeasure('area')">面积量算</button><button v-if="measureResult" type="button" @click="clearMeasure"><el-icon><Delete/></el-icon>{{measureResult}}</button><span><el-icon><Position/></el-icon>{{mouseCoordinate}}</span></section><aside v-if="selected" class="map-inspector"><button type="button" aria-label="关闭对象详情" @click="clearSelection"><el-icon><Close/></el-icon></button><span class="object-type" :style="{'--object-color':typeMeta[selected.objectType].color}">{{typeMeta[selected.objectType].label}} / {{selected.geometryType}}</span><h2>{{selected.name}}</h2><dl><div><dt>中心坐标</dt><dd>{{Number(selected.centerLongitude).toFixed(5)}}, {{Number(selected.centerLatitude).toFixed(5)}}</dd></div><div><dt>来源资料</dt><dd>{{selected.documentName}}</dd></div><div><dt>来源页码</dt><dd>第 {{selected.page}} 页</dd></div><div><dt>模型置信度</dt><dd>{{Math.round(selected.confidence*100)}}%</dd></div><div v-if="selected.geocodingSource"><dt>坐标来源</dt><dd>{{selected.geocodingSource}}</dd></div></dl><blockquote>{{selected.sourceText}}</blockquote><button class="source-locate" type="button" @click="openSource"><el-icon><Position/></el-icon>定位到原文并高亮</button></aside><section class="evidence-rail"><header><div><strong>原文证据轨道</strong><small>点击证据定位地图对象</small></div><span>{{visibleObjects.length}} / {{objects.length}}</span></header><div><button v-for="object in visibleObjects" :key="object.id" type="button" :data-spatial-id="object.id" :class="{active:selected?.id===object.id}" @click="selectObject(object.id)"><i :style="{background:typeMeta[object.objectType].color}"></i><span><b>{{object.name}}</b><small>{{object.documentName}} · 第 {{object.page}} 页</small><em>{{object.sourceText}}</em></span></button></div></section><div class="map-legend"><span v-for="(meta,type) in typeMeta" :key="type"><i :style="{background:meta.color}"></i>{{meta.label}}</span></div></div></template>

<style scoped>.source-locate{width:100%;display:flex;justify-content:center;align-items:center;gap:6px;margin-top:12px;border:0;background:#d88443;color:#173e36;padding:10px;font-weight:700}</style>
