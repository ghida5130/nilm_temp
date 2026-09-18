import { createBrowserRouter, Navigate } from 'react-router-dom'
import ProtectedRoute from '../components/common/ProtectedRoute'
import HomePage from '../pages/HomePage'
import StaffPage from '../pages/StaffPage'
import UserPage from '../pages/UserPage'

export const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  { element: <ProtectedRoute role="staff" />, children: [
    { path: '/staff', element: <StaffPage /> },
    { path: '/staff/subjects/:subjectId', element: <StaffPage /> },
  ] },
  { element: <ProtectedRoute role="user" />, children: [
    { path: '/user', element: <UserPage /> },
    { path: '/user/orange-preview', element: <UserPage comparison /> },
  ] },
  { path: '*', element: <Navigate to="/" replace /> },
])
