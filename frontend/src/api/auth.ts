import client from './client'
import { AuthRequest, AuthResponse } from '../types'

export const authApi = {
  register: (data: AuthRequest) =>
    client.post<AuthResponse>('/auth/register', data).then((r) => r.data),

  login: (data: AuthRequest) =>
    client.post<AuthResponse>('/auth/login', data).then((r) => r.data),
}
