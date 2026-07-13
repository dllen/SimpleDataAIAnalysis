import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import Login from './pages/Login'
import AnalysisWorkspace from './pages/AnalysisWorkspace'

const App = () => {
  const token = useAuthStore((s) => s.token)

  return (
    <Routes>
      <Route path="/login" element={token ? <Navigate to="/workspace" /> : <Login />} />
      <Route path="/workspace" element={token ? <AnalysisWorkspace /> : <Navigate to="/login" />} />
      <Route path="*" element={<Navigate to={token ? "/workspace" : "/login"} />} />
    </Routes>
  )
}

export default App
