import { useEffect, useState } from 'react'
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

  useEffect(() => {
    setData(null)
    api.admin.products({ q: search, page }).then(setData).catch(setError)
  }, [search, page, reload])

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
        <Link to="/quan-tri/san-pham/moi" className="btn btn-primary"><Icon name="plus" size={16} /> Thêm sản phẩm</Link>
      </AdminHeader>
      <ErrorBox error={error} />
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
