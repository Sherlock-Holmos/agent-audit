import client from './client'

export const listDataSources = (params) => client.get('/data/sources', { params })
export const createDatabaseSource = (payload) => client.post('/data/sources/database', payload)

export const createFileSource = (payload) => {
  const formData = new FormData()
  formData.append('name', payload.name)
  formData.append('remark', payload.remark || '')
  formData.append('file', payload.file)

  return client.post('/data/sources/file', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const updateDataSourceStatus = (id, status) =>
  client.patch(`/data/sources/${id}/status`, { status })

export const deleteDataSource = (id) => client.delete(`/data/sources/${id}`)

export const listDataSourceObjects = (id) => client.get(`/data/sources/${id}/objects`)
