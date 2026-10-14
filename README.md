# MODA – Website bán quần áo

Website bán hàng thời trang: khách xem và đặt hàng, cửa hàng quản lý sản phẩm, đơn hàng, kho và doanh thu.

🌐 **Xem trực tiếp:** https://moda-shop-535e.onrender.com
<sub>(Gói miễn phí của Render tạm ngủ khi không có truy cập, lần mở đầu có thể chờ khoảng 1 phút.)</sub>

> Dự án có sử dụng **[Claude Code](https://claude.com/claude-code)** hỗ trợ.

## Tính năng

**Khách hàng**
- Tìm kiếm không dấu, lọc theo danh mục, size, màu, giá, khuyến mãi
- Chọn màu/size theo tồn kho, giỏ hàng, đặt hàng thanh toán khi nhận (COD)
- Theo dõi và hủy đơn, nhận email xác nhận và cập nhật trạng thái đơn, đánh giá sản phẩm đã mua
- 4 giao diện chuyển đổi được: Cơ bản, Tối giản, Năng động, Cao cấp
- Đăng nhập bằng Google (qua Keycloak, khi chạy trên máy)

**Quản trị** (`/quan-tri`)
- Quản lý sản phẩm, danh mục, đơn hàng; nhập/xuất sản phẩm bằng Excel
- Kho hàng: tồn kho theo SKU, phiếu nhập, kiểm kê, lịch sử xuất nhập
- Báo cáo doanh thu, sản phẩm bán chạy, doanh thu theo danh mục
- Phân quyền nhân viên theo từng mục

## Công nghệ

Java 21 · Spring Boot 4 · Spring Security · JPA · React 19 · Vite · PostgreSQL (Neon) · Redis · Mailpit · Docker · Render

## Chạy trên máy

Cần JDK 21, Maven, Node.js 22 và một CSDL PostgreSQL.

1. Copy `backend/.env.example` thành `backend/.env` và điền `DATABASE_URL`, `ADMIN_EMAILS` (không commit file `.env`).
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

### Dịch vụ phụ trợ (Docker, tùy chọn)

`docker compose up -d` chạy **Redis** (lưu phiên đăng nhập), **Mailpit** (hộp thư giả nhận email đơn hàng, xem tại http://localhost:8025) và **Keycloak** (đăng nhập bằng Google, quản trị tại http://localhost:8180). Bật trong `backend/.env` bằng các dòng mẫu có sẵn trong `.env.example`:
- `spring.profiles.active=redis,keycloak`
- `spring.mail.host=localhost` và `spring.mail.port=1025`

Đăng nhập bằng Google cần thêm một lần thiết lập: tạo OAuth Client trên Google Cloud Console với redirect URI `http://localhost:8180/realms/moda/broker/google/endpoint`, rồi trong Keycloak (realm **moda**) vào **Identity providers → Google**, nhập Client ID/Secret và bật **Trust email**.

Không bật thì web vẫn chạy bình thường: phiên lưu trong bộ nhớ, không gửi email, không có nút Google.

