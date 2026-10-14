// Thin client for the Spring Boot API (same origin via the Vite proxy).
// Auth is a session cookie; every write must echo the XSRF-TOKEN cookie in the X-XSRF-TOKEN header.

export class ApiError extends Error {
  constructor(status, message, fieldErrors = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

function readXsrfCookie() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

let csrfBootstrap = null

/** Makes sure the XSRF-TOKEN cookie exists (after first load and after logout). */
export async function ensureCsrf(force = false) {
  if (!force && readXsrfCookie()) return
  if (!csrfBootstrap || force) {
    csrfBootstrap = fetch('/api/auth/csrf', { credentials: 'same-origin' }).finally(() => {
      csrfBootstrap = null
    })
  }
  await csrfBootstrap
}

async function request(method, path, body, { retried = false } = {}) {
  const unsafe = method !== 'GET'
  if (unsafe) await ensureCsrf()

  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (unsafe) {
    const token = readXsrfCookie()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const res = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  // A stale or missing CSRF token: fetch a fresh one and retry once.
  if (res.status === 403 && unsafe && !retried && !readXsrfCookie()) {
    await ensureCsrf(true)
    return request(method, path, body, { retried: true })
  }

  if (res.status === 204) return null
  const text = await res.text()
  const data = text ? safeJson(text) : null
  if (!res.ok) {
    const message = data?.detail || defaultMessage(res.status)
    throw new ApiError(res.status, message, data?.errors || {})
  }
  return data
}

function safeJson(text) {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function defaultMessage(status) {
  if (status === 401) return 'Vui lòng đăng nhập'
  if (status === 403) return 'Bạn không có quyền thực hiện thao tác này'
  if (status === 404) return 'Không tìm thấy dữ liệu'
  if (status >= 500) return 'Máy chủ đang gặp sự cố, vui lòng thử lại sau'
  return 'Đã có lỗi xảy ra'
}

function query(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '' || value === false) return
    if (Array.isArray(value)) value.forEach((v) => search.append(key, v))
    else search.append(key, value)
  })
  const s = search.toString()
  return s ? `?${s}` : ''
}

export const api = {
  // Auth
  me: () => request('GET', '/api/auth/me'),
  login: (email, password) => request('POST', '/api/auth/login', { email, password }),
  register: (fullName, email, password) => request('POST', '/api/auth/register', { fullName, email, password }),
  logout: async () => {
    await request('POST', '/api/auth/logout')
    await ensureCsrf(true) // logout clears the token cookie
  },

  // Storefront
  config: () => request('GET', '/api/config'),
  categories: () => request('GET', '/api/categories'),
  products: (params = {}) => request('GET', `/api/products${query(params)}`),
  filters: () => request('GET', '/api/products/filters'),
  product: (slug) => request('GET', `/api/products/${encodeURIComponent(slug)}`),
  related: (slug) => request('GET', `/api/products/${encodeURIComponent(slug)}/related`),
  reviews: (slug, page = 0) => request('GET', `/api/products/${encodeURIComponent(slug)}/reviews${query({ page })}`),
  reviewEligibility: (slug) => request('GET', `/api/products/${encodeURIComponent(slug)}/reviews/eligibility`),
  addReview: (slug, rating, comment) =>
    request('POST', `/api/products/${encodeURIComponent(slug)}/reviews`, { rating, comment }),

  // Orders
  placeOrder: (order) => request('POST', '/api/orders', order),
  myOrders: (page = 0) => request('GET', `/api/orders${query({ page })}`),
  myOrder: (id) => request('GET', `/api/orders/${id}`),
  cancelOrder: (id) => request('POST', `/api/orders/${id}/cancel`),

  // Admin
  admin: {
    stats: () => request('GET', '/api/admin/stats'),
    products: (params = {}) => request('GET', `/api/admin/products${query(params)}`),
    product: (id) => request('GET', `/api/admin/products/${id}`),
    createProduct: (data) => request('POST', '/api/admin/products', data),
    updateProduct: (id, data) => request('PUT', `/api/admin/products/${id}`, data),
    hideProduct: (id) => request('DELETE', `/api/admin/products/${id}`),
    createCategory: (data) => request('POST', '/api/admin/categories', data),
    updateCategory: (id, data) => request('PUT', `/api/admin/categories/${id}`, data),
    deleteCategory: (id) => request('DELETE', `/api/admin/categories/${id}`),
    orders: (params = {}) => request('GET', `/api/admin/orders${query(params)}`),
    order: (id) => request('GET', `/api/admin/orders/${id}`),
    updateOrderStatus: (id, status) => request('PATCH', `/api/admin/orders/${id}/status`, { status }),
    inventory: {
      items: (params = {}) => request('GET', `/api/admin/inventory/items${query(params)}`),
      summary: () => request('GET', '/api/admin/inventory/summary'),
      updateSettings: (variantId, sku, lowStockThreshold) =>
        request('PATCH', `/api/admin/inventory/items/${variantId}`, { sku, lowStockThreshold }),
      movements: (params = {}) => request('GET', `/api/admin/inventory/movements${query(params)}`),
      adjust: (data) => request('POST', '/api/admin/inventory/adjustments', data),
      receipts: (page = 0) => request('GET', `/api/admin/inventory/receipts${query({ page })}`),
      createReceipt: (data) => request('POST', '/api/admin/inventory/receipts', data),
    },
    reports: (days) => request('GET', `/api/admin/reports${query({ days })}`),
    users: (params = {}) => request('GET', `/api/admin/users${query(params)}`),
    updateAccess: (id, role, permissions) => request('PUT', `/api/admin/users/${id}/access`, { role, permissions }),
  },
}
