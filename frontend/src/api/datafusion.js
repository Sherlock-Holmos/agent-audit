import client from './client'

export const listFusionTasks = (params) => client.get('/data/fusion/tasks', { params })
export const createFusionTask = (payload) => client.post('/data/fusion/tasks', payload)
export const runFusionTask = (id) => client.post(`/data/fusion/tasks/${id}/run`)
export const deleteFusionTask = (id) => client.delete(`/data/fusion/tasks/${id}`)
