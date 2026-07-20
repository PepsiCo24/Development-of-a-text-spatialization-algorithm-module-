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
    <section class="dashboard-hero"><div><span class="eyebrow">Phase 8 · v1.0 科研交付版</span><h1>从地质原文出发，<br>交付可核查的空间知识。</h1><p>文档、LLM、GIS、知识图谱、证据问答、成果导出与系统治理现已贯通，面向科研展示、专家评审和部署演示。</p></div><div class="hero-seal"><span>系统版本</span><strong>1.0</strong><small>RELEASE</small></div></section>
    <section class="service-row" aria-label="服务状态">
      <article><span class="service-index">A</span><div><small>业务服务 / Spring Boot</small><strong><i :class="backend"></i>{{ stateLabel[backend] }}</strong></div><code>:8080</code></article>
      <article><span class="service-index">B</span><div><small>智能服务 / FastAPI</small><strong><i :class="ai"></i>{{ stateLabel[ai] }}</strong></div><code>:8000</code></article>
      <article><span class="service-index">D</span><div><small>数据底座 / PostgreSQL</small><strong><i class="checking"></i>等待配置</strong></div><code>:5432</code></article>
    </section>
    <section class="workflow-section"><div class="section-heading"><div><span class="eyebrow">处理链</span><h2>资料如何成为空间知识</h2></div><p>每个阶段保留来源页码与原文证据，确保识别结果可定位、可核查。</p></div><div class="workflow-grid"><article v-for="stage in stages" :key="stage[0]"><span>{{ stage[0] }}</span><strong>{{ stage[1] }}</strong><small>{{ stage[2] }}</small></article></div></section>
    <section class="foundation-note"><span>最终成果</span><p>八阶段完整流程、四类成果导出、用户/任务/模型/日志管理、三份 Demo 数据及部署与测试文档均已交付。</p><strong>RELEASE / 1.0</strong></section>
  </div>
</template>
