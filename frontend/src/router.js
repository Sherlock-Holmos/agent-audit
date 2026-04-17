import { createRouter, createWebHistory } from 'vue-router'
import { ROLES, ROLE_HOME_ROUTE, isRoleAllowed } from './constants/rbac'
import { getCurrentRole } from './utils/currentUser'

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
    redirect: '/workbench/auditor'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('./views/DashboardView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN, ROLES.AUDITOR] }
  },
  {
    path: '/workbench/audit-admin',
    name: 'workbench-audit-admin',
    component: () => import('./views/AuditAdminWorkbenchView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN] }
  },
  {
    path: '/workbench/auditor',
    name: 'workbench-auditor',
    component: () => import('./views/AuditorWorkbenchView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDITOR] }
  },
  {
    path: '/workbench/org-admin',
    name: 'workbench-org-admin',
    component: () => import('./views/AuditedOrgAdminWorkbenchView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.ORG_ADMIN] }
  },
  {
    path: '/workbench/org-operator',
    name: 'workbench-org-operator',
    component: () => import('./views/AuditedOrgOperatorWorkbenchView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.ORG_OPERATOR] }
  },
  {
    path: '/forbidden',
    name: 'forbidden',
    component: () => import('./views/PermissionDeniedView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/datasource',
    name: 'datasource',
    component: () => import('./views/DatasourceView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN, ROLES.AUDITOR] }
  },
  {
    path: '/datasource/clean',
    name: 'datasource-clean',
    component: () => import('./views/DatasourceCleanView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN, ROLES.AUDITOR] }
  },
  {
    path: '/datasource/clean-rules',
    redirect: '/datasource/clean-rules/rules'
  },
  {
    path: '/datasource/clean-rules/rules',
    name: 'datasource-clean-rules-rules',
    component: () => import('./views/DatasourceRuleStrategyView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN] }
  },
  {
    path: '/datasource/clean-rules/synonyms',
    name: 'datasource-clean-rules-synonyms',
    component: () => import('./views/DatasourceFusionKeySynonymView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN] }
  },
  {
    path: '/datasource/clean-rules/nifi-templates',
    name: 'datasource-clean-rules-nifi-templates',
    component: () => import('./views/DatasourceNifiTemplateView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN] }
  },
  {
    path: '/datasource/clean-rules/layer-stats',
    name: 'datasource-clean-rules-layer-stats',
    component: () => import('./views/DatasourceLayerStatsView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN, ROLES.AUDITOR] }
  },
  {
    path: '/datasource/fusion',
    name: 'datasource-fusion',
    component: () => import('./views/DatasourceFusionView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN, ROLES.AUDITOR] }
  },
  {
    path: '/settings',
    name: 'settings',
    component:  () => import('./views/ConfigView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN] }
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
  const currentRole = getCurrentRole()
  const roleHome = ROLE_HOME_ROUTE[currentRole] || '/workbench/auditor'

  if (to.meta.public) {
    if ((to.path === '/login' || to.path === '/register') && token) {
      next(roleHome)
      return
    }
    next()
    return
  }

  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }

  if (to.path === '/' && token) {
    next(roleHome)
    return
  }

  if (to.meta.roles && !isRoleAllowed(currentRole, to.meta.roles)) {
    if (to.path === '/forbidden') {
      next()
      return
    }
    next('/forbidden')
    return
  }

  next()
})

export default router
