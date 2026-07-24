<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Connection, Refresh } from '@element-plus/icons-vue'
import {
  createEntity, deleteEntity, getDocument, getDocumentChunks, getDocumentEntities, getEntityExtractionStatus, reviewEntity, startEntityExtraction, updateEntity,
  type DocumentChunk, type EntityExtractionStatus, type EntityType, type GeologicalDocument, type GeologicalEntity, type ManualEntity,
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
const activeReview = ref<'ALL' | 'PENDING' | 'CONFIRMED' | 'REJECTED'>('ALL')
const minConfidence = ref(0)
const selected = ref<GeologicalEntity>()
const manualOpen = ref(false)
const manualSaving = ref(false)
const editingEntityId = ref<number>()
const manualForm = ref<ManualEntity>({ chunkId: 0, entityName: '', entityType: 'PLACE', confidence: 0.9, sourceText: '', page: 1, reviewStatus: 'PENDING' })
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
const visibleEntities = computed(() => entities.value.filter(item => (activeType.value === 'ALL' || item.entityType === activeType.value) && (activeReview.value === 'ALL' || item.reviewStatus === activeReview.value) && item.confidence >= minConfidence.value))
const isExtracting = computed(() => status.value?.status === 'EXTRACTING')
const statusLabel = computed(() => ({ PENDING: '等待识别', EXTRACTING: 'LLM 识别中', COMPLETED: '识别完成', FAILED: '识别失败' }[status.value?.status || 'PENDING']))

async function initialize() {
  if (!Number.isInteger(documentId) || documentId <= 0) return router.replace('/entities')
  loading.value = true
  try {
    const [metadata, textChunks, extractionStatus] = await Promise.all([getDocument(documentId), getDocumentChunks(documentId), getEntityExtractionStatus(documentId)])
    document.value = metadata; chunks.value = textChunks; status.value = extractionStatus
    if (extractionStatus.status === 'COMPLETED') { entities.value = await getDocumentEntities(documentId); await focusLinkedEntity() }
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
    schedulePoll(150)
  } catch (error) { ElMessage.error(messageOf(error)) }
  finally { starting.value = false }
}

function schedulePoll(delay = 650) {
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(pollStatus, delay)
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
async function focusLinkedEntity() {
  const entityId = Number(route.query.entityId)
  if (!Number.isFinite(entityId)) return
  const linked = entities.value.find(item => item.id === entityId)
  if (!linked) return
  activeType.value = 'ALL'; activeReview.value = 'ALL'; minConfidence.value = 0; selected.value = linked
  await nextTick()
  const mark = Array.from(window.document.querySelectorAll<HTMLButtonElement>('.entity-mark')).find(item => item.textContent?.trim() === linked.entityName)
  mark?.scrollIntoView({ behavior: 'smooth', block: 'center' }); mark?.focus()
  ElMessage.success(`已从地图定位到第 ${linked.page} 页原文：${linked.entityName}`)
}
function openManual(entity?: GeologicalEntity) {
  editingEntityId.value = entity?.id
  manualForm.value = entity ? { chunkId: entity.chunkId, entityName: entity.entityName, entityType: entity.entityType, confidence: entity.confidence, sourceText: entity.sourceText, page: entity.page, sourceStart: entity.sourceStart, sourceEnd: entity.sourceEnd, reviewStatus: entity.reviewStatus || 'PENDING' } : { chunkId: chunks.value[0]?.id || 0, entityName: '', entityType: 'PLACE', confidence: 0.9, sourceText: chunks.value[0]?.content.slice(0, 120) || '', page: chunks.value[0]?.pageStart || 1, reviewStatus: 'PENDING' }
  manualOpen.value = true
}
async function saveManual() {
  if (!manualForm.value.entityName.trim() || !manualForm.value.chunkId) return ElMessage.warning('请填写实体名称并选择来源文本块')
  manualSaving.value = true
  try {
    const saved = editingEntityId.value ? await updateEntity(documentId, editingEntityId.value, manualForm.value) : await createEntity(documentId, manualForm.value)
    entities.value = await getDocumentEntities(documentId); selected.value = saved; manualOpen.value = false
    ElMessage.success(editingEntityId.value ? '实体已人工修订' : '实体已补充')
  } catch (error) { ElMessage.error(messageOf(error)) } finally { manualSaving.value = false }
}
async function setReview(status: 'CONFIRMED' | 'REJECTED') { if (!selected.value) return; try { selected.value = await reviewEntity(documentId, selected.value.id, status); entities.value = await getDocumentEntities(documentId); ElMessage.success(status === 'CONFIRMED' ? '已确认实体' : '已标记为驳回') } catch (error) { ElMessage.error(messageOf(error)) } }
async function removeSelected() { if (!selected.value) return; try { await ElMessageBox.confirm(`删除“${selected.value.entityName}”后不可恢复。`, '删除实体', { type: 'warning' }); await deleteEntity(documentId, selected.value.id); selected.value = undefined; entities.value = await getDocumentEntities(documentId); ElMessage.success('实体已删除') } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(messageOf(error)) } }
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

    <div class="manual-review-toolbar"><el-select v-model="activeReview" size="small" style="width: 130px"><el-option label="全部校核状态" value="ALL" /><el-option label="待人工核验" value="PENDING" /><el-option label="已确认" value="CONFIRMED" /><el-option label="已驳回" value="REJECTED" /></el-select><el-select v-model="minConfidence" size="small" style="width: 140px"><el-option label="全部置信度" :value="0" /><el-option label="置信度 ≥ 60%" :value="0.6" /><el-option label="置信度 ≥ 80%" :value="0.8" /></el-select><el-button type="primary" size="small" @click="openManual()">补充实体</el-button><span v-if="selected">当前：{{ selected.entityName }} · {{ selected.reviewStatus === 'CONFIRMED' ? '已确认' : selected.reviewStatus === 'REJECTED' ? '已驳回' : '待核验' }}</span><el-button v-if="selected" size="small" @click="openManual(selected)">修改</el-button><el-button v-if="selected" type="success" size="small" @click="setReview('CONFIRMED')">确认</el-button><el-button v-if="selected" type="warning" size="small" @click="setReview('REJECTED')">驳回</el-button><el-button v-if="selected" type="danger" size="small" @click="removeSelected">删除</el-button></div>
    <section class="entity-workspace">
      <article class="evidence-pane"><header><span class="pane-index">A</span><div><strong>原文证据</strong><small>点击彩色实体查看模型判断</small></div></header><div class="evidence-scroll"><section v-for="chunk in chunks" :key="chunk.id" class="evidence-chunk"><div><span>{{ String(chunk.chunkIndex + 1).padStart(2, '0') }}</span><strong>{{ chunk.chapterTitle || '未命名章节' }}</strong><small>第 {{ chunk.pageStart }}{{ chunk.pageEnd !== chunk.pageStart ? `–${chunk.pageEnd}` : '' }} 页</small></div><p><template v-for="(segment, index) in segments(chunk)" :key="index"><button v-if="segment.entity" type="button" class="entity-mark" :class="{ selected: selected?.id === segment.entity.id }" :style="{ '--entity-color': typeMeta[segment.entity.entityType].color }" @click="selectEntity(segment.entity)">{{ segment.text }}</button><template v-else>{{ segment.text }}</template></template></p></section></div></article>
      <aside class="entity-inspector"><header><span class="pane-index">B</span><div><strong>实体信息</strong><small>{{ visibleEntities.length }} 个识别结果</small></div></header><div v-if="selected" class="entity-detail"><span class="entity-type-badge" :style="{ '--entity-color': typeMeta[selected.entityType].color }">{{ typeMeta[selected.entityType].label }}</span><h2>{{ selected.entityName }}</h2><div class="confidence"><span>模型置信度</span><strong>{{ Math.round(selected.confidence * 100) }}%</strong><i><b :style="{ width: `${selected.confidence * 100}%` }"></b></i></div><dl><div><dt>来源页码</dt><dd>第 {{ selected.page }} 页</dd></div><div><dt>模型服务</dt><dd>{{ selected.provider }} / {{ selected.model }}</dd></div></dl><blockquote>{{ selected.sourceText }}</blockquote></div><div v-else class="entity-result-list"><button v-for="entity in visibleEntities" :key="entity.id" type="button" @click="selectEntity(entity)"><i :style="{ background: typeMeta[entity.entityType].color }"></i><span><strong>{{ entity.entityName }}</strong><small>{{ typeMeta[entity.entityType].label }} · 第 {{ entity.page }} 页</small></span><b>{{ Math.round(entity.confidence * 100) }}%</b></button><div v-if="!visibleEntities.length" class="empty-parsing"><Connection /><strong>{{ isExtracting ? '模型正在阅读原文' : '尚无实体结果' }}</strong><p>{{ isExtracting ? '识别完成后将在原文中以颜色分类标记。' : '选择模型服务并开始实体识别。' }}</p></div></div></aside>
    </section>
  </div>
    <el-dialog v-model="manualOpen" :title="editingEntityId ? '人工修改实体' : '补充实体'" width="min(680px, 94vw)"><el-form label-position="top"><div class="form-grid"><el-form-item label="实体名称" required><el-input v-model="manualForm.entityName" /></el-form-item><el-form-item label="实体类型" required><el-select v-model="manualForm.entityType"><el-option v-for="(_, key) in typeMeta" :key="key" :label="typeMeta[key as EntityType].label" :value="key" /></el-select></el-form-item><el-form-item label="来源文本块" required><el-select v-model="manualForm.chunkId"><el-option v-for="chunk in chunks" :key="chunk.id" :label="`第 ${chunk.pageStart} 页 · ${chunk.chapterTitle || '文本块'}`" :value="chunk.id" /></el-select></el-form-item><el-form-item label="置信度"><el-input-number v-model="manualForm.confidence" :min="0" :max="1" :step="0.05" /></el-form-item></div><el-form-item label="来源页码"><el-input-number v-model="manualForm.page" :min="1" /></el-form-item><el-form-item label="来源原文" required><el-input v-model="manualForm.sourceText" type="textarea" :rows="4" /></el-form-item></el-form><template #footer><el-button @click="manualOpen = false">取消</el-button><el-button type="primary" :loading="manualSaving" @click="saveManual">保存人工校核</el-button></template></el-dialog>
</template>
