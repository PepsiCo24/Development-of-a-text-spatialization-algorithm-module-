<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const form = reactive({ username: 'admin', password: 'admin123' })
const loading = ref(false)
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const submit = async () => {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    await router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally { loading.value = false }
}
</script>

<template>
  <main class="login-page">
    <section class="login-landscape" aria-label="产品介绍">
      <div class="contour contour-one"></div><div class="contour contour-two"></div>
      <div class="landscape-copy"><span class="chapter">GEOSCIENCE / 01</span><h1>让沉睡在报告里的<br><em>地质位置</em>重新上图</h1><p>从文本、图件与扫描资料中识别地学实体，建立可追溯的空间知识底座。</p></div>
      <div class="coordinate-strip"><span>30°31′ N</span><span>114°23′ E</span><span>EPSG:4490</span></div>
    </section>
    <section class="login-panel">
      <form class="login-form" @submit.prevent="submit">
        <div class="mobile-brand">GeoText</div><span class="eyebrow">欢迎回来</span><h2>进入地质文本工作台</h2><p>使用数据库账号登录，访问令牌将在八小时后自动失效。</p>
        <label>用户名<input v-model="form.username" autocomplete="username" placeholder="请输入用户名" /></label>
        <label>密码<input v-model="form.password" type="password" autocomplete="current-password" placeholder="请输入密码" /></label>
        <button type="submit" :disabled="loading"><span>{{ loading ? '正在进入…' : '进入系统' }}</span><span aria-hidden="true">↗</span></button>
        <small class="demo-note">演示账号：admin / admin123</small>
      </form>
    </section>
  </main>
</template>
