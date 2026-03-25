import client from './client'

export const listCleanRules = () => client.get('/data/clean/rules')
export const uploadCleanRule = (payload) => client.post('/data/clean/rules', payload)
export const toggleCleanRule = (ruleId, enabled) =>
  client.patch(`/data/clean/rules/${ruleId}/enabled`, { enabled })
export const getCleanRuleDetail = (ruleId) => client.get(`/data/clean/rules/${ruleId}`)
export const updateCleanRule = (ruleId, payload) => client.patch(`/data/clean/rules/${ruleId}`, payload)
export const deleteCleanRule = (ruleId) => client.delete(`/data/clean/rules/${ruleId}`)

export const listCleanStrategies = () => client.get('/data/clean/strategies')
export const createCleanStrategy = (payload) => client.post('/data/clean/strategies', payload)
export const getCleanStrategyDetail = (id) => client.get(`/data/clean/strategies/${id}`)
export const updateCleanStrategy = (id, payload) => client.patch(`/data/clean/strategies/${id}`, payload)
export const toggleCleanStrategy = (id, enabled) =>
  client.patch(`/data/clean/strategies/${id}/enabled`, { enabled })
export const deleteCleanStrategy = (id) => client.delete(`/data/clean/strategies/${id}`)

export const listFusionKeySynonyms = () => client.get('/data/fusion/key-synonyms')
export const createFusionKeySynonym = (payload) => client.post('/data/fusion/key-synonyms', payload)
export const getFusionKeySynonymDetail = (id) => client.get(`/data/fusion/key-synonyms/${id}`)
export const updateFusionKeySynonym = (id, payload) => client.patch(`/data/fusion/key-synonyms/${id}`, payload)
export const toggleFusionKeySynonym = (id, enabled) =>
  client.patch(`/data/fusion/key-synonyms/${id}/enabled`, { enabled })
export const deleteFusionKeySynonym = (id) => client.delete(`/data/fusion/key-synonyms/${id}`)
export const listFusionKeySynonymHistory = (id, params) =>
  client.get(`/data/fusion/key-synonyms/${id}/history`, { params })
export const listFusionKeySynonymHistoryByCanonicalKey = (params) =>
  client.get('/data/fusion/key-synonyms/history', { params })

export const getNifiStatus = () => client.get('/data/control-plane/nifi/status')
export const triggerNifiFlow = (payload) => client.post('/data/control-plane/nifi/flows/run', payload)
export const listNifiFlowRuns = (params) => client.get('/data/control-plane/nifi/flows', { params })
export const listNifiFlowTemplates = () => client.get('/data/control-plane/nifi/templates')
export const saveNifiFlowTemplate = (payload) => client.post('/data/control-plane/nifi/templates', payload)
export const listLayerStats = (params) => client.get('/data/control-plane/layers/stats', { params })
