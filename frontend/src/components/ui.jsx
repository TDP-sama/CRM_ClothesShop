import { useState } from 'react'
import Icon from './Icon.jsx'
import { discountPercent, formatPrice, ORDER_STATUS } from '../lib/format.js'

export function Price({ price, compareAtPrice, size = 'md' }) {
  const off = discountPercent(price, compareAtPrice)
  return (
    <span className={`price price-${size}`}>
      <span className={off ? 'price-now price-sale' : 'price-now'}>{formatPrice(price)}</span>
      {off > 0 && <span className="price-was">{formatPrice(compareAtPrice)}</span>}
      {off > 0 && size !== 'sm' && <span className="badge badge-sale">-{off}%</span>}
    </span>
  )
}

export function Stars({ value = 0, size = 14, count }) {
  return (
    <span className="stars" aria-label={`${value.toFixed(1)} trên 5 sao`}>
      {[1, 2, 3, 4, 5].map((i) => (
        <Icon key={i} name="star" size={size} filled={i <= Math.round(value)} className={i <= Math.round(value) ? 'star-on' : 'star-off'} />
      ))}
      {count !== undefined && <span className="stars-count">({count})</span>}
    </span>
  )
}

export function StarInput({ value, onChange }) {
  const [hover, setHover] = useState(0)
  return (
    <div className="star-input" onMouseLeave={() => setHover(0)} role="radiogroup" aria-label="Số sao">
      {[1, 2, 3, 4, 5].map((i) => (
        <button
          type="button"
          key={i}
          role="radio"
          aria-checked={value === i}
          aria-label={`${i} sao`}
          onMouseEnter={() => setHover(i)}
          onClick={() => onChange(i)}
        >
          <Icon name="star" size={26} filled={i <= (hover || value)} className={i <= (hover || value) ? 'star-on' : 'star-off'} />
        </button>
      ))}
    </div>
  )
}

export function StatusBadge({ status }) {
  const s = ORDER_STATUS[status] ?? { label: status, tone: 'neutral' }
  return <span className={`badge badge-${s.tone}`}>{s.label}</span>
}

export function Spinner({ label = 'Đang tải...' }) {
  return (
    <div className="spinner-wrap" role="status">
      <span className="spinner" />
      <span className="sr-only">{label}</span>
    </div>
  )
}

export function EmptyState({ icon = 'package', title, children, action }) {
  return (
    <div className="empty">
      <div className="empty-icon">
        <Icon name={icon} size={32} />
      </div>
      <h3>{title}</h3>
      {children && <p className="muted">{children}</p>}
      {action}
    </div>
  )
}

export function ErrorBox({ error }) {
  if (!error) return null
  return <div className="alert alert-danger">{error.message ?? String(error)}</div>
}

export function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null
  const pages = []
  for (let i = 0; i < totalPages; i++) {
    if (i === 0 || i === totalPages - 1 || Math.abs(i - page) <= 1) pages.push(i)
    else if (pages[pages.length - 1] !== '…') pages.push('…')
  }
  return (
    <nav className="pagination" aria-label="Phân trang">
      <button className="page-btn" disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Trang trước">
        <Icon name="chevronLeft" size={16} />
      </button>
      {pages.map((p, idx) =>
        p === '…' ? (
          <span key={`gap-${idx}`} className="page-gap">…</span>
        ) : (
          <button key={p} className={`page-btn ${p === page ? 'active' : ''}`} onClick={() => onChange(p)}>
            {p + 1}
          </button>
        ),
      )}
      <button className="page-btn" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)} aria-label="Trang sau">
        <Icon name="chevronRight" size={16} />
      </button>
    </nav>
  )
}

export function QuantityStepper({ value, max, onChange, size = 'md' }) {
  return (
    <div className={`stepper stepper-${size}`}>
      <button type="button" onClick={() => onChange(value - 1)} disabled={value <= 1} aria-label="Giảm">
        <Icon name="minus" size={14} />
      </button>
      <span aria-live="polite">{value}</span>
      <button type="button" onClick={() => onChange(value + 1)} disabled={value >= max} aria-label="Tăng">
        <Icon name="plus" size={14} />
      </button>
    </div>
  )
}

/** Product photo with a graceful placeholder when the URL is missing or broken. */
export function ProductImage({ src, alt, className = '' }) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) {
    return (
      <div className={`img-placeholder ${className}`} aria-label={alt}>
        <Icon name="image" size={32} />
      </div>
    )
  }
  return <img src={src} alt={alt} loading="lazy" className={className} onError={() => setFailed(true)} />
}

export function Field({ label, error, children, hint }) {
  return (
    <label className={`field ${error ? 'field-error' : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="field-message">{error}</span> : hint && <span className="field-hint">{hint}</span>}
    </label>
  )
}
