import client from './client'
import { DatasetResponse, QueryResult } from '../types'

export const datasetApi = {
  upload: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return client.post<DatasetResponse>('/datasets/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then((r) => r.data)
  },

  list: () =>
    client.get<DatasetResponse[]>('/datasets').then((r) => r.data),

  get: (id: number) =>
    client.get<DatasetResponse>(`/datasets/${id}`).then((r) => r.data),

  preview: (id: number) =>
    client.get<QueryResult>(`/datasets/${id}/preview`).then((r) => r.data),

  delete: (id: number) =>
    client.delete(`/datasets/${id}`).then((r) => r.data),
}
