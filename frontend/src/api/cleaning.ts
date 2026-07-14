import { DatasetResponse, CleaningExecutionRequest, CleaningHistoryRecord, CleaningProposal } from '../types'
import client from './client'

export const cleaningApi = {
  analyze: (datasetId: number) =>
    client.post<CleaningProposal>(`/datasets/${datasetId}/cleaning/analyze`),

  execute: (datasetId: number, request: CleaningExecutionRequest) =>
    client.post<CleaningHistoryRecord>(`/datasets/${datasetId}/cleaning/execute`, request),

  saveAs: (datasetId: number, request: CleaningExecutionRequest) =>
    client.post<DatasetResponse>(`/datasets/${datasetId}/cleaning/save-as`, request),

  history: (datasetId: number) =>
    client.get<CleaningHistoryRecord[]>(`/datasets/${datasetId}/cleaning/history`),
}