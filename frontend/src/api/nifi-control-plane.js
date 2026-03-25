import client from './client'

export const getNifiStatus = () => client.get('/data/control-plane/nifi/status')
export const triggerNifiFlow = (payload) => client.post('/data/control-plane/nifi/flows/run', payload)
export const listNifiFlowRuns = (params) => client.get('/data/control-plane/nifi/flows', { params })
export const listNifiFlowTemplates = () => client.get('/data/control-plane/nifi/templates')
export const saveNifiFlowTemplate = (payload) => client.post('/data/control-plane/nifi/templates', payload)
