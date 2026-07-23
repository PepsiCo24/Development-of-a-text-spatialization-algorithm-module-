import { http } from './http'

export type GraphNodeType='STRATUM'|'ROCK_BODY'|'STRUCTURE'|'ORE_BODY'|'MINERAL'|'REGION'
export interface GraphNode{id:number;name:string;nodeType:GraphNodeType;documentId:number;sourceText:string;page:number;longitude?:number;latitude?:number}
export interface GraphLink{source:number;target:number;relationType:'LOCATED_IN'|'OCCURS_IN'|'INTRUDES'|'CONTACTS'|'CONTROLS'|'CONTAINS';confidence?:number;sourceText?:string;page?:number}
export interface GraphView{nodes:GraphNode[];links:GraphLink[]}
export interface GraphStatus{documentId:number;status:'PENDING'|'SYNCING'|'COMPLETED'|'FAILED';progress:number;errorMessage?:string;nodeCount:number;relationCount:number;vectorCount:number;syncedAt?:string}
export interface QaResponse{answer:string;relatedEntities:Array<{id:number;name:string;nodeType:GraphNodeType;page:number}>;spatialLocations:Array<{entityId:number;name:string;longitude:number;latitude:number}>;sources:Array<{documentId:number;documentName:string;chunkId:number;pageStart:number;pageEnd:number;content:string;score:number}>;provider:string;model:string}

export async function syncGraph(documentId:number):Promise<GraphStatus>{const response=await http.post(`/documents/${documentId}/graph/sync`,undefined,{timeout:120000});return response.data.data}
export async function getGraphStatus(documentId:number):Promise<GraphStatus>{const response=await http.get(`/documents/${documentId}/graph/status`);return response.data.data}
export async function queryGraphNodes(query='',documentId?:number):Promise<GraphView>{const response=await http.get('/graph/nodes',{params:{query,documentId,limit:200}});return response.data.data}
export async function expandGraphNode(id:number,depth=1):Promise<GraphView>{const response=await http.get(`/graph/nodes/${id}/expand`,{params:{depth}});return response.data.data}
export async function queryGraphPath(sourceId:number,targetId:number):Promise<GraphView>{const response=await http.get('/graph/path',{params:{sourceId,targetId}});return response.data.data}
export async function askGeologicalQuestion(question:string,provider:'deepseek'|'qwen',limit=5):Promise<QaResponse>{const response=await http.post('/qa/ask',{question,provider,limit},{timeout:180000});return response.data.data}
