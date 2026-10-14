import { useEffect, useState } from 'react'
import { api } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { ErrorBox, Field, ProductImage, Spinner } from '../../components/ui.jsx'
import { useToast } from '../../state/toast.jsx'
import { AdminHeader } from './AdminLayout.jsx'

const BLANK = { name: '', slug: '', imageUrl: '', sortOrder: 0 }

export default function AdminCategories() {
  const toast = useToast()
  const [categories, setCategories] = useState(null)
  const [editing, setEditing] = useState(null) // null = closed, {} without id = new
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  const load = () => api.categories().then(setCategories).catch(setError)
  useEffect(() => {
    load()
  }, [])

  const save = async (e) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    const payload = { ...editing, sortOrder: Number(editing.sortOrder) || 0, slug: editing.slug || null }
    try {
      if (editing.id) await api.admin.updateCategory(editing.id, payload)
      else await api.admin.createCategory(payload)
      toast.success('Đã lưu danh mục')
      setEditing(null)
      load()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  const remove = async (c) => {
    if (!window.confirm(`Xóa danh mục "${c.name}"?`)) return
    try {
      await api.admin.deleteCategory(c.id)
      toast.success('Đã xóa danh mục')
      load()
    } catch (err) {
      toast.error(err.message)
    }
  }

  const fe = error?.fieldErrors ?? {}

  return (
    <>
      <AdminHeader title="Danh mục">
        <button className="btn btn-primary" onClick={() => { setError(null); setEditing({ ...BLANK }) }}>
          <Icon name="plus" size={16} /> Thêm danh mục
        </button>
      </AdminHeader>

      {editing && (
        <form className="card stack" onSubmit={save} noValidate>
          <h3>{editing.id ? `Sửa "${editing.name}"` : 'Danh mục mới'}</h3>
          <ErrorBox error={error && Object.keys(fe).length === 0 ? error : null} />
          <div className="form-grid form-grid-2">
            <Field label="Tên danh mục" error={fe.name}>
              <input value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} maxLength={100} autoFocus />
            </Field>
            <Field label="Đường dẫn (slug)" error={fe.slug} hint="Để trống để tạo tự động">
              <input value={editing.slug ?? ''} onChange={(e) => setEditing({ ...editing, slug: e.target.value })} maxLength={120} />
            </Field>
            <Field label="Ảnh đại diện (URL)" error={fe.imageUrl}>
              <input value={editing.imageUrl ?? ''} onChange={(e) => setEditing({ ...editing, imageUrl: e.target.value })} />
            </Field>
            <Field label="Thứ tự hiển thị" error={fe.sortOrder}>
              <input type="number" min="0" value={editing.sortOrder} onChange={(e) => setEditing({ ...editing, sortOrder: e.target.value })} />
            </Field>
          </div>
          <div className="row-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setEditing(null)}>Hủy</button>
            <button className="btn btn-primary" disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu'}</button>
          </div>
        </form>
      )}

      <div className="card">
        {!categories ? (
          <Spinner />
        ) : (
          <table className="table">
            <thead><tr><th>Danh mục</th><th>Slug</th><th>Thứ tự</th><th>Sản phẩm</th><th /></tr></thead>
            <tbody>
              {categories.map((c) => (
                <tr key={c.id}>
                  <td>
                    <div className="cart-product">
                      <ProductImage src={c.imageUrl} alt={c.name} className="table-img" />
                      <strong>{c.name}</strong>
                    </div>
                  </td>
                  <td className="muted">/{c.slug}</td>
                  <td>{c.sortOrder}</td>
                  <td>{c.productCount}</td>
                  <td className="right nowrap">
                    <button className="icon-btn icon-btn-sm" onClick={() => { setError(null); setEditing({ ...c }) }} title="Sửa">
                      <Icon name="edit" size={16} />
                    </button>
                    <button className="icon-btn icon-btn-sm" onClick={() => remove(c)} title="Xóa">
                      <Icon name="trash" size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
