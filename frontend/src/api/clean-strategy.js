import client from './client'

export const listCleanStrategies = () => client.get('/data/clean/strategies')
export const createCleanStrategy = (payload) => client.post('/data/clean/strategies', payload)
export const getCleanStrategyDetail = (id) => client.get(`/data/clean/strategies/${id}`)
export const updateCleanStrategy = (id, payload) => client.patch(`/data/clean/strategies/${id}`, payload)
export const toggleCleanStrategy = (id, enabled) =>
  client.patch(`/data/clean/strategies/${id}/enabled`, { enabled })
export const deleteCleanStrategy = (id) => client.delete(`/data/clean/strategies/${id}`)
