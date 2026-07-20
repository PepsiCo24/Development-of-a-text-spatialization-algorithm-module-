<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Connection, Refresh } from '@element-plus/icons-vue'
import {
  getDocument, getDocumentChunks, getDocumentEntities, getEntityExtractionStatus, startEntityExtraction,
  type DocumentChunk, type EntityExtractionStatus, type EntityType, type GeologicalDocument, type GeologicalEntity,
} from '@/api/documents'

const route = useRoute()
const router = useRouter()
const documentId = Number(route.params.id)
const loading = ref(true)
const starting = ref(false)
const document = ref<GeologicalDocument>()
const chunks = ref<DocumentChunk[]>([])
const entities = ref<GeologicalEntity[]>([])
const status = ref<EntityExtractionStatus>()
const provider = ref<'deepseek' | 'qwen'>('deepseek')
const activeType = ref<EntityType | 'ALL'>('ALL')
const selected = ref<GeologicalEntity>()
let pollTimer: number | undefined

const typeMeta: Record<EntityType, { label: string; color: string }> = {
  STRATUM: { label: '地层', color: '#4f7f67' }, LITHOLOGY: { label: '岩性', color: '#a96b43' },
  ROCK_BODY: { label: '岩体', color: '#8a5d75' }, FAULT: { label: '断裂', color: '#b34f42' },
  MINERAL: { label: '矿种', color: '#b8872d' }, ORE_BODY: { label: '矿体', color: '#906b26' },
  MINERALIZATION_ZONE: { label: '矿化带', color: '#c16d36' }, GEOLOGICAL_AGE: { label: '地质年代', color: '#6873a0' },
  PLACE: { label: '地名', color: '#2f7881' }, COORDINATE: { label: '坐标', color: '#397c9d' },
  GRADE: { label: '品位', color: '#7b6a32' }, THICKNESS: { label: '厚度', color: '#746852' },
  DIP_DIRECTION: { label: '倾向', color: '#55728e' }, DIP_ANGLE: { label: '倾角', color: '#6b6291' },
}
const entityTypes = computed(() => Array.from(new Set(entities.value.map(item => item.entityType))))
const visibleEntities = computed(() => activeType.value === 'ALL' ? entities.value : entities.value.filter(item => item.entityType === activeType.value))
const isExtracting = computed(() => status.value?.status === 'EXTRACTING')
const statusLabel = computed(() => ({ PENDING: '等待识别', EXTRACTING: 'LLM 识别中', COMPLETED: '识别完成', FAILED: '识别失败' }[status.value?.status || 'PENDING']))

async function initialize() {
  if (!Number.isInteger(documentId) || documentId <= 0) return router.replace('/entities')
  loading.value = true
  try {
    const [metadata, textChunks, extractionStatus] = await Promise.all([getDocument(documentId), getDocumentChunks(documentId), getEntityExtractionStatus(documentId)])
    document.value = metadata; chunks.value = textChunks; status.value = extractionStatus
    if (extractionStatus.status === 'COMPLETED') entities.value = await getDocumentEntities(documentId)
    if (extractionStatus.status === 'EXTRACTING') schedulePoll()
  } catch (error) { ElMessage.error(messageOf(error)) }
  finally { loading.value = false }
}

async function startExtraction() {
  starting.value = true
  try {
    status.value = await startEntityExtraction(documentId, provider.value)
    entities.value = []; selected.value = undefined
    ElMessage.success(`已提交 ${provider.value === 'deepseek' ? 'DeepSeek' : 'Qwen'} 识别任务`)
    schedulePoll()
  } catch (error) { ElMessage.error(messageOf(error)) }
  finally { starting.value = false }
}

function schedulePoll() {
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(pollStatus, 1600)
}
async function pollStatus() {
  try {
    const latest = await getEntityExtractionStatus(documentId); status.value = latest
    if (latest.status === 'EXTRACTING') return schedulePoll()
    if (latest.status === 'COMPLETED') {
      entities.value = await getDocumentEntities(documentId)
      ElMessage.success(`识别完成，共发现 ${latest.entityCount} 个地质实体`)
    }
  } catch (error) { ElMessage.error(messageOf(error)) }
}

type Segment = { text: string; entity?: GeologicalEntity }
function segments(chunk: DocumentChunk): Segment[] {
  const matches = visibleEntities.value.filter(item => item.chunkId === chunk.id && item.sourceStart !== undefined && item.sourceEnd !== undefined)
    .filter(item => item.sourceStart! >= 0 && item.sourceEnd! <= chunk.content.length)
    .sort((a, b) => a.sourceStart! - b.sourceStart! || b.confidence - a.confidence)
  const result: Segment[] = []; let cursor = 0
  for (const entity of matches) {
    if (entity.sourceStart! < cursor) continue
    if (entity.sourceStart! > cursor) result.push({ text: chunk.content.slice(cursor, entity.sourceStart) })
    result.push({ text: chunk.content.slice(entity.sourceStart, entity.sourceEnd), entity })
    cursor = entity.sourceEnd!
  }
  if (cursor < chunk.content.length) result.push({ text: chunk.content.slice(cursor) })
  return result.length ? result : [{ text: chunk.content }]
}
function selectEntity(entity: GeologicalEntity) { selected.value = entity }
function messageOf(error: unknown) { return error instanceof Error ? error.message : '操作失败' }
onMounted(initialize)
onBeforeUnmount(() => { if (pollTimer) window.clearTimeout(pollTimer) })
</script>

<template>
  <div class="entity-page" v-loading="loading">
    <header class="entity-header">
      <button class="back-action" type="button" @click="router.push('/entities')"><el-icon><ArrowLeft /></el-icon>返回实体任务</button>
      <div><span class="eyebrow">Phase 4 · Geological entities</span><h1>{{ document?.name || '地质实体识别' }}</h1><p>原文证据与模型识别结果对照核查</p></div>
      <div class="entity-run-controls"><div class="provider-switch" aria-label="模型服务"><button type="button" :class="{ active: provider === 'deepseek' }" @click="provider = 'deepseek'">DeepSeek</button><button type="button" :class="{ active: provider === 'qwen' }" @click="provider = 'qwen'">Qwen</button></div><button class="run-entity-button" type="button" :disabled="isExtracting || starting" @click="startExtraction"><el-icon><Refresh v-if="status?.status === 'COMPLETED' || status?.status === 'FAILED'" /><Connection v-else /></el-icon>{{ status?.status === 'COMPLETED' ? '重新识别' : status?.status === 'FAILED' ? '重试识别' : '开始识别' }}</button></div>
    </header>

    <section class="entity-status-strip"><div><small>识别状态</small><strong :class="`is-${status?.status?.toLowerCase()}`">{{ statusLabel }}</strong></div><el-progress :percentage="status?.progress || 0" :stroke-width="6" :status="status?.status === 'FAILED' ? 'exception' : status?.status === 'COMPLETED' ? 'success' : undefined" /><div><strong>{{ status?.entityCount || 0 }}</strong><small>实体</small></div></section>
    <div v-if="status?.errorMessage" class="parse-error"><strong>模型调用异常</strong><p>{{ status.errorMessage }}</p><button type="button" @click="startExtraction">重新尝试</button></div>

    <div class="entity-filter-bar"><button type="button" :class="{ active: activeType === 'ALL' }" @click="activeType = 'ALL'">全部 <small>{{ entities.length }}</small></button><button v-for="type in entityTypes" :key="type" type="button" :class="{ active: activeType === type }" @click="activeType = type"><i :style="{ background: typeMeta[type].color }"></i>{{ typeMeta[type].label }} <small>{{ entities.filter(item => item.entityType === type).length }}</small></button></div>

    <section class="entity-workspace">
      <article class="evidence-pane"><header><span class="pane-index">A</span><div><strong>原文证据</strong><small>点击彩色实体查看模型判断</small></div></header><div class="evidence-scroll"><section v-for="chunk in chunks" :key="chunk.id" class="evidence-chunk"><div><span>{{ String(chunk.chunkIndex + 1).padStart(2, '0') }}</span><strong>{{ chunk.chapterTitle || '未命名章节' }}</strong><small>第 {{ chunk.pageStart }}{{ chunk.pageEnd !== chunk.pageStart ? `–${chunk.pageEnd}` : '' }} 页</small></div><p><template v-for="(segment, index) in segments(chunk)" :key="index"><button v-if="segment.entity" type="button" class="entity-mark" :class="{ selected: selected?.id === segment.entity.id }" :style="{ '--entity-color': typeMeta[segment.entity.entityType].color }" @click="selectEntity(segment.entity)">{{ segment.text }}</button><template v-else>{{ segment.text }}</template></template></p></section></div></article>
      <aside class="entity-inspector"><header><span class="pane-index">B</span><div><strong>实体信息</strong><small>{{ visibleEntities.length }} 个识别结果</small></div></header><div v-if="selected" class="entity-detail"><span class="entity-type-badge" :style="{ '--entity-color': typeMeta[selected.entityType].color }">{{ typeMeta[selected.entityType].label }}</span><h2>{{ selected.entityName }}</h2><div class="confidence"><span>模型置信度</span><strong>{{ Math.round(selected.confidence * 100) }}%</strong><i><b :style="{ width: `${selected.confidence * 100}%` }"></b></i></div><dl><div><dt>来源页码</dt><dd>第 {{ selected.page }} 页</dd></div><div><dt>模型服务</dt><dd>{{ selected.provider }} / {{ selected.model }}</dd></div></dl><blockquote>{{ selected.sourceText }}</blockquote></div><div v-else class="entity-result-list"><button v-for="entity in visibleEntities" :key="entity.id" type="button" @click="selectEntity(entity)"><i :style="{ background: typeMeta[entity.entityType].color }"></i><span><strong>{{ entity.entityName }}</strong><small>{{ typeMeta[entity.entityType].label }} · 第 {{ entity.page }} 页</small></span><b>{{ Math.round(entity.confidence * 100) }}%</b></button><div v-if="!visibleEntities.length" class="empty-parsing"><Connection /><strong>{{ isExtracting ? '模型正在阅读原文' : '尚无实体结果' }}</strong><p>{{ isExtracting ? '识别完成后将在原文中以颜色分类标记。' : '选择模型服务并开始实体识别。' }}</p></div></div></aside>
    </section>
  </div>
</template>
