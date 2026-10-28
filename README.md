# Sportshop Backend

REST API Java 21 + Spring Boot 3.5.7, Spring Security, JWT HS256, JPA, Flyway. Backend là repo Git riêng, không phụ thuộc source frontend khi chạy API.

## Chạy nhanh bằng Docker

1. Mở Docker Desktop, chờ Docker Engine sẵn sàng.
2. Sao chép .env.example thành .env và thay các giá trị mật khẩu/secret.
3. Chạy các lệnh dưới trong repo backend.

```powershell
Copy-Item .env.example .env
# Sửa .env trước khi chạy; ADMIN_PASSWORD từ 12 đến 64 ký tự.
docker compose up --build
```

API: http://localhost:8080/api/products. Health: http://localhost:8080/api/health.

Chạy cả frontend nếu hai repo là thư mục cạnh nhau:

```sh
docker compose -f compose.yaml -f compose.full.yaml up --build
```

Giao diện: http://localhost:3000. API cũng đi qua http://localhost:3000/api. Database PostgreSQL không publish cổng ra máy; dữ liệu nằm trong named volume. docker compose down giữ dữ liệu; thêm -v sẽ xóa volume dữ liệu. Lần khởi động đầu backend có thể cần vài giây để hoàn tất migration trước khi frontend gọi API thành công.

## Chạy bằng Java, không cần Docker

Cài JDK 21 và đặt JAVA_HOME. Maven Wrapper có sẵn; lần đầu cần internet để tải Maven/dependencies.

```powershell
$env:ADMIN_EMAIL = 'admin@sportshop.local'
$env:ADMIN_PASSWORD = 'ThayBangMatKhauRieng123!'
.\mvnw.cmd spring-boot:run
```

macOS/Linux: dùng ./mvnw spring-boot:run và export biến môi trường tương ứng. Mặc định dùng H2 file tại data/sportshop; không cần cài database. PostgreSQL được dùng trong Docker. Dữ liệu H2 và PostgreSQL là hai môi trường riêng, không tự đồng bộ.

.env chỉ được Docker Compose đọc; chạy Java trực tiếp cần đặt environment variables hoặc cấu hình IDE. Khi JWT_SECRET trống, ứng dụng tự tạo khóa ngẫu nhiên mỗi lần khởi động để phát triển; token cũ hết hiệu lực sau restart. Khi triển khai, đặt JWT_SECRET ngẫu nhiên ít nhất 32 byte và giữ ổn định.

AdminBootstrap chỉ tạo admin khi ADMIN_EMAIL chưa tồn tại; không đổi mật khẩu tài khoản có sẵn và không nâng USER thành ADMIN. Không có tài khoản/mật khẩu admin mặc định trong source. Người mua tự đăng ký qua giao diện.

## Cấu trúc

```text
src/main/java/vn/sportshop/
  auth/       Đăng ký, đăng nhập, thông tin tài khoản; request/response records
  config/     Spring Security, JWT, CORS, tạo admin
  user/       Entity và repository người dùng
  product/    Controller → Service → Repository; request/response records
  order/      Controller → Service → Repository; tính tiền, tồn kho, trạng thái
  common/     Lỗi API và health endpoint
src/main/resources/db/migration/   Schema + 6 sản phẩm mẫu
src/test/                         Kiểm thử tích hợp và phân quyền
```

DTO được khai báo dạng record trong controller/service để khởi đầu gọn; có thể chuyển sang package dto khi tính năng phát triển. Entity không được trả trực tiếp từ API.

## API

Xem [docs/API.md](docs/API.md) để biết endpoint, quyền và ví dụ JSON.

- Đăng ký luôn tạo USER. Trường role hoặc giá/tổng tiền tự gửi lên bị từ chối.
- Mật khẩu băm BCrypt; JWT hết hạn 1 giờ. Bearer token trong Authorization, không dùng cookie phiên nên CSRF bị tắt cho API stateless.
- GET sản phẩm công khai. /api/admin/** chỉ ADMIN. GET /api/orders lọc bằng user ID lấy từ JWT.
- Đặt hàng tính giá ở server, chụp lại tên/giá sản phẩm trong order_item. Khóa dòng sản phẩm theo thứ tự ID và transaction giúp tránh bán quá tồn kho.
- Xóa sản phẩm là ẩn; lịch sử đơn vẫn giữ nguyên.
- PENDING → CONFIRMED → SHIPPING → COMPLETED. Có thể hủy từ PENDING/CONFIRMED và hoàn tồn kho đúng một lần.

## Kiểm thử

```powershell
.\mvnw.cmd verify
```

Kiểm thử dùng H2 in-memory, không tác động dữ liệu chạy thật. Bao gồm quyền admin, chống tự nâng quyền, token sai, đơn của từng user, tính tiền, rollback tồn kho, hủy đơn và mua đồng thời. Mockito agent được khai báo tường minh trong Maven Surefire để không cần tự attach trong lúc chạy test.

## Giới hạn cần phát triển tiếp

Bản khởi đầu dùng COD, một phiên bản cho mỗi sản phẩm. Chưa có thanh toán trực tuyến, email xác nhận, quên mật khẩu, refresh/revoke token, rate limiting, phân trang, idempotency key chống đặt trùng khi retry, phí vận chuyển và audit log. Khi dùng thật cần HTTPS, quản lý secret, backup, giới hạn đăng nhập và kiểm thử PostgreSQL trong môi trường triển khai. Token đã phát hành vẫn dùng được đến hết hạn ngay cả khi người dùng đăng xuất phía frontend.

## Git

Đã khởi tạo repo local trên nhánh main. Đẩy đến remote rỗng do bạn tạo:

```sh
git remote add origin <URL-REPO-BACKEND>
git push -u origin main
```

Tài liệu nền tảng: [Spring Boot](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Vite](https://vite.dev/guide/).
