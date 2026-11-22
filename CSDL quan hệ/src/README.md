# Hướng dẫn Quy trình Làm việc & Chạy Thực nghiệm CSDL Quan hệ (PostgreSQL 17)

Tài liệu này hướng dẫn cách sử dụng **cùng một bộ file `.sql` gốc trên ổ đĩa** giữa trình soạn thảo mã nguồn và **pgAdmin 4 / DBeaver** (nơi kết nối PostgreSQL 17 và thực thi SQL), đảm bảo không sinh ra bản sao trùng lặp.

---

## 1. Danh sách các file mã nguồn trong `CSDL quan hệ/src/`

| STT | Tên file | Đường dẫn tương đối | Vai trò |
| :--- | :--- | :--- | :--- |
| 1 | `01_create_tables.sql` | `CSDL quan hệ/src/01_create_tables.sql` | Khởi tạo 18 bảng và toàn bộ ràng buộc PK, FK, UNIQUE, CHECK, DEFAULT |
| 2 | `02_insert_data.sql` | `CSDL quan hệ/src/02_insert_data.sql` | Dữ liệu mẫu ngành giày cho 18 bảng |
| 3 | `03_queries.sql` | `CSDL quan hệ/src/03_queries.sql` | 12 câu truy vấn dữ liệu (JOIN, Group By, Subquery, Window Function, Recursive CTE) |
| 4 | `04_functions_procedures.sql` | `CSDL quan hệ/src/04_functions_procedures.sql` | 3 Function, 4 Procedure và Dynamic SQL (`fn_bao_cao_doanh_thu_dong`) |
| 5 | `05_triggers.sql` | `CSDL quan hệ/src/05_triggers.sql` | 8 nhóm Trigger bảo toàn dữ liệu (tồn kho, ISA rời rạc, chống chu trình, trả/đổi hàng) |
| 6 | `06_transactions.sql` | `CSDL quan hệ/src/06_transactions.sql` | 6 kịch bản kiểm thử Giao dịch (`COMMIT`, `ROLLBACK`, `SAVEPOINT`, Concurrency, Lock Ordering) |
| 7 | `07_indexes.sql` | `CSDL quan hệ/src/07_indexes.sql` | 18 B-Tree, Composite `DESC` & Covering Index (`INCLUDE`) |
| 8 | `08_benchmark_explain.sql` | `CSDL quan hệ/src/08_benchmark_explain.sql` | Đo hiệu năng `EXPLAIN (ANALYZE, BUFFERS)` Trước/Sau Index (50.000 đơn, tự dọn sạch `'BM_%'`) |
| 9 | `embedded_sql_demo/` | `CSDL quan hệ/src/embedded_sql_demo` | Ứng dụng Java JDBC (`EmbeddedSqlDemo`, `WebDemoServer` trực quan Web) & bộ kiểm thử JUnit 5 (`DatabaseFeaturesTest.java`) |

---

## 2. Chỉnh sửa và lưu file SQL

1. Mở trực tiếp các file trong thư mục `CSDL quan hệ/src/` trên IDE/trình soạn thảo.
2. Sau khi chỉnh sửa xong, nhấn **`Ctrl + S`** để ghi thay đổi xuống ổ đĩa trước khi chuyển sang pgAdmin 4 hoặc DBeaver chạy lại.

---

## 3. Cấu hình kết nối PostgreSQL trong pgAdmin 4 / DBeaver

Theo cấu hình tại [`docker-compose.yml`](../../docker-compose.yml), thông số kết nối PostgreSQL 17 như sau:

- **Host name / address:** `localhost` *(hoặc `127.0.0.1`)*
- **Port:** `5433`
- **Database (Maintenance database):** `ql_banhang`
- **Username:** `postgres`
- **Password:** `123456`

*(Nếu container chưa chạy, khởi động Docker Desktop và chạy `docker compose up -d` tại thư mục gốc đồ án.)*

---

## 4. Mở trực tiếp file SQL gốc trong pgAdmin 4 / DBeaver (Không tạo bản sao)

> **Lưu ý quan trọng:** Không tạo script mới rồi copy-paste code vào, hãy mở trực tiếp file từ thư mục `CSDL quan hệ/src/` để luôn đồng bộ một nguồn duy nhất.

### Cách 1: Trong pgAdmin 4
1. Mở rộng `Servers -> ... -> Databases -> ql_banhang`, bấm biểu tượng **Query Tool**.
2. Bấm biểu tượng **Open File** (📁) trên thanh công cụ của Query Tool và chọn trực tiếp các file trong `CSDL quan hệ/src/`.

### Cách 2: Trong DBeaver (Open File hoặc Link Folder)
1. Chọn **File -> Open File...** (`Ctrl + O`) tới `CSDL quan hệ/src/`, hoặc trong tab **Projects** chuột phải vào **Scripts -> Link Folder...** trỏ tới `CSDL quan hệ/src/`.

---

## 5. Chọn PostgreSQL Connection và Database cho file SQL trong DBeaver

Khi mở file `.sql` từ ổ đĩa bằng `File -> Open File...`, thanh công cụ phía trên trình soạn thảo SQL của DBeaver có 2 ô chọn hiển thị `<N/A>`:

1. **Chọn Connection (Active Datasource):**
   - Nhấp vào ô `<N/A>` đầu tiên trên thanh công cụ của tab SQL (hoặc nhấn **`Ctrl + 9`**).
   - Chọn kết nối **`postgres - ql_banhang (localhost:5433)`**.
2. **Chọn Database & Schema (Active Schema):**
   - Nhấp vào ô bên cạnh và chọn **`ql_banhang -> public`**.

---

## 6. Thực thi câu lệnh hoặc toàn bộ Script trong DBeaver

- **Chạy 1 câu lệnh đơn lẻ** (tại vị trí con trỏ chuột hoặc đoạn code đang bôi đen):
  - Nhấn **`Ctrl + Enter`** (hoặc nút ▶ **Execute SQL Statement** ở lề trái trình soạn thảo).
- **Chạy toàn bộ file SQL từ đầu đến cuối:**
  - Nhấn **`Alt + X`** (hoặc nút 📜▶ **Execute SQL Script**).

### Thứ tự chạy các file chuẩn khi khởi tạo từ đầu:
1. `01_create_tables.sql` (`Alt + X`) — Tạo 18 bảng và ràng buộc.
2. `05_triggers.sql` (`Alt + X`) — Cài đặt 8 nhóm Trigger kiểm soát dữ liệu.
3. `04_functions_procedures.sql` (`Alt + X`) — Cài đặt Function, Procedure, Dynamic SQL.
4. `02_insert_data.sql` (`Alt + X`) — Nạp bộ dữ liệu mẫu ngành giày.
5. `07_indexes.sql` (`Alt + X`) — Tạo 18 Index tối ưu truy vấn (`B-Tree`, `Composite DESC`, `INCLUDE` Covering Index).
6. `03_queries.sql` — Bôi đen từng câu truy vấn và nhấn `Ctrl + Enter` để xem từng bảng kết quả riêng biệt.
7. `06_transactions.sql` (`Alt + X`) — Kiểm thử `COMMIT`, `ROLLBACK`, `SAVEPOINT`, xử lý đồng thời & tự dọn sạch dữ liệu test.
8. `08_benchmark_explain.sql` (`Alt + X` khi bật Auto-commit, hoặc chạy `psql -f 08_benchmark_explain.sql`) — Thực nghiệm `EXPLAIN (ANALYZE, BUFFERS)` Trước vs. Sau Index và tự động xóa dữ liệu benchmark `'BM_%'`.

### Chạy Demo Embedded SQL, Mini Web Dashboard & Bộ kiểm thử JUnit 5 (Java JDBC):
- Mở terminal tại `CSDL quan hệ/src/embedded_sql_demo` (hoặc bấm chạy trực tiếp các file `.bat`):
  - **Chạy Mini Web Demo trực quan (PostgreSQL 17 + ObjectDB tại `http://127.0.0.1:8080`):**
    ```bat
    run_web_demo.bat
    ```
    *(Hoặc `mvn compile exec:java -Dexec.mainClass=com.csdl.rdb.WebDemoServer`. Đóng tab trình duyệt để server tự tắt sau ~2 giây, hoặc nhấn `Ctrl + C` / Stop trong IDE.)*
  - **Chạy Demo Embedded SQL & Concurrency 2 luồng thật (Console):**
    ```bat
    run_embedded_demo.bat
    ```
    *(Hoặc `mvn compile exec:java`. Kết quả lưu tại `embedded_sql_demo/target/embedded_sql_output.txt`.)*
  - **Chạy Bộ kiểm thử JUnit 5 toàn bộ chức năng CSDL (18 bảng, 12 queries, 7 Function/Procedure, 8 nhóm Trigger, 18 Index, Concurrency):**
    ```bat
    run_tests.bat
    ```
    *(Hoặc `mvn test`. Báo cáo kết quả lưu tại `embedded_sql_demo/target/surefire-reports/`.)*
- Mặc định kết nối `jdbc:postgresql://localhost:5433/ql_banhang` (`postgres` / `123456`). Có thể đổi qua biến môi trường `PG_URL`, `PG_USER`, `PG_PASS` (hoặc `-Dpg.url=... -Dpg.user=... -Dpg.pass=...` khi chạy `mvn test`).

---

## 7. Tải lại (Reload) nội dung file trong DBeaver sau khi sửa ở Antigravity

Khi bạn sửa file trong Antigravity và bấm **`Ctrl + S`**, file trên ổ đĩa sẽ thay đổi:

1. **Tự động nhận diện khi chuyển cửa sổ:**
   - Ngay khi bạn click sang tab file đó trong DBeaver, DBeaver sẽ hiện thông báo:
     > *"The file '...' has been changed on the file system. Do you want to replace the editor contents with these changes?"*
   - Chọn **Yes** để DBeaver nạp ngay nội dung mới nhất từ Antigravity.
2. **Tải lại thủ công (nếu hộp thoại không tự bật):**
   - Nhấp vào vùng soạn thảo của file trong DBeaver và nhấn **`F5`**, hoặc vào menu **File -> Revert** (nếu lỡ gõ nhầm trong DBeaver và muốn lấy lại đúng bản đã lưu trên đĩa của Antigravity).
3. **Nguyên tắc tránh xung đột ghi đè:**
   - Luôn chỉ chỉnh sửa và nhấn `Ctrl + S` ở **một nơi tại một thời điểm** (ưu tiên sửa trên Antigravity, lưu `Ctrl + S`, sau đó sang DBeaver chọn **Yes** / `F5` để chạy).
   - **Không** nhấn `Ctrl + S` trong DBeaver trước khi DBeaver đã tải lại nội dung mới từ Antigravity, tránh việc bản cũ trên bộ nhớ đệm của DBeaver ghi đè ngược lên ổ đĩa.
