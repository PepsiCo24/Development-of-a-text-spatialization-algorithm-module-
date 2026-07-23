import { http } from './http'
import type { EntityAttribute, EntityRelation, GeologicalDocument, GeologicalEntity, SpatialObject } from './documents'

export interface AdminUser{id:number;username:string;displayName:string;role:'ADMIN'|'USER';enabled:boolean;createTime:string;updateTime:string}
export interface UserInput{username:string;password?:string;displayName:string;role:'ADMIN'|'USER';enabled:boolean}
export interface LlmConfig{id:number;provider:'deepseek'|'qwen';baseUrl:string;modelName:string;keyConfigured:boolean;temperature:number;promptTemplate?:string;enabled:boolean;updateTime:string}
export interface SystemLog{id:number;module:string;action:string;requestMethod?:string;requestPath?:string;status:'SUCCESS'|'FAILED';errorMessage?:string;elapsedMs?:number;provider?:string;model?:string;functionName?:string;createTime:string}
export interface AnalysisReport{document:GeologicalDocument;summary:{entityCount:number;attributeCount:number;relationCount:number;spatialObjectCount:number};entities:GeologicalEntity[];attributes:EntityAttribute[];relations:EntityRelation[];spatialObjects:SpatialObject[];generatedAt:string}

export async function listUsers():Promise<AdminUser[]>{const r=await http.get('/admin/users');return r.data.data}
export async function saveUser(input:UserInput,id?:number):Promise<AdminUser>{const r=id?await http.put(`/admin/users/${id}`,input):await http.post('/admin/users',input);return r.data.data}
export async function deleteUser(id:number):Promise<void>{await http.delete(`/admin/users/${id}`)}
export async function listTasks():Promise<GeologicalDocument[]>{const r=await http.get('/admin/tasks');return r.data.data}
export async function listLogs(module=''):Promise<SystemLog[]>{const r=await http.get('/admin/logs',{params:{module}});return r.data.data}
export async function listLlmConfigs():Promise<LlmConfig[]>{const r=await http.get('/admin/llm-configs');return r.data.data}
export async function saveLlmConfig(input:{provider:string;baseUrl:string;modelName:string;apiKey?:string;temperature:number;promptTemplate?:string;enabled:boolean}):Promise<LlmConfig>{const r=await http.put('/admin/llm-configs',input,{timeout:30000});return r.data.data}
export async function downloadExport(documentId:number,format:string,dataset:string):Promise<void>{const r=await http.get('/exports',{params:{documentId,format,dataset},responseType:'blob',timeout:120000});const disposition=String(r.headers['content-disposition']||'');const match=disposition.match(/filename\*=UTF-8''([^;]+)/);const filename=match?decodeURIComponent(match[1]):`geotext-export.${format}`;const url=URL.createObjectURL(r.data);const link=document.createElement('a');link.href=url;link.download=filename;link.click();URL.revokeObjectURL(url)}
export async function restoreDemoData():Promise<GeologicalDocument>{const r=await http.post('/admin/demo-data/restore');return r.data.data}
export async function pushToOneMap(documentId:number):Promise<{status:string;message:string;filename:string;bytes:number}>{const r=await http.post('/admin/one-map/push',undefined,{params:{documentId}});return r.data.data}
export async function getAnalysisReport(documentId:number):Promise<AnalysisReport>{const r=await http.get(`/reports/${documentId}`);return r.data.data}
export async function downloadAnalysisReport(documentId:number):Promise<void>{const r=await http.get(`/reports/${documentId}/pdf`,{responseType:'blob',timeout:120000});const disposition=String(r.headers['content-disposition']||'');const match=disposition.match(/filename\*=UTF-8''([^;]+)/);const filename=match?decodeURIComponent(match[1]):'地质文本空间化分析报告.pdf';const url=URL.createObjectURL(r.data);const link=document.createElement('a');link.href=url;link.download=filename;link.click();URL.revokeObjectURL(url)}
