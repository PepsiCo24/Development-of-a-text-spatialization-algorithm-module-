import { http } from './http'

export interface GeologicalDocument {
  id: number
  name: string
  type: 'PDF' | 'WORD' | 'TXT' | 'IMAGE'
  region?: string
  year?: number
  keyword?: string
  summary?: string
  originalName: string
  contentType: string
  status: 'UPLOADED' | 'PARSING' | 'PARSED' | 'FAILED' | 'ARCHIVED'
  parseProgress?: number
  errorMessage?: string
  pageCount?: number
  chunkCount?: number
  parsedAt?: string
  fileSize: number
  createTime: string
  updateTime: string
}

export interface DocumentMetadata {
  name?: string
  region?: string
  year?: number
  keyword?: string
  summary?: string
}

export interface DocumentPage {
  records: GeologicalDocument[]
  total: number
  page: number
  size: number
  pages: number
}

export interface DocumentChunk {
  id: number
  documentId: number
  chunkIndex: number
  chapterTitle?: string
  content: string
  pageStart: number
  pageEnd: number
  charCount: number
}

export interface ParseStatus {
  documentId: number
  status: GeologicalDocument['status']
  progress: number
  errorMessage?: string
  pageCount: number
  chunkCount: number
  parsedAt?: string
}

export interface DocumentFilters {
  query?: string
  type?: string
  region?: string
  year?: number
  status?: string
  page: number
  size: number
}

export async function listDocuments(filters: DocumentFilters): Promise<DocumentPage> {
  const response = await http.get('/documents', { params: filters })
  return response.data.data
}

export async function getDocument(id: number): Promise<GeologicalDocument> {
  const response = await http.get(`/documents/${id}`)
  return response.data.data
}

export async function uploadDocument(file: File, metadata: DocumentMetadata): Promise<GeologicalDocument> {
  const form = new FormData()
  form.append('file', file)
  Object.entries(metadata).forEach(([key, value]) => {
    if (value !== undefined && value !== '') form.append(key, String(value))
  })
  const response = await http.post('/documents', form)
  return response.data.data
}

export async function updateDocument(id: number, metadata: DocumentMetadata): Promise<GeologicalDocument> {
  const response = await http.put(`/documents/${id}`, metadata)
  return response.data.data
}

export async function updateDocumentStatus(id: number, status: GeologicalDocument['status']): Promise<void> {
  await http.patch(`/documents/${id}/status`, { status })
}

export async function deleteDocument(id: number): Promise<void> {
  await http.delete(`/documents/${id}`)
}

export async function fetchDocumentFile(id: number, disposition = 'inline'): Promise<Blob> {
  const response = await http.get(`/documents/${id}/preview`, { params: { disposition }, responseType: 'blob' })
  return response.data
}

export async function startDocumentParsing(id: number): Promise<ParseStatus> {
  const response = await http.post(`/documents/${id}/parse`)
  return response.data.data
}

export async function getDocumentParseStatus(id: number): Promise<ParseStatus> {
  const response = await http.get(`/documents/${id}/parse/status`)
  return response.data.data
}

export async function getDocumentChunks(id: number): Promise<DocumentChunk[]> {
  const response = await http.get(`/documents/${id}/chunks`)
  return response.data.data
}
