<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Download, MagicStick, Refresh } from '@element-plus/icons-vue'
import {
  fetchDocumentFile,
  getDocument,
  getDocumentChunks,
  getDocumentParseStatus,
  startDocumentParsing,
  type DocumentChunk,
  type GeologicalDocument,
  type ParseStatus,
} from '@/api/documents'

const route = useRoute()
const router = useRouter()
const documentId = Number(route.params.id)
const loading = ref(true)
const starting = ref(false)
const document = ref<GeologicalDocument>()
const status = ref<ParseStatus>()
const chunks = ref<DocumentChunk[]>([])
const sourceUrl = ref('')
let pollTimer: number | undefined

const isParsing = computed(() => status.value?.status === 'PARSING')
const canPreview = computed(() => document.value && document.value.type !== 'WORD')
const actionLabel = computed(() => status.value?.status === 'PARSED' ? '重新解析' : status.value?.status === 'FAILED' ? '重试解析' : '开始解析')
const statusLabel = computed(() => ({ UPLOADED: '等待解析', PARSING: '正在解析', PARSED: '解析完成', FAILED: '解析失败', ARCHIVED: '已归档' }[status.value?.status ?? 'UPLOADED']))

async function initialize() {
  if (!Number.isInteger(documentId) || documentId <= 0) return router.replace('/documents')
  loading.value = true
  try {
    const [metadata, parseStatus] = await Promise.all([getDocument(documentId), getDocumentParseStatus(documentId)])
    document.value = metadata
    status.value = parseStatus
    if (parseStatus.status === 'PARSED') chunks.value = await getDocumentChunks(documentId)
    await loadSource()
    if (parseStatus.status === 'PARSING') schedulePoll()
  } catch (error) { ElMessage.error(messageOf(error)) }
  finally { loading.value = false }
}

async function loadSource() {
  if (sourceUrl.value) URL.revokeObjectURL(sourceUrl.value)
  const blob = await fetchDocumentFile(documentId)
  sourceUrl.value = URL.createObjectURL(blob)
}

async function startParsing() {
  starting.value = true
  try {
    status.value = await startDocumentParsing(documentId)
    chunks.value = []
    ElMessage.success('解析任务已启动')
    schedulePoll(120)
  } catch (error) { ElMessage.error(messageOf(error)) }
  finally { starting.value = false }
}

function schedulePoll(delay = 500) {
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(pollStatus, delay)
}

async function pollStatus() {
  try {
    const latest = await getDocumentParseStatus(documentId)
    status.value = latest
    if (latest.status === 'PARSING') return schedulePoll()
    document.value = await getDocument(documentId)
    if (latest.status === 'PARSED') {
      chunks.value = await getDocumentChunks(documentId)
      ElMessage.success(`解析完成，共生成 ${latest.chunkCount} 个文本块`)
    }
  } catch (error) { ElMessage.error(messageOf(error)) }
}

function downloadSource() {
  if (!sourceUrl.value || !document.value) return
  const anchor = window.document.createElement('a')
  anchor.href = sourceUrl.value
  anchor.download = document.value.originalName || document.value.name
  anchor.click()
}

function pageLabel(chunk: DocumentChunk) {
  return chunk.pageStart === chunk.pageEnd ? `第 ${chunk.pageStart} 页` : `第 ${chunk.pageStart}–${chunk.pageEnd} 页`
}
function messageOf(error: unknown) { return error instanceof Error ? error.message : '操作失败' }

onMounted(initialize)
onBeforeUnmount(() => {
  if (pollTimer) window.clearTimeout(pollTimer)
  if (sourceUrl.value) URL.revokeObjectURL(sourceUrl.value)
})
</script>

<template>
  <div class="parse-page" v-loading="loading">
    <header class="parse-header">
      <button class="back-action" type="button" @click="router.push('/documents')"><el-icon><ArrowLeft /></el-icon><span>返回资料池</span></button>
      <div class="parse-title"><span class="eyebrow">Phase 3 · Intelligent parsing</span><h1>{{ document?.name || '文档解析' }}</h1><p>{{ document?.region || '未设置区域' }}<span>·</span>{{ document?.year || '未设置年份' }}<span>·</span>{{ document?.type }}</p></div>
      <div class="parse-actions"><button type="button" @click="downloadSource"><el-icon><Download /></el-icon>下载原件</button><button type="button" :disabled="isParsing || starting" @click="startParsing"><el-icon><Refresh v-if="status?.status === 'PARSED' || status?.status === 'FAILED'" /><MagicStick v-else /></el-icon>{{ actionLabel }}</button></div>
    </header>

    <section class="parse-status-card">
      <div><small>任务状态</small><strong :class="`status-${status?.status?.toLowerCase()}`">{{ statusLabel }}</strong></div>
      <el-progress :percentage="status?.progress || 0" :stroke-width="8" :status="status?.status === 'FAILED' ? 'exception' : status?.status === 'PARSED' ? 'success' : undefined" />
      <div class="parse-metrics"><span><strong>{{ status?.pageCount || '—' }}</strong>页</span><span><strong>{{ status?.chunkCount || '—' }}</strong>文本块</span></div>
    </section>
    <div v-if="status?.errorMessage" class="parse-error"><strong>解析异常</strong><p>{{ status.errorMessage }}</p><button type="button" @click="startParsing">重新尝试</button></div>

    <section class="parse-workspace">
      <article class="source-pane"><header><div><span class="pane-index">A</span><strong>原始资料</strong></div><small>{{ document?.originalName }}</small></header><div class="source-viewer"><img v-if="document?.type === 'IMAGE'" :src="sourceUrl" :alt="document.name" /><iframe v-else-if="canPreview" :src="sourceUrl" :title="document?.name"></iframe><div v-else class="word-placeholder"><strong>Word 文档</strong><p>浏览器无法保持原始 Word 版式，请下载原件查看；右侧展示解析后的结构化文本。</p><button type="button" @click="downloadSource">下载原件</button></div></div></article>
      <article class="text-pane"><header><div><span class="pane-index">B</span><strong>解析文本</strong></div><small>{{ chunks.length ? `${chunks.length} 个文本块` : '尚未生成' }}</small></header><div class="chunk-list" v-if="chunks.length"><section v-for="chunk in chunks" :key="chunk.id ?? chunk.chunkIndex" class="text-chunk"><div class="chunk-meta"><span>{{ String(chunk.chunkIndex + 1).padStart(2, '0') }}</span><strong>{{ chunk.chapterTitle || '未命名章节' }}</strong><small>{{ pageLabel(chunk) }} · {{ chunk.charCount }} 字</small></div><p>{{ chunk.content }}</p></section></div><div v-else class="empty-parsing"><MagicStick /><strong>{{ isParsing ? '正在提取和清洗文本' : '尚未解析此资料' }}</strong><p>{{ isParsing ? '完成后将在此按章节和页码展示文本块。' : '点击右上角“开始解析”生成结构化文本。' }}</p></div></article>
    </section>
  </div>
</template>

