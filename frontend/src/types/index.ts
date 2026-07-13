export interface AuthRequest {
  username: string
  password: string
}

export interface AuthResponse {
  token: string
  username: string
  userId: number
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
  timestamp: number
}
