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
    redirect: '/auditor/issues/new'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('./views/DashboardFusionView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN, ROLES.AUDITOR, ROLES.ORG_ADMIN] }
  },
  {
    path: '/audit-admin/overview',
    name: 'audit-admin-overview',
    redirect: '/dashboard'
  },
  {
    path: '/audit-admin/focus-issues',
    name: 'audit-admin-focus-issues',
    component: () => import('./views/AuditAdminFocusIssuesView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN] }
  },
  {
    path: '/audit-admin/rules',
    name: 'audit-admin-rules',
    component: () => import('./views/AuditAdminRulesConfigView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN] }
  },
  {
    path: '/audit-admin/user-permissions',
    name: 'audit-admin-user-permissions',
    component: () => import('./views/AuditAdminUserPermissionView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN] }
  },
  {
    path: '/auditor/issues/new',
    name: 'auditor-issue-entry',
    component: () => import('./views/AuditorIssueEntryView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDITOR] }
  },
  {
    path: '/auditor/tasks/assign',
    redirect: '/auditor/issues/new'
  },
  {
    path: '/auditor/tasks/tracking',
    redirect: '/auditor/issues/new'
  },
  {
    path: '/auditor/issues/share',
    redirect: '/auditor/issues/new'
  },
  {
    path: '/auditor/review',
    redirect: '/auditor/issues/new'
  },
  {
    path: '/org-admin/tasks/collaboration',
    name: 'org-admin-task-collaboration',
    component: () => import('./views/OrgAdminTaskCollaborationView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.ORG_ADMIN] }
  },
  {
    path: '/org-admin/report/submit',
    name: 'org-admin-report-submit',
    component: () => import('./views/OrgAdminReportSubmitView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.ORG_ADMIN] }
  },
  {
    path: '/org-admin/departments',
    name: 'org-admin-departments',
    component: () => import('./views/OrgAdminDepartmentManagementView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.ORG_ADMIN] }
  },
  {
    path: '/org-operator/tasks/claim',
    name: 'org-operator-task-claim',
    component: () => import('./views/OrgOperatorTaskClaimView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.ORG_OPERATOR] }
  },
  {
    path: '/org-operator/execution-center',
    redirect: '/org-operator/tasks/claim'
  },
  {
    path: '/messages',
    name: 'message-center',
    component: () => import('./views/MessageCenterView.vue'),
    meta: { requiresAuth: true, roles: [ROLES.AUDIT_ADMIN, ROLES.AUDITOR, ROLES.ORG_ADMIN, ROLES.ORG_OPERATOR] }
  },
  {
    path: '/workbench/audit-admin',
    redirect: '/dashboard'
  },
  {
    path: '/workbench/auditor',
    redirect: '/auditor/issues/new'
  },
  {
    path: '/workbench/org-admin',
    redirect: '/org-admin/tasks/collaboration'
  },
  {
    path: '/workbench/org-operator',
    redirect: '/org-operator/tasks/claim'
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
