import { normalizeRole } from '../constants/rbac'

export function getCurrentUser() {
  try {
    const parsed = JSON.parse(localStorage.getItem('user') || '{}')
    if (!parsed || typeof parsed !== 'object') return {}
    return parsed
  } catch {
    return {}
  }
}

export function getCurrentRole() {
  const user = getCurrentUser()
  return normalizeRole(user.role)
}

export function getCurrentUnit() {
  const user = getCurrentUser()
  return String(user.department || '').trim() || '默认被审计单位'
}
