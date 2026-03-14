import { createRouter, createWebHistory } from 'vue-router'

export const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('./views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('./views/RegisterView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    name: 'dashboard',
    component: () => import('./views/DashboardView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/datasource',
    name: 'datasource',
    component: () => import('./views/DatasourceView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/datasource/clean',
    name: 'datasource-clean',
    component: () => import('./views/DatasourceCleanView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/datasource/clean-rules',
    name: 'datasource-clean-rules',
    component: () => import('./views/DatasourceCleanRuleView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/datasource/fusion',
    name: 'datasource-fusion',
    component: () => import('./views/DatasourceFusionView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/settings',
    name: 'settings',
    component:  () => import('./views/ConfigView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/config',
    redirect: '/settings'
  },
  {
    path: '/help',
    name: 'help',
    component: () => import('./views/HelpView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/ai',
    name: 'ai',
    component:  () => import('./views/AIView.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')

  if (to.meta.public) {
    if ((to.path === '/login' || to.path === '/register') && token) {
      next('/')
      return
    }
    next()
    return
  }

  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }

  next()
})

export default router
