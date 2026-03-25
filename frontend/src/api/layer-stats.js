import client from './client'

export const listLayerStats = (params) => client.get('/data/control-plane/layers/stats', { params })
