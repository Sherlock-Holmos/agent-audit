import client from './client'

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
