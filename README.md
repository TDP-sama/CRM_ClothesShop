# MODA – Website bán quần áo

Website thương mại điện tử cho một cửa hàng thời trang: khách xem và mua hàng, cửa hàng quản lý sản phẩm, đơn hàng, kho và theo dõi doanh thu.

> Dự án có sử dụng **[Claude Code](https://claude.com/claude-code)** hỗ trợ trong quá trình phát triển.

## Tính năng

### Cho khách hàng
- **Trang chủ**: banner, danh mục, sản phẩm nổi bật, hàng mới về, khuyến mãi.
- **Danh sách sản phẩm**: tìm kiếm (gõ không dấu vẫn tìm được, ví dụ `ao khoac` → "Áo khoác"), lọc theo danh mục, size, màu, khoảng giá, hàng giảm giá; sắp xếp và phân trang.
- **Chi tiết sản phẩm**: bộ ảnh, chọn màu và size theo tồn kho thực tế, sản phẩm liên quan.
- **Giỏ hàng** lưu trên trình duyệt, báo còn bao nhiêu thì được miễn phí vận chuyển (đơn từ 500.000₫).
- **Đặt hàng thanh toán khi nhận (COD)**, xem lịch sử và hủy đơn đang chờ xác nhận.
- **Đánh giá sản phẩm**: chỉ khách đã nhận hàng mới được đánh giá, mỗi sản phẩm một lần.
- **4 giao diện** chuyển đổi bằng nút Cài đặt: Cơ bản, Tối giản, Năng động, Cao cấp (nền tối).

### Cho cửa hàng (trang quản trị `/quan-tri`)
- **Tổng quan**: doanh thu, đơn chờ xác nhận, mẫu sắp hết hàng.
- **Sản phẩm & danh mục**: thêm/sửa/ẩn sản phẩm, biến thể size × màu, ảnh, giá khuyến mãi.
- **Đơn hàng**: lọc theo trạng thái, chuyển trạng thái Chờ xác nhận → Đã xác nhận → Đang giao → Đã giao, hoặc hủy (tự hoàn kho).
- **Kho hàng**: tồn kho theo SKU, cảnh báo sắp hết, phiếu nhập kho, điều chỉnh/kiểm kê, lịch sử xuất nhập (thẻ kho) ghi lại mọi thay đổi kèm chứng từ và người thực hiện.
- **Báo cáo**: doanh thu theo ngày/tháng, sản phẩm bán chạy, doanh thu theo danh mục, so sánh với kỳ trước.
- **Phân quyền**: quản trị viên cấp quyền cho nhân viên vào từng mục (Tổng quan, Sản phẩm, Danh mục, Đơn hàng, Kho hàng, Báo cáo); thay đổi có hiệu lực ngay.

## Công nghệ

| Phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Security 7, Spring Data JPA (Hibernate), Maven |
| Frontend | React 19, React Router 7, Vite; biểu đồ vẽ bằng SVG/CSS, không dùng thư viện UI |
| Cơ sở dữ liệu | PostgreSQL trên [Neon](https://neon.tech) (test dùng H2 in-memory) |
| Triển khai | Docker, [Render](https://render.com) |

**Bảo mật**: đăng nhập bằng session cookie (HttpOnly), chống CSRF bằng cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN`, mật khẩu băm BCrypt, quyền quản trị kiểm tra trên CSDL ở mỗi request.

## Cấu trúc thư mục

```
backend/    API Spring Boot (package com.example.shop)
  auth/       đăng ký, đăng nhập, vai trò và phân quyền
  catalog/    danh mục, sản phẩm, biến thể; dữ liệu mẫu
  order/      giỏ hàng → đơn hàng, trạng thái đơn
  inventory/  tồn kho, phiếu nhập, thẻ kho
  report/     báo cáo doanh thu
  review/     đánh giá sản phẩm
frontend/   Giao diện React (cửa hàng + trang quản trị)
Dockerfile  Đóng gói frontend + backend thành một ứng dụng
render.yaml Cấu hình triển khai trên Render
```

## Chạy trên máy

**Cần có**: JDK 21, Maven 3.9+, Node.js 22, một CSDL PostgreSQL (ví dụ Neon miễn phí).

1. Tạo file `backend/.env` (đã được Git bỏ qua, không bao giờ commit file này):
   ```properties
   DATABASE_URL=jdbc:postgresql://<host>/<db>?user=<user>&password=<password>&sslmode=require
   ADMIN_EMAILS=email-cua-ban@example.com
   ```
2. Chạy backend (cổng 8080). Lần đầu chạy sẽ tự tạo bảng và thêm dữ liệu mẫu:
   ```bash
   mvn -f backend/pom.xml spring-boot:run
   ```
3. Chạy frontend (cổng 5173, tự chuyển `/api` sang backend):
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
4. Mở http://localhost:5173. Đăng ký bằng email trong `ADMIN_EMAILS` để có quyền quản trị.

Tài liệu API (Swagger): http://localhost:8080/swagger-ui.html

### Biến môi trường

| Biến | Bắt buộc | Ý nghĩa |
|---|---|---|
| `DATABASE_URL` | Có | Chuỗi kết nối JDBC, gồm cả tài khoản và mật khẩu CSDL |
| `ADMIN_EMAILS` | Không | Danh sách email (cách nhau bằng dấu phẩy) được cấp quyền quản trị viên |
| `SESSION_COOKIE_SECURE` | Không | Đặt `true` khi chạy sau HTTPS (mặc định `false` cho máy local) |
| `SESSION_TIMEOUT` | Không | Thời gian giữ đăng nhập, mặc định `2h` |
| `SEED_DEMO_DATA` | Không | Thêm sản phẩm mẫu khi CSDL trống, mặc định `true` |

## Kiểm thử

```bash
mvn -f backend/pom.xml test
```

Test tích hợp chạy trên H2 in-memory, không đụng tới CSDL thật: đăng nhập và CSRF, phân quyền nhân viên, đặt hàng và tồn kho, kho hàng, báo cáo.

## Triển khai lên Render

Repo có sẵn `Dockerfile` và `render.yaml`:

1. Trên Render chọn **New → Blueprint** và kết nối repo này.
2. Nhập `DATABASE_URL` và `ADMIN_EMAILS` khi được hỏi (`SESSION_COOKIE_SECURE=true` đã khai báo sẵn).
3. Mỗi lần push lên nhánh `main`, Render tự build và deploy lại.

Gói miễn phí của Render tạm ngủ sau 15 phút không có truy cập; lần mở tiếp theo cần chờ khoảng một phút.

---

Dự án luyện tập. Có sử dụng Claude Code hỗ trợ.
