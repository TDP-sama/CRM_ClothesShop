import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { ErrorBox, Pagination, Price, ProductImage, Spinner } from '../../components/ui.jsx'
import { useToast } from '../../state/toast.jsx'
import { AdminHeader } from './AdminLayout.jsx'

export default function AdminProducts() {
  const toast = useToast()
  const [q, setQ] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [reload, setReload] = useState(0)
  const [importing, setImporting] = useState(false)
  const [importErrors, setImportErrors] = useState(null)
  const fileInput = useRef(null)

  useEffect(() => {
    setData(null)
    api.admin.products({ q: search, page }).then(setData).catch(setError)
  }, [search, page, reload])

  const importFile = async (e) => {
    const file = e.target.files?.[0]
    e.target.value = '' // allow choosing the same file again after fixing it
    if (!file) return
    setImporting(true)
    setImportErrors(null)
    try {
      const result = await api.admin.importProducts(file)
      toast.success(`Đã nhập ${result.rows} dòng: ${result.created} sản phẩm mới, ${result.updated} sản phẩm cập nhật`)
      setReload((r) => r + 1)
    } catch (err) {
      setImportErrors({ message: err.message, rows: err.rowErrors ?? [] })
    } finally {
      setImporting(false)
    }
  }

  const hide = async (p) => {
    if (!window.confirm(`Ẩn "${p.name}" khỏi cửa hàng? Đơn hàng cũ vẫn được giữ nguyên.`)) return
    try {
      await api.admin.hideProduct(p.id)
      toast.success('Đã ẩn sản phẩm')
      setReload((r) => r + 1)
    } catch (err) {
      toast.error(err.message)
    }
  }

  return (
    <>
      <AdminHeader title="Sản phẩm">
        <form onSubmit={(e) => { e.preventDefault(); setPage(0); setSearch(q) }} className="search search-admin">
          <Icon name="search" size={18} />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Tìm theo tên..." />
        </form>
        {/* A plain link: the browser downloads the file with the session cookie. */}
        <a href={api.admin.exportProductsUrl} className="btn btn-ghost" download>
          <Icon name="arrowDown" size={16} /> Xuất Excel
        </a>
        <button className="btn btn-ghost" onClick={() => fileInput.current?.click()} disabled={importing}>
          <Icon name="arrowUp" size={16} /> {importing ? 'Đang nhập...' : 'Nhập Excel'}
        </button>
        <input ref={fileInput} type="file" accept=".xlsx" hidden onChange={importFile} />
        <Link to="/quan-tri/san-pham/moi" className="btn btn-primary"><Icon name="plus" size={16} /> Thêm sản phẩm</Link>
      </AdminHeader>
      <p className="muted small">
        Mẹo: bấm <strong>Xuất Excel</strong>, sửa file rồi <strong>Nhập Excel</strong> để cập nhật nhiều sản phẩm cùng lúc.
        Xem sheet "Huong dan" trong file để biết cách điền.
      </p>
      <ErrorBox error={error} />
      {importErrors && (
        <div className="alert alert-danger import-errors">
          <div className="import-errors-head">
            <strong>{importErrors.message}. Chưa có thay đổi nào được lưu, hãy sửa file rồi nhập lại.</strong>
            <button className="icon-btn icon-btn-sm" onClick={() => setImportErrors(null)} aria-label="Đóng">
              <Icon name="close" size={16} />
            </button>
          </div>
          {importErrors.rows.length > 0 && (
            <table className="table table-compact">
              <thead><tr><th>Dòng</th><th>Lỗi</th></tr></thead>
              <tbody>
                {importErrors.rows.map((e, i) => (
                  <tr key={i}><td className="nowrap">{e.row > 0 ? e.row : '—'}</td><td>{e.message}</td></tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
      <div className="card">
        {!data ? (
          <Spinner />
        ) : data.items.length === 0 ? (
          <p className="muted">Không có sản phẩm nào.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Sản phẩm</th><th>Danh mục</th><th>Giá</th><th>Tồn kho</th><th>Trạng thái</th><th /></tr>
            </thead>
            <tbody>
              {data.items.map((p) => (
                <tr key={p.id} className={p.active ? '' : 'row-muted'}>
                  <td>
                    <div className="cart-product">
                      <ProductImage src={p.imageUrl} alt={p.name} className="table-img" />
                      <div>
                        <Link to={`/quan-tri/san-pham/${p.id}`} className="strong">{p.name}</Link>
                        {p.featured && <span className="badge badge-info badge-inline">Nổi bật</span>}
                        <div className="muted small">/{p.slug}</div>
                      </div>
                    </div>
                  </td>
                  <td>{p.categoryName}</td>
                  <td><Price price={p.price} compareAtPrice={p.compareAtPrice} size="sm" /></td>
                  <td className={p.totalStock === 0 ? 'text-danger strong' : ''}>{p.totalStock}</td>
                  <td>{p.active ? <span className="badge badge-success">Đang bán</span> : <span className="badge badge-muted">Đã ẩn</span>}</td>
                  <td className="right nowrap">
                    <Link to={`/quan-tri/san-pham/${p.id}`} className="icon-btn icon-btn-sm" title="Sửa"><Icon name="edit" size={16} /></Link>
                    {p.active && (
                      <button className="icon-btn icon-btn-sm" onClick={() => hide(p)} title="Ẩn khỏi cửa hàng">
                        <Icon name="eyeOff" size={16} />
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
    </>
  )
}
