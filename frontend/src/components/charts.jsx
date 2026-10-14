import { useEffect, useRef, useState } from 'react'
import Icon from './Icon.jsx'

// Hand-built SVG/CSS charts following one spec: 2px lines, ~10% area wash, bars <= 24px with a 4px rounded data-end,
// hairline solid grid, text in text tokens (never the series color), hover/focus tooltips that never gate a value
// (every chart has a table twin), one validated series color per theme (--series-1 in App.css).

/** Compact VND for axes and labels: 1,2 tỷ · 350tr · 90k. */
export function compactVnd(value) {
  const abs = Math.abs(value)
  const fmt = (n) => n.toLocaleString('vi-VN', { maximumFractionDigits: n < 10 ? 1 : 0 })
  if (abs >= 1e9) return `${fmt(value / 1e9)} tỷ`
  if (abs >= 1e6) return `${fmt(value / 1e6)}tr`
  if (abs >= 1e3) return `${fmt(value / 1e3)}k`
  return String(value)
}

/** Rounds up to a clean axis maximum (1, 2, 2.5, 5 × 10^n) and returns evenly spaced ticks. */
function niceTicks(max, count = 4) {
  if (max <= 0) return [0, 1]
  const raw = max / count
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw)
  return Array.from({ length: Math.ceil(max / step) + 1 }, (_, i) => i * step)
}

function useWidth() {
  const ref = useRef(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el) return undefined
    const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)))
    observer.observe(el)
    return () => observer.disconnect()
  }, [])
  return [ref, width]
}

/**
 * Single-series trend: line + area wash, crosshair snapping to the nearest point, endpoint/peak dot.
 * {@code points}: [{ key, label, value, detail }] in order.
 */
export function TrendChart({ points, formatValue, formatAxis = formatValue, ariaLabel, height = 240 }) {
  const [ref, width] = useWidth()
  const [active, setActive] = useState(null)
  const pad = { top: 16, right: 16, bottom: 28, left: 56 }
  const plotW = Math.max(0, width - pad.left - pad.right)
  const plotH = height - pad.top - pad.bottom
  const ticks = niceTicks(Math.max(...points.map((p) => p.value), 0))
  const yMax = ticks[ticks.length - 1]
  const x = (i) => pad.left + (points.length <= 1 ? plotW / 2 : (i / (points.length - 1)) * plotW)
  const y = (v) => pad.top + plotH - (v / yMax) * plotH

  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ')
  const area = points.length ? `${line} L${x(points.length - 1).toFixed(1)},${y(0)} L${x(0).toFixed(1)},${y(0)} Z` : ''
  const peak = points.reduce((best, p, i) => (p.value > (points[best]?.value ?? -1) ? i : best), 0)
  const labelEvery = Math.max(1, Math.ceil(points.length / Math.max(2, Math.floor(plotW / 72))))

  const pick = (clientX) => {
    const rect = ref.current.getBoundingClientRect()
    const rel = clientX - rect.left - pad.left
    const i = points.length <= 1 ? 0 : Math.round((rel / plotW) * (points.length - 1))
    setActive(Math.min(points.length - 1, Math.max(0, i)))
  }
  const onKey = (e) => {
    if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
      e.preventDefault()
      const step = e.key === 'ArrowRight' ? 1 : -1
      setActive((a) => Math.min(points.length - 1, Math.max(0, (a ?? (step > 0 ? -1 : points.length)) + step)))
    }
  }
  const shown = active ?? null

  return (
    <div className="chart" ref={ref}>
      {width > 0 && (
        <svg width={width} height={height} role="img" aria-label={ariaLabel} tabIndex={0}
          onPointerMove={(e) => pick(e.clientX)} onPointerLeave={() => setActive(null)}
          onFocus={() => setActive((a) => a ?? points.length - 1)} onBlur={() => setActive(null)} onKeyDown={onKey}>
          {ticks.map((t) => (
            <g key={t}>
              <line className="chart-grid" x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} />
              <text className="chart-axis" x={pad.left - 8} y={y(t)} dy="0.32em" textAnchor="end">{formatAxis(t)}</text>
            </g>
          ))}
          {points.map((p, i) => i % labelEvery === 0 && (
            <text key={p.key} className="chart-axis" x={x(i)} y={height - 8} textAnchor="middle">{p.label}</text>
          ))}
          <path d={area} className="chart-area" />
          <path d={line} className="chart-line" />
          {points[peak]?.value > 0 && shown === null && (
            <g>
              <circle cx={x(peak)} cy={y(points[peak].value)} r={4} className="chart-dot" />
              <text className="chart-label" x={x(peak)} y={y(points[peak].value) - 10}
                textAnchor={peak > points.length * 0.8 ? 'end' : peak < points.length * 0.2 ? 'start' : 'middle'}>
                Cao nhất: {formatValue(points[peak].value)}
              </text>
            </g>
          )}
          {shown !== null && (
            <g>
              <line className="chart-crosshair" x1={x(shown)} x2={x(shown)} y1={pad.top} y2={pad.top + plotH} />
              <circle cx={x(shown)} cy={y(points[shown].value)} r={4} className="chart-dot" />
            </g>
          )}
        </svg>
      )}
      {shown !== null && width > 0 && (
        <div className="chart-tooltip" style={{
          left: Math.min(Math.max(x(shown), 90), width - 90),
          top: Math.max(0, y(points[shown].value) - 64),
        }}>
          <strong>{formatValue(points[shown].value)}</strong>
          <span>{points[shown].detail}</span>
        </div>
      )}
    </div>
  )
}

/**
 * Horizontal bars for nominal categories (products, categories): one color for every bar, value at the tip,
 * per-bar hover/focus tooltip. {@code rows}: [{ key, label, sub, value, tooltip }].
 */
export function BarList({ rows, formatValue, ariaLabel }) {
  const [active, setActive] = useState(null)
  const max = Math.max(...rows.map((r) => r.value), 1)
  return (
    <ul className="barlist" aria-label={ariaLabel}>
      {rows.map((r) => (
        <li key={r.key} tabIndex={0} className={active === r.key ? 'active' : ''}
          onPointerEnter={() => setActive(r.key)} onPointerLeave={() => setActive(null)}
          onFocus={() => setActive(r.key)} onBlur={() => setActive(null)}>
          <div className="barlist-label">
            <span className="barlist-name">{r.label}</span>
            {r.sub && <span className="muted small">{r.sub}</span>}
          </div>
          <div className="barlist-track">
            {/* The longest bar takes 85% of the track so the value at its tip always fits. */}
            <span className="barlist-bar" style={{ width: `${Math.max(1.5, (r.value / max) * 85)}%` }} />
            <span className="barlist-value">{formatValue(r.value)}</span>
          </div>
          {active === r.key && r.tooltip && <div className="chart-tooltip barlist-tooltip">{r.tooltip}</div>}
        </li>
      ))}
    </ul>
  )
}

/** A stat tile: label, value and the change against the previous period (sign + arrow + text, not color alone). */
export function KpiTile({ label, value, current, previous, icon }) {
  let delta = null
  if (previous > 0) {
    const pct = ((current - previous) / previous) * 100
    const rounded = Math.round(pct * 10) / 10
    delta = { up: rounded >= 0, text: `${rounded >= 0 ? '+' : ''}${rounded.toLocaleString('vi-VN')}%` }
  }
  return (
    <div className="card stat kpi">
      <span className="stat-icon"><Icon name={icon} size={20} /></span>
      <span className="muted small">{label}</span>
      <strong className="stat-value">{value}</strong>
      {delta ? (
        <span className={`kpi-delta ${delta.up ? 'text-success' : 'text-danger'}`}>
          <Icon name={delta.up ? 'arrowUp' : 'arrowDown'} size={14} /> {delta.text}
          <span className="muted"> so với kỳ trước</span>
        </span>
      ) : (
        <span className="kpi-delta muted">Kỳ trước chưa có dữ liệu</span>
      )}
    </div>
  )
}

/** Card with title and a chart ⇄ table switch; the table is the accessible twin of every chart. */
export function ChartCard({ title, subtitle, table, children, loading }) {
  const [asTable, setAsTable] = useState(false)
  return (
    <section className={`card chart-card ${loading ? 'is-loading' : ''}`}>
      <div className="chart-card-head">
        <div>
          <h3>{title}</h3>
          {subtitle && <p className="muted small">{subtitle}</p>}
        </div>
        <button className="btn btn-ghost btn-sm" onClick={() => setAsTable((t) => !t)}>
          <Icon name={asTable ? 'chart' : 'grid'} size={14} /> {asTable ? 'Xem biểu đồ' : 'Xem dạng bảng'}
        </button>
      </div>
      {asTable ? table : children}
    </section>
  )
}
