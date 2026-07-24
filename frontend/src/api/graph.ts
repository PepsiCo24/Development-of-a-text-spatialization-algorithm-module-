import { http } from './http'

export type GraphNodeType='STRATUM'|'ROCK_BODY'|'STRUCTURE'|'ORE_BODY'|'MINERAL'|'REGION'
export interface GraphNode{id:number;name:string;nodeType:GraphNodeType;documentId:number;sourceText:string;page:number;longitude?:number;latitude?:number}
export interface GraphLink{source:number;target:number;relationType:'LOCATED_IN'|'OCCURS_IN'|'INTRUDES'|'CONTACTS'|'CONTROLS'|'CONTAINS';confidence?:number;sourceText?:string;page?:number}
export interface GraphView{nodes:GraphNode[];links:GraphLink[]}
export interface GraphStatus{documentId:number;status:'PENDING'|'SYNCING'|'COMPLETED'|'FAILED';progress:number;errorMessage?:string;nodeCount:number;relationCount:number;vectorCount:number;syncedAt?:string}
export interface QaResponse{answer:string;relatedEntities:Array<{id:number;name:string;nodeType:GraphNodeType;page:number}>;spatialLocations:Array<{entityId:number;name:string;longitude:number;latitude:number}>;sources:Array<{documentId:number;documentName:string;chunkId:number;pageStart:number;pageEnd:number;content:string;score:number}>;provider:string;model:string}
export interface QaStreamHandlers{onStatus?:(message:string)=>void;onMetadata?:(metadata:Omit<QaResponse,'answer'>)=>void;onDraft?:(content:string)=>void;onReset?:()=>void;onDelta?:(content:string)=>void;onWarning?:(message:string)=>void}

export async function syncGraph(documentId:number):Promise<GraphStatus>{const response=await http.post(`/documents/${documentId}/graph/sync`,undefined,{timeout:120000});return response.data.data}
export async function getGraphStatus(documentId:number):Promise<GraphStatus>{const response=await http.get(`/documents/${documentId}/graph/status`);return response.data.data}
export async function queryGraphNodes(query='',documentId?:number):Promise<GraphView>{const response=await http.get('/graph/nodes',{params:{query,documentId,limit:200}});return response.data.data}
export async function expandGraphNode(id:number,depth=1):Promise<GraphView>{const response=await http.get(`/graph/nodes/${id}/expand`,{params:{depth}});return response.data.data}
export async function queryGraphPath(sourceId:number,targetId:number):Promise<GraphView>{const response=await http.get('/graph/path',{params:{sourceId,targetId}});return response.data.data}
export async function askGeologicalQuestion(question:string,provider:'deepseek'|'qwen',limit=5):Promise<QaResponse>{const response=await http.post('/qa/ask',{question,provider,limit},{timeout:180000});return response.data.data}
export async function streamGeologicalQuestion(question:string,provider:'deepseek'|'qwen',handlers:QaStreamHandlers,limit=5,signal?:AbortSignal):Promise<void>{
  const token=localStorage.getItem('geotext-token')
  const response=await fetch('/api/qa/ask/stream',{method:'POST',headers:{'Content-Type':'application/json',Accept:'text/event-stream',...(token?{Authorization:`Bearer ${token}`}:{})},body:JSON.stringify({question,provider,limit}),signal})
  if(response.status===401){localStorage.removeItem('geotext-token');localStorage.removeItem('geotext-profile');throw new Error('登录状态已失效，请重新登录')}
  if(!response.ok||!response.body)throw new Error(`智能问答服务返回 HTTP ${response.status}`)
  const reader=response.body.getReader(),decoder=new TextDecoder();let buffer=''
  const dispatch=(frame:string)=>{let event='message';const data:string[]=[];for(const line of frame.split(/\r?\n/)){if(line.startsWith('event:'))event=line.slice(6).trim();else if(line.startsWith('data:'))data.push(line.slice(5).trim())}if(!data.length)return;const payload=JSON.parse(data.join('\n'));if(event==='status')handlers.onStatus?.(payload.message);else if(event==='metadata')handlers.onMetadata?.({relatedEntities:payload.related_entities,spatialLocations:payload.spatial_locations,sources:payload.sources,provider:payload.provider,model:payload.model});else if(event==='draft')handlers.onDraft?.(payload.content);else if(event==='reset')handlers.onReset?.();else if(event==='delta')handlers.onDelta?.(payload.content);else if(event==='warning')handlers.onWarning?.(payload.message);else if(event==='error')throw new Error(payload.message||'智能问答失败')}
  while(true){const {done,value}=await reader.read();buffer+=decoder.decode(value||new Uint8Array(),{stream:!done});const frames=buffer.split(/\r?\n\r?\n/);buffer=frames.pop()||'';for(const frame of frames)dispatch(frame);if(done)break}
  if(buffer.trim())dispatch(buffer)
}
