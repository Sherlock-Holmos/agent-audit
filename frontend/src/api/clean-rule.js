import client from './client'

export const listCleanRules = () => client.get('/data/clean/rules')
export const uploadCleanRule = (payload) => client.post('/data/clean/rules', payload)
export const toggleCleanRule = (ruleId, enabled) =>
  client.patch(`/data/clean/rules/${ruleId}/enabled`, { enabled })
export const getCleanRuleDetail = (ruleId) => client.get(`/data/clean/rules/${ruleId}`)
export const updateCleanRule = (ruleId, payload) => client.patch(`/data/clean/rules/${ruleId}`, payload)
export const deleteCleanRule = (ruleId) => client.delete(`/data/clean/rules/${ruleId}`)
