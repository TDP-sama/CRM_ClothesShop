import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api.js'
import Icon from '../components/Icon.jsx'
import { ProductGrid } from '../components/ProductCard.jsx'
import { EmptyState, ErrorBox, Pagination } from '../components/ui.jsx'
import { swatchColor } from '../lib/format.js'

const PRICE_RANGES = [
  { id: '0-300000', label: 'Dưới 300.000₫', min: null, max: 300000 },
  { id: '300000-600000', label: '300.000₫ – 600.000₫', min: 300000, max: 600000 },
  { id: '600000-1000000', label: '600.000₫ – 1.000.000₫', min: 600000, max: 1000000 },
  { id: '1000000-', label: 'Trên 1.000.000₫', min: 1000000, max: null },
]

const SORTS = [
  { id: 'newest', label: 'Mới nhất' },
  { id: 'price-asc', label: 'Giá tăng dần' },
  { id: 'price-desc', label: 'Giá giảm dần' },
  { id: 'name', label: 'Tên A–Z' },
]

/** All filters live in the URL, so results can be shared, bookmarked and restored with Back. */
export default function Catalog() {
  const [params, setParams] = useSearchParams()
  const [categories, setCategories] = useState([])
  const [facets, setFacets] = useState({ sizes: [], colors: [] })
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [filtersOpen, setFiltersOpen] = useState(false)

  const category = params.get('category') || ''
  const q = params.get('q') || ''
  const sizes = params.getAll('sizes')
  const colors = params.getAll('colors')
  const price = params.get('price') || ''
  const sale = params.get('sale') === 'true'
  const sort = params.get('sort') || 'newest'
  const page = Number(params.get('page') || 0)
  const key = params.toString()

  useEffect(() => {
    api.categories().then(setCategories).catch(() => {})
    api.filters().then(setFacets).catch(() => {})
  }, [])

  useEffect(() => {
    const range = PRICE_RANGES.find((r) => r.id === price)
    setResult(null)
    setError(null)
    api
      .products({ category, q, sizes, colors, sale, sort, page, size: 12, minPrice: range?.min, maxPrice: range?.max })
      .then(setResult)
      .catch(setError)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  const update = (changes, keepPage = false) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => {
      next.delete(k)
      if (Array.isArray(v)) v.forEach((x) => next.append(k, x))
      else if (v !== '' && v !== null && v !== false && v !== undefined) next.set(k, v)
    })
    if (!keepPage) next.delete('page')
    setParams(next)
  }
  const toggle = (list, value) => (list.includes(value) ? list.filter((x) => x !== value) : [...list, value])

  const currentCategory = categories.find((c) => c.slug === category)
  const title = q ? `Kết quả cho "${q}"` : sale ? 'Đang giảm giá' : currentCategory?.name || 'Tất cả sản phẩm'
  const activeCount = sizes.length + colors.length + (price ? 1 : 0) + (sale ? 1 : 0)

  const filterPanel = (
      <>
        <FilterGroup title="Danh mục">
          <button className={`filter-link ${!category ? 'active' : ''}`} onClick={() => update({ category: '' })}>Tất cả</button>
          {categories.map((c) => (
            <button key={c.id} className={`filter-link ${category === c.slug ? 'active' : ''}`} onClick={() => update({ category: c.slug })}>
              {c.name} <span className="muted small">({c.productCount})</span>
            </button>
          ))}
        </FilterGroup>
        <FilterGroup title="Kích cỡ">
          <div className="chip-list">
            {facets.sizes.map((s) => (
              <button key={s} className={`chip ${sizes.includes(s) ? 'active' : ''}`} onClick={() => update({ sizes: toggle(sizes, s) })}>
                {s}
              </button>
            ))}
          </div>
        </FilterGroup>
        <FilterGroup title="Màu sắc">
          <div className="color-list">
            {facets.colors.map((c) => (
              <button key={c} className={`color-option ${colors.includes(c) ? 'active' : ''}`} onClick={() => update({ colors: toggle(colors, c) })}>
                <span className="swatch" style={{ background: swatchColor(c) }} />
                {c}
              </button>
            ))}
          </div>
        </FilterGroup>
        <FilterGroup title="Khoảng giá">
          {PRICE_RANGES.map((r) => (
            <label key={r.id} className="radio">
              <input type="radio" name="price" checked={price === r.id} onChange={() => update({ price: r.id })} />
              {r.label}
            </label>
          ))}
          {price && <button className="link small" onClick={() => update({ price: '' })}>Bỏ chọn giá</button>}
        </FilterGroup>
        <FilterGroup title="Khuyến mãi">
          <label className="checkbox">
            <input type="checkbox" checked={sale} onChange={(e) => update({ sale: e.target.checked ? 'true' : '' })} />
            Chỉ hiện sản phẩm giảm giá
          </label>
        </FilterGroup>
      </>
  )

  return (
    <div className="container page">
      <nav className="breadcrumb">
        <Link to="/">Trang chủ</Link> <span>/</span> <span>{title}</span>
      </nav>
      <div className="catalog-head">
        <div>
          <h1 className="page-title">{title}</h1>
          {result && <p className="muted">{result.totalElements} sản phẩm</p>}
        </div>
        <div className="catalog-tools">
          <button className="btn btn-ghost only-mobile" onClick={() => setFiltersOpen((o) => !o)}>
            <Icon name="filter" size={16} /> Bộ lọc {activeCount > 0 && `(${activeCount})`}
          </button>
          <label className="select-wrap">
            <span className="sr-only">Sắp xếp</span>
            <select value={sort} onChange={(e) => update({ sort: e.target.value })}>
              {SORTS.map((s) => (
                <option key={s.id} value={s.id}>{s.label}</option>
              ))}
            </select>
          </label>
        </div>
      </div>

      <div className="catalog">
        <aside className={`filters ${filtersOpen ? 'filters-open' : ''}`}>
          {filterPanel}
          {activeCount > 0 && (
            <button className="btn btn-ghost btn-block" onClick={() => update({ sizes: [], colors: [], price: '', sale: '' })}>
              Xóa bộ lọc
            </button>
          )}
        </aside>
        <section>
          <ErrorBox error={error} />
          {result && result.items.length === 0 ? (
            <EmptyState icon="search" title="Không tìm thấy sản phẩm phù hợp"
              action={<button className="btn btn-primary" onClick={() => setParams({})}>Xem tất cả sản phẩm</button>}>
              Hãy thử bỏ bớt bộ lọc hoặc tìm với từ khóa khác.
            </EmptyState>
          ) : (
            <ProductGrid products={result?.items ?? []} loading={!result && !error} columns={3} />
          )}
          {result && (
            <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => update({ page: String(p) }, true)} />
          )}
        </section>
      </div>
    </div>
  )
}

function FilterGroup({ title, children }) {
  return (
    <div className="filter-group">
      <h4>{title}</h4>
      {children}
    </div>
  )
}
