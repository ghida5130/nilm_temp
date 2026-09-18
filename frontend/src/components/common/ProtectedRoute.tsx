import { Outlet } from 'react-router-dom'
import { useSession } from '../../hooks/useSession'
import LoginPage from '../../pages/LoginPage'

export default function ProtectedRoute({ role }: { role: 'staff' | 'user' }) {
  return useSession() ? <Outlet /> : <LoginPage role={role} />
}
