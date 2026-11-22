# Minh Chứng Thực Nghiệm Hệ Quản Trị CSDL Quan Hệ (PostgreSQL)

Tài liệu này tổng hợp kết quả thực thi thực tế của bộ mã nguồn tại [CSDL quan hệ/src/](../src/) nhằm đáp ứng **Mục II.3 (Embedded SQL, Dynamic SQL, Concurrency)**, **Mục II.6 (Indexing & Tối ưu truy vấn)**, **Bước 5–6a** và **Mục IV (Minh chứng bắt buộc)** trong `Hướng dẫn dồ án.docx` cho Đề tài 2 (*Quản lý Bán hàng & Tồn kho Đa kho – Chuyên ngành Giày*).

---

## 1. Xác Nhận Thực Thi Toàn Bộ Kịch Bản SQL & JDBC

Toàn bộ các script và ứng dụng minh họa đã được kiểm thử nối tiếp bằng `psql` (cờ `-v ON_ERROR_STOP=1`) và Maven + PostgreSQL JDBC `42.7.5` trên PostgreSQL (mã thoát `0` ở tất cả các bước):

| Thứ tự | Thành phần thực thi | Kết quả xác nhận | Ghi chú khôi phục dữ liệu mẫu |
| :--- | :--- | :--- | :--- |
| 1 | [01_create_tables.sql](../src/01_create_tables.sql) | `CREATE TABLE` x 18 bảng thành công (`rc=0`) | Bao gồm đầy đủ PK, FK, `UNIQUE`, `CHECK`, `DEFAULT` |
| 2 | [05_triggers.sql](../src/05_triggers.sql) | 8 nhóm Trigger + Function tạo thành công (`rc=0`) | Tự động bảo vệ tồn kho, ISA, chu trình, trả/đổi hàng |
| 3 | [04_functions_procedures.sql](../src/04_functions_procedures.sql) | 3 Function + 4 Procedure tạo thành công (`rc=0`) | Bao gồm `sp_tao_don_hang`, `sp_chuyen_kho`, `fn_bao_cao_doanh_thu_dong` |
| 4 | [02_insert_data.sql](../src/02_insert_data.sql) | Nạp sạch dữ liệu mẫu 18 bảng (`rc=0`) | Baseline: `10` `DONHANG`, `18` `CHITIET_DONHANG`, `40` `TONKHO` |
| 5 | [03_queries.sql](../src/03_queries.sql) | Toàn bộ 12 câu truy vấn chạy thành công (`rc=0`) | Câu 12 (Recursive CTE) trả về đủ 11 nút cây danh mục |
| 6 | [06_transactions.sql](../src/06_transactions.sql) | 6 kịch bản giao dịch & đồng thời chạy thành công (`rc=0`) | Tự cleanup về đúng `10` `DONHANG`, `40` `TONKHO` |
| 7 | [07_indexes.sql](../src/07_indexes.sql) | Tạo đủ `18` Index bổ sung + `ANALYZE` (`rc=0`) | Xác minh `COUNT(*) = 18` trên `pg_indexes` (`idx_%`) |
| 8 | [08_benchmark_explain.sql](../src/08_benchmark_explain.sql) | Benchmark `30.000` KH / `50.000` đơn / `100.000` CTĐT Trước & Sau Index (`rc=0`) | Tự xóa sạch `'BM_%'` -> giữ nguyên `8` KH, `10` `DONHANG`, `40` `TONKHO` |
| 9 | [EmbeddedSqlDemo.java](../src/embedded_sql_demo/src/main/java/com/csdl/rdb/EmbeddedSqlDemo.java) | Maven build & chạy 4 Demo (`rc=0`, Java 17, JDBC `42.7.5`) | Tự hủy & xóa `DH_DEMO_%`, `DH_CONC_%` -> `(KHO01,CTSP02)=23`, `(KHO01,CTSP18)=2` |
| 10 | [DatabaseFeaturesTest.java](../src/embedded_sql_demo/src/test/java/com/csdl/rdb/DatabaseFeaturesTest.java) | Bộ JUnit 5 (`mvn test` trên Docker PG 17): **`15/15` ca PASS (`0` lỗi, `1.508s`)** | Phủ trọn 18 bảng, 12 queries, 7 Function/Procedure (`sp_chuyen_kho`...), 8 nhóm Trigger, 18 Index, Savepoint & Concurrency; tự `rollback()` / cleanup giữ nguyên baseline |

---

## 2. Minh Chứng 1 (Mục IV.1): Ràng Buộc Tồn Kho Không Âm

Hệ thống áp dụng cơ chế phòng thủ **3 lớp** tại PostgreSQL để bảo đảm số lượng tồn kho và khả dụng không bao giờ âm hoặc bị giữ vượt quá thực tế:

1. **Lớp Ràng buộc Bảng (`CHECK` trong [01_create_tables.sql](../src/01_create_tables.sql#L152-L154)):**
   - `chk_tonkho_sl_thuc_te CHECK (SoLuongThucTe >= 0)`
   - `chk_tonkho_sl_da_giu CHECK (SoLuongDaGiu >= 0)`
   - `chk_tonkho_da_giu_hop_le CHECK (SoLuongDaGiu <= SoLuongThucTe)`
2. **Lớp Trigger ([05_triggers.sql](../src/05_triggers.sql#L46-L100)):** `trg_01_tonkho_khong_am` (`fn_trg_tonkho_khong_am`) và `trg_02_tonkho_kiem_tra_da_giu` (`fn_trg_tonkho_kiem_tra_da_giu`) chặn trước mọi thao tác `INSERT`/`UPDATE` làm âm tồn hoặc làm `SoLuongDaGiu > SoLuongThucTe` với mã `ERRCODE = 'check_violation'`.
3. **Lớp Thủ tục Nghiệp vụ (`sp_tao_don_hang` trong [04_functions_procedures.sql](../src/04_functions_procedures.sql#L162-L175)):** Khóa dòng `TONKHO ... FOR UPDATE`, kiểm tra `(SoLuongThucTe - SoLuongDaGiu) < v_item.so_luong` và ném ngoại lệ `RAISE EXCEPTION`.

### Kết quả thực nghiệm (Kịch bản 2 trong `06_transactions.sql`)
Đơn hàng `DH12` có 2 dòng chi tiết: dòng 1 giữ `2` đôi `CTSP02` (hợp lệ, tồn `23`), dòng 2 cố tình giữ `10` đôi `CTSP18` khi tồn thực tế tại `KHO01` chỉ có `2`:

```text
NOTICE:  [KỊCH BẢN 2 - TRƯỚC GIAO DỊCH] CTSP02 tại KHO01 có SoLuongDaGiu = 0
NOTICE:  [KỊCH BẢN 2 - GIỮA GIAO DỊCH] Đã tạo DH12 & giữ 2 đôi CTSP02 (SoLuongDaGiu tạm thời tăng lên = 2)
NOTICE:  [KỊCH BẢN 2 - PHÁT HIỆN LỖI & ROLLBACK] Hệ thống chặn lỗi: Lỗi tồn kho tại (KHO01, CTSP18): SoLuongDaGiu (10) vượt quá SoLuongThucTe (2).
NOTICE:  [KỊCH BẢN 2 - ĐÃ ROLLBACK] Toàn bộ thao tác tạo DH12, CTDH21 và giữ 2 đôi CTSP02 đã được hoàn tác!
```

Kiểm tra sau `ROLLBACK`:
- `sodonhang_dh12_tontai = 0`, `sochitiet_dh12_tontai = 0`
- `(KHO01, CTSP02)`: `SoLuongDaGiu = 0`, `SoLuongKhaDung = 23` (không giữ hàng ảo)
- `(KHO01, CTSP18)`: `SoLuongThucTe = 2`, `SoLuongDaGiu = 0`, `SoLuongKhaDung = 2` (không âm)

---

## 3. Minh Chứng 2 (Mục IV.2 & Bước 5): Toàn Vẹn Giao Dịch & Xử Lý Đồng Thời 2 Luồng Thật

### 3.1. Kết quả kiểm thử SQL thuần (`06_transactions.sql`)
- **Kịch bản 1 (`COMMIT`):** Tạo đơn `DH11` (`5,907,500.00`), giữ thành công `1` đôi `CTSP01` (`SoLuongKhaDung`: `27 -> 26`) và `1` đôi `CTSP07` (`15 -> 14`).
- **Kịch bản 2 (`ROLLBACK`):** Toàn bộ thao tác trung gian (`DH12`, `CTDH21`, giữ `2` đôi `CTSP02`) bị hoàn tác khi dòng thứ 2 lỗi tồn kho.
- **Kịch bản 3 (`SAVEPOINT`):** Tạo `DH13` gồm `CTSP03`, đặt `SAVEPOINT sp_them_dong_2`, chọn nhầm `CTSP18` vượt tồn (hoặc thêm nhầm dòng), thực hiện `ROLLBACK TO SAVEPOINT sp_them_dong_2`, đổi sang `CTSP08`, `RELEASE SAVEPOINT` và `COMMIT` -> `DH13` lưu đúng 2 dòng `CTSP03` và `CTSP08`.
- **Kịch bản 4 (Giao dịch phức hợp):** Lập phiếu trả `PT05` đổi `CTSP13` size `42` còn mới (`CON_MOI`) sang `CTSP12` size `41` (nhập trả `PK19` + xuất đổi `PK20`, cập nhật đồng bộ tồn kho `CTSP13` tăng `1` và `CTSP12` giảm `1`).
- **Kịch bản 5 & 6 (Đồng thời & Chống Deadlock):** Phiên A mua `2` đôi cuối `CTSP18` -> `SoLuongKhaDung = 0`; Phiên B yêu cầu `1` đôi bị chặn (`Không đủ tồn khả dụng cho CTSP18 tại kho KHO01 (cần 1, còn 0)`). Khóa nhiều mặt hàng luôn sắp theo `ORDER BY tk.MaKho ASC, tk.MaChiTietSP ASC FOR UPDATE`.

### 3.2. Kết quả thực nghiệm Embedded SQL + Concurrency 2 Luồng Thật (Java JDBC)

Chương trình [EmbeddedSqlDemo.java](../src/embedded_sql_demo/src/main/java/com/csdl/rdb/EmbeddedSqlDemo.java) kết nối trực tiếp PostgreSQL qua JDBC Driver `42.7.5`, chứng minh đủ 4 kỹ thuật:
1. **Parameterized SQL (`PreparedStatement`):** Truy vấn biến thể tồn kho và kiểm thử đầu vào độc hại `"KH01' OR '1'='1"` -> trả về `0` dòng (chống SQL Injection).
2. **Dynamic SQL (`fn_bao_cao_doanh_thu_dong`):** Gọi báo cáo động lọc theo `KHO01` và lọc theo danh mục cha `DM01` (kích hoạt Recursive CTE bên trong hàm).
3. **Transaction `COMMIT` & `ROLLBACK` qua JDBC:** Tạo `DH_DEMO_OK` giảm tồn khả dụng `(KHO01, CTSP02)` từ `23 -> 22`, sau đó gọi `DH_DEMO_FAIL` đặt `999` đôi `CTSP18` nhận `SQLState=P0001` và thực hiện `conn.rollback()`.
4. **Concurrency 2 Thread Song Song Thật (`CountDownLatch`, `TRANSACTION_READ_COMMITTED`, 2 `Connection` độc lập):**

Log chạy thực tế thu được từ `mvn clean compile exec:java`:

```text
--------------------------------------------------------------------------------
[DEMO 4] TWO-THREAD CONCURRENCY (2 JDBC Connections, READ COMMITTED, FOR UPDATE)
--------------------------------------------------------------------------------
Trang thai ban dau (KHO01, CTSP18): KhaDung = 2
[18:58:32.814] [Thread-A] Bat dau giao dich, dat mua 2 doi CTSP18 (DH_CONC_A)...
[18:58:32.819] [Thread-A] Da giu lock FOR UPDATE tren TONKHO(KHO01, CTSP18). Giu lock 1500ms...
[18:58:32.921] [Thread-B] Bat dau giao dich, co dat mua 1 doi CTSP18 (DH_CONC_B)...
[18:58:34.326] [Thread-A] Da COMMIT DH_CONC_A! (Giai phong row lock).
[18:58:34.327] [Thread-B] Bi BLOCK cho lock trong 1405 ms -> nhan loi tu PostgreSQL: ERROR: Không đủ tồn khả dụng cho CTSP18 tại kho KHO01 (cần 1, còn 0).
[18:58:34.328] [Thread-B] Da ROLLBACK giao dich DH_CONC_B (Chong overselling thanh cong).
Ket qua sau 2 Thread: DH_CONC_A=true | DH_CONC_B=false | Ton kha dung CTSP18=0 (khong am!)

[CLEANUP] Khoi phuc du lieu mau goc...
Trang thai sau cleanup: DONHANG=10 dong, TONKHO=40 dong | (KHO01,CTSP02)=23 | (KHO01,CTSP18)=2
```

**Nhận xét cơ chế đồng thời:** `Thread-B` gửi yêu cầu lúc `18:58:32.921`, bị PostgreSQL giữ ở trạng thái **chờ Row-Level Lock (`FOR UPDATE`) trong đúng `1405 ms`** cho tới khi `Thread-A` `COMMIT` vào `18:58:34.326`. Dưới mức cô lập `READ COMMITTED`, ngay khi tỉnh dậy, `Thread-B` đọc phiên bản dữ liệu mới nhất vừa commit của `Thread-A` (`SoLuongKhaDung = 0`), lập tức bị `sp_tao_don_hang` chặn lại và `ROLLBACK`, ngăn triệt để lỗi *Lost Update / Overselling*.

---

## 4. Minh Chứng 3 (Mục II.6, Bước 6a & Mục IV.3): Tối Ưu Index & Phân Tích `EXPLAIN (ANALYZE, BUFFERS)`

### 4.1. Chiến lược thiết kế 18 Index bổ sung ([07_indexes.sql](../src/07_indexes.sql))

Ngoài các B-Tree Index mặc định cho Khóa chính (`PK`) và `UNIQUE`, hệ thống bổ sung `18` Index chiến lược theo 4 nhóm truy cập:
- **Composite B-Tree theo hướng lọc & sắp xếp:** `idx_donhang_kh_ngaydat` trên `DONHANG(MaKH, NgayDat DESC)` (`2008 kB`), `idx_donhang_kho_trangthai` trên `DONHANG(MaKho, TrangThai, NgayDat DESC)` (`2048 kB`), `idx_donhang_trangthai_ngaydat` (`1960 kB`), `idx_sanpham_danhmuc_thuonghieu`, `idx_chitiet_sp_masanpham`, `idx_phieukho_loai_trangthai_ngay`...
- **Covering Index (`INCLUDE`) chống truy cập Heap dư thừa:** `idx_ctdh_machitietsp_cover` trên `CHITIET_DONHANG(MaChiTietSP) INCLUDE (MaDonHang, SoLuong, DonGiaBan, ChietKhau)` (`4960 kB`).
- **Index tối ưu quản trị Khách hàng quy mô lớn:** `idx_khachhang_ngaydangky` trên `KHACHHANG(NgayDangKy DESC)` (`672 kB`) giúp phân trang danh sách khách hàng mới nhất (`LIMIT 20`) triệt tiêu hoàn toàn bước `Sort`, cùng `idx_khachhang_sdt` trên `KHACHHANG(SDT)` (`936 kB`).
- **Index đảo chiều khóa ngoại & cây đệ quy:** `idx_danhmuc_sp_cha` (tăng tốc `Recursive CTE`), `idx_tonkho_machitietsp_kho`, `idx_cungcap_mancc_ctsp`, `idx_phieutrahang_madonhang`, `idx_chitiet_trahang_mactdh`, `idx_chitiet_phieukho_ctsp` (phục vụ kiểm tra Trigger 5, 7, 8).

### 4.2. So sánh Thực nghiệm `EXPLAIN (ANALYZE, BUFFERS)` Trước vs. Sau Index

Kịch bản [08_benchmark_explain.sql](../src/08_benchmark_explain.sql) (và tab Benchmark trên Web Demo) sinh tập dữ liệu chuẩn hóa tiền tố `'BM_'`: **`30.000`** khách hàng (`30.008` tổng), **`50.000`** đơn hàng (`50.010` tổng), và **`100.000`** dòng chi tiết đơn (`100.018` tổng, tham chiếu `18` biến thể giày thật `CTSP01..CTSP18`).

| Truy vấn thực nghiệm | Trước khi tạo Index (Baseline) | Sau khi tạo Index (`07_indexes.sql` + `ANALYZE`) | Mức cải thiện thực tế |
| :--- | :--- | :--- | :--- |
| **BM1:** Lịch sử đơn của `KH_BM_00042`, `ORDER BY NgayDat DESC` (`50 / 50.010` = `0.10%`) | `Sort` + `Seq Scan on donhang`<br>Cost `1327.50..1327.62` · **`701` buffers** · **`3.018 ms`** | `Sort` + `Bitmap Heap Scan` + `Bitmap Index Scan` (`idx_donhang_kh_ngaydat`)<br>Cost `161.10..161.22` · **`53` buffers** (`50 hit + 3 read`) · **`0.109 ms`** | Nhanh hơn **~27.7x**<br>Giảm **92.4%** số page buffers |
| **BM2:** Báo cáo doanh thu chốt ngày `HOAN_TAT` tại `KHO01` (`2025-02-01`) JOIN `CHITIET_DONHANG` | `GroupAggregate` + `Nested Loop`: `Seq Scan on donhang` (`701` buf) + PK Join `chitiet_donhang` (`215` buf)<br>Cost `2290.65..2292.96` · **`916` buffers** · **`2.966 ms`** | `GroupAggregate` + `Nested Loop`: **`Bitmap Index Scan` (`idx_donhang_kho_trangthai`, `9` buf)** + PK Join `chitiet_donhang`<br>Cost `740.84..743.10` · **`224` buffers** · **`0.260 ms`** | Nhanh hơn **~11.4x**<br>Giảm **75.5%** tổng buffers (riêng `DONHANG`: `701 -> 9` buf) |
| **BM3:** Xem 20 khách hàng mới đăng ký gần nhất (`ORDER BY NgayDangKy DESC LIMIT 20` trên `30.008` KH) | `Sort (top-N heapsort)` + `Seq Scan on khachhang`<br>Cost `1607.58..1607.63` · **`509` buffers** · **`6.981 ms`** | **`Index Scan using idx_khachhang_ngaydangky`** *(dừng sau 20 dòng, bỏ hẳn `Sort`)*<br>Cost `0.29..1.16` · **`4` buffers** (`2 hit + 2 read`) · **`0.030 ms`** | Nhanh hơn **~232.7x**<br>Giảm **99.2%** số page buffers |
| **BM4 (Đối chiếu lý thuyết):** Thống kê theo `TrangThai = 'HOAN_TAT'` (`35.005 / 50.010` = `70%`) | `HashAggregate` + `Seq Scan on donhang`<br>Cost `1501.26..1501.31` · **`701` buffers** · **`6.655 ms`** | `HashAggregate` + **`Seq Scan on donhang`** *(Planner bỏ qua `idx_donhang_trangthai_ngaydat` `1960 kB`)*<br>Cost `1501.14..1501.19` · **`701` buffers** · **`7.128 ms`** | Khớp lý thuyết: độ chọn lọc thấp (`70%`) -> `Seq Scan` rẻ hơn Index |

### 4.3. Kết quả chi tiết `EXPLAIN (ANALYZE, BUFFERS)` và đối chiếu lý thuyết Tối ưu hóa

#### Benchmark 1 (`MaKH = 'KH_BM_00042'`)
- **Trước Index:**
```text
 Sort  (cost=1327.50..1327.62 rows=49 width=58) (actual time=3.004..3.007 rows=50 loops=1)
   Sort Key: ngaydat DESC
   Sort Method: quicksort  Memory: 28kB
   Buffers: shared hit=701
   ->  Seq Scan on donhang dh  (cost=0.00..1326.12 rows=49 width=58) (actual time=0.008..2.992 rows=50 loops=1)
         Filter: ((makh)::text = 'KH_BM_00042'::text)
         Rows Removed by Filter: 49960
         Buffers: shared hit=701
 Execution Time: 3.018 ms
```
- **Sau Index:**
```text
 Sort  (cost=161.10..161.22 rows=49 width=58) (actual time=0.096..0.097 rows=50 loops=1)
   Sort Key: ngaydat DESC
   Sort Method: quicksort  Memory: 28kB
   Buffers: shared hit=50 read=3
   ->  Bitmap Heap Scan on donhang dh  (cost=4.79..159.73 rows=49 width=58) (actual time=0.029..0.080 rows=50 loops=1)
         Recheck Cond: ((makh)::text = 'KH_BM_00042'::text)
         Heap Blocks: exact=50
         Buffers: shared hit=50 read=3
         ->  Bitmap Index Scan on idx_donhang_kh_ngaydat  (cost=0.00..4.78 rows=49 width=0) (actual time=0.021..0.021 rows=50 loops=1)
               Index Cond: ((makh)::text = 'KH_BM_00042'::text)
               Buffers: shared read=3
 Execution Time: 0.109 ms
```
- **Phân tích:** Do 50 đơn của `KH_BM_00042` nằm rải ở 50 trang heap (`Heap Blocks: exact=50`), Planner chọn `Bitmap Index Scan` đọc `3` trang chỉ mục rồi đọc gom trang tuần tự, giảm buffer từ `701` xuống `53` và tăng tốc `27.7` lần.

#### Benchmark 2 (Doanh thu chốt ngày `HOAN_TAT` tại `KHO01` — `JOIN` 100k `CHITIET_DONHANG`)
- **Trước Index:** `Seq Scan on donhang dh` phải duyệt toàn bộ `50.010` đơn (`Rows Removed by Filter: 49967`, tốn `701` buffers riêng bảng `DONHANG`), tổng cả phép `Nested Loop` tốn **`916` buffers** và **`2.966 ms`**.
- **Sau Index:**
```text
 GroupAggregate  (cost=740.84..743.10 rows=1 width=54) (actual time=0.241..0.242 rows=1 loops=1)
   Buffers: shared hit=220 read=4
   ->  Sort  (cost=740.84..741.09 rows=100 width=29) (actual time=0.205..0.208 rows=86 loops=1)
         Sort Key: dh.madonhang
         Sort Method: quicksort  Memory: 29kB
         Buffers: shared hit=220 read=4
         ->  Nested Loop  (cost=5.60..737.51 rows=100 width=29) (actual time=0.039..0.187 rows=86 loops=1)
               Buffers: shared hit=220 read=4
               ->  Bitmap Heap Scan on donhang dh  (cost=5.18..163.31 rows=50 width=17) (actual time=0.031..0.039 rows=43 loops=1)
                     Recheck Cond: (((makho)::text = 'KHO01'::text) AND ((trangthai)::text = 'HOAN_TAT'::text) AND (ngaydat >= '2025-02-01 00:00:00'::timestamp without time zone) AND (ngaydat < '2025-02-02 00:00:00'::timestamp without time zone))
                     Heap Blocks: exact=5
                     Buffers: shared hit=5 read=4
                     ->  Bitmap Index Scan on idx_donhang_kho_trangthai  (cost=0.00..5.17 rows=50 width=0) (actual time=0.027..0.028 rows=43 loops=1)
                           Index Cond: (((makho)::text = 'KHO01'::text) AND ((trangthai)::text = 'HOAN_TAT'::text) AND (ngaydat >= '2025-02-01 00:00:00'::timestamp without time zone) AND (ngaydat < '2025-02-02 00:00:00'::timestamp without time zone))
                           Buffers: shared read=4
               ->  Index Scan using uq_chitiet_donhang_dh_ctsp on chitiet_donhang ctdh  (cost=0.42..11.46 rows=2 width=23) (actual time=0.003..0.003 rows=2 loops=43)
                     Index Cond: ((madonhang)::text = (dh.madonhang)::text)
                     Buffers: shared hit=215
 Execution Time: 0.260 ms
```
- **Phân tích:** Index 3 cột `(MaKho, TrangThai, NgayDat DESC)` cho phép PostgreSQL lọc cùng lúc kho, trạng thái và khoảng ngày ngay trong B-Tree (`701 -> 9` buffers trên `DONHANG`), rồi mới `Nested Loop` sang `CHITIET_DONHANG` lấy `86` dòng chi tiết tương ứng, giảm thời gian tổng `11.4` lần (`2.966 ms -> 0.260 ms`).

#### Benchmark 3 (Top 20 khách hàng mới đăng ký — `idx_khachhang_ngaydangky`)
- **Trước Index:** `Sort (top-N heapsort, 30kB)` + `Seq Scan on khachhang` duyệt hết `30.008` khách hàng, tốn **`509` buffers** và **`6.981 ms`**.
- **Sau Index:**
```text
 Limit  (cost=0.29..1.16 rows=20 width=71) (actual time=0.013..0.018 rows=20 loops=1)
   Buffers: shared hit=2 read=2
   ->  Index Scan using idx_khachhang_ngaydangky on khachhang kh  (cost=0.29..1303.28 rows=30008 width=71) (actual time=0.013..0.016 rows=20 loops=1)
         Buffers: shared hit=2 read=2
 Execution Time: 0.030 ms
```
- **Phân tích:** B-Tree `idx_khachhang_ngaydangky` đã lưu sẵn thứ tự `NgayDangKy DESC`, nên `Limit (20)` chỉ lấy `20` khóa đầu qua `Index Scan` rồi dừng ngay (`4` buffers thay vì `509`), **triệt tiêu hoàn toàn bước `Sort`** và nhanh hơn `232.7` lần.

#### Benchmark 4 (Độ chọn lọc thấp — Cost-Based Optimizer bỏ qua Index)
- **Sau khi đã có `idx_donhang_trangthai_ngaydat` (`1960 kB`):**
```text
 HashAggregate  (cost=1501.14..1501.19 rows=5 width=14) (actual time=7.114..7.115 rows=4 loops=1)
   Group Key: phuongthucthanhtoan
   Batches: 1  Memory Usage: 24kB
   Buffers: shared hit=701
   ->  Seq Scan on donhang dh  (cost=0.00..1326.12 rows=35004 width=6) (actual time=0.004..3.634 rows=35005 loops=1)
         Filter: ((trangthai)::text = 'HOAN_TAT'::text)
         Rows Removed by Filter: 15005
         Buffers: shared hit=701
 Execution Time: 7.128 ms
```
- **Phân tích đối chiếu lý thuyết (cặp so sánh BM2 ↔ BM4):** Khi lọc đơn độc `TrangThai = 'HOAN_TAT'` chiếm `35,005 / 50,010` (`70%`), Optimizer giữ `Seq Scan` (`701` buffers) vì đi qua B-Tree cho 70% bảng tốn I/O ngẫu nhiên hơn đọc tuần tự. Ngược lại ở **BM2**, khi kết hợp thêm `MaKho` và khoảng ngày ngắn (`NgayDat`), độ chọn lọc xuống dưới `0.1%` nên Index phát huy hiệu quả tối đa.

