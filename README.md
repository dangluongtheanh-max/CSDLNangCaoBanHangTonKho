# Đồ án Cơ sở dữ liệu Nâng cao — Quản lý Bán hàng & Tồn kho Đa kho (Chuyên ngành Giày)

Hệ thống quản lý bán hàng và tồn kho đa kho được thiết kế và hiện thực song song trên hai phân hệ:
- **CSDL Quan hệ (PostgreSQL 17)** kết hợp **Java 17 (JDBC 42.7.5 + Mini Web Demo HTTP Server + JUnit 5.11.4)**.
- **CSDL Hướng đối tượng (ODMG 3.0 ODL/OQL)** thực nghiệm trên **ObjectDB 2.9.1 (JPA 2.2 + JUnit 5.11.4)**.

---

## Cấu trúc thư mục

```text
.
├── docker-compose.yml                    # Container PostgreSQL 17 (port 5433, DB ql_banhang)
├── CSDL quan hệ/
│   ├── Tài liệu/                         # Sơ đồ EER, RDM (.drawio), Data Dictionary, minh chứng thực nghiệm
│   └── src/
│       ├── 01_create_tables.sql          # 18 bảng và toàn bộ ràng buộc PK, FK, UNIQUE, CHECK, DEFAULT
│       ├── 02_insert_data.sql            # Dữ liệu mẫu ngành giày cho 18 bảng
│       ├── 03_queries.sql                # 12 truy vấn SQL (JOIN, Group By, Subquery, Window Function, Recursive CTE)
│       ├── 04_functions_procedures.sql   # 3 Function, 4 Stored Procedure và Dynamic SQL (fn_bao_cao_doanh_thu_dong)
│       ├── 05_triggers.sql               # 8 nhóm Trigger toàn vẹn (tồn kho, ISA rời rạc, chống chu trình, trả/đổi hàng)
│       ├── 06_transactions.sql           # 6 kịch bản kiểm thử ACID (COMMIT, ROLLBACK, SAVEPOINT, Lock Ordering)
│       ├── 07_indexes.sql                # 18 chỉ mục (B-Tree, Composite DESC, INCLUDE Covering Index)
│       ├── 08_benchmark_explain.sql      # Đo hiệu năng EXPLAIN (ANALYZE, BUFFERS) Trước/Sau Index
│       ├── README.md                     # Hướng dẫn chạy chi tiết trên pgAdmin 4 / DBeaver
│       └── embedded_sql_demo/            # Java JDBC demo, Mini Web Demo & JUnit 5 (DatabaseFeaturesTest)
└── CSDL Hướng đối tượng/
    ├── Tài liệu/                         # OODB_ClassDiagram.drawio, 02_sample_objects.md, 04_oql_vs_jpql_mapping.md
    └── src/
        ├── 01_odl_schema.odl             # Lược đồ 7 lớp chuẩn ODMG 3.0 ODL
        ├── 03_oql_queries.oql            # 06 truy vấn OQL chuẩn (ODMG 3.0)
        └── objectdb_demo/                # Dự án Maven ObjectDB 2.9.1 & JUnit 5 (OodbFeaturesTest)
```

---

## Yêu cầu môi trường

- **Docker Desktop** (hoặc **PostgreSQL 17**)
- **Java JDK 17+**
- **Apache Maven 3.8+**

---

## Hướng dẫn chạy nhanh

### 1. Khởi động PostgreSQL 17 bằng Docker Compose

```powershell
docker compose up -d
```

Thông số kết nối mặc định trong `docker-compose.yml`:
- **Host:** `localhost`
- **Port:** `5433`
- **Database:** `ql_banhang`
- **Username / Password:** `postgres` / `123456`

*(Có thể ghi đè khi chạy Java qua biến môi trường `PG_URL`, `PG_USER`, `PG_PASS` hoặc `-Dpg.url=... -Dpg.user=... -Dpg.pass=...`.)*

### 2. Nạp lược đồ và dữ liệu PostgreSQL

Thực thi lần lượt các script trong `CSDL quan hệ/src/` (trên DBeaver, pgAdmin 4 hoặc `psql`):
1. `01_create_tables.sql`
2. `05_triggers.sql`
3. `04_functions_procedures.sql`
4. `02_insert_data.sql`
5. `07_indexes.sql`
6. Chạy thử `03_queries.sql`, `06_transactions.sql`, `08_benchmark_explain.sql` (xem thêm `CSDL quan hệ/src/README.md`).

### 3. Chạy Mini Web Demo (PostgreSQL 17 + ObjectDB)

Tại thư mục `CSDL quan hệ/src/embedded_sql_demo`:

```powershell
# Windows 1-Click
.\run_web_demo.bat

# Hoặc dùng Maven
mvn compile exec:java -Dexec.mainClass=com.csdl.rdb.WebDemoServer
```

Sau khi server khởi động, mở trình duyệt tại **`http://127.0.0.1:8080`**.

### 4. Chạy Demo & Kiểm thử tự động (JUnit 5)

- **Phân hệ CSDL Quan hệ (`CSDL quan hệ/src/embedded_sql_demo`):**
  - Chạy demo Embedded SQL & tương tranh 2 luồng: `.\run_embedded_demo.bat` hoặc `mvn compile exec:java`
  - Chạy bộ kiểm thử tự động: `.\run_tests.bat` hoặc `mvn test`
- **Phân hệ CSDL Hướng đối tượng (`CSDL Hướng đối tượng/src/objectdb_demo`):**
  - Chạy demo 06 truy vấn OQL/JPQL trên ObjectDB: `.\run_demo.bat` hoặc `mvn compile exec:java`
  - Chạy bộ kiểm thử tự động (`9/9` ca kiểm thử OODB): `.\run_tests.bat` hoặc `mvn test`
