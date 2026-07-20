import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: 'documents', name: 'documents', component: () => import('@/views/DocumentsView.vue') },
        { path: 'documents/:id/parse', name: 'document-parse', component: () => import('@/views/DocumentParseView.vue') },
        { path: 'parsing', name: 'parsing', component: () => import('@/views/ParsingQueueView.vue') },
        { path: 'entities', name: 'entities', component: () => import('@/views/EntityQueueView.vue') },
        { path: 'documents/:id/entities', name: 'document-entities', component: () => import('@/views/EntityRecognitionView.vue') },
        { path: 'knowledge', name: 'knowledge', component: () => import('@/views/KnowledgeQueueView.vue') },
        { path: 'documents/:id/knowledge', name: 'document-knowledge', component: () => import('@/views/KnowledgeExtractionView.vue') },
        { path: 'dictionary', name: 'dictionary', component: () => import('@/views/DictionaryView.vue') },
        { path: 'map', name: 'map', component: () => import('@/views/SpatialMapView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.authenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.name === 'login' && auth.authenticated) return { name: 'dashboard' }
})

export default router
