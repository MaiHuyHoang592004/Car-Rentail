# Frontend ↔ Backend: BFF vs direct rewrite

> Quy ước phân chia request giữa BFF (Next.js route handlers chạy server-side trong FE process) và direct passthrough tới Spring backend.

Current local dev assumption:
- Frontend dev server: `http://localhost:3000`
- Backend local profile: `http://localhost:8087`

## Tóm tắt

Mọi request từ browser đều bắt đầu bằng prefix `/api/v1/...` (xem `frontend/src/lib/api-client.ts:3`). Có 2 luồng phía sau prefix đó:

```
                                 ┌───────────────────────────────┐
  /api/auth/login        ───────▶│ Next.js route handler (BFF)   │──▶ Spring backend
  /api/auth/register             │ - đọc/ghi httpOnly cookies     │   (callBackend)
  /api/auth/refresh              │   rentflow_refresh             │
  /api/auth/logout               │   rentflow_role                │
  /api/auth/session              │ - không bao giờ trả refresh    │
                                 │   token cho client             │
                                 └───────────────────────────────┘

  /api/v1/listings       ───────▶┌───────────────────────────────┐
  /api/v1/bookings               │ next.config.ts rewrite         │──▶ Spring backend
  /api/v1/vehicles               │ /api/v1/* → API_BACKEND_URL    │   (direct)
  /api/v1/users/me               │ (default http://localhost:8087)│
  /api/v1/*                      └───────────────────────────────┘
                                          ▲
                                          │ Authorization: Bearer <accessToken>
                                          │ (access token giữ trong memory,
                                          │  không bao giờ vào storage/cookie)
```

## Quy ước

### Đi qua BFF (`src/app/api/auth/*/route.ts`)
Bất kỳ endpoint nào cần một trong:
- Đọc hoặc ghi cookie `httpOnly` (refresh token, role companion).
- Sử dụng secret server-side (signing key, internal API token).
- Cần chuyển đổi/aggregate nhiều backend call thành 1 response.

Hiện tại chỉ có 5 endpoint thuộc nhóm này:
- `POST /api/auth/login` — gọi backend `/auth/login`, set `rentflow_refresh` + `rentflow_role`.
- `POST /api/auth/register` — gọi register → login, set 2 cookie.
- `POST /api/auth/refresh` — đọc refresh cookie, gọi backend `/auth/refresh`, sau đó gọi `/users/me` bằng access token mới để rotate `rentflow_refresh` và đồng bộ lại `rentflow_role`.
- `POST /api/auth/logout` — gọi backend `/auth/logout`, clear cả 2 cookie.
- `GET /api/auth/session` — đọc refresh cookie, refresh + `GET /users/me`, set lại 2 cookie. Dùng khi app khởi động để khôi phục session.

`/api/auth/refresh` và `/api/auth/session` là hai điểm đồng bộ role cookie với backend sau khi có access token mới. Nếu refresh thành công nhưng lookup `/users/me` fail, cả `rentflow_refresh` và `rentflow_role` đều bị clear để tránh giữ session drift.

### Đi qua rewrite (direct)
Tất cả endpoint còn lại. `next.config.ts:6-12` định nghĩa:
```ts
rewrites: () => [{ source: "/api/v1/:path*", destination: `${API_BACKEND_URL}/api/v1/:path*` }]
```
- Browser tự gắn `Authorization: Bearer <accessToken>` (xem `api-client.ts:62-66`).
- Khi 401 → `apiFetch` retry sau khi gọi BFF `/api/auth/refresh` (xem `api-client.ts:84-92` + `auth-context.tsx`).
- Runtime auth state cho các direct API call được cấp bởi active client do `AuthProvider` sở hữu; feature APIs vẫn import `api` từ `api-client.ts`, nhưng token getter / refresh handler không còn là module-global mutable state.
- Không có Next.js server-side code nằm giữa — request chỉ đi qua Next dev server / Edge proxy.

## Khi nào thêm endpoint vào BFF

Mặc định: **không**. Để rewrite cho nhẹ. Chỉ chuyển sang BFF nếu thỏa một trong các tiêu chí ở mục "Đi qua BFF" phía trên.

Nếu chỉ cần biến đổi response shape → làm trong `features/<x>/api.ts` ở client side; không cần BFF.

## Production deployment

- BFF route handlers chạy như serverless function / Node process cùng với FE.
- Rewrite được resolved ở edge (Vercel) hoặc proxy (Nginx). Trong môi trường self-host cần đảm bảo cấu hình proxy giống `next.config.ts` rewrite.
- `API_BACKEND_URL` phải trỏ tới backend internal address — browser **không** gọi trực tiếp domain backend (CORS hiện đang allowlist FE origin, xem `SecurityConfig`).
- Trong local dev, nếu không override `API_BACKEND_URL`, tài liệu current-state giả định backend đang chạy ở `http://localhost:8087`.
