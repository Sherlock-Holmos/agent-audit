import client from './client'

const CACHE_TTL_MS = 15000
const responseCache = new Map()
const inflightRequests = new Map()

function buildCacheKey(path, params) {
	if (!params) return path
	return `${path}?${JSON.stringify(params)}`
}

function requestWithDedupeAndCache(path, params, options = {}) {
	const key = buildCacheKey(path, params)
	const forceRefresh = Boolean(options.forceRefresh)
	const now = Date.now()

	if (!forceRefresh) {
		const cached = responseCache.get(key)
		if (cached && now - cached.timestamp < CACHE_TTL_MS) {
			return Promise.resolve(cached.response)
		}

		const pending = inflightRequests.get(key)
		if (pending) {
			return pending
		}
	}

	const reqPromise = client.get(path, { params: params || {} })
		.then((response) => {
			responseCache.set(key, { response, timestamp: Date.now() })
			return response
		})
		.finally(() => {
			inflightRequests.delete(key)
		})

	inflightRequests.set(key, reqPromise)
	return reqPromise
}

export const fetchDashboard = (fusionTaskId, options) => requestWithDedupeAndCache('/data/dashboard', fusionTaskId ? { fusionTaskId } : undefined, options)

export const fetchTrend = (fusionTaskId, options) => requestWithDedupeAndCache('/data/trend', fusionTaskId ? { fusionTaskId } : undefined, options)

export const fetchHeatmap = (fusionTaskId, options) => requestWithDedupeAndCache('/data/heatmap', fusionTaskId ? { fusionTaskId } : undefined, options)

export const fetchFusionOptions = (options) => requestWithDedupeAndCache('/data/dashboard/fusion-options', undefined, options)

export function clearDashboardApiCache() {
	responseCache.clear()
	inflightRequests.clear()
}
