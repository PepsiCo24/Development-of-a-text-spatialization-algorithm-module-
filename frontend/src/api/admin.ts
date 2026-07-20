import { http } from './http'
import type { GeologicalDocument } from './documents'

export interface AdminUser{id:number;username:string;displayName:string;role:'ADMIN'|'USER';enabled:boolean;createTime:string;updateTime:string}
export interface UserInput{username:string;password?:string;displayName:string;role:'ADMIN'|'USER';enabled:boolean}
export interface LlmConfig{id:number;provider:'deepseek'|'qwen';baseUrl:string;modelName:string;keyConfigured:boolean;temperature:number;promptTemplate?:string;enabled:boolean;updateTime:string}
export interface SystemLog{id:number;module:string;action:string;requestMethod?:string;requestPath?:string;status:'SUCCESS'|'FAILED';errorMessage?:string;elapsedMs?:number;provider?:string;model?:string;functionName?:string;createTime:string}

export async function listUsers():Promise<AdminUser[]>{const r=await http.get('/admin/users');return r.data.data}
export async function saveUser(input:UserInput,id?:number):Promise<AdminUser>{const r=id?await http.put(`/admin/users/${id}`,input):await http.post('/admin/users',input);return r.data.data}
export async function deleteUser(id:number):Promise<void>{await http.delete(`/admin/users/${id}`)}
export async function listTasks():Promise<GeologicalDocument[]>{const r=await http.get('/admin/tasks');return r.data.data}
export async function listLogs(module=''):Promise<SystemLog[]>{const r=await http.get('/admin/logs',{params:{module}});return r.data.data}
export async function listLlmConfigs():Promise<LlmConfig[]>{const r=await http.get('/admin/llm-configs');return r.data.data}
export async function saveLlmConfig(input:{provider:string;baseUrl:string;modelName:string;apiKey?:string;temperature:number;promptTemplate?:string;enabled:boolean}):Promise<LlmConfig>{const r=await http.put('/admin/llm-configs',input,{timeout:30000});return r.data.data}
export async function downloadExport(documentId:number,format:string,dataset:string):Promise<void>{const r=await http.get('/exports',{params:{documentId,format,dataset},responseType:'blob',timeout:120000});const disposition=String(r.headers['content-disposition']||'');const match=disposition.match(/filename\*=UTF-8''([^;]+)/);const filename=match?decodeURIComponent(match[1]):`geotext-export.${format}`;const url=URL.createObjectURL(r.data);const link=document.createElement('a');link.href=url;link.download=filename;link.click();URL.revokeObjectURL(url)}
