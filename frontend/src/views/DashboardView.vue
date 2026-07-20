<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'

type ServiceState = 'checking' | 'online' | 'offline'
const backend = ref<ServiceState>('checking')
const ai = ref<ServiceState>('checking')
const stateLabel = computed(() => ({ checking: '检测中', online: '正常', offline: '未连接' }))

onMounted(async () => {
  await Promise.allSettled([
    axios.get('/api/health', { timeout: 2500 }).then(() => backend.value = 'online').catch(() => backend.value = 'offline'),
    axios.get('/ai-api/v1/health', { timeout: 2500 }).then(() => ai.value = 'online').catch(() => ai.value = 'offline'),
  ])
})

const stages = [
  ['01', '资料入库', 'Phase 2'], ['02', '文档解析', 'Phase 3'], ['03', '实体识别', 'Phase 4'],
  ['04', '关系抽取', 'Phase 5'], ['05', '空间上图', 'Phase 6'], ['06', '知识问答', 'Phase 7'],
]
</script>

<template>
  <div class="dashboard-page">
    <section class="dashboard-hero"><div><span class="eyebrow">Phase 7 · 知识图谱与智能问答</span><h1>沿着地质关系探索，<br>让每个答案回到证据。</h1><p>Neo4j 关系网络、Qdrant 语义检索与 BGE-M3 向量模型已接入 DeepSeek/Qwen，回答同步给出实体、位置和来源段落。</p></div><div class="hero-seal"><span>系统阶段</span><strong>07</strong><small>GRAPH + RAG</small></div></section>
    <section class="service-row" aria-label="服务状态">
      <article><span class="service-index">A</span><div><small>业务服务 / Spring Boot</small><strong><i :class="backend"></i>{{ stateLabel[backend] }}</strong></div><code>:8080</code></article>
      <article><span class="service-index">B</span><div><small>智能服务 / FastAPI</small><strong><i :class="ai"></i>{{ stateLabel[ai] }}</strong></div><code>:8000</code></article>
      <article><span class="service-index">D</span><div><small>数据底座 / PostgreSQL</small><strong><i class="checking"></i>等待配置</strong></div><code>:5432</code></article>
    </section>
    <section class="workflow-section"><div class="section-heading"><div><span class="eyebrow">处理链</span><h2>资料如何成为空间知识</h2></div><p>每个阶段保留来源页码与原文证据，确保识别结果可定位、可核查。</p></div><div class="workflow-grid"><article v-for="stage in stages" :key="stage[0]"><span>{{ stage[0] }}</span><strong>{{ stage[1] }}</strong><small>{{ stage[2] }}</small></article></div></section>
    <section class="foundation-note"><span>当前成果</span><p>六类图谱节点、四类核心关系、节点展开、最短路径与证据约束问答已形成可追溯的地质知识服务。</p><strong>READY / 07</strong></section>
  </div>
</template>
