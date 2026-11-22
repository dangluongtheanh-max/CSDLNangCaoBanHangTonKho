# PHÂN HỆ CƠ SỞ DỮ LIỆU HƯỚNG ĐỐI TƯỢNG (OODB) & TRUY VẤN OQL

Thư mục này chứa toàn bộ sản phẩm thiết kế và mã nguồn thực nghiệm cho phân hệ **CSDL Hướng đối tượng**, đáp ứng chuẩn đầu ra tại **Mục II.4** và yêu cầu tại **Bước 6b** của đề tài đồ án CSDL Nâng cao (*Đề tài 2: Quản lý Bán hàng & Tồn kho Đa kho*).

---

## 1. Cấu trúc thư mục `CSDL Hướng đối tượng/`

| STT | Vị trí | Tên file / thư mục | Định dạng | Mục đích & Nội dung chi tiết |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `src/` | [`01_odl_schema.odl`](../src/01_odl_schema.odl) | ODMG ODL | Lược đồ đặc tả hướng đối tượng chuẩn ODMG 3.0 cho **07 lớp**: `KhachHang`, `DonHang`, `ChiTietDonHang`, `SanPham`, `ChiTietSP`, `PhieuKho`, `PhieuXuatKho`. Thể hiện đủ 5 đặc trưng: **OID**, **Kế thừa**, **Collection**, **Relationship/Inverse**, **Methods**. |
| 2 | `src/` | [`03_oql_queries.oql`](../src/03_oql_queries.oql) | ODMG OQL | **06 truy vấn OQL chuẩn** (vượt chuẩn tối thiểu 05 câu), bao quát đủ các kỹ thuật nâng cao: Path Expression, Collection Traversal, Method Invocation, Group By/Aggregate, Inheritance, Struct Projection. Kèm **bảng kết quả mong đợi chi tiết**. |
| 3 | `src/` | [`objectdb_demo/`](../src/objectdb_demo/) | Java / Maven | Dự án Maven độc lập chạy thực nghiệm trên hệ quản trị **ObjectDB** (JPA 2.2 / JDK 17+), nạp dữ liệu (`DataSeeder`), thực thi tự động 06 câu JPQL (`OqlExecutor`) và bộ kiểm thử tự động JUnit 5 (`OodbFeaturesTest` - `9/9` ca PASS). |
| 4 | `Tài liệu/` | [`OODB_ClassDiagram.drawio`](OODB_ClassDiagram.drawio) | Draw.io XML | Sơ đồ lớp hướng đối tượng UML thể hiện 7 lớp, mối quan hệ và thuộc tính/phương thức. |
| 5 | `Tài liệu/` | [`02_sample_objects.md`](02_sample_objects.md) | Markdown | Danh sách đồ thị đối tượng mẫu với OID (`#kh01`, `#dh01`, `#sp01`...), ánh xạ chính xác và khớp 100% với bộ dữ liệu mẫu trong `CSDL quan hệ/src/02_insert_data.sql`. |
| 6 | `Tài liệu/` | [`04_oql_vs_jpql_mapping.md`](04_oql_vs_jpql_mapping.md) | Markdown | Báo cáo so sánh lý thuyết giữa **OQL chuẩn ODMG** và **JPQL/JDOQL của ObjectDB**, kèm **bảng ánh xạ 1-1** cả 6 câu truy vấn sang ngôn ngữ thực thi theo đúng lưu ý của đề tài. |

---

## 2. 5 Đặc trưng Hướng đối tượng được Thể hiện

1. **Định danh đối tượng (OID - Object Identifier):**
   * Mọi đối tượng đều sở hữu một OID bất biến độc lập với dữ liệu thuộc tính nghiệp vụ.
   * Truy vấn đối tượng trả về con trỏ OID, cho phép duyệt đồ thị tham chiếu trong suốt.
2. **Kế thừa & Đa hình (Inheritance & Polymorphism):**
   * Lớp con `PhieuXuatKho` kế thừa từ lớp cha `PhieuKho` (`class PhieuXuatKho extends PhieuKho`).
   * Truy vấn trên lớp con tự động thừa hưởng thuộc tính lớp cha (`maPhieu`, `ngayLap`, `trangThai`).
3. **Tập hợp (Collection Types):**
   * Sử dụng đa dạng các kiểu tập hợp: `set<DonHang>`, `set<ChiTietSP>`, và `list<ChiTietDonHang>` (duy trì thứ tự dòng chi tiết).
4. **Mối quan hệ hai chiều tự duy trì (Bidirectional Relationship with Inverse):**
   * Khai báo liên kết trực tiếp bằng `relationship ... inverse ...` giữa `DonHang <-> KhachHang`, `DonHang <-> ChiTietDonHang <-> ChiTietSP`, và `DonHang <-> PhieuXuatKho`.
   * Thay thế hoàn toàn cơ chế khóa ngoại (Foreign Key) dạng giá trị vô hướng trong CSDL quan hệ.
5. **Phương thức nghiệp vụ nội tại (Encapsulated Methods):**
   * `DonHang::tinhTongTien()`, `DonHang::tinhTongSoLuong()`.
   * `KhachHang::tinhTongChiTieu()`, `KhachHang::demDonHoanTat()`.
   * `ChiTietDonHang::tinhThanhTien()`.
   * `SanPham::demSoBienTheDangBan()`.
   * `PhieuKho::laPhieuHoanTat()`, `PhieuXuatKho::laXuatGiaoDonHang()`.

---

## 3. Hướng dẫn Chạy Thực nghiệm & Kiểm thử ObjectDB

### A. Chạy Demo thực thi 06 truy vấn OQL/JPQL (`OqlExecutor`)
- **Cách 1 (1-Click):** Mở thư mục `CSDL Hướng đối tượng/src/objectdb_demo` và chạy [`run_demo.bat`](../src/objectdb_demo/run_demo.bat).
- **Cách 2 (Maven Terminal):**
  ```powershell
  cd "<thư-mục-đồ-án>\CSDL Hướng đối tượng\src\objectdb_demo"
  mvn compile exec:java
  ```
- **Kết quả đầu ra:** Toàn bộ kết quả 06 câu truy vấn và đối chiếu phương thức được in ra Console và ghi tại `CSDL Hướng đối tượng/src/objectdb_demo/target/execution_output.txt` (cùng tệp dữ liệu `target/csdl_giay.odb`).

### B. Chạy Bộ kiểm thử tự động JUnit 5 (`OodbFeaturesTest`)
- **Cách 1 (1-Click):** Mở thư mục `CSDL Hướng đối tượng/src/objectdb_demo` và chạy [`run_tests.bat`](../src/objectdb_demo/run_tests.bat).
- **Cách 2 (Maven Terminal):**
  ```powershell
  cd "<thư-mục-đồ-án>\CSDL Hướng đối tượng\src\objectdb_demo"
  mvn test
  ```
- **Phạm vi kiểm thử (`9/9` ca PASS):** Số lượng bản ghi `DataSeeder` & định danh `OID`, 06 câu truy vấn OQL/JPQL khớp bảng kết quả mong đợi, tính kế thừa & đa hình (`PhieuXuatKho extends PhieuKho`), liên kết 2 chiều (`Set` / `List`), toàn bộ phương thức đóng gói trên 7 lớp Entity và giao dịch `ROLLBACK` ACID (dùng tệp tạm `target/test_csdl_giay.odb` và tự xóa sau khi chạy). Báo cáo chi tiết lưu tại `CSDL Hướng đối tượng/src/objectdb_demo/target/surefire-reports/`.

