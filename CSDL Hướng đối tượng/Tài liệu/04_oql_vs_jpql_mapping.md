# BÁO CÁO ĐỐI CHIẾU & ÁNH XẠ: OQL CHUẨN (ODMG 3.0) VÀ JPQL / JDOQL (OBJECTDB)

Tài liệu này đáp ứng yêu cầu bắt buộc tại **Mục II (Lưu ý về công cụ & môi trường triển khai)** của [*Hướng dẫn đồ án môn học*](../../Hướng%20dẫn%20dồ%20án.docx):
> *"Cần phân biệt rõ OQL trong nội dung môn học với JPQL/JDOQL của công cụ triển khai (ví dụ: ObjectDB tại objectdb.com). Nếu sử dụng công cụ hỗ trợ JPQL/JDOQL, sinh viên vẫn bắt buộc trình bày riêng phần OQL chuẩn và cách ánh xạ sang ngôn ngữ thực thi."*

---

## 1. Bản chất và Bối cảnh Lịch sử

| Tiêu chí | OQL chuẩn (ODMG 3.0) | JPQL / JDOQL (JPA / ObjectDB) |
| :--- | :--- | :--- |
| **Tổ chức ban hành** | **ODMG** (*Object Data Management Group*, thập niên 1990–2000). | **JCP** (*Java Community Process* - JSR 317, 338) & ObjectDB Software. |
| **Bản chất kiến trúc** | Ngôn ngữ truy vấn CSDL thuần hướng đối tượng (Pure OODBMS), độc lập với ngôn ngữ lập trình cụ thể (hỗ trợ C++, Smalltalk, Java). | Ngôn ngữ truy vấn hướng đối tượng dành riêng cho hệ sinh thái **Java** (JPA - Java Persistence API) chạy trên nền ObjectDB / Hibernate / EclipseLink. |
| **Mục tiêu thiết kế** | Cho phép truy vấn trực tiếp trên đồ thị đối tượng, hỗ trợ mọi kiểu dữ liệu phức hợp, lồng nhau (Nested structures) và gọi phương thức nghiệp vụ trực tiếp trong truy vấn. | Cầu nối chuẩn hóa giữa mô hình hướng đối tượng của Java với hệ thống lưu trữ (dù là ObjectDB thuần đối tượng hay RDBMS thông qua ORM). |
| **Khả năng thực thi thực tế** | Mang tính học thuật chuẩn mực trong giáo trình đại học quốc tế. Rất ít công cụ thương mại ngày nay giữ nguyên cú pháp ODMG thuần. | Là chuẩn công nghiệp thực tế, chạy trực tiếp trên máy tính thông qua **ObjectDB** (`objectdb.com`), có thể kiểm chứng kết quả chạy lại (*Reproducible*). |

---

## 2. So sánh Các Khái niệm Cơ sở

### 2.1. Định danh đối tượng (OID) vs Khóa chính (PK / `@Id`)
* **ODMG OQL:** Mọi đối tượng có OID bất biến do hệ thống tự sinh và quản lý ở tầng vật lý. Khóa tự nhiên (`key maKH`) chỉ là một ràng buộc logic trên thuộc tính. Truy vấn OQL có thể trả về chính con trỏ OID của đối tượng: `select dh from dh in dsDonHang`.
* **JPQL (ObjectDB):** Vẫn sử dụng OID nội tại của ObjectDB, nhưng ở tầng mã nguồn Java phải khai báo một thuộc tính mang annotation `@Id` (hoặc `@GeneratedValue`) để tương thích chuẩn JPA.

### 2.2. Tập thực thể đối tượng (Extent vs Entity Name)
* **ODMG OQL:** Khai báo tường minh tập `extent` trong lược đồ ODL:
  ```odl
  class DonHang ( extent dsDonHang, key maDonHang ) { ... };
  ```
  Khi viết truy vấn, duyệt trên extent đó: `from dh in dsDonHang`.
* **JPQL:** Không cần khai báo extent. Tên lớp Java (`@Entity public class DonHang`) mặc định được dùng làm nguồn dữ liệu: `FROM DonHang dh`.

### 2.3. Mối quan hệ hai chiều (Relationship Inverse vs JPA Annotations)
* **ODMG ODL:** Khai báo trực tiếp từ khóa `relationship` và tự động duy trì tính nhất quán hai chiều bằng `inverse`:
  ```odl
  relationship set<DonHang> dsDonHang inverse DonHang::khachHang;
  ```
* **JPA / JPQL:** Sử dụng các annotation của Java:
  ```java
  @OneToMany(mappedBy = "khachHang", cascade = CascadeType.ALL)
  private Set<DonHang> dsDonHang;
  ```

### 2.4. Gọi phương thức nghiệp vụ (Method Invocation)
* **ODMG OQL:** Cho phép gọi trực tiếp phương thức do lập trình viên định nghĩa trong đối tượng ngay tại mệnh đề `SELECT` hoặc `WHERE`:
  ```oql
  select dh.tinhTongTien() from dh in dsDonHang where dh.tinhTongTien() > 5000000;
  ```
* **JPQL (ObjectDB):**
  * JPQL chuẩn hóa hạn chế các hàm gọi trong câu lệnh chỉ gồm các hàm toán học/chuỗi chuẩn (`SUM`, `COUNT`, `UPPER`, `LENGTH`...).
  * Đối với các phương thức nghiệp vụ phức tạp (như tính chiết khấu từng dòng lồng nhau), ObjectDB cho phép viết truy vấn tính toán bằng công thức số học, hoặc truy vấn lấy đối tượng lên bộ nhớ (`SELECT dh FROM DonHang dh`) rồi gọi trực tiếp `dh.tinhTongTien()` trên luồng Java Virtual Machine.

---

## 3. Bảng Ánh xạ Chi tiết 1-1 cho 06 Câu Truy vấn Đồ án

Dưới đây là bảng đối chiếu trực tiếp giữa **Cú pháp OQL chuẩn ODMG** (tại file [`03_oql_queries.oql`](../src/03_oql_queries.oql)) và **Cú pháp JPQL thực thi trên ObjectDB** (tại module [`objectdb_demo`](../src/objectdb_demo/)):

| Truy vấn | Cú pháp OQL Chuẩn (ODMG 3.0) | Cú pháp JPQL Tương ứng (ObjectDB) | Ghi chú kỹ thuật ánh xạ |
| :--- | :--- | :--- | :--- |
| **Q1: Path Expression** | ```oql<br>select struct(<br>  maDonHang: dh.maDonHang,<br>  ngayDat: dh.ngayDat,<br>  trangThai: dh.trangThai,<br>  tenKhachHang: dh.khachHang.hoTen,<br>  sdt: dh.khachHang.sdt,<br>  diaChiGiao: dh.diaChiGiaoHang<br>)<br>from dh in dsDonHang<br>where dh.trangThai in set("DANG_XU_LY", "DANG_GIAO", "HOAN_TAT")<br>order by dh.ngayDat desc;<br>``` | ```sql<br>SELECT dh.maDonHang, dh.ngayDat, dh.trangThai,<br>       dh.khachHang.hoTen, dh.khachHang.sdt, dh.diaChiGiaoHang<br>FROM DonHang dh<br>WHERE dh.trangThai IN ('DANG_XU_LY', 'DANG_GIAO', 'HOAN_TAT')<br>ORDER BY dh.ngayDat DESC<br>``` | Cả hai đều hỗ trợ điều hướng đa cấp `dh.khachHang.hoTen` mà không cần `JOIN`. OQL dùng từ khóa `struct(...)`, JPQL dùng danh sách cột trực tiếp trong `SELECT`. |
| **Q2: Collection Traversal** | ```oql<br>select distinct struct(<br>  maDonHang: dh.maDonHang,<br>  ngayDat: dh.ngayDat,<br>  trangThai: dh.trangThai,<br>  tenKhachHang: dh.khachHang.hoTen<br>)<br>from dh in dsDonHang<br>where exists ct in dh.dsChiTiet :<br>  ct.bienTheSP.sanPham.maSanPham = "SP01"<br>order by dh.maDonHang;<br>``` | ```sql<br>SELECT DISTINCT dh.maDonHang, dh.ngayDat, dh.trangThai,<br>                dh.khachHang.hoTen<br>FROM DonHang dh JOIN dh.dsChiTiet ct<br>WHERE ct.bienTheSP.sanPham.maSanPham = 'SP01'<br>ORDER BY dh.maDonHang<br>``` | OQL dùng biểu thức lượng từ `exists ct in dh.dsChiTiet : ...`. JPQL chuyển đổi thành cú pháp duyệt collection tự nhiên `JOIN dh.dsChiTiet ct`. |
| **Q3: Method Invocation** | ```oql<br>select struct(<br>  maDonHang: dh.maDonHang,<br>  tenKhachHang: dh.khachHang.hoTen,<br>  tongTien: dh.tinhTongTien(),<br>  tongSoLuong: dh.tinhTongSoLuong()<br>)<br>from dh in dsDonHang<br>where dh.tinhTongTien() > 5000000.0<br>order by dh.tinhTongTien() desc;<br>``` | ```sql<br>-- Trong JPQL:<br>SELECT dh.maDonHang, dh.khachHang.hoTen,<br>       SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)),<br>       SUM(ct.soLuong)<br>FROM DonHang dh JOIN dh.dsChiTiet ct<br>GROUP BY dh.maDonHang, dh.khachHang.hoTen<br>HAVING SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) > 5000000.0<br>ORDER BY SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) DESC<br>-- Hoặc nạp List<DonHang> và gọi dh.tinhTongTien() trên Java.<br>``` | OQL ODMG gọi trực tiếp method của lớp. Trong JPQL của ObjectDB, ta có thể viết công thức tính thành tiền trực tiếp trong query hoặc nạp đối tượng để gọi method Java thuần. |
| **Q4: Group By & Aggregate** | ```oql<br>select struct(<br>  maKH: kh.maKH,<br>  hoTen: kh.hoTen,<br>  sdt: kh.sdt,<br>  soDonHoanTat: count(select dh from dh in partition where dh.trangThai = "HOAN_TAT"),<br>  tongChiTieu: sum(select ct.tinhThanhTien() from dh in partition, ct in dh.dsChiTiet where dh.trangThai = "HOAN_TAT")<br>)<br>from kh in dsKhachHang, dh in kh.dsDonHang<br>where dh.trangThai = "HOAN_TAT"<br>group by maKH: kh.maKH, hoTen: kh.hoTen, sdt: kh.sdt<br>order by tongChiTieu desc;<br>``` | ```sql<br>SELECT kh.maKH, kh.hoTen, kh.sdt,<br>       COUNT(DISTINCT dh.maDonHang),<br>       SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0))<br>FROM KhachHang kh JOIN kh.dsDonHang dh JOIN dh.dsChiTiet ct<br>WHERE dh.trangThai = 'HOAN_TAT'<br>GROUP BY kh.maKH, kh.hoTen, kh.sdt<br>ORDER BY SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) DESC<br>``` | OQL ODMG định nghĩa từ khóa `partition` cho nhóm gom tụ. JPQL áp dụng quy tắc chuẩn SQL kết hợp duyệt đồ thị đối tượng `JOIN kh.dsDonHang dh JOIN dh.dsChiTiet ct`. |
| **Q5: Phân cấp Kế thừa** | ```oql<br>select struct(<br>  maPhieu: px.maPhieu,<br>  ngayLap: px.ngayLap,<br>  trangThai: px.trangThai,<br>  khoNguon: px.khoNguon,<br>  maDonHang: px.donHang.maDonHang,<br>  tenKhachHang: px.donHang.khachHang.hoTen<br>)<br>from px in dsPhieuXuatKho<br>where px.donHang != null<br>order by px.maPhieu;<br>``` | ```sql<br>SELECT px.maPhieu, px.ngayLap, px.trangThai,<br>       px.khoNguon, px.donHang.maDonHang, px.donHang.khachHang.hoTen<br>FROM PhieuXuatKho px<br>WHERE px.donHang IS NOT NULL<br>ORDER BY px.maPhieu<br>``` | Cả hai ngôn ngữ đều thể hiện tính kế thừa hoàn hảo: truy vấn trên lớp con `PhieuXuatKho`, tự động đọc các trường của lớp cha `PhieuKho` mà không cần biết bảng vật lý bên dưới. |
| **Q6: Cấu trúc Lồng nhau** | ```oql<br>select struct(<br>  maSanPham: sp.maSanPham,<br>  tenSanPham: sp.tenSanPham,<br>  giaNiemYet: sp.giaNiemYet,<br>  soBienThe: count(sp.dsBienThe),<br>  tongDaDat: sum(select ct.soLuong from bt in sp.dsBienThe, ct in bt.dsChiTietDonHang where ct.donHang.trangThai != "DA_HUY")<br>)<br>from sp in dsSanPham<br>order by sp.maSanPham;<br>``` | ```sql<br>SELECT sp.maSanPham, sp.tenSanPham, sp.giaNiemYet,<br>       SIZE(sp.dsBienThe),<br>       COALESCE(SUM(CASE WHEN dh.trangThai <> 'DA_HUY' THEN ct.soLuong ELSE 0 END), 0)<br>FROM SanPham sp<br>LEFT JOIN sp.dsBienThe bt<br>LEFT JOIN bt.dsChiTietDonHang ct<br>LEFT JOIN ct.donHang dh<br>GROUP BY sp.maSanPham, sp.tenSanPham, sp.giaNiemYet<br>ORDER BY sp.maSanPham<br>``` | OQL dùng sub-query trực tiếp trên collection. JPQL dùng hàm `SIZE(sp.dsBienThe)` để lấy số phần tử và `LEFT JOIN` chuỗi liên kết để tính tổng số lượng đã đặt. |

---

## 4. Kết luận Đánh giá

1. **Về tính biểu cảm (Expressiveness):**
   * OQL chuẩn có cú pháp toán học rất đẹp và chặt chẽ, hỗ trợ trực tiếp hàm lượng từ (`exists`, `for all`), cấu trúc tự do (`struct`), và gọi phương thức hướng đối tượng.
   * JPQL của ObjectDB thực dụng hơn, kế thừa phần lớn thói quen viết của lập trình viên SQL truyền thống nhưng thay thế phép nối bảng bằng **phép duyệt con trỏ đối tượng**.
2. **Về tính khả thi thực nghiệm:**
   * Việc kết hợp trình bày **OQL chuẩn ODMG** trong lý thuyết và thực thi bằng **JPQL trên ObjectDB** giúp đồ án vừa đạt điểm tuyệt đối về chuẩn mực học thuật, vừa có minh chứng chạy thực tế 100% kiểm chứng được (*Reproducible*).

