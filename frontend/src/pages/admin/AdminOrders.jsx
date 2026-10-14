import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { ErrorBox, Pagination, Spinner, StatusBadge } from '../../components/ui.jsx'
import { formatDateTime, formatPrice, ORDER_STATUS } from '../../lib/format.js'
import { useToast } from '../../state/toast.jsx'
import { OrderItems, OrderTimeline } from '../AccountPages.jsx'
import { OrderTotals } from '../CartPage.jsx'
import { AdminHeader } from './AdminLayout.jsx'

const ACTION_LABELS = {
  CONFIRMED: 'Xác nhận đơn',
  SHIPPING: 'Bắt đầu giao',
  DELIVERED: 'Đã giao xong',
  CANCELLED: 'Hủy đơn',
}

export default function AdminOrders() {
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const status = params.get('status') || ''
  const openId = params.get('open')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [selected, setSelected] = useState(null)
  const [updating, setUpdating] = useState(false)

  useEffect(() => {
    setData(null)
    api.admin.orders({ status, page }).then(setData).catch(setError)
  }, [status, page])

  useEffect(() => {
    if (openId) api.admin.order(openId).then(setSelected).catch((e) => toast.error(e.message))
    else setSelected(null)
  }, [openId, toast])

  const open = (id) => {
    const next = new URLSearchParams(params)
    next.set('open', id)
    setParams(next)
  }
  const close = () => {
    const next = new URLSearchParams(params)
    next.delete('open')
    setParams(next)
  }
  const filter = (s) => {
    setPage(0)
    setParams(s ? { status: s } : {})
  }

  const changeStatus = async (next) => {
    if (next === 'CANCELLED' && !window.confirm('Hủy đơn này? Sản phẩm sẽ được hoàn lại kho.')) return
    setUpdating(true)
    try {
      const updated = await api.admin.updateOrderStatus(selected.id, next)
      setSelected(updated)
      setData((d) => d && { ...d, items: d.items.map((o) => (o.id === updated.id ? updated : o)) })
      toast.success(`Đơn ${updated.code}: ${ORDER_STATUS[next].label}`)
    } catch (err) {
      toast.error(err.message)
    } finally {
      setUpdating(false)
    }
  }

  return (
    <>
      <AdminHeader title="Đơn hàng" />
      <div className="tabs">
        <button className={`tab ${!status ? 'active' : ''}`} onClick={() => filter('')}>Tất cả</button>
        {Object.entries(ORDER_STATUS).map(([key, s]) => (
          <button key={key} className={`tab ${status === key ? 'active' : ''}`} onClick={() => filter(key)}>{s.label}</button>
        ))}
      </div>
      <ErrorBox error={error} />
      <div className="card">
        {!data ? (
          <Spinner />
        ) : data.items.length === 0 ? (
          <p className="muted">Không có đơn hàng nào.</p>
        ) : (
          <table className="table table-hover">
            <thead>
              <tr><th>Mã đơn</th><th>Khách hàng</th><th>Thời gian</th><th>SL</th><th>Trạng thái</th><th className="right">Tổng</th></tr>
            </thead>
            <tbody>
              {data.items.map((o) => (
                <tr key={o.id} onClick={() => open(o.id)} className="clickable">
                  <td className="strong">{o.code}</td>
                  <td>{o.recipientName}<div className="muted small">{o.phone}</div></td>
                  <td className="small">{formatDateTime(o.createdAt)}</td>
                  <td>{o.itemCount}</td>
                  <td><StatusBadge status={o.status} /></td>
                  <td className="right strong">{formatPrice(o.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
      </div>

      {selected && (
        <div className="modal-root" onClick={close}>
          <div className="modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <div>
                <h2>Đơn {selected.code}</h2>
                <span className="muted small">{formatDateTime(selected.createdAt)} · {selected.customerEmail}</span>
              </div>
              <button className="icon-btn" onClick={close} aria-label="Đóng"><Icon name="close" /></button>
            </div>
            <OrderTimeline status={selected.status} />
            <div className="two-col two-col-even">
              <div>
                <h4>Sản phẩm</h4>
                <OrderItems items={selected.items} />
                <OrderTotals subtotal={selected.subtotal} shippingFee={selected.shippingFee} total={selected.total} />
              </div>
              <div>
                <h4>Giao hàng</h4>
                <p className="strong">{selected.recipientName}</p>
                <p>{selected.phone}</p>
                <p>{selected.address}</p>
                {selected.note && <p className="muted small">Ghi chú: {selected.note}</p>}
                <h4>Thanh toán</h4>
                <p>COD – thu tiền khi giao</p>
              </div>
            </div>
            {selected.nextStatuses.length > 0 && (
              <div className="modal-foot">
                {selected.nextStatuses.map((s) => (
                  <button key={s} disabled={updating} onClick={() => changeStatus(s)}
                    className={s === 'CANCELLED' ? 'btn btn-danger-outline' : 'btn btn-primary'}>
                    {ACTION_LABELS[s]}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </>
  )
}
