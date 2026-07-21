<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const form = reactive({ username: 'admin', displayName: '', password: 'admin123', confirmPassword: '' })
const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const switchMode = () => {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  form.username = ''
  form.displayName = ''
  form.password = ''
  form.confirmPassword = ''
}

const submit = async () => {
  loading.value = true
  try {
    if (mode.value === 'register') {
      if (form.password !== form.confirmPassword) throw new Error('两次输入的密码不一致')
      await auth.register(form.username, form.displayName, form.password)
      ElMessage.success('注册成功，已登录系统')
    } else {
      await auth.login(form.username, form.password)
    }
    await router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败，请稍后重试')
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
        <div class="mobile-brand">基于填图对象智能识别的文本空间化算法模块</div><span class="eyebrow">{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</span><h2>{{ mode === 'login' ? '进入地质文本工作台' : '注册新的工作账号' }}</h2><p>{{ mode === 'login' ? '使用账号登录，访问令牌将在八小时后自动失效。' : '注册后将以普通用户身份进入系统，管理员权限需要由管理员分配。' }}</p>
        <label v-if="mode === 'register'">显示名称<input v-model.trim="form.displayName" autocomplete="name" maxlength="100" placeholder="例如：张三" /></label>
        <label>用户名<input v-model.trim="form.username" autocomplete="username" minlength="3" maxlength="64" pattern="[A-Za-z0-9_-]+" placeholder="3 至 64 位字母、数字或下划线" /></label>
        <label>密码<input v-model="form.password" type="password" autocomplete="current-password" minlength="8" placeholder="至少 8 位" /></label>
        <label v-if="mode === 'register'">确认密码<input v-model="form.confirmPassword" type="password" autocomplete="new-password" minlength="8" placeholder="再次输入密码" /></label>
        <button type="submit" :disabled="loading"><span>{{ loading ? '正在处理…' : mode === 'login' ? '进入系统' : '注册并进入系统' }}</span><span aria-hidden="true">→</span></button>
        <button class="form-switch" type="button" @click="switchMode">{{ mode === 'login' ? '没有账号？立即注册' : '已有账号？返回登录' }}</button>
        <small v-if="mode === 'login'" class="demo-note">演示管理员：admin / admin123</small>
      </form>
    </section>
  </main>
</template>
