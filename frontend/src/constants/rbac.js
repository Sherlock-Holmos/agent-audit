export const ROLES = {
  AUDIT_ADMIN: 'AUDIT_ADMIN',
  AUDITOR: 'AUDITOR',
  ORG_ADMIN: 'ORG_ADMIN',
  ORG_OPERATOR: 'ORG_OPERATOR'
}

const ROLE_ALIASES = {
  ADMIN: ROLES.AUDIT_ADMIN,
  AUDITEE_ADMIN: ROLES.ORG_ADMIN,
  AUDITEE_OPERATOR: ROLES.ORG_OPERATOR
}

export const ROLE_OPTIONS = [
  { label: '审计管理员', value: ROLES.AUDIT_ADMIN },
  { label: '审计人员', value: ROLES.AUDITOR },
  { label: '被审计单位管理员', value: ROLES.ORG_ADMIN },
  { label: '被审计单位经办人', value: ROLES.ORG_OPERATOR }
]

export const ROLE_HOME_ROUTE = {
  [ROLES.AUDIT_ADMIN]: '/workbench/audit-admin',
  [ROLES.AUDITOR]: '/workbench/auditor',
  [ROLES.ORG_ADMIN]: '/workbench/org-admin',
  [ROLES.ORG_OPERATOR]: '/workbench/org-operator'
}

export function normalizeRole(role) {
  const text = String(role || '').trim().toUpperCase()
  if (!text) return ROLES.AUDITOR
  if (ROLE_ALIASES[text]) return ROLE_ALIASES[text]
  if (Object.values(ROLES).includes(text)) return text
  return ROLES.AUDITOR
}

export function roleLabel(role) {
  const normalized = normalizeRole(role)
  return ROLE_OPTIONS.find((item) => item.value === normalized)?.label || '审计人员'
}

export function isRoleAllowed(role, allowedRoles = []) {
  if (!allowedRoles || !allowedRoles.length) return true
  const normalized = normalizeRole(role)
  return allowedRoles.includes(normalized)
}
