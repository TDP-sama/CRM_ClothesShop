import { useEffect } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import Icon from '../../components/Icon.jsx'
import { ADMIN_SECTIONS, ROLES, STORE_NAME } from '../../lib/format.js'
import { useAuth } from '../../state/auth.jsx'

export default function AdminLayout() {
  const { user, isAdmin, can, refresh } = useAuth()

  // Permissions may have changed since login; the server already enforces the new ones.
  useEffect(() => {
    refresh()
  }, [refresh])

  const sections = ADMIN_SECTIONS.filter((s) => can(s.id))

  return (
    <div className="admin">
      <aside className="admin-sidebar">
        <Link to="/quan-tri" className="logo">{STORE_NAME} <span className="admin-tag">{isAdmin ? 'Admin' : 'Staff'}</span></Link>
        <nav>
          {sections.map((s) => (
            <NavLink key={s.id} to={s.path} end={s.path === '/quan-tri'} className="admin-link">
              <Icon name={s.icon} size={18} /> {s.label}
            </NavLink>
          ))}
          {isAdmin && (
            <NavLink to="/quan-tri/phan-quyen" className="admin-link">
              <Icon name="shield" size={18} /> Phân quyền
            </NavLink>
          )}
        </nav>
        <div className="admin-sidebar-foot">
          <Link to="/" className="admin-link"><Icon name="arrowLeft" size={18} /> Về cửa hàng</Link>
          <div className="muted small">{user?.email}</div>
          <div className="muted small">{ROLES[user?.role]?.label}</div>
        </div>
      </aside>
      <main className="admin-main">
        <Outlet />
      </main>
    </div>
  )
}

export function AdminHeader({ title, children }) {
  return (
    <div className="admin-header">
      <h1>{title}</h1>
      <div className="admin-header-actions">{children}</div>
    </div>
  )
}
