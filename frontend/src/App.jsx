import { Route, Routes } from 'react-router-dom'
import Layout, { RequireAuth } from './components/Layout.jsx'
import { NotFound, OrderDetailPage, OrdersPage } from './pages/AccountPages.jsx'
import AdminCategories from './pages/admin/AdminCategories.jsx'
import { AdminHome } from './pages/admin/AdminDashboard.jsx'
import AdminInventory from './pages/admin/AdminInventory.jsx'
import AdminLayout from './pages/admin/AdminLayout.jsx'
import AdminOrders from './pages/admin/AdminOrders.jsx'
import AdminProductForm from './pages/admin/AdminProductForm.jsx'
import AdminProducts from './pages/admin/AdminProducts.jsx'
import AdminReports from './pages/admin/AdminReports.jsx'
import AdminUsers from './pages/admin/AdminUsers.jsx'
import { LoginPage, RegisterPage } from './pages/AuthPages.jsx'
import CartPage from './pages/CartPage.jsx'
import Catalog from './pages/Catalog.jsx'
import Checkout from './pages/Checkout.jsx'
import Home from './pages/Home.jsx'
import ProductPage from './pages/ProductPage.jsx'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="san-pham" element={<Catalog />} />
        <Route path="san-pham/:slug" element={<ProductPage />} />
        <Route path="gio-hang" element={<CartPage />} />
        <Route path="thanh-toan" element={<RequireAuth><Checkout /></RequireAuth>} />
        <Route path="tai-khoan/don-hang" element={<RequireAuth><OrdersPage /></RequireAuth>} />
        <Route path="tai-khoan/don-hang/:id" element={<RequireAuth><OrderDetailPage /></RequireAuth>} />
        <Route path="*" element={<NotFound />} />
      </Route>
      <Route path="dang-nhap" element={<LoginPage />} />
      <Route path="dang-ky" element={<RegisterPage />} />
      <Route path="quan-tri" element={<RequireAuth backOffice><AdminLayout /></RequireAuth>}>
        <Route index element={<AdminHome />} />
        <Route path="san-pham" element={<RequireAuth section="PRODUCTS"><AdminProducts /></RequireAuth>} />
        <Route path="san-pham/moi" element={<RequireAuth section="PRODUCTS"><AdminProductForm /></RequireAuth>} />
        <Route path="san-pham/:id" element={<RequireAuth section="PRODUCTS"><AdminProductForm /></RequireAuth>} />
        <Route path="danh-muc" element={<RequireAuth section="CATEGORIES"><AdminCategories /></RequireAuth>} />
        <Route path="don-hang" element={<RequireAuth section="ORDERS"><AdminOrders /></RequireAuth>} />
        <Route path="kho" element={<RequireAuth section="INVENTORY"><AdminInventory /></RequireAuth>} />
        <Route path="bao-cao" element={<RequireAuth section="REPORTS"><AdminReports /></RequireAuth>} />
        <Route path="phan-quyen" element={<RequireAuth adminOnly><AdminUsers /></RequireAuth>} />
      </Route>
    </Routes>
  )
}
