<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as mammoth from 'mammoth/mammoth.browser'
import { Delete, EditPen, MagicStick, Search, UploadFilled, View } from '@element-plus/icons-vue'
import {
  deleteDocument,
  fetchDocumentFile,
  listDocuments,
  pasteDocument,
  updateDocument,
  updateDocumentStatus,
  uploadDocument,
  type DocumentMetadata,
  type GeologicalDocument,
  type PastedDocument,
} from '@/api/documents'

const loading = ref(false)
const router = useRouter()
const records = ref<GeologicalDocument[]>([])
const total = ref(0)
const filters = reactive({ query: '', type: '', status: '', region: '', year: undefined as number | undefined, page: 1, size: 10 })

const uploadOpen = ref(false)
const uploadLoading = ref(false)
const selectedFile = ref<File>()
const fileInput = ref<HTMLInputElement>()
const uploadForm = reactive<DocumentMetadata>({ name: '', region: '', year: undefined, keyword: '', summary: '' })
const pasteOpen = ref(false)
const pasteLoading = ref(false)
const pastedForm = reactive<PastedDocument>({ name: '矿区地质调查演示资料', region: '鄂东南', year: 2025, keyword: '铁铜矿,断裂,钻孔', summary: '用于系统演示的可直接解析文本。', content: '' })

const editOpen = ref(false)
const editLoading = ref(false)
const editingId = ref<number>()
const editForm = reactive<DocumentMetadata>({ name: '', region: '', year: undefined, keyword: '', summary: '' })

const previewOpen = ref(false)
const previewUrl = ref('')
const previewDocument = ref<GeologicalDocument>()

const typeLabels: Record<string, string> = { PDF: 'PDF', WORD: 'Word', TXT: '文本', IMAGE: '图片' }
const statusLabels: Record<string, string> = { UPLOADED: '待解析', PARSING: '解析中', PARSED: '已解析', FAILED: '异常', ARCHIVED: '已归档' }
const statusTypes: Record<string, 'info' | 'warning' | 'success' | 'danger'> = { UPLOADED: 'info', PARSING: 'warning', PARSED: 'success', FAILED: 'danger', ARCHIVED: 'info' }
const accept = '.pdf,.docx,.txt,.png,.jpg,.jpeg,.tif,.tiff'

async function loadDocuments() {
  loading.value = true
  try {
    const page = await listDocuments({ ...filters })
    records.value = page.records
    total.value = page.total
  } catch (error) { ElMessage.error(messageOf(error)) }
  finally { loading.value = false }
}

function search() { filters.page = 1; loadDocuments() }
function resetFilters() {
  Object.assign(filters, { query: '', type: '', status: '', region: '', year: undefined, page: 1, size: 10 })
  loadDocuments()
}

function chooseFile() { fileInput.value?.click() }
function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file && file.size > 100 * 1024 * 1024) {
    input.value = ''
    selectedFile.value = undefined
    ElMessage.warning('单个文件不能超过 100 MB')
    return
  }
  selectedFile.value = file
  if (selectedFile.value && !uploadForm.name) uploadForm.name = selectedFile.value.name
}

async function submitUpload() {
  if (!selectedFile.value) return ElMessage.warning('请先选择资料文件')
  uploadLoading.value = true
  try {
    await uploadDocument(selectedFile.value, uploadForm)
    ElMessage.success('资料已上传')
    uploadOpen.value = false
    resetUpload()
    await loadDocuments()
  } catch (error) { ElMessage.error(messageOf(error)) }
  finally { uploadLoading.value = false }
}

function resetUpload() {
  selectedFile.value = undefined
  Object.assign(uploadForm, { name: '', region: '', year: undefined, keyword: '', summary: '' })
  if (fileInput.value) fileInput.value.value = ''
}

function applyPreset() {
  Object.assign(pastedForm, {
    name: '大冶矿区钻孔记录（演示）', region: '大冶矿区', year: 2025,
    keyword: '铜铁矿,钻孔,ZK001,断裂', summary: '含地层、岩性、矿体、品位、厚度、坐标和构造关系的演示文本。',
    content: `大冶矿区钻孔 ZK001 地质记录\n\n钻孔位于大冶矿区铜绿山矿段，孔口坐标为东经114.9384°、北纬30.0840°（WGS84），孔深 286.50 m。\n\n0—38.20 m 为第四系覆盖层；38.20—126.40 m 为下三叠统大冶组灰岩，灰白色，中厚层状，产状 35°∠58°。126.40—188.70 m 见闪长玢岩，侵入大冶组灰岩。\n\n188.70—214.30 m 为铜铁矿体，厚度 25.60 m，主要矿物为黄铜矿、磁铁矿，铜平均品位 1.26%，铁平均品位 38.40%。矿体受北东向 F1 断裂控制，并赋存于矽卡岩化带。\n\nF1 断裂走向北东，倾向南东，倾角 68°，与闪长玢岩接触带共同控制矿化。`
  })
}

async function submitPasted() {
  if (!pastedForm.name.trim() || !pastedForm.content.trim()) return ElMessage.warning('请填写资料名称和文本内容')
  pasteLoading.value = true
  try {
    const document = await pasteDocument(pastedForm)
    ElMessage.success('演示文本已创建，可立即进入智能解析')
    pasteOpen.value = false
    await loadDocuments()
    router.push({ name: 'document-parse', params: { id: document.id } })
  } catch (error) { ElMessage.error(messageOf(error)) }
  finally { pasteLoading.value = false }
}

function openEdit(document: GeologicalDocument) {
  editingId.value = document.id
  Object.assign(editForm, { name: document.name, region: document.region ?? '', year: document.year, keyword: document.keyword ?? '', summary: document.summary ?? '' })
  editOpen.value = true
}

function openParsing(document: GeologicalDocument) {
  router.push({ name: 'document-parse', params: { id: document.id } })
}

async function submitEdit() {
  if (!editingId.value || !editForm.name?.trim()) return ElMessage.warning('资料名称不能为空')
  editLoading.value = true
  try {
    await updateDocument(editingId.value, editForm)
    ElMessage.success('资料信息已更新')
    editOpen.value = false
    await loadDocuments()
  } catch (error) { ElMessage.error(messageOf(error)) }
  finally { editLoading.value = false }
}

async function changeStatus(document: GeologicalDocument, status: GeologicalDocument['status']) {
  const previous = document.status
  document.status = status
  try { await updateDocumentStatus(document.id, status); ElMessage.success('状态已更新') }
  catch (error) { document.status = previous; ElMessage.error(messageOf(error)) }
}

async function remove(document: GeologicalDocument) {
  try {
    await ElMessageBox.confirm(`删除“${document.name}”后，服务器文件也会一并移除。`, '确认删除资料', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await deleteDocument(document.id)
    ElMessage.success('资料已删除')
    if (records.value.length === 1 && filters.page > 1) filters.page -= 1
    await loadDocuments()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(messageOf(error)) }
}

async function preview(document: GeologicalDocument) {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewDocument.value = document
  try {
    const blob = await fetchDocumentFile(document.id)
    if (document.type === 'WORD') {
      if (document.originalName.toLowerCase().endsWith('.doc')) return ElMessage.warning('旧版 .doc 无法在线预览，请转换为 .docx 后重新上传')
      const converted = await mammoth.convertToHtml({ arrayBuffer: await blob.arrayBuffer() })
      const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>body{max-width:900px;margin:32px auto;padding:0 28px;font:16px/1.8 "Microsoft YaHei",sans-serif;color:#233d37}img{max-width:100%}table{border-collapse:collapse;width:100%}td,th{border:1px solid #bbb;padding:6px}</style></head><body>${converted.value}</body></html>`
      previewUrl.value = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=UTF-8' }))
      if (converted.messages.length) ElMessage.info('Word 已在线转换，复杂版式可能与原文件略有差异')
    } else previewUrl.value = URL.createObjectURL(blob)
    previewOpen.value = true
  } catch (error) { ElMessage.error(messageOf(error)) }
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 ** 2).toFixed(1)} MB`
}
function messageOf(error: unknown) { return error instanceof Error ? error.message : '操作失败' }

onMounted(loadDocuments)
onBeforeUnmount(() => { if (previewUrl.value) URL.revokeObjectURL(previewUrl.value) })
</script>

<template>
  <div class="documents-page">
    <header class="page-intro">
      <el-button class="paste-demo-action" @click="pasteOpen = true">粘贴文本演示</el-button>
      <el-dialog v-model="pasteOpen" title="粘贴文本创建演示任务" width="min(760px, 94vw)">
        <p class="paste-help">可直接粘贴地质文本，或装入预设样例后创建 TXT 资料并跳转到解析页面。</p>
        <el-button plain @click="applyPreset">装入“大冶矿区钻孔”预设样例</el-button>
        <el-form label-position="top" class="metadata-form">
          <div class="form-grid"><el-form-item label="资料名称" required><el-input v-model="pastedForm.name" maxlength="255" /></el-form-item><el-form-item label="所属区域"><el-input v-model="pastedForm.region" /></el-form-item><el-form-item label="资料年份"><el-input-number v-model="pastedForm.year" :min="1800" :max="2100" controls-position="right" /></el-form-item><el-form-item label="关键词"><el-input v-model="pastedForm.keyword" /></el-form-item></div>
          <el-form-item label="资料摘要"><el-input v-model="pastedForm.summary" type="textarea" :rows="2" maxlength="5000" show-word-limit /></el-form-item>
          <el-form-item label="地质文本" required><el-input v-model="pastedForm.content" type="textarea" :rows="12" maxlength="500000" show-word-limit placeholder="粘贴需要解析的地质调查、钻孔或矿产资料文本" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="pasteOpen = false">取消</el-button><el-button type="primary" :loading="pasteLoading" @click="submitPasted">创建并开始解析</el-button></template>
      </el-dialog>
      <div><span class="eyebrow">Phase 2 · Geological archive</span><h1>地质资料资源池</h1><p>集中管理报告、区域调查与矿产调查资料，为后续智能解析保留完整来源。</p></div>
      <button class="primary-action" type="button" @click="uploadOpen = true"><el-icon><UploadFilled /></el-icon><span>上传资料</span></button>
    </header>

    <section class="document-filters" aria-label="资料筛选">
      <el-input v-model="filters.query" clearable placeholder="搜索名称、关键词或摘要" :prefix-icon="Search" @keyup.enter="search" />
      <el-select v-model="filters.type" clearable placeholder="文件类型"><el-option label="PDF" value="PDF" /><el-option label="Word" value="WORD" /><el-option label="文本" value="TXT" /><el-option label="图片" value="IMAGE" /></el-select>
      <el-select v-model="filters.status" clearable placeholder="处理状态"><el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" /></el-select>
      <el-input v-model="filters.region" clearable placeholder="所属区域" @keyup.enter="search" />
      <div class="filter-actions"><button type="button" @click="resetFilters">重置</button><button type="button" @click="search">查询资料</button></div>
    </section>

    <section class="document-table-wrap">
      <div class="table-caption"><span>资料清单</span><small>共 {{ total }} 份资料</small></div>
      <el-table v-loading="loading" :data="records" row-key="id" empty-text="暂无资料，点击右上角上传第一份地质资料">
        <el-table-column label="资料名称" min-width="250"><template #default="{ row }"><div class="document-name"><span :class="`type-${row.type.toLowerCase()}`">{{ typeLabels[row.type] }}</span><div><strong>{{ row.name }}</strong><small>{{ row.keyword || '未设置关键词' }}</small></div></div></template></el-table-column>
        <el-table-column prop="region" label="区域" width="110"><template #default="{ row }">{{ row.region || '—' }}</template></el-table-column>
        <el-table-column prop="year" label="年份" width="75"><template #default="{ row }">{{ row.year || '—' }}</template></el-table-column>
        <el-table-column label="大小" width="85"><template #default="{ row }">{{ formatSize(row.fileSize) }}</template></el-table-column>
        <el-table-column label="状态" width="125"><template #default="{ row }"><el-select :model-value="row.status" size="small" @change="(value: GeologicalDocument['status']) => changeStatus(row, value)"><el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value"><el-tag :type="statusTypes[value]" size="small" effect="plain">{{ label }}</el-tag></el-option></el-select></template></el-table-column>
        <el-table-column label="入库日期" width="118"><template #default="{ row }">{{ new Date(row.createTime).toLocaleDateString('zh-CN') }}</template></el-table-column>
        <el-table-column label="操作" width="165"><template #default="{ row }"><div class="row-actions"><el-tooltip content="智能解析"><button type="button" aria-label="智能解析" @click="openParsing(row)"><el-icon><MagicStick /></el-icon></button></el-tooltip><el-tooltip content="预览"><button type="button" aria-label="预览" @click="preview(row)"><el-icon><View /></el-icon></button></el-tooltip><el-tooltip content="编辑"><button type="button" aria-label="编辑" @click="openEdit(row)"><el-icon><EditPen /></el-icon></button></el-tooltip><el-tooltip content="删除"><button class="danger" type="button" aria-label="删除" @click="remove(row)"><el-icon><Delete /></el-icon></button></el-tooltip></div></template></el-table-column>
      </el-table>
      <el-pagination v-if="total > filters.size" v-model:current-page="filters.page" v-model:page-size="filters.size" background layout="prev, pager, next" :total="total" @current-change="loadDocuments" />
    </section>

    <el-dialog v-model="uploadOpen" title="上传地质资料" width="min(620px, 92vw)" @closed="resetUpload">
      <div class="file-picker" role="button" tabindex="0" @click="chooseFile" @keydown.enter="chooseFile"><input ref="fileInput" type="file" :accept="accept" @change="onFileSelected" /><el-icon><UploadFilled /></el-icon><strong>{{ selectedFile?.name || '选择 PDF、DOCX、TXT 或图片' }}</strong><small>{{ selectedFile ? formatSize(selectedFile.size) : '旧版 .doc 请先另存为 .docx；单个文件最大 100 MB' }}</small></div>
      <el-form label-position="top" class="metadata-form"><div class="form-grid"><el-form-item label="资料名称"><el-input v-model="uploadForm.name" placeholder="默认使用文件名" maxlength="255" /></el-form-item><el-form-item label="所属区域"><el-input v-model="uploadForm.region" placeholder="例如：鄂东南" /></el-form-item><el-form-item label="资料年份"><el-input-number v-model="uploadForm.year" :min="1800" :max="2100" controls-position="right" /></el-form-item><el-form-item label="关键词"><el-input v-model="uploadForm.keyword" placeholder="多个关键词用逗号分隔" /></el-form-item></div><el-form-item label="摘要"><el-input v-model="uploadForm.summary" type="textarea" :rows="3" placeholder="简要说明资料内容" maxlength="5000" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="uploadOpen = false">取消</el-button><el-button type="primary" :loading="uploadLoading" @click="submitUpload">上传资料</el-button></template>
    </el-dialog>

    <el-dialog v-model="editOpen" title="编辑资料信息" width="min(620px, 92vw)"><el-form label-position="top" class="metadata-form"><div class="form-grid"><el-form-item label="资料名称" required><el-input v-model="editForm.name" maxlength="255" /></el-form-item><el-form-item label="所属区域"><el-input v-model="editForm.region" /></el-form-item><el-form-item label="资料年份"><el-input-number v-model="editForm.year" :min="1800" :max="2100" controls-position="right" /></el-form-item><el-form-item label="关键词"><el-input v-model="editForm.keyword" /></el-form-item></div><el-form-item label="摘要"><el-input v-model="editForm.summary" type="textarea" :rows="4" maxlength="5000" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="editOpen = false">取消</el-button><el-button type="primary" :loading="editLoading" @click="submitEdit">保存修改</el-button></template></el-dialog>

    <el-dialog v-model="previewOpen" :title="previewDocument?.name" width="min(1100px, 94vw)" destroy-on-close><div class="document-preview"><img v-if="previewDocument?.type === 'IMAGE'" :src="previewUrl" :alt="previewDocument.name" /><iframe v-else :src="previewUrl" :title="previewDocument?.name" sandbox=""></iframe></div></el-dialog>
  </div>
</template>
