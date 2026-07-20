<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Document as DocumentIcon, Refresh } from '@element-plus/icons-vue'
import { listDocuments, type GeologicalDocument } from '@/api/documents'

const router = useRouter()
const loading = ref(false)
const documents = ref<GeologicalDocument[]>([])
const selectedStatus = ref('ALL')
const filters = [
  ['ALL', '全部'], ['UPLOADED', '等待解析'], ['PARSING', '解析中'], ['PARSED', '已完成'], ['FAILED', '异常'],
]
const labels: Record<string, string> = { UPLOADED: '等待解析', PARSING: '解析中', PARSED: '已完成', FAILED: '异常', ARCHIVED: '已归档' }
const visibleDocuments = computed(() => selectedStatus.value === 'ALL' ? documents.value : documents.value.filter(item => item.status === selectedStatus.value))
const parsedCount = computed(() => documents.value.filter(item => item.status === 'PARSED').length)
const pendingCount = computed(() => documents.value.filter(item => ['UPLOADED', 'PARSING'].includes(item.status)).length)
const failedCount = computed(() => documents.value.filter(item => item.status === 'FAILED').length)

async function load() {
  loading.value = true
  try { documents.value = (await listDocuments({ page: 1, size: 100 })).records }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '任务列表加载失败') }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <div class="parsing-queue-page">
    <header class="page-intro"><div><span class="eyebrow">Phase 3 · Parsing pipeline</span><h1>智能文档解析</h1><p>提取正文与扫描文字，识别章节结构，并按页码生成可追溯的文本块。</p></div><button class="secondary-action" type="button" @click="load"><el-icon><Refresh /></el-icon>刷新任务</button></header>
    <section class="parsing-overview"><article><small>资料总数</small><strong>{{ documents.length }}</strong><span>DOCUMENTS</span></article><article><small>已解析</small><strong>{{ parsedCount }}</strong><span>COMPLETED</span></article><article><small>等待 / 进行中</small><strong>{{ pendingCount }}</strong><span>IN QUEUE</span></article><article><small>异常</small><strong>{{ failedCount }}</strong><span>ATTENTION</span></article></section>
    <div class="queue-toolbar"><div><button v-for="filter in filters" :key="filter[0]" type="button" :class="{ active: selectedStatus === filter[0] }" @click="selectedStatus = filter[0]">{{ filter[1] }}</button></div><small>{{ visibleDocuments.length }} 项任务</small></div>
    <section class="parse-task-list" v-loading="loading">
      <article
        v-for="item in visibleDocuments"
        :key="item.id"
        role="link"
        tabindex="0"
        :aria-label="`查看 ${item.name} 的解析详情`"
        @click="router.push({ name: 'document-parse', params: { id: item.id } })"
        @keydown.enter="router.push({ name: 'document-parse', params: { id: item.id } })"
        @keydown.space.prevent="router.push({ name: 'document-parse', params: { id: item.id } })"
      >
        <div class="task-file-icon"><el-icon><DocumentIcon /></el-icon><small>{{ item.type }}</small></div>
        <div class="task-main"><div><strong>{{ item.name }}</strong><el-tag size="small" effect="plain" :type="item.status === 'PARSED' ? 'success' : item.status === 'FAILED' ? 'danger' : item.status === 'PARSING' ? 'warning' : 'info'">{{ labels[item.status] }}</el-tag></div><p>{{ item.region || '未设置区域' }} · {{ item.year || '未设置年份' }} · {{ item.keyword || '未设置关键词' }}</p><el-progress v-if="item.status === 'PARSING'" :percentage="item.parseProgress || 0" :stroke-width="5" /></div>
        <div class="task-result"><span v-if="item.status === 'PARSED'"><strong>{{ item.pageCount || 0 }}</strong> 页 / <strong>{{ item.chunkCount || 0 }}</strong> 块</span><span v-else-if="item.status === 'FAILED'" class="task-error">{{ item.errorMessage || '解析失败' }}</span><span v-else>{{ item.parseProgress || 0 }}%</span><el-icon><ArrowRight /></el-icon></div>
      </article>
      <div v-if="!loading && !visibleDocuments.length" class="empty-queue"><strong>当前筛选下没有任务</strong><p>前往资料资源池上传文件或选择其他状态。</p></div>
    </section>
  </div>
</template>
