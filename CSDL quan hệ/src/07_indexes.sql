-- =============================================================================
-- ĐỒ ÁN CƠ SỞ DỮ LIỆU NÂNG CAO - ĐỀ TÀI 2
-- Hệ thống Quản lý Bán hàng & Tồn kho Đa kho (Chuyên ngành Giày)
-- Hệ quản trị CSDL: PostgreSQL 17
-- File: 07_indexes.sql
-- Mô tả: Tạo hệ thống chỉ mục (Index) bổ sung cho các bảng nghiệp vụ nhằm tăng
--        tốc độ truy vấn (03_queries.sql), báo cáo động (04_functions_procedures.sql)
--        và các kiểm tra ràng buộc trong Trigger (05_triggers.sql).
--
-- LƯU Ý QUAN TRỌNG VỀ THIẾT KẾ INDEX:
--   - Các cột Khóa chính (PK) và Ràng buộc Duy nhất (UNIQUE) đã được PostgreSQL
--     tự động tạo B-Tree Index tương ứng trong 01_create_tables.sql.
--   - File này CHỈ tạo các Index bổ sung cho các khóa ngoại (FK), cột lọc WHERE,
--     cột gom nhóm GROUP BY / ORDER BY chưa có Index hỗ trợ.
--   - Để đo sự khác biệt thực tế Trước vs Sau khi tạo Index trên dữ liệu lớn,
--     chạy file 08_benchmark_explain.sql.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- NHÓM 1: CHỈ MỤC TỐI ƯU ĐƠN HÀNG & CHI TIẾT ĐƠN HÀNG
-- -----------------------------------------------------------------------------

-- 1.1 Tra cứu lịch sử đơn hàng theo khách hàng (sắp xếp mới nhất lên đầu)
-- Phục vụ: Câu 1, Câu 8 (03_queries.sql) và màn hình lịch sử đơn của khách.
CREATE INDEX IF NOT EXISTS idx_donhang_kh_ngaydat
    ON DONHANG (MaKH, NgayDat DESC);

-- 1.2 Lọc đơn hàng theo trạng thái & khoảng thời gian đặt hàng
-- Phục vụ: Câu 3 (doanh thu theo tháng), Câu 11, fn_bao_cao_doanh_thu_dong().
CREATE INDEX IF NOT EXISTS idx_donhang_trangthai_ngaydat
    ON DONHANG (TrangThai, NgayDat DESC);

-- 1.3 Lọc đơn hàng theo Kho, Trạng thái và khoảng thời gian đặt hàng
-- Phục vụ: Câu 4 (doanh thu theo kho), fn_bao_cao_doanh_thu_dong(p_ma_kho => ..., p_tu_ngay, p_den_ngay).
CREATE INDEX IF NOT EXISTS idx_donhang_kho_trangthai
    ON DONHANG (MaKho, TrangThai, NgayDat DESC);

-- 1.4 Covering Index đảo chiều trên CHITIET_DONHANG theo MaChiTietSP
-- Giải thích: Ràng buộc uq_chitiet_donhang_dh_ctsp chỉ đánh index theo cặp (MaDonHang, MaChiTietSP),
-- nên các truy vấn thống kê ngược theo từng đôi giày (CTSP) phải quét toàn bảng (Seq Scan).
-- Bổ sung INCLUDE (MaDonHang, SoLuong, DonGiaBan, ChietKhau) giúp PostgreSQL dùng Index-Only Scan!
CREATE INDEX IF NOT EXISTS idx_ctdh_machitietsp_cover
    ON CHITIET_DONHANG (MaChiTietSP) INCLUDE (MaDonHang, SoLuong, DonGiaBan, ChietKhau);


-- -----------------------------------------------------------------------------
-- NHÓM 2: CHỈ MỤC TỐI ƯU SẢN PHẨM, BIẾN THỂ & CÂY DANH MỤC
-- -----------------------------------------------------------------------------

-- 2.1 Tìm tất cả biến thể size/màu thuộc một mẫu sản phẩm
-- Phục vụ: Câu 2, Câu 7 (Top bán chạy), Câu 9 (SP chưa từng bán).
CREATE INDEX IF NOT EXISTS idx_chitiet_sp_masanpham
    ON CHITIET_SP (MaSanPham, TrangThai);

-- 2.2 Lọc sản phẩm theo Danh mục và Thương hiệu
-- Phục vụ: Câu 2, Câu 7, Câu 9, fn_bao_cao_doanh_thu_dong().
CREATE INDEX IF NOT EXISTS idx_sanpham_danhmuc_thuonghieu
    ON SANPHAM (MaDanhMuc, MaThuongHieu);

-- 2.3 Tăng tốc duyệt đệ quy cây danh mục (Recursive CTE)
-- Phục vụ: Câu 12 (03_queries.sql), nhánh đệ quy của fn_bao_cao_doanh_thu_dong(),
-- và Trigger 6 chống chu trình (fn_trg_danhmuc_chong_chu_trinh).
CREATE INDEX IF NOT EXISTS idx_danhmuc_sp_cha
    ON DANHMUC_SP (MaDanhMucCha);


-- -----------------------------------------------------------------------------
-- NHÓM 3: CHỈ MỤC TỐI ƯU TỒN KHO ĐA KHO & NHÀ CUNG CẤP
-- -----------------------------------------------------------------------------

-- 3.1 Index đảo chiều trên TONKHO (PK đang là (MaKho, MaChiTietSP))
-- Phục vụ: Tra cứu một biến thể giày cụ thể hiện còn tồn tại những kho nào.
CREATE INDEX IF NOT EXISTS idx_tonkho_machitietsp_kho
    ON TONKHO (MaChiTietSP, MaKho);

-- 3.2 Xem nhanh danh sách khách hàng mới đăng ký gần nhất (Top-N / phân trang theo thời gian)
-- Phục vụ: Màn hình Quản trị Khách hàng (ORDER BY NgayDangKy DESC LIMIT ...) khi bảng KHACHHANG phình lớn.
CREATE INDEX IF NOT EXISTS idx_khachhang_ngaydangky
    ON KHACHHANG (NgayDangKy DESC);

-- 3.3 Index đảo chiều trên CUNGCAP (PK đang là (MaChiTietSP, MaNCC))
-- Phục vụ: Câu 6 (xếp hạng NCC theo số mặt hàng cung cấp).
CREATE INDEX IF NOT EXISTS idx_cungcap_mancc_ctsp
    ON CUNGCAP (MaNCC, MaChiTietSP);

-- 3.4 Tìm kiếm nhanh khách hàng theo Số điện thoại (SĐT)
CREATE INDEX IF NOT EXISTS idx_khachhang_sdt
    ON KHACHHANG (SDT);


-- -----------------------------------------------------------------------------
-- NHÓM 4: CHỈ MỤC TỐI ƯU PHIẾU KHO & TRẢ/ĐỔI HÀNG (HỖ TRỢ TRIGGER 5, 7, 8)
-- -----------------------------------------------------------------------------

-- 4.1 Tìm các phiếu trả hàng của một đơn hàng gốc
-- Phục vụ: Trigger 7 (fn_trg_chitiet_trahang_hop_le).
CREATE INDEX IF NOT EXISTS idx_phieutrahang_madonhang
    ON PHIEUTRAHANG (MaDonHang, TrangThai);

-- 4.2 Tính tổng số lượng đã trả trước đó cho một dòng chi tiết đơn hàng
-- Phục vụ: Trigger 7 (ngăn chặn trả vượt quá số lượng đã mua).
CREATE INDEX IF NOT EXISTS idx_chitiet_trahang_mactdh
    ON CHITIET_TRAHANG (MaCTDonHang, MaPhieuTra);

-- 4.3 Lọc phiếu kho theo loại phiếu, trạng thái và ngày lập
CREATE INDEX IF NOT EXISTS idx_phieukho_loai_trangthai_ngay
    ON PHIEUKHO (LoaiPhieu, TrangThai, NgayLap DESC);

-- 4.4 Tra cứu lịch sử nhập/xuất/chuyển theo biến thể sản phẩm
CREATE INDEX IF NOT EXISTS idx_chitiet_phieukho_ctsp
    ON CHITIET_PHIEUKHO (MaChiTietSP, MaPhieu);

-- 4.5 Các khóa ngoại trên bảng con của PHIEUKHO phục vụ JOIN & kiểm tra xóa cha
CREATE INDEX IF NOT EXISTS idx_phieunhapkho_mancc_khodich
    ON PHIEUNHAPKHO (MaNCC, KhoDich);

CREATE INDEX IF NOT EXISTS idx_phieuxuatkho_khonguon
    ON PHIEUXUATKHO (KhoNguon);

CREATE INDEX IF NOT EXISTS idx_phieuchuyenkho_kho
    ON PHIEUCHUYENKHO (KhoNguon, KhoDich);

-- Cập nhật thống kê phân bố dữ liệu (pg_statistic) cho Query Planner
ANALYZE;

