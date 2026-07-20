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
