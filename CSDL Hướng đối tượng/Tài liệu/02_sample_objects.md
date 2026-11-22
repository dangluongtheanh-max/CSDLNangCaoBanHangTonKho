# DANH SÁCH ĐỐI TƯỢNG MẪU PHÂN HỆ OODB (ÁNH XẠ TỪ DỮ LIỆU SQL)

Tài liệu này thể hiện đồ thị đối tượng (**Object Graph**) của phân hệ Bán hàng - Sản phẩm - Xuất kho với **07 lớp**, được ánh xạ trực tiếp và khớp 100% với bộ dữ liệu mẫu trong [`CSDL quan hệ/src/02_insert_data.sql`](../../CSDL%20quan%20h%E1%BB%87/src/02_insert_data.sql).

---

## 1. Nguyên lý biểu diễn đối tượng trong CSDL Hướng đối tượng

1. **Định danh đối tượng (OID - Object Identifier):** Mỗi đối tượng khi khởi tạo được cấp một định danh duy nhất do hệ thống quản trị gán ngầm (ký hiệu dạng `#kh01`, `#dh01`, `#sp01`...). OID độc lập hoàn toàn với giá trị thuộc tính nghiệp vụ.
2. **Không dùng Khóa ngoại (Foreign Key) dạng giá trị vô hướng:** Thay vì lưu chuỗi `'KH01'` trong đơn hàng, đối tượng `DonHang` chứa **con trỏ tham chiếu trực tiếp** `-> #kh01` tới đối tượng `KhachHang`.
3. **Mối quan hệ hai chiều tự duy trì (Relationship / Inverse):** Khi gán `dh.khachHang = kh`, hệ thống OODB tự động cập nhật `kh.dsDonHang` bổ sung thêm con trỏ `-> #dh`.
4. **Phương thức tính toán nội tại (Method):** Các giá trị tổng tiền, thành tiền, số đơn hoàn tất không cần lưu cứng mà được tính toán động qua phương thức của lớp.

---

## 2. Bảng đối tượng mẫu lớp `KhachHang` (`extent dsKhachHang`)

| OID | maKH | hoTen | sdt | email | diaChi | dsDonHang (Con trỏ) | tinhTongChiTieu() (VNĐ) | demDonHoanTat() |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `#kh01` | `KH01` | Nguyễn Minh Tuấn | 0903111222 | tuan.nguyen@gmail.com | 25 Lê Lợi, Q.1, TP.HCM | `[-> #dh01, -> #dh06]` | **16,240,000** | 1 |
| `#kh02` | `KH02` | Trần Thị Thu Hà | 0912222333 | thuha.tran@gmail.com | 14 Trần Duy Hưng, Cầu Giấy, Hà Nội | `[-> #dh02]` | **14,620,000** | 1 |
| `#kh03` | `KH03` | Lê Hoàng Nam | 0988333444 | nam.le@outlook.com | 302 Cách Mạng Tháng 8, Q.3, TP.HCM | `[-> #dh03, -> #dh10]` | **4,465,000** | 1 |
| `#kh04` | `KH04` | Phạm Quốc Bảo | 0935444555 | *(null)* | 52 Bạch Đằng, Hải Châu, Đà Nẵng | `[-> #dh04]` | **6,200,000** | 1 |
| `#kh05` | `KH05` | Võ Ngọc Lan Anh | 0909555666 | lananh.vo@gmail.com | 19 Nguyễn Thị Minh Khai, Q.1, TP.HCM | `[-> #dh05]` | **9,780,000** | 1 |
| `#kh06` | `KH06` | Đặng Văn Hùng | 0977666777 | hung.dang@yahoo.com | 78 Phan Xích Long, Phú Nhuận, TP.HCM | `[-> #dh07]` | 0 *(Đơn chưa HT)* | 0 |
| `#kh07` | `KH07` | Hoàng Thùy Linh | 0966777888 | *(null)* | 105 Kim Mã, Ba Đình, Hà Nội | `[-> #dh08]` | 0 *(Đơn chưa HT)* | 0 |
| `#kh08` | `KH08` | Bùi Đức Thịnh | 0944888999 | thinh.bui@gmail.com | *(null)* | `[-> #dh09]` | 0 *(Đơn chưa HT)* | 0 |

---

## 3. Bảng đối tượng mẫu lớp `SanPham` & `ChiTietSP`

### 3.1. Lớp `SanPham` (`extent dsSanPham`)

| OID | maSanPham | tenSanPham | giaNiemYet | trangThai | dsBienThe (Con trỏ) | demSoBienTheDangBan() |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `#sp01` | `SP01` | Nike Pegasus 41 | 3,200,000 | `DANG_KINH_DOANH` | `[-> #ctsp01, -> #ctsp02, -> #ctsp03]` | 3 |
| `#sp02` | `SP02` | Adidas Ultraboost Light | 3,800,000 | `DANG_KINH_DOANH` | `[-> #ctsp04, -> #ctsp05]` | 2 |
| `#sp03` | `SP03` | Nike LeBron Witness 8 | 2,900,000 | `DANG_KINH_DOANH` | `[-> #ctsp06, -> #ctsp07]` | 2 |
| `#sp04` | `SP04` | Biti's Hunter Street | 950,000 | `DANG_KINH_DOANH` | `[-> #ctsp08, -> #ctsp09]` | 2 |
| `#sp05` | `SP05` | Adidas Predator League TF | 2,150,000 | `DANG_KINH_DOANH` | `[-> #ctsp10, -> #ctsp11]` | 2 |
| `#sp06` | `SP06` | New Balance Fresh Foam Hierro | 3,450,000 | `DANG_KINH_DOANH` | `[-> #ctsp12, -> #ctsp13, -> #ctsp14]` | 3 |
| `#sp07` | `SP07` | Puma Suede Classic | 1,950,000 | `DANG_KINH_DOANH` | `[-> #ctsp15, -> #ctsp16]` | 2 |
| `#sp08` | `SP08` | Biti's Hunter Core Slip-on | 850,000 | `DANG_KINH_DOANH` | `[-> #ctsp17, -> #ctsp18]` | 2 |
| `#sp09` | `SP09` | Puma Velocity Nitro 2 | 2,750,000 | `TAM_NGUNG` | `[-> #ctsp19]` | 0 |
| `#sp10` | `SP10` | Nike Air Max Classic | 2,600,000 | `NGUNG_KINH_DOANH` | `[-> #ctsp20]` | 0 |

### 3.2. Lớp `ChiTietSP` (`extent dsChiTietSP`) (Biến thể theo size/màu)

| OID | maChiTietSP | sku | size | mauSac | trangThai | sanPham (Con trỏ) | dsChiTietDonHang (Con trỏ) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `#ctsp01` | `CTSP01` | NK-PEG41-DEN-41 | 41 | Đen | `DANG_BAN` | `-> #sp01` | `[-> #ctdh01, -> #ctdh13, -> #ctdh15]` |
| `#ctsp02` | `CTSP02` | NK-PEG41-DEN-42 | 42 | Đen | `DANG_BAN` | `-> #sp01` | `[-> #ctdh09, -> #ctdh17]` |
| `#ctsp03` | `CTSP03` | NK-PEG41-TRG-42 | 42 | Trắng | `DANG_BAN` | `-> #sp01` | `[-> #ctdh18]` *(Đơn đã hủy)* |
| `#ctsp04` | `CTSP04` | AD-UBL-TRG-41 | 41 | Trắng | `DANG_BAN` | `-> #sp02` | `[-> #ctdh03]` |
| `#ctsp05` | `CTSP05` | AD-UBL-XNH-42 | 42 | Xanh navy | `DANG_BAN` | `-> #sp02` | `[-> #ctdh11]` |
| `#ctsp06` | `CTSP06` | NK-LBW8-DO-42 | 42 | Đỏ | `DANG_BAN` | `-> #sp03` | `[-> #ctdh05]` |
| `#ctsp07` | `CTSP07` | NK-LBW8-DO-43 | 43 | Đỏ | `DANG_BAN` | `-> #sp03` | `[]` |
| `#ctsp08` | `CTSP08` | BT-HST-XAM-40 | 40 | Xám | `DANG_BAN` | `-> #sp04` | `[-> #ctdh07]` |
| `#ctsp09` | `CTSP09` | BT-HST-XAM-41 | 41 | Xám | `DANG_BAN` | `-> #sp04` | `[]` |
| `#ctsp10` | `CTSP10` | AD-PRD-DEN-40 | 40 | Đen | `DANG_BAN` | `-> #sp05` | `[-> #ctdh08]` |
| `#ctsp11` | `CTSP11` | AD-PRD-DEN-41 | 41 | Đen | `DANG_BAN` | `-> #sp05` | `[]` |
| `#ctsp12` | `CTSP12` | NB-FFH-REU-41 | 41 | Xanh rêu | `DANG_BAN` | `-> #sp06` | `[-> #ctdh02, -> #ctdh16]` |
| `#ctsp13` | `CTSP13` | NB-FFH-REU-42 | 42 | Xanh rêu | `DANG_BAN` | `-> #sp06` | `[-> #ctdh10]` |
| `#ctsp14` | `CTSP14` | NB-FFH-CAM-41 | 41 | Cam đất | `DANG_BAN` | `-> #sp06` | `[-> #ctdh14]` |
| `#ctsp15` | `CTSP15` | PM-SDE-XNH-40 | 40 | Xanh dương | `DANG_BAN` | `-> #sp07` | `[-> #ctdh04]` |
| `#ctsp16` | `CTSP16` | PM-SDE-XNH-41 | 41 | Xanh dương | `DANG_BAN` | `-> #sp07` | `[]` |
| `#ctsp17` | `CTSP17` | BT-SLP-DEN-39 | 39 | Đen | `DANG_BAN` | `-> #sp08` | `[-> #ctdh06, -> #ctdh12]` |
| `#ctsp18` | `CTSP18` | BT-SLP-XAM-40 | 40 | Xám | `DANG_BAN` | `-> #sp08` | `[]` |
| `#ctsp19` | `CTSP19` | PM-VN2-BAC-41 | 41 | Bạc | `TAM_NGUNG` | `-> #sp09` | `[]` |
| `#ctsp20` | `CTSP20` | NK-AMC-TRG-41 | 41 | Trắng | `NGUNG_BAN` | `-> #sp10` | `[]` |

---

## 4. Bảng đối tượng mẫu lớp `DonHang` & `ChiTietDonHang`

### 4.1. Lớp `DonHang` (`extent dsDonHang`)

| OID | maDonHang | ngayDat | trangThai | khachHang (Con trỏ) | dsChiTiet (Con trỏ) | phieuXuat (Con trỏ) | tinhTongTien() (VNĐ) | tinhTongSoLuong() |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `#dh01` | `DH01` | 2026-09-20 10:15:00 | `HOAN_TAT` | `-> #kh01` | `[-> #ctdh01, -> #ctdh02]` | `-> #pk07` | **16,240,000** | 5 |
| `#dh02` | `DH02` | 2026-09-25 15:30:00 | `HOAN_TAT` | `-> #kh02` | `[-> #ctdh03, -> #ctdh04]` | `-> #pk08` | **14,620,000** | 6 |
| `#dh03` | `DH03` | 2026-10-10 09:40:00 | `HOAN_TAT` | `-> #kh03` | `[-> #ctdh05, -> #ctdh06]` | `-> #pk09` | **4,465,000** | 3 |
| `#dh04` | `DH04` | 2026-10-18 14:20:00 | `HOAN_TAT` | `-> #kh04` | `[-> #ctdh07, -> #ctdh08]` | `-> #pk10` | **6,200,000** | 4 |
| `#dh05` | `DH05` | 2026-10-25 11:05:00 | `HOAN_TAT` | `-> #kh05` | `[-> #ctdh09, -> #ctdh10]` | `-> #pk11` | **9,780,000** | 3 |
| `#dh06` | `DH06` | 2026-11-05 16:00:00 | `DANG_GIAO` | `-> #kh01` | `[-> #ctdh11, -> #ctdh12]` | `-> #pk12` | **8,450,000** | 3 |
| `#dh07` | `DH07` | 2026-11-08 10:30:00 | `DA_XAC_NHAN` | `-> #kh06` | `[-> #ctdh13, -> #ctdh14]` | *(null)* | **16,232,500** | 5 |
| `#dh08` | `DH08` | 2026-11-09 09:15:00 | `DANG_XU_LY` | `-> #kh07` | `[-> #ctdh15, -> #ctdh16]` | *(null)* | **17,000,000** | 5 |
| `#dh09` | `DH09` | 2026-11-10 08:45:00 | `CHO_XAC_NHAN` | `-> #kh08` | `[-> #ctdh17]` | *(null)* | **3,200,000** | 1 |
| `#dh10` | `DH10` | 2026-11-02 17:20:00 | `DA_HUY` | `-> #kh03` | `[-> #ctdh18]` | *(null)* | **3,200,000** | 1 |

### 4.2. Lớp `ChiTietDonHang` (`extent dsChiTietDonHang`)

| OID | maCTDonHang | donHang | bienTheSP | soLuong | donGiaBan | chietKhau | tinhThanhTien() (VNĐ) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `#ctdh01` | `CTDH01` | `-> #dh01` | `-> #ctsp01` (Pegasus 41 sz41) | 2 | 3,100,000 | 5% | 5,890,000 |
| `#ctdh02` | `CTDH02` | `-> #dh01` | `-> #ctsp12` (Hierro sz41) | 3 | 3,450,000 | 0% | 10,350,000 |
| `#ctdh03` | `CTDH03` | `-> #dh02` | `-> #ctsp04` (Ultraboost sz41) | 2 | 3,800,000 | 0% | 7,600,000 |
| `#ctdh04` | `CTDH04` | `-> #dh02` | `-> #ctsp15` (Puma Suede sz40) | 4 | 1,950,000 | 10% | 7,020,000 |
| `#ctdh05` | `CTDH05` | `-> #dh03` | `-> #ctsp06` (LeBron 8 sz42) | 1 | 2,850,000 | 0% | 2,850,000 |
| `#ctdh06` | `CTDH06` | `-> #dh03` | `-> #ctsp17` (Biti's Slip-on sz41) | 2 | 850,000 | 5% | 1,615,000 |
| `#ctdh07` | `CTDH07` | `-> #dh04` | `-> #ctsp08` (Biti's Hunter sz40) | 2 | 950,000 | 0% | 1,900,000 |
| `#ctdh08` | `CTDH08` | `-> #dh04` | `-> #ctsp10` (Predator TF sz40) | 2 | 2,150,000 | 0% | 4,300,000 |
| `#ctdh09` | `CTDH09` | `-> #dh05` | `-> #ctsp02` (Pegasus 41 sz42) | 1 | 3,200,000 | 10% | 2,880,000 |
| `#ctdh10` | `CTDH10` | `-> #dh05` | `-> #ctsp13` (Hierro sz42) | 2 | 3,450,000 | 0% | 6,900,000 |
| `#ctdh11` | `CTDH11` | `-> #dh06` | `-> #ctsp05` (Ultraboost sz42) | 2 | 3,800,000 | 0% | 7,600,000 |
| `#ctdh12` | `CTDH12` | `-> #dh06` | `-> #ctsp17` (Biti's Slip-on sz41) | 1 | 850,000 | 0% | 850,000 |
| `#ctdh13` | `CTDH13` | `-> #dh07` | `-> #ctsp01` (Pegasus 41 sz41) | 2 | 3,200,000 | 0% | 6,400,000 |
| `#ctdh14` | `CTDH14` | `-> #dh07` | `-> #ctsp14` (Hierro sz42) | 3 | 3,450,000 | 5% | 9,832,500 |
| `#ctdh15` | `CTDH15` | `-> #dh08` | `-> #ctsp01` (Pegasus 41 sz41) | 1 | 3,200,000 | 0% | 3,200,000 |
| `#ctdh16` | `CTDH16` | `-> #dh08` | `-> #ctsp12` (Hierro sz41) | 4 | 3,450,000 | 0% | 13,800,000 |
| `#ctdh17` | `CTDH17` | `-> #dh09` | `-> #ctsp02` (Pegasus 41 sz42) | 1 | 3,200,000 | 0% | 3,200,000 |
| `#ctdh18` | `CTDH18` | `-> #dh10` | `-> #ctsp03` (Pegasus 41 sz42-Trg)| 1 | 3,200,000 | 0% | 3,200,000 |

---

## 5. Bảng đối tượng mẫu lớp Kế thừa: `PhieuKho` $\leftarrow$ `PhieuXuatKho`

### Lớp `PhieuXuatKho extends PhieuKho` (`extent dsPhieuXuatKho`)

| OID | maPhieu *(Thuộc tính kế thừa)* | ngayLap *(Kế thừa)* | loaiPhieu *(Kế thừa)* | trangThai *(Kế thừa)* | khoNguon | donHang (Con trỏ) | laXuatGiaoDonHang() |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `#pk07` | `PK07` | 2026-09-21 09:00:00 | `XUAT` | `HOAN_TAT` | `KHO01` | `-> #dh01` | `true` |
| `#pk08` | `PK08` | 2026-09-26 09:30:00 | `XUAT` | `HOAN_TAT` | `KHO02` | `-> #dh02` | `true` |
| `#pk09` | `PK09` | 2026-10-11 08:45:00 | `XUAT` | `HOAN_TAT` | `KHO01` | `-> #dh03` | `true` |
| `#pk10` | `PK10` | 2026-10-19 10:15:00 | `XUAT` | `HOAN_TAT` | `KHO03` | `-> #dh04` | `true` |
| `#pk11` | `PK11` | 2026-10-26 09:20:00 | `XUAT` | `HOAN_TAT` | `KHO01` | `-> #dh05` | `true` |
| `#pk12` | `PK12` | 2026-11-06 08:40:00 | `XUAT` | `HOAN_TAT` | `KHO02` | `-> #dh06` | `true` |
| `#pk17` | `PK17` | 2026-09-26 11:00:00 | `XUAT` | `HOAN_TAT` | `KHO01` | *(null - xuất đổi PTH01)* | `false` |

