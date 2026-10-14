import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { api } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { ErrorBox, Spinner, StatusBadge } from '../../components/ui.jsx'
import { ADMIN_SECTIONS, formatDateTime, formatPrice } from '../../lib/format.js'
import { useAuth } from '../../state/auth.jsx'
import { AdminHeader } from './AdminLayout.jsx'

/** /quan-tri: the dashboard if allowed, otherwise the first section this staff member may open. */
export function AdminHome() {
  const { can, isAdmin } = useAuth()
  if (can('DASHBOARD')) return <AdminDashboard />
  const first = ADMIN_SECTIONS.find((s) => can(s.id))
  if (first) return <Navigate to={first.path} replace />
  if (isAdmin) return <Navigate to="/quan-tri/phan-quyen" replace />
  return (
    <div className="empty">
      <div className="empty-icon"><Icon name="shield" size={32} /></div>
      <h3>Bạn chưa được cấp quyền mục nào</h3>
      <p className="muted">Hãy liên hệ quản trị viên để được cấp quyền truy cập.</p>
    </div>
  )
}

export default function AdminDashboard() {
  const { can } = useAuth()
  const showOrders = can('ORDERS')
  const [stats, setStats] = useState(null)
  const [recent, setRecent] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    api.admin.stats().then(setStats).catch(setError)
    // Recent orders are part of the Orders section; staff without it only see the numbers.
    if (showOrders) api.admin.orders({ size: 6 }).then((r) => setRecent(r.items)).catch(setError)
  }, [showOrders])

  return (
    <>
      <AdminHeader title="Tổng quan" />
      <ErrorBox error={error} />
      {!stats ? (
        <Spinner />
      ) : (
        <div className="stat-grid">
          <Stat icon="cash" label="Doanh thu (đơn đã giao)" value={formatPrice(stats.deliveredRevenue)} />
          <Stat icon="package" label="Tổng đơn hàng" value={stats.orders} />
          <Stat icon="refresh" label="Đơn chờ xác nhận" value={stats.pendingOrders} highlight={stats.pendingOrders > 0}
            link={showOrders ? '/quan-tri/don-hang?status=PENDING' : undefined} />
          <Stat icon="warehouse" label="Mẫu sắp hết / hết hàng" value={stats.lowStockVariants}
            highlight={stats.lowStockVariants > 0} link={can('INVENTORY') ? '/quan-tri/kho?status=LOW' : undefined} />
          <Stat icon="tag" label="Sản phẩm đang bán" value={stats.activeProducts} />
          <Stat icon="user" label="Tài khoản" value={stats.customers} />
        </div>
      )}

      {showOrders && (
      <section className="card">
        <div className="section-head">
          <h3>Đơn hàng gần đây</h3>
          <Link to="/quan-tri/don-hang" className="link-arrow">Xem tất cả <Icon name="chevronRight" size={16} /></Link>
        </div>
        {!recent ? (
          <Spinner />
        ) : recent.length === 0 ? (
          <p className="muted">Chưa có đơn hàng nào.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Mã đơn</th><th>Khách hàng</th><th>Thời gian</th><th>Trạng thái</th><th className="right">Tổng</th></tr>
            </thead>
            <tbody>
              {recent.map((o) => (
                <tr key={o.id}>
                  <td><Link to={`/quan-tri/don-hang?open=${o.id}`} className="strong">{o.code}</Link></td>
                  <td>{o.recipientName}<div className="muted small">{o.customerEmail}</div></td>
                  <td className="small">{formatDateTime(o.createdAt)}</td>
                  <td><StatusBadge status={o.status} /></td>
                  <td className="right strong">{formatPrice(o.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
      )}
    </>
  )
}

function Stat({ icon, label, value, highlight, link }) {
  const body = (
    <>
      <span className="stat-icon"><Icon name={icon} size={20} /></span>
      <span className="muted small">{label}</span>
      <strong className="stat-value">{value}</strong>
    </>
  )
  return link ? (
    <Link to={link} className={`card stat ${highlight ? 'stat-highlight' : ''}`}>{body}</Link>
  ) : (
    <div className={`card stat ${highlight ? 'stat-highlight' : ''}`}>{body}</div>
  )
}
