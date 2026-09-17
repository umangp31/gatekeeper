import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './components/Auth'
import { ToastProvider } from './components/Toast'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/Login'
import { OverviewPage } from './pages/Overview'
import { UsersPage } from './pages/Users'
import { RolesPage } from './pages/Roles'
import { FlagsPage } from './pages/Flags'
import { PlaygroundPage } from './pages/Playground'
import { EndUserPage } from './pages/EndUser'

function Gate() {
  const { session, loading } = useAuth()
  if (loading) return <div className="grid min-h-screen place-items-center text-sm text-muted-foreground">Restoring session…</div>
  if (!session) return <LoginPage />
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<EndUserPage />} />
        <Route path="overview" element={<OverviewPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="roles" element={<RolesPage />} />
        <Route path="flags" element={<FlagsPage />} />
        <Route path="playground" element={<PlaygroundPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Gate />
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
