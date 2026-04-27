import client from './client'

export const fetchRectificationSnapshotApi = () => client.get('/data/rectification/snapshot')

export const createIssueApi = (payload) => client.post('/data/rectification/issues', payload)
export const deleteIssueApi = (issueId) => client.delete(`/data/rectification/issues/${issueId}`)

export const addIssueSupervisionApi = (issueId, payload) => client.post(`/data/rectification/issues/${issueId}/supervisions`, payload)
export const deleteIssueSupervisionApi = (issueId, supervisionId) => client.delete(`/data/rectification/issues/${issueId}/supervisions/${supervisionId}`)

export const createRectificationTaskApi = (issueId, payload) => client.post(`/data/rectification/issues/${issueId}/tasks`, payload)
export const splitIssueTasksApi = (issueId, payload) => client.post(`/data/rectification/issues/${issueId}/split-tasks`, payload)
export const dispatchSubTaskApi = (parentTaskId, payload) => client.post(`/data/rectification/tasks/${parentTaskId}/subtasks`, payload)
export const deleteTaskApi = (taskId) => client.delete(`/data/rectification/tasks/${taskId}`)
export const acceptTaskApi = (taskId) => client.post(`/data/rectification/tasks/${taskId}/accept`)
export const claimTaskApi = (taskId) => client.post(`/data/rectification/tasks/${taskId}/claim`)
export const uploadTaskAttachmentApi = (taskId, file) => {
	const formData = new FormData()
	formData.append('file', file)
	return client.post(`/data/rectification/tasks/${taskId}/attachments`, formData, {
		headers: {
			'Content-Type': 'multipart/form-data'
		}
	})
}
export const downloadTaskAttachmentApi = (taskId, attachmentIndex) =>
	client.get(`/data/rectification/tasks/${taskId}/attachments/${attachmentIndex}`, { responseType: 'blob' })
export const submitTaskExecutionApi = (taskId, payload) => client.post(`/data/rectification/tasks/${taskId}/execution`, payload)
export const reviewTaskApi = (taskId, payload) => client.post(`/data/rectification/tasks/${taskId}/review`, payload)
export const updateTaskDeadlineApi = (taskId, deadline) => client.patch(`/data/rectification/tasks/${taskId}/deadline`, { deadline })

export const addRuleApi = (name) => client.post('/data/rectification/rules', { name })
export const updateRuleApi = (ruleId, enabled) => client.patch(`/data/rectification/rules/${ruleId}`, { enabled })

export const listReminderRulesApi = () => client.get('/data/rectification/reminder-rules')
export const createReminderRuleApi = (payload) => client.post('/data/rectification/reminder-rules', payload)
export const updateReminderRuleApi = (ruleId, payload) => client.patch(`/data/rectification/reminder-rules/${ruleId}`, payload)
export const deleteReminderRuleApi = (ruleId) => client.delete(`/data/rectification/reminder-rules/${ruleId}`)
export const runReminderScanApi = () => client.post('/data/rectification/reminder-rules/scan')

export const createUserApi = (payload) => client.post('/data/rectification/users', payload)
export const deleteUserApi = (userId) => client.delete(`/data/rectification/users/${userId}`)
export const updateUserApi = (userId, payload) => client.patch(`/data/rectification/users/${userId}`, payload)
export const updateUserRoleApi = (userId, role) => client.patch(`/data/rectification/users/${userId}/role`, { role })
export const updateUserStatusApi = (userId, status) => client.patch(`/data/rectification/users/${userId}/status`, { status })
export const bindUserDepartmentApi = (userId, department) =>
	client.patch(`/data/rectification/users/${userId}/department`, { department })
export const listDepartmentsApi = () => client.get('/data/rectification/departments')
export const createDepartmentApi = (name) => client.post('/data/rectification/departments', { name })
export const updateDepartmentApi = (departmentId, name) => client.patch(`/data/rectification/departments/${departmentId}`, { name })
export const deleteDepartmentApi = (departmentId) => client.delete(`/data/rectification/departments/${departmentId}`)
export const listDeletedUsersApi = () => client.get('/data/rectification/users/deleted')
export const restoreUserApi = (userId) => client.post(`/data/rectification/users/${userId}/restore`)

export const submitOrgReportApi = (payload) => client.post('/data/rectification/reports', payload)

export const listNotificationsApi = () => client.get('/data/rectification/notifications')
export const markNotificationReadApi = (notificationId) => client.post(`/data/rectification/notifications/${notificationId}/read`)
export const interactNotificationApi = (notificationId, payload) => client.post(`/data/rectification/notifications/${notificationId}/interact`, payload)

export const listOrgDepartmentsApi = () => client.get('/data/rectification/org-admin/departments')
export const createOrgDepartmentApi = (payload) => client.post('/data/rectification/org-admin/departments', payload)
export const updateOrgDepartmentApi = (departmentId, payload) =>
	client.patch(`/data/rectification/org-admin/departments/${departmentId}`, payload)
export const deleteOrgDepartmentApi = (departmentId) => client.delete(`/data/rectification/org-admin/departments/${departmentId}`)

export const listOrgMembersApi = (department) =>
	client.get('/data/rectification/org-admin/members', { params: department ? { department } : {} })
export const createOrgMemberApi = (payload) => client.post('/data/rectification/org-admin/members', payload)
export const updateOrgMemberApi = (userId, payload) => client.patch(`/data/rectification/org-admin/members/${userId}`, payload)
export const deleteOrgMemberApi = (userId) => client.delete(`/data/rectification/org-admin/members/${userId}`)
