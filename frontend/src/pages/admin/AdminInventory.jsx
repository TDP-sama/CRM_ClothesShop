import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { ErrorBox, Field, Pagination, ProductImage, Spinner } from '../../components/ui.jsx'
import { ADJUSTMENT_REASONS, formatDateTime, MOVEMENT_TYPES, STOCK_STATUS } from '../../lib/format.js'
import { useToast } from '../../state/toast.jsx'
import { AdminHeader } from './AdminLayout.jsx'

const TABS = [
  { id: 'ton-kho', label: 'Tồn kho', icon: 'warehouse' },
  { id: 'nhap-kho', label: 'Nhập kho', icon: 'plus' },
  { id: 'lich-su', label: 'Lịch sử xuất nhập', icon: 'history' },
]

/** Warehouse: stock levels, goods receipts and the stock ledger. Filters live in the URL. */
export default function AdminInventory() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') || 'ton-kho'
  const setTab = (id, extra = {}) => setParams({ tab: id, ...extra })

  return (
    <>
      <AdminHeader title="Kho hàng" />
      <div className="tabs">
        {TABS.map((t) => (
          <button key={t.id} className={`tab ${tab === t.id ? 'active' : ''}`} onClick={() => setTab(t.id)}>
            <Icon name={t.icon} size={16} /> {t.label}
          </button>
        ))}
      </div>
      {tab === 'ton-kho' && <StockTab params={params} setParams={setParams} openHistory={(item) =>
        setTab('lich-su', { variant: item.variantId, label: `${item.productName} · ${item.color} / ${item.size}` })} />}
      {tab === 'nhap-kho' && <ReceiptsTab />}
      {tab === 'lich-su' && <MovementsTab params={params} setParams={setParams} />}
    </>
  )
}

// ---------------------------------------------------------------- Stock levels

function StockTab({ params, setParams, openHistory }) {
  const status = params.get('status') || ''
  const category = params.get('category') || ''
  const page = Number(params.get('page') || 0)
  const [q, setQ] = useState(params.get('q') || '')
  const [summary, setSummary] = useState(null)
  const [categories, setCategories] = useState([])
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [adjusting, setAdjusting] = useState(null)
  const [settings, setSettings] = useState(null)
  const [reload, setReload] = useState(0)

  const update = (changes) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === '' || v === null ? next.delete(k) : next.set(k, v)))
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  useEffect(() => {
    api.categories().then(setCategories).catch(() => {})
  }, [])

  useEffect(() => {
    setData(null)
    api.admin.inventory.summary().then(setSummary).catch(setError)
    api.admin.inventory
      .items({ q: params.get('q') || '', status, category, page })
      .then(setData)
      .catch(setError)
  }, [params, status, category, page, reload])

  const refresh = () => setReload((r) => r + 1)

  return (
    <>
      {summary && (
        <div className="stat-grid">
          <SummaryCard icon="warehouse" label="Tổng số lượng tồn" value={summary.totalUnits.toLocaleString('vi-VN')}
            active={!status} onClick={() => update({ status: '' })} />
          <SummaryCard icon="tag" label="Mẫu đang bán" value={summary.variants} />
          <SummaryCard icon="refresh" label="Sắp hết hàng" value={summary.lowStock} tone="warning"
            active={status === 'LOW'} onClick={() => update({ status: 'LOW' })} />
          <SummaryCard icon="close" label="Hết hàng" value={summary.outOfStock} tone="danger"
            active={status === 'OUT'} onClick={() => update({ status: 'OUT' })} />
        </div>
      )}

      <div className="toolbar">
        <form onSubmit={(e) => { e.preventDefault(); update({ q: q.trim() }) }} className="search search-admin">
          <Icon name="search" size={18} />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Tìm theo tên sản phẩm hoặc SKU..." />
        </form>
        <select value={category} onChange={(e) => update({ category: e.target.value })} className="toolbar-select">
          <option value="">Tất cả danh mục</option>
          {categories.map((c) => <option key={c.id} value={c.slug}>{c.name}</option>)}
        </select>
        <select value={status} onChange={(e) => update({ status: e.target.value })} className="toolbar-select">
          <option value="">Mọi trạng thái</option>
          {Object.entries(STOCK_STATUS).map(([k, s]) => <option key={k} value={k}>{s.label}</option>)}
        </select>
      </div>

      <ErrorBox error={error} />
      <div className="card">
        {!data ? (
          <Spinner />
        ) : data.items.length === 0 ? (
          <p className="muted">Không có mặt hàng nào phù hợp.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Sản phẩm</th><th>SKU</th><th>Màu / Size</th><th className="right">Tồn kho</th><th>Ngưỡng</th><th>Trạng thái</th><th /></tr>
            </thead>
            <tbody>
              {data.items.map((item) => (
                <tr key={item.variantId} className={item.productActive ? '' : 'row-muted'}>
                  <td>
                    <div className="cart-product">
                      <ProductImage src={item.imageUrl} alt={item.productName} className="table-img" />
                      <div>
                        <strong>{item.productName}</strong>
                        <div className="muted small">{item.categoryName}{!item.productActive && ' · Đã ẩn'}</div>
                      </div>
                    </div>
                  </td>
                  <td><code>{item.sku ?? '—'}</code></td>
                  <td>{item.color} / {item.size}</td>
                  <td className={`right stock-number stock-${item.status}`}>{item.stock}</td>
                  <td className="muted">{item.lowStockThreshold}</td>
                  <td><span className={`badge badge-${STOCK_STATUS[item.status].tone}`}>{STOCK_STATUS[item.status].label}</span></td>
                  <td className="right nowrap">
                    <button className="btn btn-ghost btn-sm" onClick={() => setAdjusting(item)}>Điều chỉnh</button>
                    <button className="icon-btn icon-btn-sm" onClick={() => openHistory(item)} title="Lịch sử xuất nhập">
                      <Icon name="history" size={16} />
                    </button>
                    <button className="icon-btn icon-btn-sm" onClick={() => setSettings(item)} title="SKU và ngưỡng cảnh báo">
                      <Icon name="edit" size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => update({ page: String(p) })} />}
      </div>

      {adjusting && <AdjustModal item={adjusting} onClose={() => setAdjusting(null)} onDone={() => { setAdjusting(null); refresh() }} />}
      {settings && <SettingsModal item={settings} onClose={() => setSettings(null)} onDone={() => { setSettings(null); refresh() }} />}
    </>
  )
}

function SummaryCard({ icon, label, value, tone, active, onClick }) {
  const Tag = onClick ? 'button' : 'div'
  return (
    <Tag className={`card stat ${tone ? `stat-${tone}` : ''} ${active ? 'stat-active' : ''}`} onClick={onClick}>
      <span className="stat-icon"><Icon name={icon} size={20} /></span>
      <span className="muted small">{label}</span>
      <strong className="stat-value">{value}</strong>
    </Tag>
  )
}

function Modal({ title, subtitle, onClose, children, footer }) {
  return (
    <div className="modal-root" onClick={onClose}>
      <div className="modal modal-sm" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <h2>{title}</h2>
            {subtitle && <span className="muted small">{subtitle}</span>}
          </div>
          <button className="icon-btn" onClick={onClose} aria-label="Đóng"><Icon name="close" /></button>
        </div>
        {children}
        <div className="modal-foot">{footer}</div>
      </div>
    </div>
  )
}

function AdjustModal({ item, onClose, onDone }) {
  const toast = useToast()
  const [actual, setActual] = useState(String(item.stock))
  const [reason, setReason] = useState('COUNT')
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const diff = actual === '' ? 0 : Number(actual) - item.stock

  const save = async () => {
    setSaving(true)
    setError(null)
    try {
      await api.admin.inventory.adjust({ variantId: item.variantId, actualQuantity: Number(actual), reason, note })
      toast.success(`Đã điều chỉnh tồn kho: ${diff > 0 ? '+' : ''}${diff}`)
      onDone()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title="Điều chỉnh tồn kho" subtitle={`${item.productName} · ${item.color} / ${item.size} · ${item.sku ?? ''}`}
      onClose={onClose}
      footer={<>
        <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
        <button className="btn btn-primary" onClick={save} disabled={saving || diff === 0 || actual === ''}>
          {saving ? 'Đang lưu...' : 'Lưu điều chỉnh'}
        </button>
      </>}>
      <ErrorBox error={error} />
      <div className="form-grid">
        <div className="adjust-row">
          <div><span className="muted small">Tồn hiện tại</span><strong className="stat-value">{item.stock}</strong></div>
          <Icon name="chevronRight" />
          <Field label="Số lượng thực tế" error={error?.fieldErrors?.actualQuantity}>
            <input type="number" min="0" value={actual} onChange={(e) => setActual(e.target.value)} autoFocus />
          </Field>
          <div>
            <span className="muted small">Chênh lệch</span>
            <strong className={`stat-value ${diff > 0 ? 'text-success' : diff < 0 ? 'text-danger' : ''}`}>{diff > 0 ? `+${diff}` : diff}</strong>
          </div>
        </div>
        <Field label="Lý do">
          <select value={reason} onChange={(e) => setReason(e.target.value)}>
            {Object.entries(ADJUSTMENT_REASONS).map(([k, label]) => <option key={k} value={k}>{label}</option>)}
          </select>
        </Field>
        <Field label="Ghi chú" error={error?.fieldErrors?.note}>
          <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} maxLength={500}
            placeholder="Ví dụ: kiểm kê cuối tháng, 2 áo bị rách đường may" />
        </Field>
      </div>
    </Modal>
  )
}

function SettingsModal({ item, onClose, onDone }) {
  const toast = useToast()
  const [sku, setSku] = useState(item.sku ?? '')
  const [threshold, setThreshold] = useState(String(item.lowStockThreshold))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const save = async () => {
    setSaving(true)
    setError(null)
    try {
      await api.admin.inventory.updateSettings(item.variantId, sku, Number(threshold))
      toast.success('Đã lưu cài đặt')
      onDone()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title="Cài đặt mặt hàng" subtitle={`${item.productName} · ${item.color} / ${item.size}`} onClose={onClose}
      footer={<>
        <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
        <button className="btn btn-primary" onClick={save} disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu'}</button>
      </>}>
      <ErrorBox error={error && !error.fieldErrors?.sku ? error : null} />
      <div className="form-grid">
        <Field label="Mã SKU" error={error?.fieldErrors?.sku} hint="Mã định danh duy nhất của mặt hàng">
          <input value={sku} onChange={(e) => setSku(e.target.value)} maxLength={64} />
        </Field>
        <Field label="Ngưỡng cảnh báo sắp hết" error={error?.fieldErrors?.lowStockThreshold}
          hint="Khi tồn kho bằng hoặc thấp hơn số này, mặt hàng được đánh dấu Sắp hết">
          <input type="number" min="0" value={threshold} onChange={(e) => setThreshold(e.target.value)} />
        </Field>
      </div>
    </Modal>
  )
}

// ---------------------------------------------------------------- Goods receipts

function ReceiptsTab() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [creating, setCreating] = useState(false)
  const [open, setOpen] = useState(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    setData(null)
    api.admin.inventory.receipts(page).then(setData).catch(setError)
  }, [page, reload])

  return (
    <>
      {creating ? (
        <ReceiptForm onCancel={() => setCreating(false)} onSaved={() => { setCreating(false); setPage(0); setReload((r) => r + 1) }} />
      ) : (
        <div className="toolbar">
          <p className="muted small grow">Lập phiếu khi nhận hàng từ nhà cung cấp. Tồn kho được cộng ngay khi lưu phiếu.</p>
          <button className="btn btn-primary" onClick={() => setCreating(true)}><Icon name="plus" size={16} /> Tạo phiếu nhập</button>
        </div>
      )}

      <ErrorBox error={error} />
      <div className="card">
        {!data ? (
          <Spinner />
        ) : data.items.length === 0 ? (
          <p className="muted">Chưa có phiếu nhập nào.</p>
        ) : (
          <table className="table table-hover">
            <thead>
              <tr><th>Mã phiếu</th><th>Thời gian</th><th>Nhà cung cấp</th><th className="right">Số dòng</th><th className="right">Tổng SL</th><th>Người lập</th></tr>
            </thead>
            <tbody>
              {data.items.map((r) => (
                <ReceiptRow key={r.id} receipt={r} open={open === r.id} onToggle={() => setOpen(open === r.id ? null : r.id)} />
              ))}
            </tbody>
          </table>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
      </div>
    </>
  )
}

function ReceiptRow({ receipt: r, open, onToggle }) {
  return (
    <>
      <tr className="clickable" onClick={onToggle}>
        <td className="strong nowrap"><Icon name={open ? 'chevronDown' : 'chevronRight'} size={14} /> {r.code}</td>
        <td className="small">{formatDateTime(r.createdAt)}</td>
        <td>{r.supplier ?? <span className="muted">—</span>}</td>
        <td className="right">{r.lines.length}</td>
        <td className="right strong">+{r.totalQuantity}</td>
        <td className="small muted">{r.createdBy}</td>
      </tr>
      {open && (
        <tr className="row-detail">
          <td colSpan={6}>
            {r.note && <p className="small"><strong>Ghi chú:</strong> {r.note}</p>}
            <table className="table table-compact">
              <thead><tr><th>Sản phẩm</th><th>SKU</th><th>Màu / Size</th><th className="right">Số lượng</th></tr></thead>
              <tbody>
                {r.lines.map((l) => (
                  <tr key={l.variantId}>
                    <td>{l.productName}</td>
                    <td><code>{l.sku ?? '—'}</code></td>
                    <td>{l.color} / {l.size}</td>
                    <td className="right strong">+{l.quantity}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </td>
        </tr>
      )}
    </>
  )
}

function ReceiptForm({ onCancel, onSaved }) {
  const toast = useToast()
  const [supplier, setSupplier] = useState('')
  const [note, setNote] = useState('')
  const [lines, setLines] = useState([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const addLine = (item) => {
    if (lines.some((l) => l.item.variantId === item.variantId)) {
      toast.error('Mặt hàng này đã có trong phiếu')
      return
    }
    setLines([...lines, { item, quantity: '1' }])
  }
  const setQuantity = (variantId, quantity) =>
    setLines(lines.map((l) => (l.item.variantId === variantId ? { ...l, quantity } : l)))
  const total = lines.reduce((sum, l) => sum + (Number(l.quantity) || 0), 0)

  const save = async () => {
    setSaving(true)
    setError(null)
    try {
      const receipt = await api.admin.inventory.createReceipt({
        supplier,
        note,
        lines: lines.map((l) => ({ variantId: l.item.variantId, quantity: Number(l.quantity) })),
      })
      toast.success(`Đã lưu phiếu ${receipt.code}: +${receipt.totalQuantity} sản phẩm`)
      onSaved()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="card stack receipt-form">
      <div className="section-head section-head-tight">
        <h3>Phiếu nhập kho mới</h3>
      </div>
      <ErrorBox error={error} />
      <div className="form-grid form-grid-2">
        <Field label="Nhà cung cấp" error={error?.fieldErrors?.supplier}>
          <input value={supplier} onChange={(e) => setSupplier(e.target.value)} maxLength={200} placeholder="Ví dụ: Xưởng may Hòa Bình" />
        </Field>
        <Field label="Ghi chú" error={error?.fieldErrors?.note}>
          <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} placeholder="Ví dụ: lô hàng tháng 10" />
        </Field>
      </div>

      <VariantPicker onPick={addLine} />

      {lines.length === 0 ? (
        <p className="muted small">Tìm và chọn mặt hàng ở ô phía trên để thêm vào phiếu.</p>
      ) : (
        <table className="table table-compact">
          <thead><tr><th>Sản phẩm</th><th>SKU</th><th>Màu / Size</th><th className="right">Tồn hiện tại</th><th>SL nhập</th><th className="right">Sau khi nhập</th><th /></tr></thead>
          <tbody>
            {lines.map(({ item, quantity }) => (
              <tr key={item.variantId}>
                <td className="strong">{item.productName}</td>
                <td><code>{item.sku ?? '—'}</code></td>
                <td>{item.color} / {item.size}</td>
                <td className="right">{item.stock}</td>
                <td className="qty-cell">
                  <input type="number" min="1" value={quantity} onChange={(e) => setQuantity(item.variantId, e.target.value)} />
                </td>
                <td className="right strong text-success">{item.stock + (Number(quantity) || 0)}</td>
                <td className="right">
                  <button className="icon-btn icon-btn-sm" onClick={() => setLines(lines.filter((l) => l.item.variantId !== item.variantId))} aria-label="Xóa dòng">
                    <Icon name="trash" size={16} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="row-actions">
        <span className="grow muted">Tổng: <strong>{lines.length}</strong> mặt hàng · <strong>{total}</strong> sản phẩm</span>
        <button className="btn btn-ghost" onClick={onCancel}>Hủy</button>
        <button className="btn btn-primary" onClick={save} disabled={saving || lines.length === 0}>
          {saving ? 'Đang lưu...' : 'Lưu phiếu nhập'}
        </button>
      </div>
    </section>
  )
}

/** Search box that looks up variants by product name or SKU and lets you pick one. */
function VariantPicker({ onPick }) {
  const [q, setQ] = useState('')
  const [results, setResults] = useState([])
  const [open, setOpen] = useState(false)
  const timer = useRef(null)

  const search = useCallback((text) => {
    clearTimeout(timer.current)
    if (!text.trim()) {
      setResults([])
      return
    }
    timer.current = setTimeout(() => {
      api.admin.inventory.items({ q: text.trim(), size: 10 }).then((r) => setResults(r.items)).catch(() => setResults([]))
    }, 250)
  }, [])

  useEffect(() => () => clearTimeout(timer.current), [])

  return (
    <div className="picker">
      <div className="search search-admin picker-input">
        <Icon name="search" size={18} />
        <input value={q} placeholder="Tìm mặt hàng theo tên hoặc SKU để thêm vào phiếu..."
          onChange={(e) => { setQ(e.target.value); setOpen(true); search(e.target.value) }}
          onFocus={() => setOpen(true)} onBlur={() => setTimeout(() => setOpen(false), 150)} />
      </div>
      {open && results.length > 0 && (
        <ul className="picker-results">
          {results.map((item) => (
            <li key={item.variantId}>
              <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={() => { onPick(item); setQ(''); setResults([]) }}>
                <ProductImage src={item.imageUrl} alt="" className="picker-img" />
                <span className="grow">
                  <strong>{item.productName}</strong>
                  <span className="muted small"> · {item.color} / {item.size}</span>
                  <div className="muted small"><code>{item.sku}</code></div>
                </span>
                <span className={`small stock-${item.status}`}>Tồn: {item.stock}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- Stock ledger

function MovementsTab({ params, setParams }) {
  const variant = params.get('variant') || ''
  const variantLabel = params.get('label') || ''
  const type = params.get('type') || ''
  const page = Number(params.get('page') || 0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  const update = (changes) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === '' || v === null ? next.delete(k) : next.set(k, v)))
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  useEffect(() => {
    setData(null)
    api.admin.inventory.movements({ variantId: variant, type, page }).then(setData).catch(setError)
  }, [variant, type, page])

  return (
    <>
      <div className="toolbar">
        <select value={type} onChange={(e) => update({ type: e.target.value })} className="toolbar-select">
          <option value="">Tất cả loại</option>
          {Object.entries(MOVEMENT_TYPES).map(([k, t]) => <option key={k} value={k}>{t.label}</option>)}
        </select>
        {variant && (
          <span className="filter-chip">
            {variantLabel || `Mặt hàng #${variant}`}
            <button onClick={() => update({ variant: '', label: '' })} aria-label="Bỏ lọc"><Icon name="close" size={14} /></button>
          </span>
        )}
      </div>
      <ErrorBox error={error} />
      <div className="card">
        {!data ? (
          <Spinner />
        ) : data.items.length === 0 ? (
          <p className="muted">Chưa có giao dịch nào.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Thời gian</th><th>Mặt hàng</th><th>Loại</th><th className="right">Thay đổi</th><th className="right">Tồn trước → sau</th><th>Chứng từ</th><th>Người thực hiện</th></tr>
            </thead>
            <tbody>
              {data.items.map((m) => (
                <tr key={m.id}>
                  <td className="small nowrap">{formatDateTime(m.createdAt)}</td>
                  <td>
                    <strong>{m.productName}</strong>
                    <div className="muted small">{m.color} / {m.size} {m.sku && <>· <code>{m.sku}</code></>}</div>
                  </td>
                  <td>
                    <span className={`badge badge-${MOVEMENT_TYPES[m.type].tone}`}>{MOVEMENT_TYPES[m.type].label}</span>
                    {m.reason && <div className="muted small">{ADJUSTMENT_REASONS[m.reason] ?? m.reason}</div>}
                  </td>
                  <td className={`right strong ${m.quantityChange > 0 ? 'text-success' : 'text-danger'}`}>
                    {m.quantityChange > 0 ? `+${m.quantityChange}` : m.quantityChange}
                  </td>
                  <td className="right nowrap">{m.stockBefore} → <strong>{m.stockAfter}</strong></td>
                  <td>
                    {m.referenceCode ? <code>{m.referenceCode}</code> : <span className="muted">—</span>}
                    {m.note && <div className="muted small">{m.note}</div>}
                  </td>
                  <td className="small muted">{m.actorEmail ?? 'Hệ thống'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => update({ page: String(p) })} />}
      </div>
    </>
  )
}
