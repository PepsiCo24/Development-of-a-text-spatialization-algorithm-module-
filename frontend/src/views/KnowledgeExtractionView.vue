<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, Connection, Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAttribute, createRelation, deleteAttribute, deleteRelation, getDocument, getKnowledgeResult, getKnowledgeStatus,
  reviewAttribute, reviewRelation, startKnowledgeExtraction, updateAttribute, updateRelation,
  type AttributeType, type EntityAttribute, type EntityRelation, type GeologicalDocument, type GeologicalEntity,
  type KnowledgeResult, type KnowledgeStatus, type ManualAttribute, type ManualRelation, type RelationType,
} from '@/api/documents'

const route = useRoute(), router = useRouter(), documentId = Number(route.params.id)
const loading = ref(true), starting = ref(false), document = ref<GeologicalDocument>(), status = ref<KnowledgeStatus>()
const result = ref<KnowledgeResult>({ entities: [], attributes: [], relations: [] })
const provider = ref<'deepseek' | 'qwen'>('deepseek'), tab = ref<'relations' | 'attributes' | 'terms'>('relations')
const attributeDialog = ref(false), relationDialog = ref(false), saving = ref(false)
const editingAttributeId = ref<number>(), editingRelationId = ref<number>()
const attributeForm = ref<ManualAttribute>({ entityId: 0, attributeType: 'AGE', originalValue: '', confidence: 0.9, sourceText: '', page: 1, reviewStatus: 'PENDING' })
const relationForm = ref<ManualRelation>({ sourceEntityId: 0, targetEntityId: 0, relationType: 'LOCATED_IN', confidence: 0.9, sourceText: '', page: 1, reviewStatus: 'PENDING' })
let timer: number | undefined

const entityMap = computed(() => new Map(result.value.entities.map(item => [item.id, item])))
const isExtracting = computed(() => status.value?.status === 'EXTRACTING')
const statusLabel = computed(() => ({ PENDING: '等待抽取', EXTRACTING: '知识抽取中', COMPLETED: '抽取完成', FAILED: '抽取失败' }[status.value?.status || 'PENDING']))
const relationLabels: Record<RelationType, string> = { LOCATED_IN: '位于', OCCURS_IN: '赋存于', INTRUDES: '侵入', CONTACTS: '接触', CONTROLS: '控制', CONTAINS: '包含' }
const attributeLabels: Record<AttributeType, string> = { AGE: '年代', THICKNESS: '厚度', SCALE: '规模', GRADE: '品位', LITHOLOGY: '岩性' }
const normalizationLabels: Record<string, string> = { EXACT: '标准名', ALIAS: '别名匹配', UNMATCHED: '未匹配', PENDING: '待处理' }

async function initialize() {
  if (!Number.isInteger(documentId) || documentId <= 0) return router.replace('/knowledge')
  loading.value = true
  try {
    const [metadata, extractionStatus] = await Promise.all([getDocument(documentId), getKnowledgeStatus(documentId)])
    document.value = metadata; status.value = extractionStatus
    if (extractionStatus.status === 'COMPLETED') await reload()
    if (extractionStatus.status === 'EXTRACTING') schedule()
  } catch (error) { ElMessage.error(message(error)) } finally { loading.value = false }
}
async function reload() { result.value = await getKnowledgeResult(documentId) }
async function start() {
  starting.value = true
  try { status.value = await startKnowledgeExtraction(documentId, provider.value); result.value = { entities: [], attributes: [], relations: [] }; ElMessage.success('属性关系抽取任务已提交'); schedule() }
  catch (error) { ElMessage.error(message(error)) } finally { starting.value = false }
}
function schedule() { if (timer) clearTimeout(timer); timer = window.setTimeout(poll, 1600) }
async function poll() {
  try { const latest = await getKnowledgeStatus(documentId); status.value = latest; if (latest.status === 'EXTRACTING') return schedule(); if (latest.status === 'COMPLETED') { await reload(); ElMessage.success(`抽取完成：${latest.attributeCount} 个属性，${latest.relationCount} 条关系`) } }
  catch (error) { ElMessage.error(message(error)); if (status.value?.status === 'EXTRACTING') schedule() }
}
function entity(id: number): GeologicalEntity | undefined { return entityMap.value.get(id) }
function openAttribute(item?: EntityAttribute) {
  editingAttributeId.value = item?.id
  attributeForm.value = item ? { entityId: item.entityId, attributeType: item.attributeType, originalValue: item.originalValue, confidence: item.confidence, sourceText: item.sourceText, page: item.page, reviewStatus: item.reviewStatus || 'PENDING' } : { entityId: result.value.entities[0]?.id || 0, attributeType: 'AGE', originalValue: '', confidence: 0.9, sourceText: '', page: 1, reviewStatus: 'PENDING' }
  attributeDialog.value = true
}
function openRelation(item?: EntityRelation) {
  editingRelationId.value = item?.id
  relationForm.value = item ? { sourceEntityId: item.sourceEntityId, targetEntityId: item.targetEntityId, relationType: item.relationType, confidence: item.confidence, sourceText: item.sourceText, page: item.page, reviewStatus: item.reviewStatus || 'PENDING' } : { sourceEntityId: result.value.entities[0]?.id || 0, targetEntityId: result.value.entities[1]?.id || 0, relationType: 'LOCATED_IN', confidence: 0.9, sourceText: '', page: 1, reviewStatus: 'PENDING' }
  relationDialog.value = true
}
async function saveAttribute() {
  if (!attributeForm.value.entityId || !attributeForm.value.originalValue.trim() || !attributeForm.value.sourceText.trim()) return ElMessage.warning('请完整填写属性值和来源原文')
  saving.value = true
  try { editingAttributeId.value ? await updateAttribute(documentId, editingAttributeId.value, attributeForm.value) : await createAttribute(documentId, attributeForm.value); await reload(); attributeDialog.value = false; ElMessage.success(editingAttributeId.value ? '属性已修改' : '属性已补充') }
  catch (error) { ElMessage.error(message(error)) } finally { saving.value = false }
}
async function saveRelation() {
  if (!relationForm.value.sourceEntityId || !relationForm.value.targetEntityId || relationForm.value.sourceEntityId === relationForm.value.targetEntityId || !relationForm.value.sourceText.trim()) return ElMessage.warning('请选择不同的起止实体并填写来源原文')
  saving.value = true
  try { editingRelationId.value ? await updateRelation(documentId, editingRelationId.value, relationForm.value) : await createRelation(documentId, relationForm.value); await reload(); relationDialog.value = false; ElMessage.success(editingRelationId.value ? '关系已修改' : '关系已补充') }
  catch (error) { ElMessage.error(message(error)) } finally { saving.value = false }
}
async function confirmAttribute(item: EntityAttribute) { try { await reviewAttribute(documentId, item.id, 'CONFIRMED'); await reload(); ElMessage.success('属性已人工确认') } catch (error) { ElMessage.error(message(error)) } }
async function confirmRelation(item: EntityRelation) { try { await reviewRelation(documentId, item.id, 'CONFIRMED'); await reload(); ElMessage.success('关系已人工确认') } catch (error) { ElMessage.error(message(error)) } }
async function removeAttribute(item: EntityAttribute) { try { await ElMessageBox.confirm(`确认删除属性“${item.originalValue}”？`, '删除属性', { type: 'warning' }); await deleteAttribute(documentId, item.id); await reload(); ElMessage.success('属性已删除') } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(message(error)) } }
async function removeRelation(item: EntityRelation) { try { await ElMessageBox.confirm('确认删除这条实体关系？', '删除关系', { type: 'warning' }); await deleteRelation(documentId, item.id); await reload(); ElMessage.success('关系已删除') } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(message(error)) } }
function reviewLabel(value?: string) { return value === 'CONFIRMED' ? '已确认' : value === 'REJECTED' ? '已驳回' : '待核验' }
function message(error: unknown) { return error instanceof Error ? error.message : '操作失败' }
onMounted(initialize); onBeforeUnmount(() => { if (timer) clearTimeout(timer) })
</script>

<template>
  <div class="knowledge-page" v-loading="loading">
    <header class="entity-header">
      <button class="back-action" type="button" @click="router.push('/knowledge')"><el-icon><ArrowLeft /></el-icon>返回知识任务</button>
      <div><span class="eyebrow">Phase 5 · Attributes & relations</span><h1>{{ document?.name || '知识抽取' }}</h1><p>属性、关系与标准术语的证据化核查</p></div>
      <div class="entity-run-controls"><div class="provider-switch"><button type="button" :class="{ active: provider === 'deepseek' }" @click="provider = 'deepseek'">DeepSeek</button><button type="button" :class="{ active: provider === 'qwen' }" @click="provider = 'qwen'">Qwen</button></div><button class="run-entity-button" type="button" :disabled="isExtracting || starting" @click="start"><el-icon><Refresh v-if="status?.status === 'COMPLETED' || status?.status === 'FAILED'" /><Connection v-else /></el-icon>{{ status?.status === 'COMPLETED' ? '重新抽取' : status?.status === 'FAILED' ? '重试抽取' : '开始抽取' }}</button></div>
    </header>
    <section class="knowledge-status"><div><small>任务状态</small><strong :class="`is-${status?.status?.toLowerCase()}`">{{ statusLabel }}</strong></div><el-progress :percentage="status?.progress || 0" :stroke-width="6" :status="status?.status === 'FAILED' ? 'exception' : status?.status === 'COMPLETED' ? 'success' : undefined" /><div class="knowledge-metrics"><span><b>{{ result.attributes.length }}</b>属性</span><span><b>{{ result.relations.length }}</b>关系</span><span><b>{{ status?.normalizedCount || 0 }}</b>标准化</span></div></section>
    <div v-if="status?.errorMessage" class="parse-error"><strong>抽取异常</strong><p>{{ status.errorMessage }}</p><button type="button" @click="start">重新尝试</button></div>
    <nav class="knowledge-tabs" aria-label="知识结果类型"><button type="button" :class="{ active: tab === 'relations' }" @click="tab = 'relations'">关系网络 <small>{{ result.relations.length }}</small></button><button type="button" :class="{ active: tab === 'attributes' }" @click="tab = 'attributes'">实体属性 <small>{{ result.attributes.length }}</small></button><button type="button" :class="{ active: tab === 'terms' }" @click="tab = 'terms'">术语标准化 <small>{{ result.entities.length }}</small></button></nav>
    <section class="knowledge-results">
      <div v-if="tab === 'relations'" class="result-toolbar"><div><strong>关系三元组</strong><small>可补充、修订、删除并人工确认</small></div><el-button type="primary" @click="openRelation()"><el-icon><Plus /></el-icon>补充关系</el-button></div>
      <div v-if="tab === 'relations'" class="relation-list"><article v-for="relation in result.relations" :key="relation.id"><div class="relation-route"><span><small>{{ entity(relation.sourceEntityId)?.entityType }}</small><strong>{{ entity(relation.sourceEntityId)?.standardName || entity(relation.sourceEntityId)?.entityName }}</strong></span><b>{{ relationLabels[relation.relationType] }}</b><span><small>{{ entity(relation.targetEntityId)?.entityType }}</small><strong>{{ entity(relation.targetEntityId)?.standardName || entity(relation.targetEntityId)?.entityName }}</strong></span></div><footer><blockquote>{{ relation.sourceText }}</blockquote><span>第 {{ relation.page }} 页 · {{ Math.round(relation.confidence * 100) }}% · {{ reviewLabel(relation.reviewStatus) }}</span><div class="evidence-actions"><button type="button" @click="openRelation(relation)"><el-icon><Edit /></el-icon>修改</button><button v-if="relation.reviewStatus !== 'CONFIRMED'" type="button" @click="confirmRelation(relation)"><el-icon><Check /></el-icon>确认</button><button class="danger" type="button" @click="removeRelation(relation)"><el-icon><Delete /></el-icon>删除</button></div></footer></article></div>
      <div v-else-if="tab === 'attributes'" class="result-toolbar"><div><strong>实体属性</strong><small>每项修改都保留来源证据</small></div><el-button type="primary" @click="openAttribute()"><el-icon><Plus /></el-icon>补充属性</el-button></div>
      <div v-if="tab === 'attributes'" class="attribute-grid"><article v-for="attribute in result.attributes" :key="attribute.id"><header><span>{{ attributeLabels[attribute.attributeType] }}</span><b>{{ Math.round(attribute.confidence * 100) }}%</b></header><h3>{{ entity(attribute.entityId)?.standardName || entity(attribute.entityId)?.entityName }}</h3><strong>{{ attribute.originalValue }}</strong><p>{{ attribute.sourceText }}</p><small>第 {{ attribute.page }} 页 · {{ reviewLabel(attribute.reviewStatus) }}</small><div class="evidence-actions"><button type="button" @click="openAttribute(attribute)"><el-icon><Edit /></el-icon>修改</button><button v-if="attribute.reviewStatus !== 'CONFIRMED'" type="button" @click="confirmAttribute(attribute)"><el-icon><Check /></el-icon>确认</button><button class="danger" type="button" @click="removeAttribute(attribute)"><el-icon><Delete /></el-icon>删除</button></div></article></div>
      <div v-if="tab === 'terms'" class="term-table"><header><span>原名称</span><span>标准名称</span><span>实体类型</span><span>匹配结果</span></header><div v-for="item in result.entities" :key="item.id"><strong>{{ item.entityName }}</strong><span>{{ item.standardName || item.entityName }}</span><span>{{ item.entityType }}</span><b :class="`match-${(item.normalizationStatus || 'PENDING').toLowerCase()}`">{{ normalizationLabels[item.normalizationStatus || 'PENDING'] }}</b></div></div>
      <div v-if="status?.status !== 'COMPLETED' && !isExtracting" class="empty-parsing"><Connection /><strong>尚未生成知识结果</strong><p>选择模型并启动属性关系抽取。</p></div>
    </section>
  </div>

  <el-dialog v-model="attributeDialog" :title="editingAttributeId ? '修改实体属性' : '补充实体属性'" width="min(640px,94vw)"><el-form label-position="top"><div class="dialog-grid"><el-form-item label="所属实体" required><el-select v-model="attributeForm.entityId" filterable><el-option v-for="item in result.entities" :key="item.id" :label="item.standardName || item.entityName" :value="item.id" /></el-select></el-form-item><el-form-item label="属性类型" required><el-select v-model="attributeForm.attributeType"><el-option v-for="(label,key) in attributeLabels" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-form-item label="属性值" required><el-input v-model="attributeForm.originalValue" /></el-form-item><el-form-item label="置信度"><el-input-number v-model="attributeForm.confidence" :min="0" :max="1" :step="0.05" /></el-form-item></div><el-form-item label="来源原文" required><el-input v-model="attributeForm.sourceText" type="textarea" :rows="4" /></el-form-item><el-form-item label="来源页码"><el-input-number v-model="attributeForm.page" :min="1" /></el-form-item></el-form><template #footer><el-button @click="attributeDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveAttribute">保存属性</el-button></template></el-dialog>
  <el-dialog v-model="relationDialog" :title="editingRelationId ? '修改实体关系' : '补充实体关系'" width="min(680px,94vw)"><el-form label-position="top"><div class="dialog-grid"><el-form-item label="源实体" required><el-select v-model="relationForm.sourceEntityId" filterable><el-option v-for="item in result.entities" :key="item.id" :label="item.standardName || item.entityName" :value="item.id" /></el-select></el-form-item><el-form-item label="关系类型" required><el-select v-model="relationForm.relationType"><el-option v-for="(label,key) in relationLabels" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-form-item label="目标实体" required><el-select v-model="relationForm.targetEntityId" filterable><el-option v-for="item in result.entities" :key="item.id" :label="item.standardName || item.entityName" :value="item.id" /></el-select></el-form-item><el-form-item label="置信度"><el-input-number v-model="relationForm.confidence" :min="0" :max="1" :step="0.05" /></el-form-item></div><el-form-item label="来源原文" required><el-input v-model="relationForm.sourceText" type="textarea" :rows="4" /></el-form-item><el-form-item label="来源页码"><el-input-number v-model="relationForm.page" :min="1" /></el-form-item></el-form><template #footer><el-button @click="relationDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRelation">保存关系</el-button></template></el-dialog>
</template>

<style scoped>
.result-toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;padding:13px 16px;border:1px solid #d7d0c2;background:#f5f1e8}.result-toolbar>div{display:grid;gap:3px}.result-toolbar small{color:#738079}.evidence-actions{display:flex;gap:6px;margin-left:auto}.evidence-actions button{display:inline-flex;align-items:center;gap:4px;border:1px solid #c9c1b3;background:#f7f3eb;color:#28574e;padding:6px 9px}.evidence-actions button.danger{color:#a4473c}.dialog-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}@media(max-width:700px){.dialog-grid{grid-template-columns:1fr}.relation-list footer{display:grid}.evidence-actions{margin:8px 0 0}}
</style>
