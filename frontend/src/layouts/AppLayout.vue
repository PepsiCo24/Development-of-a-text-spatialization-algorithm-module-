<script setup lang="ts">
import { DataAnalysis, Document, Fold, MapLocation, Menu as MenuIcon, Opportunity, Setting } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const collapsed = ref(false)
const router = useRouter()
const auth = useAuthStore()
const signOut = () => { auth.logout(); router.push('/login') }
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand-mark"><span class="brand-strata">G</span><div><strong>GeoText</strong><small>地质文本空间化</small></div></div>
      <nav aria-label="主导航">
        <router-link to="/dashboard"><el-icon><DataAnalysis /></el-icon><span>工作台</span></router-link>
        <router-link to="/documents"><el-icon><Document /></el-icon><span>资料资源池</span><small>Phase 2</small></router-link>
        <a class="is-disabled"><el-icon><Opportunity /></el-icon><span>智能解析</span><small>Phase 3</small></a>
        <a class="is-disabled"><el-icon><MapLocation /></el-icon><span>空间地图</span><small>Phase 6</small></a>
        <a class="is-disabled"><el-icon><Setting /></el-icon><span>系统管理</span><small>Phase 8</small></a>
      </nav>
      <button class="collapse-button" type="button" @click="collapsed = !collapsed" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'">
        <el-icon><component :is="collapsed ? MenuIcon : Fold" /></el-icon><span>收起导航</span>
      </button>
    </aside>
    <main class="main-area">
      <header class="topbar"><div><span class="eyebrow">中国地质大学 · 智能地学</span><strong>文本空间化算法模块</strong></div><div class="operator"><span class="status-dot"></span><span>系统运行中</span><button type="button" @click="signOut">退出</button></div></header>
      <router-view />
    </main>
  </div>
</template>
