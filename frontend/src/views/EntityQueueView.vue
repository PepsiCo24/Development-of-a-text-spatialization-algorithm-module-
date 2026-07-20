<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Connection, Refresh } from '@element-plus/icons-vue'
import { listDocuments, type GeologicalDocument } from '@/api/documents'

const router = useRouter()
const loading = ref(false)
const documents = ref<GeologicalDocument[]>([])
const eligible = computed(() => documents.value.filter(item => item.status === 'PARSED'))
const completed = computed(() => eligible.value.filter(item => item.entityStatus === 'COMPLETED').length)
const extracting = computed(() => eligible.value.filter(item => item.entityStatus === 'EXTRACTING').length)

async function load() {
  loading.value = true
  try { documents.value = (await listDocuments({ page: 1, size: 100, status: 'PARSED' })).records }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '实体任务加载失败') }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <div class="parsing-queue-page">
    <header class="page-intro"><div><span class="eyebrow">Phase 4 · LLM entity recognition</span><h1>地质实体识别</h1><p>通过 DeepSeek 或 Qwen 从结构化原文中识别十四类地质实体，并保留置信度与来源证据。</p></div><button class="secondary-action" type="button" @click="load"><el-icon><Refresh /></el-icon>刷新任务</button></header>
    <section class="parsing-overview entity-overview"><article><small>可识别资料</small><strong>{{ eligible.length }}</strong><span>READY</span></article><article><small>已完成</small><strong>{{ completed }}</strong><span>COMPLETED</span></article><article><small>识别中</small><strong>{{ extracting }}</strong><span>RUNNING</span></article><article><small>待处理</small><strong>{{ eligible.length - completed - extracting }}</strong><span>PENDING</span></article></section>
    <section class="entity-document-list" v-loading="loading">
      <button v-for="item in eligible" :key="item.id" type="button" @click="router.push({ name: 'document-entities', params: { id: item.id } })">
        <span class="entity-doc-icon"><el-icon><Connection /></el-icon></span><span><strong>{{ item.name }}</strong><small>{{ item.region || '未设置区域' }} · {{ item.chunkCount || 0 }} 个文本块</small></span><span class="entity-doc-state" :class="`is-${(item.entityStatus || 'PENDING').toLowerCase()}`">{{ item.entityStatus === 'COMPLETED' ? `${item.entityCount || 0} 个实体` : item.entityStatus === 'EXTRACTING' ? `${item.entityProgress || 0}%` : item.entityStatus === 'FAILED' ? '识别异常' : '等待识别' }}</span><el-icon><ArrowRight /></el-icon>
      </button>
      <div v-if="!loading && !eligible.length" class="empty-queue"><strong>暂无可识别资料</strong><p>请先在智能解析模块完成文档解析。</p></div>
    </section>
  </div>
</template>
