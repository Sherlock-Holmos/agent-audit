import client from './client'

export const fetchDashboard = (fusionTaskId) => client.get('/data/dashboard', {
	params: fusionTaskId ? { fusionTaskId } : {}
})

export const fetchTrend = (fusionTaskId) => client.get('/data/trend', {
	params: fusionTaskId ? { fusionTaskId } : {}
})

export const fetchHeatmap = (fusionTaskId) => client.get('/data/heatmap', {
	params: fusionTaskId ? { fusionTaskId } : {}
})

export const fetchFusionOptions = () => client.get('/data/dashboard/fusion-options')
