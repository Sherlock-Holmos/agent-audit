import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  timeout: 10000
})


client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  response => response,
  error => {
    const status = error.response?.status
    const requestUrl = error.config?.url || ''
    const isProfileMeApi = requestUrl.includes('/auth/me')

    if (status === 401 || (status === 404 && isProfileMeApi)) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default client
