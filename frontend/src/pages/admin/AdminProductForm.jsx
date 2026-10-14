import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { ErrorBox, Field, ProductImage, Spinner } from '../../components/ui.jsx'
import { swatchColor } from '../../lib/format.js'
import { useToast } from '../../state/toast.jsx'
import { AdminHeader } from './AdminLayout.jsx'

const EMPTY = {
  name: '',
  slug: '',
  description: '',
  price: '',
  compareAtPrice: '',
  categoryId: '',
  images: [''],
  variants: [],
  active: true,
  featured: false,
}

const splitList = (text) => text.split(',').map((s) => s.trim()).filter(Boolean)

export default function AdminProductForm() {
  const { id } = useParams()
  const isNew = !id
  const navigate = useNavigate()
  const toast = useToast()
  const [form, setForm] = useState(isNew ? EMPTY : null)
  const [categories, setCategories] = useState([])
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [generator, setGenerator] = useState({ sizes: 'S, M, L, XL', colors: '', stock: 10 })

  useEffect(() => {
    api.categories().then((cs) => {
      setCategories(cs)
      if (isNew && cs.length) setForm((f) => (f.categoryId ? f : { ...f, categoryId: cs[0].id }))
    })
    if (!isNew) {
      api.admin
        .product(id)
        .then((p) =>
          setForm({
            ...p,
            price: String(p.price),
            compareAtPrice: p.compareAtPrice ? String(p.compareAtPrice) : '',
            description: p.description ?? '',
            images: p.images.length ? p.images : [''],
          }),
        )
        .catch(setError)
    }
  }, [id, isNew])

  if (!form) return error ? <ErrorBox error={error} /> : <Spinner />

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })
  const setImage = (i, value) => setForm({ ...form, images: form.images.map((x, j) => (j === i ? value : x)) })
  const moveImage = (i, delta) => {
    const images = [...form.images]
    const j = i + delta
    if (j < 0 || j >= images.length) return
    ;[images[i], images[j]] = [images[j], images[i]]
    setForm({ ...form, images })
  }
  const setVariant = (i, field, value) =>
    setForm({ ...form, variants: form.variants.map((v, j) => (j === i ? { ...v, [field]: value } : v)) })

  const generate = () => {
    const sizes = splitList(generator.sizes)
    const colors = splitList(generator.colors)
    if (!sizes.length || !colors.length) {
      toast.error('Nhập ít nhất một size và một màu')
      return
    }
    const existing = new Set(form.variants.map((v) => `${v.size.toLowerCase()}|${v.color.toLowerCase()}`))
    const added = []
    colors.forEach((color) =>
      sizes.forEach((size) => {
        if (!existing.has(`${size.toLowerCase()}|${color.toLowerCase()}`)) added.push({ size, color, stock: Number(generator.stock) || 0 })
      }),
    )
    setForm({ ...form, variants: [...form.variants, ...added] })
    toast.success(`Đã thêm ${added.length} biến thể`)
  }

  const submit = async (e) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    const payload = {
      name: form.name,
      slug: form.slug || null,
      description: form.description,
      price: form.price === '' ? null : Number(form.price),
      compareAtPrice: form.compareAtPrice === '' ? null : Number(form.compareAtPrice),
      categoryId: form.categoryId ? Number(form.categoryId) : null,
      images: form.images.map((s) => s.trim()).filter(Boolean),
      // The server ignores stock for existing variants; for new ones it is the initial stock.
      variants: form.variants.map((v) => ({ id: v.id ?? null, size: v.size, color: v.color, stock: v.id ? null : Number(v.stock) || 0 })),
      active: form.active,
      featured: form.featured,
    }
    try {
      if (isNew) await api.admin.createProduct(payload)
      else await api.admin.updateProduct(id, payload)
      toast.success(isNew ? 'Đã tạo sản phẩm' : 'Đã lưu thay đổi')
      navigate('/quan-tri/san-pham')
    } catch (err) {
      setError(err)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } finally {
      setSaving(false)
    }
  }

  const fe = error?.fieldErrors ?? {}

  return (
    <form onSubmit={submit} noValidate>
      <AdminHeader title={isNew ? 'Thêm sản phẩm' : 'Sửa sản phẩm'}>
        <Link to="/quan-tri/san-pham" className="btn btn-ghost">Hủy</Link>
        <button className="btn btn-primary" disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu sản phẩm'}</button>
      </AdminHeader>
      <ErrorBox error={error} />

      <div className="admin-form">
        <div className="stack">
          <section className="card">
            <h3>Thông tin chung</h3>
            <div className="form-grid">
              <Field label="Tên sản phẩm" error={fe.name}>
                <input value={form.name} onChange={set('name')} required maxLength={200} />
              </Field>
              <Field label="Đường dẫn (slug)" error={fe.slug} hint="Để trống để tạo tự động từ tên">
                <input value={form.slug} onChange={set('slug')} maxLength={220} placeholder="vi-du-ao-thun" />
              </Field>
              <Field label="Mô tả" error={fe.description}>
                <textarea value={form.description} onChange={set('description')} rows={5} maxLength={5000} />
              </Field>
            </div>
          </section>

          <section className="card">
            <h3>Hình ảnh</h3>
            <p className="muted small">Dán đường dẫn ảnh (URL). Ảnh đầu tiên là ảnh chính, ảnh thứ hai hiện khi rê chuột.</p>
            <div className="image-list">
              {form.images.map((url, i) => (
                <div key={i} className="image-row">
                  <ProductImage src={url.trim() || null} alt="" className="image-row-preview" />
                  <input value={url} onChange={(e) => setImage(i, e.target.value)} placeholder="https://..." />
                  <button type="button" className="icon-btn icon-btn-sm" onClick={() => moveImage(i, -1)} disabled={i === 0} aria-label="Lên">
                    <Icon name="chevronLeft" size={16} style={{ transform: 'rotate(90deg)' }} />
                  </button>
                  <button type="button" className="icon-btn icon-btn-sm" onClick={() => setForm({ ...form, images: form.images.filter((_, j) => j !== i) })} aria-label="Xóa ảnh">
                    <Icon name="trash" size={16} />
                  </button>
                </div>
              ))}
            </div>
            {form.images.length < 10 && (
              <button type="button" className="btn btn-ghost" onClick={() => setForm({ ...form, images: [...form.images, ''] })}>
                <Icon name="plus" size={16} /> Thêm ảnh
              </button>
            )}
          </section>

          <section className="card">
            <h3>Biến thể (size / màu / tồn kho)</h3>
            <p className="muted small">
              Tồn kho chỉ nhập khi tạo biến thể mới. Sau đó, nhập thêm hàng hoặc điều chỉnh số lượng tại mục <strong>Kho hàng</strong> để
              mọi thay đổi đều được ghi vào lịch sử.
            </p>
            {fe.variants && <p className="text-danger small">{fe.variants}</p>}
            <div className="generator">
              <Field label="Các size"><input value={generator.sizes} onChange={(e) => setGenerator({ ...generator, sizes: e.target.value })} /></Field>
              <Field label="Các màu"><input value={generator.colors} onChange={(e) => setGenerator({ ...generator, colors: e.target.value })} placeholder="Đen, Trắng" /></Field>
              <Field label="Tồn ban đầu mỗi loại"><input type="number" min="0" value={generator.stock} onChange={(e) => setGenerator({ ...generator, stock: e.target.value })} /></Field>
              <button type="button" className="btn btn-outline" onClick={generate}>Tạo biến thể</button>
            </div>
            {form.variants.length === 0 ? (
              <p className="muted small">Chưa có biến thể nào. Dùng công cụ phía trên hoặc thêm từng dòng.</p>
            ) : (
              <table className="table table-compact">
                <thead><tr><th>Màu</th><th>Size</th><th>SKU</th><th>Tồn kho</th><th /></tr></thead>
                <tbody>
                  {form.variants.map((v, i) => (
                    <tr key={v.id ?? `new-${i}`}>
                      <td>
                        <div className="inline-input">
                          <span className="swatch" style={{ background: swatchColor(v.color) }} />
                          <input value={v.color} onChange={(e) => setVariant(i, 'color', e.target.value)} maxLength={40} />
                        </div>
                      </td>
                      <td><input value={v.size} onChange={(e) => setVariant(i, 'size', e.target.value)} maxLength={20} /></td>
                      <td>{v.sku ? <code>{v.sku}</code> : <span className="muted small">Tự tạo khi lưu</span>}</td>
                      <td>
                        {v.id ? (
                          // Existing variants: stock only changes through the warehouse, so it is shown, not edited.
                          <div className="variant-stock" title="Thay đổi qua Kho hàng: phiếu nhập hoặc điều chỉnh">
                            <strong>{v.stock}</strong>
                            <span className="muted small">· quản lý tại Kho hàng</span>
                          </div>
                        ) : (
                          <input type="number" min="0" value={v.stock} onChange={(e) => setVariant(i, 'stock', e.target.value)}
                            aria-label="Tồn ban đầu" placeholder="Tồn ban đầu" />
                        )}
                      </td>
                      <td className="right">
                        <button type="button" className="icon-btn icon-btn-sm" aria-label="Xóa biến thể"
                          onClick={() => setForm({ ...form, variants: form.variants.filter((_, j) => j !== i) })}>
                          <Icon name="trash" size={16} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <button type="button" className="btn btn-ghost" onClick={() => setForm({ ...form, variants: [...form.variants, { size: '', color: '', stock: 0 }] })}>
              <Icon name="plus" size={16} /> Thêm một dòng
            </button>
          </section>
        </div>

        <div className="stack">
          <section className="card">
            <h3>Trạng thái</h3>
            <label className="checkbox"><input type="checkbox" checked={form.active} onChange={set('active')} /> Hiển thị trên cửa hàng</label>
            <label className="checkbox"><input type="checkbox" checked={form.featured} onChange={set('featured')} /> Sản phẩm nổi bật (trang chủ)</label>
          </section>
          <section className="card">
            <h3>Giá bán</h3>
            <div className="form-grid">
              <Field label="Giá bán (₫)" error={fe.price}>
                <input type="number" min="0" step="1000" value={form.price} onChange={set('price')} required />
              </Field>
              <Field label="Giá gốc (₫)" error={fe.compareAtPrice} hint="Để trống nếu không giảm giá; phải lớn hơn giá bán">
                <input type="number" min="0" step="1000" value={form.compareAtPrice} onChange={set('compareAtPrice')} />
              </Field>
            </div>
          </section>
          <section className="card">
            <h3>Danh mục</h3>
            <Field label="Danh mục" error={fe.categoryId}>
              <select value={form.categoryId} onChange={set('categoryId')}>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
          </section>
        </div>
      </div>
    </form>
  )
}
