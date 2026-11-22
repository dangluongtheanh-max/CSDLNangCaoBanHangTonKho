-- =============================================================================
-- ĐỒ ÁN CƠ SỞ DỮ LIỆU NÂNG CAO - ĐỀ TÀI 2
-- Hệ thống Quản lý Bán hàng & Tồn kho Đa kho (Chuyên ngành Giày)
-- Hệ quản trị CSDL: PostgreSQL 17
-- File: 08_benchmark_explain.sql
-- Mô tả: Kịch bản thực nghiệm đo hiệu năng bằng EXPLAIN (ANALYZE, BUFFERS)
--        TRƯỚC và SAU khi tạo Index (đáp ứng Bước 6a, Mục II.6 & Minh chứng IV.3).
--
-- CƠ CHẾ HOẠT ĐỘNG (REPRODUCIBLE & AN TOÀN DỮ LIỆU MẪU):
--   1. Gỡ tạm thời 18 Index phụ (của 07_indexes.sql) để đo Baseline thực tế.
--   2. Sinh dữ liệu benchmark quy mô lớn gắn tiền tố 'BM_' (30.000 khách hàng,
--      50.000 đơn hàng, 100.000 dòng chi tiết đơn dùng 18 biến thể giày thật).
--   3. Chạy EXPLAIN (ANALYZE, BUFFERS) cho 4 truy vấn nghiệp vụ đại diện (TRƯỚC INDEX).
--   4. Tạo lại bộ 18 Index từ 07_indexes.sql và cập nhật thống kê ANALYZE.
--   5. Chạy lại đúng 4 truy vấn trên với EXPLAIN (ANALYZE, BUFFERS) (SAU INDEX).
--   6. Dọn sạch toàn bộ dữ liệu benchmark 'BM_%' -> trả bảng về đúng dữ liệu mẫu gốc.
--
-- HƯỚNG DẪN CHẠY:
--   - Chạy bằng psql: psql -h localhost -p 5433 -U postgres -d ql_banhang -f 08_benchmark_explain.sql
--   - Hoặc chạy trong DBeaver (bật Auto-commit, nhấn Alt + X hoặc chạy từng phần).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- BƯỚC 1: GỠ TẠM CÁC INDEX PHỤ ĐỂ ĐO HIỆU NĂNG TRƯỚC KHI ĐÁNH INDEX
--         (Chỉ giữ lại PK và UNIQUE index mặc định từ 01_create_tables.sql)
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_donhang_kh_ngaydat;
DROP INDEX IF EXISTS idx_donhang_trangthai_ngaydat;
DROP INDEX IF EXISTS idx_donhang_kho_trangthai;
DROP INDEX IF EXISTS idx_ctdh_machitietsp_cover;
DROP INDEX IF EXISTS idx_chitiet_sp_masanpham;
DROP INDEX IF EXISTS idx_sanpham_danhmuc_thuonghieu;
DROP INDEX IF EXISTS idx_danhmuc_sp_cha;
DROP INDEX IF EXISTS idx_tonkho_machitietsp_kho;
DROP INDEX IF EXISTS idx_khachhang_ngaydangky;
DROP INDEX IF EXISTS idx_cungcap_mancc_ctsp;
DROP INDEX IF EXISTS idx_khachhang_sdt;
DROP INDEX IF EXISTS idx_phieutrahang_madonhang;
DROP INDEX IF EXISTS idx_chitiet_trahang_mactdh;
DROP INDEX IF EXISTS idx_phieukho_loai_trangthai_ngay;
DROP INDEX IF EXISTS idx_chitiet_phieukho_ctsp;
DROP INDEX IF EXISTS idx_phieunhapkho_mancc_khodich;
DROP INDEX IF EXISTS idx_phieuxuatkho_khonguon;
DROP INDEX IF EXISTS idx_phieuchuyenkho_kho;

-- Đảm bảo sạch dữ liệu benchmark cũ (nếu lần chạy trước bị ngắt giữa chừng)
DELETE FROM CHITIET_DONHANG WHERE MaCTDonHang LIKE 'CTDH_BM_%';
DELETE FROM DONHANG         WHERE MaDonHang   LIKE 'DH_BM_%';
DELETE FROM TONKHO          WHERE MaChiTietSP LIKE 'CTSP_BM_%';
DELETE FROM CHITIET_SP      WHERE MaChiTietSP LIKE 'CTSP_BM_%';
DELETE FROM KHACHHANG       WHERE MaKH        LIKE 'KH_BM_%';


-- -----------------------------------------------------------------------------
-- BƯỚC 2: SINH DỮ LIỆU BENCHMARK QUY MÔ LỚN (TIỀN TỐ 'BM_')
-- -----------------------------------------------------------------------------
-- 30.000 khách hàng đăng ký tích lũy theo thời gian (2024-01-01 trở đi)
INSERT INTO KHACHHANG (MaKH, HoTen, SDT, Email, DiaChi, NgayDangKy)
SELECT
    'KH_BM_' || LPAD(i::TEXT, 5, '0'),
    'Khách Benchmark ' || i,
    '09' || LPAD((10000000 + i)::TEXT, 8, '0'),
    'bm' || i || '@example.com',
    'Địa chỉ số ' || i || ', TP.HCM',
    TIMESTAMP '2024-01-01 00:00:00' + (i * 3 || ' minutes')::INTERVAL
FROM generate_series(1, 30000) AS s(i);

-- 50.000 đơn hàng (70% HOAN_TAT, 10% DANG_GIAO, 10% DA_XAC_NHAN, 10% DA_HUY)
INSERT INTO DONHANG (MaDonHang, NgayDat, TrangThai, DiaChiGiaoHang, PhuongThucThanhToan, GhiChu, MaKH, MaKho)
SELECT
    'DH_BM_' || LPAD(i::TEXT, 5, '0'),
    TIMESTAMP '2025-01-01 08:00:00' + (i * 5 || ' minutes')::INTERVAL,
    CASE
        WHEN (i % 10) < 7 THEN 'HOAN_TAT'
        WHEN (i % 10) < 8 THEN 'DANG_GIAO'
        WHEN (i % 10) < 9 THEN 'DA_XAC_NHAN'
        ELSE 'DA_HUY'
    END,
    'Địa chỉ giao #' || i,
    CASE (i % 3) WHEN 0 THEN 'COD' WHEN 1 THEN 'CHUYEN_KHOAN' ELSE 'THE' END,
    NULL,
    'KH_BM_' || LPAD((((i - 1) % 1000) + 1)::TEXT, 5, '0'),
    'KHO0' || (((i - 1) % 4) + 1)
FROM generate_series(1, 50000) AS s(i);

-- 100.000 dòng chi tiết đơn hàng (mỗi đơn 2 biến thể giày thật CTSP01..CTSP18)
INSERT INTO CHITIET_DONHANG (MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau)
SELECT
    'CTDH_BM_' || LPAD((i * 2 - 1)::TEXT, 6, '0'),
    'DH_BM_' || LPAD(i::TEXT, 5, '0'),
    'CTSP' || LPAD((((i - 1) % 18) + 1)::TEXT, 2, '0'),
    (i % 3) + 1,
    1500000.00 + ((i % 10) * 100000.00),
    CASE WHEN i % 5 = 0 THEN 5.00 ELSE 0.00 END
FROM generate_series(1, 50000) AS s(i)
UNION ALL
SELECT
    'CTDH_BM_' || LPAD((i * 2)::TEXT, 6, '0'),
    'DH_BM_' || LPAD(i::TEXT, 5, '0'),
    'CTSP' || LPAD((((i + 5) % 18) + 1)::TEXT, 2, '0'),
    1,
    2200000.00,
    0.00
FROM generate_series(1, 50000) AS s(i);

-- Cập nhật thống kê bảng cho Query Planner trước khi đo
ANALYZE;


-- -----------------------------------------------------------------------------
-- BƯỚC 3: ĐO HIỆU NĂNG TRƯỚC KHI TẠO INDEX (BASELINE - KHÔNG CÓ INDEX PHỤ)
-- -----------------------------------------------------------------------------

-- [BENCHMARK 1 - TRƯỚC INDEX] Lịch sử đơn hàng của khách hàng KH_BM_00042 (50 / 50.010 đơn = 0.1%)
EXPLAIN (ANALYZE, BUFFERS)
SELECT dh.MaDonHang, dh.NgayDat, dh.TrangThai, dh.MaKho, dh.DiaChiGiaoHang
FROM DONHANG dh
WHERE dh.MaKH = 'KH_BM_00042'
ORDER BY dh.NgayDat DESC;

-- [BENCHMARK 2 - TRƯỚC INDEX] Báo cáo doanh thu chốt ngày (HOAN_TAT) tại KHO01 ngày 2025-02-01
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    dh.MaKho,
    COUNT(DISTINCT dh.MaDonHang) AS SoDonHoanTat,
    SUM(ctdh.SoLuong) AS TongDoiGiayBan,
    ROUND(SUM(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0)), 2) AS DoanhThu
FROM DONHANG dh
JOIN CHITIET_DONHANG ctdh ON dh.MaDonHang = ctdh.MaDonHang
WHERE dh.MaKho = 'KHO01'
  AND dh.TrangThai = 'HOAN_TAT'
  AND dh.NgayDat >= TIMESTAMP '2025-02-01 00:00:00'
  AND dh.NgayDat <  TIMESTAMP '2025-02-02 00:00:00'
GROUP BY dh.MaKho;

-- [BENCHMARK 3 - TRƯỚC INDEX] Xem trang đầu danh sách 20 khách hàng mới đăng ký gần nhất (trên 30.008 khách)
EXPLAIN (ANALYZE, BUFFERS)
SELECT kh.MaKH, kh.HoTen, kh.SDT, kh.Email, kh.NgayDangKy
FROM KHACHHANG kh
ORDER BY kh.NgayDangKy DESC
LIMIT 20;

-- [BENCHMARK 4 - TRƯỚC INDEX] Đối chiếu lý thuyết: Lọc TrangThai = 'HOAN_TAT' (~70% số dòng)
EXPLAIN (ANALYZE, BUFFERS)
SELECT dh.PhuongThucThanhToan, COUNT(*) AS SoDon
FROM DONHANG dh
WHERE dh.TrangThai = 'HOAN_TAT'
GROUP BY dh.PhuongThucThanhToan;


-- -----------------------------------------------------------------------------
-- BƯỚC 4: TẠO HỆ THỐNG 18 INDEX (ĐỒNG BỘ VỚI 07_indexes.sql) & CẬP NHẬT THỐNG KÊ
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_donhang_kh_ngaydat
    ON DONHANG (MaKH, NgayDat DESC);
CREATE INDEX IF NOT EXISTS idx_donhang_trangthai_ngaydat
    ON DONHANG (TrangThai, NgayDat DESC);
CREATE INDEX IF NOT EXISTS idx_donhang_kho_trangthai
    ON DONHANG (MaKho, TrangThai, NgayDat DESC);
CREATE INDEX IF NOT EXISTS idx_ctdh_machitietsp_cover
    ON CHITIET_DONHANG (MaChiTietSP) INCLUDE (MaDonHang, SoLuong, DonGiaBan, ChietKhau);
CREATE INDEX IF NOT EXISTS idx_chitiet_sp_masanpham
    ON CHITIET_SP (MaSanPham, TrangThai);
CREATE INDEX IF NOT EXISTS idx_sanpham_danhmuc_thuonghieu
    ON SANPHAM (MaDanhMuc, MaThuongHieu);
CREATE INDEX IF NOT EXISTS idx_danhmuc_sp_cha
    ON DANHMUC_SP (MaDanhMucCha);
CREATE INDEX IF NOT EXISTS idx_tonkho_machitietsp_kho
    ON TONKHO (MaChiTietSP, MaKho);
CREATE INDEX IF NOT EXISTS idx_khachhang_ngaydangky
    ON KHACHHANG (NgayDangKy DESC);
CREATE INDEX IF NOT EXISTS idx_cungcap_mancc_ctsp
    ON CUNGCAP (MaNCC, MaChiTietSP);
CREATE INDEX IF NOT EXISTS idx_khachhang_sdt
    ON KHACHHANG (SDT);
CREATE INDEX IF NOT EXISTS idx_phieutrahang_madonhang
    ON PHIEUTRAHANG (MaDonHang, TrangThai);
CREATE INDEX IF NOT EXISTS idx_chitiet_trahang_mactdh
    ON CHITIET_TRAHANG (MaCTDonHang, MaPhieuTra);
CREATE INDEX IF NOT EXISTS idx_phieukho_loai_trangthai_ngay
    ON PHIEUKHO (LoaiPhieu, TrangThai, NgayLap DESC);
CREATE INDEX IF NOT EXISTS idx_chitiet_phieukho_ctsp
    ON CHITIET_PHIEUKHO (MaChiTietSP, MaPhieu);
CREATE INDEX IF NOT EXISTS idx_phieunhapkho_mancc_khodich
    ON PHIEUNHAPKHO (MaNCC, KhoDich);
CREATE INDEX IF NOT EXISTS idx_phieuxuatkho_khonguon
    ON PHIEUXUATKHO (KhoNguon);
CREATE INDEX IF NOT EXISTS idx_phieuchuyenkho_kho
    ON PHIEUCHUYENKHO (KhoNguon, KhoDich);

ANALYZE;


-- -----------------------------------------------------------------------------
-- BƯỚC 5: ĐO HIỆU NĂNG SAU KHI TẠO INDEX (SO SÁNH TRỰC TIẾP VỚI BƯỚC 3)
-- -----------------------------------------------------------------------------

-- [BENCHMARK 1 - SAU INDEX] Kỳ vọng: Bitmap Heap/Index Scan trên idx_donhang_kh_ngaydat
EXPLAIN (ANALYZE, BUFFERS)
SELECT dh.MaDonHang, dh.NgayDat, dh.TrangThai, dh.MaKho, dh.DiaChiGiaoHang
FROM DONHANG dh
WHERE dh.MaKH = 'KH_BM_00042'
ORDER BY dh.NgayDat DESC;

-- [BENCHMARK 2 - SAU INDEX] Kỳ vọng: Bitmap Index Scan trên idx_donhang_kho_trangthai + Nested Loop uq_chitiet_donhang_dh_ctsp
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    dh.MaKho,
    COUNT(DISTINCT dh.MaDonHang) AS SoDonHoanTat,
    SUM(ctdh.SoLuong) AS TongDoiGiayBan,
    ROUND(SUM(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0)), 2) AS DoanhThu
FROM DONHANG dh
JOIN CHITIET_DONHANG ctdh ON dh.MaDonHang = ctdh.MaDonHang
WHERE dh.MaKho = 'KHO01'
  AND dh.TrangThai = 'HOAN_TAT'
  AND dh.NgayDat >= TIMESTAMP '2025-02-01 00:00:00'
  AND dh.NgayDat <  TIMESTAMP '2025-02-02 00:00:00'
GROUP BY dh.MaKho;

-- [BENCHMARK 3 - SAU INDEX] Kỳ vọng: Index Scan trên idx_khachhang_ngaydangky (dừng sau 20 dòng, bỏ hẳn Sort)
EXPLAIN (ANALYZE, BUFFERS)
SELECT kh.MaKH, kh.HoTen, kh.SDT, kh.Email, kh.NgayDangKy
FROM KHACHHANG kh
ORDER BY kh.NgayDangKy DESC
LIMIT 20;

-- [BENCHMARK 4 - SAU INDEX] Đối chiếu lý thuyết: Selectivity thấp (~70%) -> Planner vẫn chọn Seq Scan
EXPLAIN (ANALYZE, BUFFERS)
SELECT dh.PhuongThucThanhToan, COUNT(*) AS SoDon
FROM DONHANG dh
WHERE dh.TrangThai = 'HOAN_TAT'
GROUP BY dh.PhuongThucThanhToan;

-- Thống kê dung lượng thực tế của các Index đã tạo
SELECT
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexname::regclass)) AS index_size
FROM pg_indexes
WHERE schemaname = 'public' AND indexname LIKE 'idx_%'
ORDER BY tablename, indexname;


-- -----------------------------------------------------------------------------
-- BƯỚC 6: DỌN SẠCH DỮ LIỆU BENCHMARK ('BM_%') ĐỂ KHÔI PHỤC DỮ LIỆU MẪU GỐC
-- -----------------------------------------------------------------------------
DELETE FROM CHITIET_DONHANG WHERE MaCTDonHang LIKE 'CTDH_BM_%';
DELETE FROM DONHANG         WHERE MaDonHang   LIKE 'DH_BM_%';
DELETE FROM KHACHHANG       WHERE MaKH        LIKE 'KH_BM_%';

ANALYZE;
