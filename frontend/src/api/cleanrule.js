import client from './client'

export const listCleanRules = () => client.get('/data/clean/rules')
export const uploadCleanRule = (payload) => client.post('/data/clean/rules', payload)
export const toggleCleanRule = (ruleId, enabled) =>
  client.patch(`/data/clean/rules/${ruleId}/enabled`, { enabled })
export const deleteCleanRule = (ruleId) => client.delete(`/data/clean/rules/${ruleId}`)

export const listCleanStrategies = () => client.get('/data/clean/strategies')
export const createCleanStrategy = (payload) => client.post('/data/clean/strategies', payload)
export const toggleCleanStrategy = (id, enabled) =>
  client.patch(`/data/clean/strategies/${id}/enabled`, { enabled })
export const deleteCleanStrategy = (id) => client.delete(`/data/clean/strategies/${id}`)
