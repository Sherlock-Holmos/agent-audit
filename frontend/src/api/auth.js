import client from './client'

export const loginApi = (payload) => client.post('/auth/login', payload)
export const registerApi = (payload) => client.post('/auth/register', payload)
export const getMyProfileApi = () => client.get('/auth/me')
export const updateMyProfileApi = (payload) => client.put('/auth/me', payload)
export const deactivateMyAccountApi = () => client.delete('/auth/me')
