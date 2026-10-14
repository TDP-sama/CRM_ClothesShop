import { useEffect, useState } from 'react'
import { api } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { ErrorBox, Pagination, Spinner } from '../../components/ui.jsx'
import { ADMIN_SECTIONS, formatDate, ROLES } from '../../lib/format.js'
import { useToast } from '../../state/toast.jsx'
import { AdminHeader } from './AdminLayout.jsx'

const SECTION_LABEL = Object.fromEntries(ADMIN_SECTIONS.map((s) => [s.id, s.label]))

/** ADMIN only: turn accounts into staff and choose which admin sections each staff member may open. */
export default function AdminUsers() {
  const toast = useToast()
  const [q, setQ] = useState('')
  const [search, setSearch] = useState('')
  const [role, setRole] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [editing, setEditing] = useState(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    setData(null)
    api.admin.users({ q: search, role, page }).then(setData).catch(setError)
  }, [search, role, page])

  const filterRole = (r) => {
    setPage(0)
    setRole(r)
  }

  const save = async () => {
    setSaving(true)
    try {
      const updated = await api.admin.updateAccess(editing.id, editing.role, editing.role === 'STAFF' ? editing.permissions : [])
      setData((d) => d && { ...d, items: d.items.map((u) => (u.id === updated.id ? updated : u)) })
      toast.success(`Đã cập nhật quyền cho ${updated.fullName}`)
      setEditing(null)
    } catch (err) {
      toast.error(err.message)
    } finally {
      setSaving(false)
    }
  }

  const togglePermission = (id) =>
    setEditing((e) => ({
      ...e,
      permissions: e.permissions.includes(id) ? e.permissions.filter((p) => p !== id) : [...e.permissions, id],
    }))

  return (
    <>
      <AdminHeader title="Phân quyền">
        <form onSubmit={(e) => { e.preventDefault(); setPage(0); setSearch(q) }} className="search search-admin">
          <Icon name="search" size={18} />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Tìm theo tên hoặc email..." />
        </form>
      </AdminHeader>
      <p className="muted small">
        Chuyển tài khoản thành <strong>nhân viên</strong> và chọn những mục của trang quản trị họ được dùng. Thay đổi có
        hiệu lực ngay ở lần thao tác tiếp theo của nhân viên. Quản trị viên được cấu hình trong <code>ADMIN_EMAILS</code> nên
        không chỉnh sửa ở đây.
      </p>

      <div className="tabs">
        <button className={`tab ${!role ? 'active' : ''}`} onClick={() => filterRole('')}>Tất cả</button>
        {Object.entries(ROLES).map(([key, r]) => (
          <button key={key} className={`tab ${role === key ? 'active' : ''}`} onClick={() => filterRole(key)}>{r.label}</button>
        ))}
      </div>
      <ErrorBox error={error} />

      <div className="card">
        {!data ? (
          <Spinner />
        ) : data.items.length === 0 ? (
          <p className="muted">Không tìm thấy tài khoản nào.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Tài khoản</th><th>Vai trò</th><th>Quyền truy cập</th><th>Ngày tạo</th><th /></tr>
            </thead>
            <tbody>
              {data.items.map((u) => (
                <tr key={u.id}>
                  <td>
                    <div className="cart-product">
                      <span className="avatar">{u.fullName.charAt(0).toUpperCase()}</span>
                      <div>
                        <strong>{u.fullName}</strong>
                        <div className="muted small">{u.email}</div>
                      </div>
                    </div>
                  </td>
                  <td><span className={`badge badge-${ROLES[u.role].tone}`}>{ROLES[u.role].label}</span></td>
                  <td>
                    {u.role === 'ADMIN' ? (
                      <span className="muted small">Toàn quyền</span>
                    ) : u.role === 'STAFF' ? (
                      u.permissions.length ? (
                        <div className="chip-list">
                          {ADMIN_SECTIONS.filter((s) => u.permissions.includes(s.id)).map((s) => (
                            <span key={s.id} className="chip chip-static">{s.label}</span>
                          ))}
                        </div>
                      ) : (
                        <span className="text-warning small">Chưa được cấp mục nào</span>
                      )
                    ) : (
                      <span className="muted small">—</span>
                    )}
                  </td>
                  <td className="small">{formatDate(u.createdAt)}</td>
                  <td className="right">
                    {u.locked ? (
                      <span className="muted small" title="Cấu hình trong ADMIN_EMAILS"><Icon name="shield" size={16} /></span>
                    ) : (
                      <button className="btn btn-ghost" onClick={() => setEditing({ ...u, permissions: [...u.permissions] })}>
                        <Icon name="edit" size={16} /> Phân quyền
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
      </div>

      {editing && (
        <div className="modal-root" onClick={() => setEditing(null)}>
          <div className="modal modal-sm" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <div>
                <h2>Phân quyền</h2>
                <span className="muted small">{editing.fullName} · {editing.email}</span>
              </div>
              <button className="icon-btn" onClick={() => setEditing(null)} aria-label="Đóng"><Icon name="close" /></button>
            </div>

            <h4>Vai trò</h4>
            <div className="role-options">
              {['CUSTOMER', 'STAFF'].map((r) => (
                <label key={r} className={`payment-option ${editing.role === r ? 'active' : 'inactive'}`}>
                  <input type="radio" name="role" checked={editing.role === r} onChange={() => setEditing({ ...editing, role: r })} />
                  <div>
                    <strong>{ROLES[r].label}</strong>
                    <span className="muted small">
                      {r === 'CUSTOMER' ? 'Chỉ mua sắm, không vào được trang quản trị' : 'Vào trang quản trị với các mục được chọn bên dưới'}
                    </span>
                  </div>
                </label>
              ))}
            </div>

            {editing.role === 'STAFF' && (
              <>
                <div className="section-head section-head-tight">
                  <h4>Mục được phép truy cập</h4>
                  <button type="button" className="link small" onClick={() =>
                    setEditing({ ...editing, permissions: editing.permissions.length === ADMIN_SECTIONS.length ? [] : ADMIN_SECTIONS.map((s) => s.id) })}>
                    {editing.permissions.length === ADMIN_SECTIONS.length ? 'Bỏ chọn tất cả' : 'Chọn tất cả'}
                  </button>
                </div>
                <div className="permission-grid">
                  {ADMIN_SECTIONS.map((s) => (
                    <label key={s.id} className={`permission-option ${editing.permissions.includes(s.id) ? 'active' : ''}`}>
                      <input type="checkbox" checked={editing.permissions.includes(s.id)} onChange={() => togglePermission(s.id)} />
                      <Icon name={s.icon} size={18} />
                      {SECTION_LABEL[s.id]}
                    </label>
                  ))}
                </div>
                {editing.permissions.length === 0 && (
                  <p className="small text-warning">Nhân viên chưa có mục nào sẽ thấy trang quản trị trống.</p>
                )}
              </>
            )}

            <div className="modal-foot">
              <button className="btn btn-ghost" onClick={() => setEditing(null)}>Hủy</button>
              <button className="btn btn-primary" onClick={save} disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu phân quyền'}</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
