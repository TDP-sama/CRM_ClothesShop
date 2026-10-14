# MODA – Website bán quần áo

Website bán hàng thời trang: khách xem và đặt hàng, cửa hàng quản lý sản phẩm, đơn hàng, kho và doanh thu.

🌐 **Xem trực tiếp:** https://moda-shop-535e.onrender.com
<sub>(Gói miễn phí của Render tạm ngủ khi không có truy cập, lần mở đầu có thể chờ khoảng 1 phút.)</sub>

> Dự án có sử dụng **[Claude Code](https://claude.com/claude-code)** hỗ trợ.

## Tính năng

**Khách hàng**
- Tìm kiếm không dấu, lọc theo danh mục, size, màu, giá, khuyến mãi
- Chọn màu/size theo tồn kho, giỏ hàng, đặt hàng thanh toán khi nhận (COD)
- Theo dõi và hủy đơn, đánh giá sản phẩm đã mua
- 4 giao diện chuyển đổi được: Cơ bản, Tối giản, Năng động, Cao cấp

**Quản trị** (`/quan-tri`)
- Quản lý sản phẩm, danh mục, đơn hàng
- Kho hàng: tồn kho theo SKU, phiếu nhập, kiểm kê, lịch sử xuất nhập
- Báo cáo doanh thu, sản phẩm bán chạy, doanh thu theo danh mục
- Phân quyền nhân viên theo từng mục

## Công nghệ

Java 21 · Spring Boot 4 · Spring Security · JPA · React 19 · Vite · PostgreSQL (Neon) · Docker · Render

## Chạy trên máy

Cần JDK 21, Maven, Node.js 22 và một CSDL PostgreSQL.

1. Tạo `backend/.env` (không commit file này):
   ```properties
   DATABASE_URL=jdbc:postgresql://<host>/<db>?user=<user>&password=<password>&sslmode=require
   ADMIN_EMAILS=email-cua-ban@example.com
   ```
2. Backend (cổng 8080, tự tạo bảng và dữ liệu mẫu):
   ```bash
   mvn -f backend/pom.xml spring-boot:run
   ```
3. Frontend (cổng 5173):
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
4. Mở http://localhost:5173, đăng ký bằng email trong `ADMIN_EMAILS` để có quyền quản trị.

Kiểm thử API: Tài liệu API: http://localhost:8080/swagger-ui.html

