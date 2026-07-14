export interface AuthRequest {
  username: string
  password: string
}

export interface AuthResponse {
  token: string
  username: string
  userId: number
}

export enum DatasetStatus {
  READY = 'READY',
  PENDING_CLEAN = 'PENDING_CLEAN',
  CLEANED = 'CLEANED',
  FAILED = 'FAILED',
}

export interface ColumnInfo {
  name: string
  type: string
  nullable: boolean
}

export interface DatasetResponse {
  id: number
  fileName: string
  fileType: string
  tableName: string
  columns: ColumnInfo[]
  rowCount: number
  createdAt: string
  status: DatasetStatus
}

export interface CleaningIssue {
  type: string
  column: string
  affectedRows: number
  description: string
  suggestion: string
  defaultSql: string
}

export interface CleaningProposal {
  datasetId: number
  tableName: string
  totalRows: number
  issues: CleaningIssue[]
  summary: string
}

export interface CleaningExecutionRequest {
  selectedIssueIndexes: number[]
  customSqls?: string[]
  saveAsNewDataset: boolean
  newDatasetName?: string
}

export interface CleaningHistoryRecord {
  id: number
  datasetId: number
  status: string
  affectedRows: number
  errorMessage?: string
  createdAt: string
}

export interface QueryResult {
  columns: string[]
  rows: (string | number | null)[][]
  totalRows: number
  executionTimeMs: number
}

export interface AnalysisRequest {
  question: string
}

export interface AnalysisResponse {
  sql: string
  result: QueryResult
  answer: string
}

export interface ChatMessage {
  id: string
  role: 'user' | 'ai'
  content: string
  sql?: string
  result?: QueryResult
  proposal?: CleaningProposal
  timestamp: number
}
