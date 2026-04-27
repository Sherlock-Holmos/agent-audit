import { normalizeRole } from '../constants/rbac'

const DEMO_UNIT_FALLBACK = {
  audit_admin_demo: '审计局',
  admin: '审计局',
  auditor_demo: '审计一处',
  org_admin_demo: '城建集团',
  org_operator_demo: '城建集团'
}

function isLikelyGarbledText(value) {
  const text = String(value || '').trim()
  if (!text) return false
  if (text.includes('�')) return true
  if (/^[?？]+$/.test(text)) return true

  const plain = text.replace(/[\s\-_/()（）【】,.，。:：;；]/g, '')
  if (!plain) return false
  const badChars = plain.match(/[?？]/g)?.length || 0
  return plain.length >= 3 && badChars / plain.length >= 0.5
}

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
  const direct = String(user.unit || user.department || '').trim()
  if (direct && !isLikelyGarbledText(direct)) {
    return direct
  }

  const username = String(user.username || '').trim()
  const fallback = DEMO_UNIT_FALLBACK[username]
  return fallback || '默认被审计单位'
}
