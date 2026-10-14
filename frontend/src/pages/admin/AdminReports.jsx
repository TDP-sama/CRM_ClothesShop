import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../../api.js'
import { BarList, ChartCard, KpiTile, TrendChart, compactVnd } from '../../components/charts.jsx'
import { EmptyState, ErrorBox, Spinner } from '../../components/ui.jsx'
import { formatDate, formatPrice } from '../../lib/format.js'
import { AdminHeader } from './AdminLayout.jsx'

const PERIODS = [
  { days: 7, label: '7 ngày' },
  { days: 30, label: '30 ngày' },
  { days: 90, label: '90 ngày' },
  { days: 365, label: '12 tháng' },
]

const dayLabel = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit' })
const monthLabel = new Intl.DateTimeFormat('vi-VN', { month: '2-digit', year: 'numeric' })
const parseDate = (iso) => new Date(`${iso}T00:00:00`)

/** Sales reports: KPIs, revenue over time, best sellers and revenue per category, all scoped by one period filter. */
export default function AdminReports() {
  const [params, setParams] = useSearchParams()
  const days = Number(params.get('days')) || 30
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    // Refetch keeps the previous render (dimmed) instead of flashing a skeleton.
    setLoading(true)
    setError(null)
    api.admin.reports(days).then(setReport).catch(setError).finally(() => setLoading(false))
  }, [days])

  const byMonth = report?.granularity === 'MONTH'
  const points = (report?.series ?? []).map((p) => {
    const d = parseDate(p.date)
    return {
      key: p.date,
      label: byMonth ? monthLabel.format(d) : dayLabel.format(d),
      value: p.revenue,
      detail: `${byMonth ? `Tháng ${monthLabel.format(d)}` : formatDate(d)} · ${p.orders} đơn`,
    }
  })
  const hasSales = (report?.current.orders ?? 0) > 0

  return (
    <>
      <AdminHeader title="Báo cáo bán hàng" />
      <div className="toolbar">
        <div className="segmented" role="radiogroup" aria-label="Khoảng thời gian">
          {PERIODS.map((p) => (
            <button key={p.days} role="radio" aria-checked={days === p.days}
              className={days === p.days ? 'active' : ''} onClick={() => setParams({ days: String(p.days) })}>
              {p.label}
            </button>
          ))}
        </div>
        {report && (
          <span className="muted small">
            {formatDate(parseDate(report.from))} – {formatDate(parseDate(report.to))} · Doanh thu tiền hàng, không gồm đơn đã hủy và phí vận chuyển
          </span>
        )}
      </div>

      <ErrorBox error={error} />
      {!report ? (
        loading && <Spinner />
      ) : (
        <div className={`reports ${loading ? 'is-loading' : ''}`}>
          <div className="stat-grid">
            <KpiTile icon="cash" label="Doanh thu" value={formatPrice(report.current.revenue)}
              current={report.current.revenue} previous={report.previous.revenue} />
            <KpiTile icon="package" label="Số đơn hàng" value={report.current.orders.toLocaleString('vi-VN')}
              current={report.current.orders} previous={report.previous.orders} />
            <KpiTile icon="tag" label="Sản phẩm bán ra" value={report.current.units.toLocaleString('vi-VN')}
              current={report.current.units} previous={report.previous.units} />
            <KpiTile icon="bag" label="Giá trị đơn trung bình" value={formatPrice(report.current.averageOrderValue)}
              current={report.current.averageOrderValue} previous={report.previous.averageOrderValue} />
          </div>

          {!hasSales ? (
            <div className="card">
              <EmptyState icon="chart" title="Chưa có đơn hàng nào trong khoảng thời gian này">
                Hãy chọn khoảng thời gian dài hơn ở phía trên.
              </EmptyState>
            </div>
          ) : (
            <>
              <ChartCard title={byMonth ? 'Doanh thu theo tháng' : 'Doanh thu theo ngày'}
                subtitle="Rê chuột hoặc dùng phím ← → trên biểu đồ để xem từng mốc" loading={loading}
                table={
                  <table className="table table-compact">
                    <thead><tr><th>{byMonth ? 'Tháng' : 'Ngày'}</th><th className="right">Số đơn</th><th className="right">Doanh thu</th></tr></thead>
                    <tbody>
                      {[...report.series].reverse().map((p) => (
                        <tr key={p.date}>
                          <td>{byMonth ? monthLabel.format(parseDate(p.date)) : formatDate(parseDate(p.date))}</td>
                          <td className="right num">{p.orders}</td>
                          <td className="right num">{formatPrice(p.revenue)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                }>
                <TrendChart points={points} formatValue={formatPrice} formatAxis={compactVnd}
                  ariaLabel={`Doanh thu ${report.days} ngày qua`} />
              </ChartCard>

              <div className="two-col two-col-even">
                <ChartCard title="Sản phẩm bán chạy" subtitle="Top 10 theo số lượng bán ra" loading={loading}
                  table={
                    <table className="table table-compact">
                      <thead><tr><th>#</th><th>Sản phẩm</th><th className="right">Đã bán</th><th className="right">Doanh thu</th></tr></thead>
                      <tbody>
                        {report.topProducts.map((p, i) => (
                          <tr key={p.productId}>
                            <td className="muted">{i + 1}</td>
                            <td><Link to={`/san-pham/${p.slug}`} className="strong">{p.name}</Link></td>
                            <td className="right num">{p.units}</td>
                            <td className="right num">{formatPrice(p.revenue)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  }>
                  <BarList ariaLabel="Sản phẩm bán chạy theo số lượng" formatValue={(v) => `${v} cái`}
                    rows={report.topProducts.map((p) => ({
                      key: p.productId,
                      label: p.name,
                      value: p.units,
                      tooltip: <><strong>{p.units} sản phẩm</strong><span>{p.name} · {formatPrice(p.revenue)}</span></>,
                    }))} />
                </ChartCard>

                <ChartCard title="Doanh thu theo danh mục" subtitle="Tỷ trọng doanh thu của từng loại sản phẩm" loading={loading}
                  table={
                    <table className="table table-compact">
                      <thead><tr><th>Danh mục</th><th className="right">Đã bán</th><th className="right">Doanh thu</th><th className="right">Tỷ trọng</th></tr></thead>
                      <tbody>
                        {report.categories.map((c) => (
                          <tr key={c.name}>
                            <td className="strong">{c.name}</td>
                            <td className="right num">{c.units}</td>
                            <td className="right num">{formatPrice(c.revenue)}</td>
                            <td className="right num">{share(c.revenue, report.current.revenue)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  }>
                  <BarList ariaLabel="Doanh thu theo danh mục" formatValue={compactVnd}
                    rows={report.categories.map((c) => ({
                      key: c.name,
                      label: c.name,
                      sub: share(c.revenue, report.current.revenue),
                      value: c.revenue,
                      tooltip: <><strong>{formatPrice(c.revenue)}</strong><span>{c.name} · {c.units} sản phẩm · {share(c.revenue, report.current.revenue)}</span></>,
                    }))} />
                </ChartCard>
              </div>
            </>
          )}
        </div>
      )}
    </>
  )
}

function share(part, total) {
  return total > 0 ? `${((part / total) * 100).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%` : '0%'
}
