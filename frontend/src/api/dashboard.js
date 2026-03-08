import client from './client'

export const fetchDashboard = () => client.get('/data/dashboard')
export const fetchTrend = () => client.get('/data/trend')
export const fetchHeatmap = () => client.get('/data/heatmap')
