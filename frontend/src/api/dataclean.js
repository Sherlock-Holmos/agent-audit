import client from './client'

export const listCleanTasks = (params) => client.get('/data/clean/tasks', { params })
export const createCleanTask = (payload) => client.post('/data/clean/tasks', payload)
export const runCleanTask = (id) => client.post(`/data/clean/tasks/${id}/run`)
export const deleteCleanTask = (id) => client.delete(`/data/clean/tasks/${id}`)
