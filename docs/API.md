# API Sportshop

Base URL: /api. JSON; giá và tổng tiền là VND nguyên. Xác thực bằng Authorization: Bearer <token>.

| Method      | Endpoint                  | Quyền                          |
| ----------- | ------------------------- | ------------------------------ |
| GET         | /health                   | Công khai                      |
| GET         | /products                 | Công khai, chỉ sản phẩm active |
| GET         | /products/{id}            | Công khai, active              |
| POST        | /auth/register            | Công khai                      |
| POST        | /auth/login               | Công khai                      |
| GET         | /auth/me                  | Đã đăng nhập                   |
| POST        | /orders                   | USER hoặc ADMIN                |
| GET         | /orders                   | Chỉ đơn của người gọi          |
| GET, POST   | /admin/products           | ADMIN                          |
| PUT, DELETE | /admin/products/{id}      | ADMIN; DELETE ẩn sản phẩm      |
| GET         | /admin/orders             | ADMIN                          |
| PATCH       | /admin/orders/{id}/status | ADMIN                          |

## Đăng ký

```json
{
  "name": "Nguyễn An",
  "email": "an@example.com",
  "password": "YourPassword123"
}
```

Response 201 gồm token và user: {id, name, email, role}. Login gửi email/password, response cùng cấu trúc. Không gửi role. Email lưu ở dạng chữ thường. 409 nếu email đã tồn tại.

## Sản phẩm (POST/PUT admin)

```json
{
  "name": "Giày chạy bộ",
  "category": "Chạy bộ",
  "description": "Giày nhẹ",
  "price": 1290000,
  "stock": 20,
  "active": true
}
```

GET trả các trường trên cùng id. POST trả 201, PUT 200, DELETE 204.

## Đặt hàng

```json
{
  "recipient": "Nguyễn An",
  "phone": "0901234567",
  "address": "123 Nguyễn Trãi, Hà Nội",
  "items": [{ "productId": 1, "quantity": 2 }]
}
```

Không gửi userId, giá hoặc tổng tiền. Backend lấy người dùng từ token, giá từ DB. Mỗi sản phẩm tối đa 99 đơn vị, tối đa 50 dòng; dòng trùng sản phẩm được gộp. Response 201 gồm id, recipient, phone, address, total, status, createdAt và items [{productId,name,unitPrice,quantity}]. Thiếu tồn kho trả 400 và rollback toàn bộ đơn.

## Đổi trạng thái

```json
{ "status": "CONFIRMED" }
```

PENDING → CONFIRMED/CANCELLED; CONFIRMED → SHIPPING/CANCELLED; SHIPPING → COMPLETED. Chuyển sai trả 400.

## Lỗi

400: dữ liệu/trạng thái không hợp lệ. 401: thiếu hoặc sai xác thực. 403: thiếu quyền. 404: không tìm thấy. 409: dữ liệu xung đột.

Lỗi nghiệp vụ có {"message":"..."}; validation có message là mảng. Lỗi từ security filter có thể không có body; FE xử lý theo HTTP status. API không chấp nhận trường JSON ngoài hợp đồng.
