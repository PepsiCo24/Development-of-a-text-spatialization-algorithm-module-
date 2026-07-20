<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight,Collection,Reading,Refresh } from '@element-plus/icons-vue'
import { listDocuments,type GeologicalDocument } from '@/api/documents'
const router=useRouter(),loading=ref(false),documents=ref<GeologicalDocument[]>([])
const eligible=computed(()=>documents.value.filter(d=>d.entityStatus==='COMPLETED'))
const completed=computed(()=>eligible.value.filter(d=>d.knowledgeStatus==='COMPLETED').length)
const attributes=computed(()=>eligible.value.reduce((sum,d)=>sum+(d.attributeCount||0),0))
const relations=computed(()=>eligible.value.reduce((sum,d)=>sum+(d.relationCount||0),0))
async function load(){loading.value=true;try{documents.value=(await listDocuments({page:1,size:100,status:'PARSED'})).records}catch(e){ElMessage.error(e instanceof Error?e.message:'任务加载失败')}finally{loading.value=false}}
onMounted(load)
</script>
<template><div class="parsing-queue-page"><header class="page-intro"><div><span class="eyebrow">Phase 5 · Structured knowledge</span><h1>属性、关系与术语</h1><p>从已识别实体中抽取可核查的地质属性和关系，并以专业词典统一名称。</p></div><div class="knowledge-header-actions"><button class="secondary-action" type="button" @click="router.push('/dictionary')"><el-icon><Reading/></el-icon>地质词典</button><button class="secondary-action" type="button" @click="load"><el-icon><Refresh/></el-icon>刷新</button></div></header><section class="parsing-overview"><article><small>可处理资料</small><strong>{{eligible.length}}</strong><span>READY</span></article><article><small>已完成</small><strong>{{completed}}</strong><span>COMPLETED</span></article><article><small>属性</small><strong>{{attributes}}</strong><span>ATTRIBUTES</span></article><article><small>关系</small><strong>{{relations}}</strong><span>RELATIONS</span></article></section><section class="entity-document-list" v-loading="loading"><button v-for="item in eligible" :key="item.id" type="button" @click="router.push({name:'document-knowledge',params:{id:item.id}})"><span class="entity-doc-icon"><el-icon><Collection/></el-icon></span><span><strong>{{item.name}}</strong><small>{{item.entityCount||0}} 个实体 · {{item.normalizedCount||0}} 个已标准化</small></span><span class="entity-doc-state" :class="`is-${(item.knowledgeStatus||'PENDING').toLowerCase()}`">{{item.knowledgeStatus==='COMPLETED'?`${item.attributeCount||0} 属性 / ${item.relationCount||0} 关系`:item.knowledgeStatus==='EXTRACTING'?`${item.knowledgeProgress||0}%`:item.knowledgeStatus==='FAILED'?'抽取异常':'等待抽取'}}</span><el-icon><ArrowRight/></el-icon></button><div v-if="!loading&&!eligible.length" class="empty-queue"><strong>暂无可处理资料</strong><p>请先完成文档解析和地质实体识别。</p></div></section></div></template>
