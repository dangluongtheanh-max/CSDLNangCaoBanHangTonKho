-- =============================================================================
-- ĐỒ ÁN CƠ SỞ DỮ LIỆU NÂNG CAO - ĐỀ TÀI 2
-- Hệ thống Quản lý Bán hàng & Tồn kho Đa kho (Chuyên ngành Giày)
-- Hệ quản trị CSDL: PostgreSQL 17
-- File: 03_queries.sql
-- Mô tả: 12 truy vấn nghiệp vụ nâng cao (Join, Group By, Subquery, Window Function,
--        Recursive CTE).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Câu 1: Xem danh sách đơn hàng kèm thông tin khách hàng và kho thực hiện
-- -----------------------------------------------------------------------------
SELECT
    dh.MaDonHang,
    dh.NgayDat,
    dh.TrangThai                                              AS TrangThaiDonHang,
    dh.PhuongThucThanhToan,
    dh.DiaChiGiaoHang,
    kh.MaKH,
    kh.HoTen                                                  AS TenKhachHang,
    kh.SDT                                                    AS SDTKhachHang,
    k.MaKho,
    k.TenKho,
    COALESCE(SUM(ctdh.SoLuong), 0)                            AS TongSoLuongGiay,
    COALESCE(
        ROUND(SUM(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0)), 2),
        0.00
    )                                                         AS TongTien
FROM DONHANG dh
JOIN KHACHHANG kh ON dh.MaKH = kh.MaKH
JOIN KHO k        ON dh.MaKho = k.MaKho
LEFT JOIN CHITIET_DONHANG ctdh ON dh.MaDonHang = ctdh.MaDonHang
GROUP BY
    dh.MaDonHang, dh.NgayDat, dh.TrangThai, dh.PhuongThucThanhToan,
    dh.DiaChiGiaoHang, kh.MaKH, kh.HoTen, kh.SDT, k.MaKho, k.TenKho
ORDER BY dh.NgayDat DESC, dh.MaDonHang;

-- -----------------------------------------------------------------------------
-- Câu 2: Xem chi tiết đơn hàng kèm thông tin sản phẩm, biến thể, số lượng và đơn giá
-- -----------------------------------------------------------------------------
SELECT
    dh.MaDonHang,
    dh.NgayDat,
    dh.TrangThai                                              AS TrangThaiDonHang,
    ctdh.MaCTDonHang,
    sp.MaSanPham,
    sp.TenSanPham,
    th.TenThuongHieu,
    dm.TenDanhMuc,
    ctsp.MaChiTietSP,
    ctsp.SKU,
    ctsp.Size,
    ctsp.MauSac,
    ctdh.SoLuong,
    ctdh.DonGiaBan,
    ctdh.ChietKhau                                            AS ChietKhauPhanTram,
    ROUND(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0), 2) AS ThanhTien
FROM CHITIET_DONHANG ctdh
JOIN DONHANG dh      ON ctdh.MaDonHang = dh.MaDonHang
JOIN CHITIET_SP ctsp ON ctdh.MaChiTietSP = ctsp.MaChiTietSP
JOIN SANPHAM sp      ON ctsp.MaSanPham = sp.MaSanPham
JOIN THUONGHIEU th   ON sp.MaThuongHieu = th.MaThuongHieu
JOIN DANHMUC_SP dm   ON sp.MaDanhMuc = dm.MaDanhMuc
ORDER BY dh.NgayDat DESC, dh.MaDonHang, ctdh.MaCTDonHang;

-- -----------------------------------------------------------------------------
-- Câu 3: Thống kê doanh thu theo tháng (tính trên các đơn hàng HOAN_TAT)
-- -----------------------------------------------------------------------------
SELECT
    TO_CHAR(dh.NgayDat, 'YYYY-MM')                            AS Thang,
    COUNT(DISTINCT dh.MaDonHang)                              AS SoDonHoanTat,
    SUM(ctdh.SoLuong)                                         AS TongDoiGiayBan,
    ROUND(
        SUM(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0)),
        2
    )                                                         AS DoanhThu
FROM DONHANG dh
JOIN CHITIET_DONHANG ctdh ON dh.MaDonHang = ctdh.MaDonHang
WHERE dh.TrangThai = 'HOAN_TAT'
GROUP BY TO_CHAR(dh.NgayDat, 'YYYY-MM')
ORDER BY Thang;

-- -----------------------------------------------------------------------------
-- Câu 4: Thống kê doanh thu theo từng kho (bao gồm cả kho chưa có doanh thu)
-- -----------------------------------------------------------------------------
SELECT
    k.MaKho,
    k.TenKho,
    k.TrangThai                                               AS TrangThaiKho,
    COUNT(DISTINCT dh.MaDonHang)                              AS SoDonHoanTat,
    COALESCE(SUM(ctdh.SoLuong), 0)                            AS TongDoiGiayBan,
    COALESCE(
        ROUND(SUM(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0)), 2),
        0.00
    )                                                         AS DoanhThu
FROM KHO k
LEFT JOIN DONHANG dh
    ON k.MaKho = dh.MaKho AND dh.TrangThai = 'HOAN_TAT'
LEFT JOIN CHITIET_DONHANG ctdh
    ON dh.MaDonHang = ctdh.MaDonHang
GROUP BY k.MaKho, k.TenKho, k.TrangThai
ORDER BY DoanhThu DESC, k.MaKho;

-- -----------------------------------------------------------------------------
-- Câu 5: Liệt kê sản phẩm (biến thể giày) sắp hết hàng tại từng kho
--        (Ngưỡng cảnh báo: SoLuongKhaDung <= 5 đối với biến thể đang bán ở kho hoạt động)
-- -----------------------------------------------------------------------------
SELECT
    k.MaKho,
    k.TenKho,
    sp.MaSanPham,
    sp.TenSanPham,
    th.TenThuongHieu,
    ctsp.MaChiTietSP,
    ctsp.SKU,
    ctsp.Size,
    ctsp.MauSac,
    tk.SoLuongThucTe,
    tk.SoLuongDaGiu,
    (tk.SoLuongThucTe - tk.SoLuongDaGiu)   AS SoLuongKhaDung
FROM TONKHO tk
JOIN KHO k           ON tk.MaKho = k.MaKho
JOIN CHITIET_SP ctsp ON tk.MaChiTietSP = ctsp.MaChiTietSP
JOIN SANPHAM sp      ON ctsp.MaSanPham = sp.MaSanPham
JOIN THUONGHIEU th   ON sp.MaThuongHieu = th.MaThuongHieu
WHERE k.TrangThai = 'HOAT_DONG'
  AND ctsp.TrangThai = 'DANG_BAN'
  AND (tk.SoLuongThucTe - tk.SoLuongDaGiu) <= 5
ORDER BY SoLuongKhaDung ASC, k.MaKho, ctsp.SKU;

-- -----------------------------------------------------------------------------
-- Câu 6: Xếp hạng nhà cung cấp theo số lượng mặt hàng có thể cung cấp
-- -----------------------------------------------------------------------------
SELECT
    DENSE_RANK() OVER (
        ORDER BY COUNT(DISTINCT cc.MaChiTietSP) DESC,
                 COUNT(DISTINCT ctsp.MaSanPham) DESC
    )                                                         AS XepHang,
    ncc.MaNCC,
    ncc.TenNCC,
    COUNT(DISTINCT cc.MaChiTietSP)                            AS SoBienTheCungCap,
    COUNT(DISTINCT ctsp.MaSanPham)                            AS SoMauGiayCungCap,
    ROUND(AVG(cc.GiaNhap), 2)                                 AS GiaNhapTrungBinh,
    MIN(cc.ThoiGianGiaoHangDuKien)                            AS GiaoNhanhNhatNgay
FROM NHACUNGCAP ncc
LEFT JOIN CUNGCAP cc     ON ncc.MaNCC = cc.MaNCC
LEFT JOIN CHITIET_SP ctsp ON cc.MaChiTietSP = ctsp.MaChiTietSP
GROUP BY ncc.MaNCC, ncc.TenNCC
ORDER BY XepHang, ncc.MaNCC;

-- -----------------------------------------------------------------------------
-- Câu 7: Tìm top 10 sản phẩm bán chạy nhất (tính trên các đơn hàng HOAN_TAT)
-- -----------------------------------------------------------------------------
SELECT
    sp.MaSanPham,
    sp.TenSanPham,
    th.TenThuongHieu,
    dm.TenDanhMuc,
    SUM(ctdh.SoLuong)                                         AS TongSoLuongBan,
    COUNT(DISTINCT dh.MaDonHang)                              AS SoDonHang,
    ROUND(
        SUM(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0)),
        2
    )                                                         AS DoanhThu
FROM SANPHAM sp
JOIN THUONGHIEU th        ON sp.MaThuongHieu = th.MaThuongHieu
JOIN DANHMUC_SP dm        ON sp.MaDanhMuc = dm.MaDanhMuc
JOIN CHITIET_SP ctsp      ON sp.MaSanPham = ctsp.MaSanPham
JOIN CHITIET_DONHANG ctdh ON ctsp.MaChiTietSP = ctdh.MaChiTietSP
JOIN DONHANG dh           ON ctdh.MaDonHang = dh.MaDonHang
WHERE dh.TrangThai = 'HOAN_TAT'
GROUP BY sp.MaSanPham, sp.TenSanPham, th.TenThuongHieu, dm.TenDanhMuc
ORDER BY TongSoLuongBan DESC, DoanhThu DESC, sp.MaSanPham
LIMIT 10;

-- -----------------------------------------------------------------------------
-- Câu 8: Tìm khách hàng có tổng chi tiêu cao hơn mức chi tiêu trung bình
--        (Tính tổng chi tiêu theo đơn HOAN_TAT trên toàn bộ khách hàng)
-- -----------------------------------------------------------------------------
WITH chi_tieu_khach_hang AS (
    SELECT
        kh.MaKH,
        kh.HoTen,
        kh.SDT,
        COUNT(DISTINCT dh.MaDonHang)                          AS SoDonHoanTat,
        COALESCE(
            ROUND(SUM(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0)), 2),
            0.00
        )                                                     AS TongChiTieu
    FROM KHACHHANG kh
    LEFT JOIN DONHANG dh
        ON kh.MaKH = dh.MaKH AND dh.TrangThai = 'HOAN_TAT'
    LEFT JOIN CHITIET_DONHANG ctdh
        ON dh.MaDonHang = ctdh.MaDonHang
    GROUP BY kh.MaKH, kh.HoTen, kh.SDT
)
SELECT
    MaKH,
    HoTen,
    SDT,
    SoDonHoanTat,
    TongChiTieu,
    (SELECT ROUND(AVG(TongChiTieu), 2) FROM chi_tieu_khach_hang) AS ChiTieuTrungBinh
FROM chi_tieu_khach_hang
WHERE TongChiTieu > (SELECT AVG(TongChiTieu) FROM chi_tieu_khach_hang)
ORDER BY TongChiTieu DESC, MaKH;

-- -----------------------------------------------------------------------------
-- Câu 9: Tìm các sản phẩm (mẫu giày) chưa từng được bán
--        (Không xuất hiện trong bất kỳ đơn hàng hợp lệ nào - loại trừ DA_HUY)
-- -----------------------------------------------------------------------------
SELECT
    sp.MaSanPham,
    sp.TenSanPham,
    th.TenThuongHieu,
    dm.TenDanhMuc,
    sp.GiaNiemYet,
    sp.TrangThai
FROM SANPHAM sp
JOIN THUONGHIEU th ON sp.MaThuongHieu = th.MaThuongHieu
JOIN DANHMUC_SP dm ON sp.MaDanhMuc = dm.MaDanhMuc
WHERE NOT EXISTS (
    SELECT 1
    FROM CHITIET_SP ctsp
    JOIN CHITIET_DONHANG ctdh ON ctsp.MaChiTietSP = ctdh.MaChiTietSP
    JOIN DONHANG dh           ON ctdh.MaDonHang = dh.MaDonHang
    WHERE ctsp.MaSanPham = sp.MaSanPham
      AND dh.TrangThai <> 'DA_HUY'
)
ORDER BY sp.MaSanPham;

-- -----------------------------------------------------------------------------
-- Câu 10: Tìm các đơn hàng có giá trị lớn hơn mức trung bình của tất cả đơn hàng
-- -----------------------------------------------------------------------------
WITH gia_tri_don_hang AS (
    SELECT
        dh.MaDonHang,
        dh.NgayDat,
        dh.TrangThai,
        kh.MaKH,
        kh.HoTen,
        COALESCE(
            ROUND(SUM(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0)), 2),
            0.00
        )                                                     AS TongTien
    FROM DONHANG dh
    JOIN KHACHHANG kh              ON dh.MaKH = kh.MaKH
    LEFT JOIN CHITIET_DONHANG ctdh ON dh.MaDonHang = ctdh.MaDonHang
    GROUP BY dh.MaDonHang, dh.NgayDat, dh.TrangThai, kh.MaKH, kh.HoTen
)
SELECT
    MaDonHang,
    NgayDat,
    TrangThai,
    MaKH,
    HoTen,
    TongTien,
    (SELECT ROUND(AVG(TongTien), 2) FROM gia_tri_don_hang)    AS GiaTriTrungBinh
FROM gia_tri_don_hang
WHERE TongTien > (SELECT AVG(TongTien) FROM gia_tri_don_hang)
ORDER BY TongTien DESC, MaDonHang;

-- -----------------------------------------------------------------------------
-- Câu 11: Thống kê số lượng đơn hàng theo trạng thái
-- -----------------------------------------------------------------------------
WITH tong_gia_tri_don AS (
    SELECT
        dh.MaDonHang,
        dh.TrangThai,
        COALESCE(
            ROUND(SUM(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0)), 2),
            0.00
        )                                                     AS TongTien
    FROM DONHANG dh
    LEFT JOIN CHITIET_DONHANG ctdh ON dh.MaDonHang = ctdh.MaDonHang
    GROUP BY dh.MaDonHang, dh.TrangThai
)
SELECT
    TrangThai,
    COUNT(*)                                                  AS SoLuongDonHang,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2)        AS TyLePhanTram,
    ROUND(SUM(TongTien), 2)                                   AS TongGiaTri
FROM tong_gia_tri_don
GROUP BY TrangThai
ORDER BY SoLuongDonHang DESC, TrangThai;

-- -----------------------------------------------------------------------------
-- Câu 12: Hiển thị cây danh mục sản phẩm nhiều cấp bằng Recursive CTE
-- -----------------------------------------------------------------------------
WITH RECURSIVE cay_danh_muc AS (
    -- Neo đệ quy: các danh mục gốc (cấp 1)
    SELECT
        dm.MaDanhMuc,
        dm.MaDanhMucCha,
        dm.TenDanhMuc,
        dm.MoTa,
        1                                                     AS Cap,
        dm.TenDanhMuc::TEXT                                   AS DuongDan,
        dm.MaDanhMuc::TEXT                                    AS ThuTuCay
    FROM DANHMUC_SP dm
    WHERE dm.MaDanhMucCha IS NULL

    UNION ALL

    -- Nhánh đệ quy: các danh mục con, cháu
    SELECT
        con.MaDanhMuc,
        con.MaDanhMucCha,
        con.TenDanhMuc,
        con.MoTa,
        cha.Cap + 1                                           AS Cap,
        (cha.DuongDan || ' > ' || con.TenDanhMuc)             AS DuongDan,
        (cha.ThuTuCay || ' > ' || con.MaDanhMuc)              AS ThuTuCay
    FROM DANHMUC_SP con
    JOIN cay_danh_muc cha ON con.MaDanhMucCha = cha.MaDanhMuc
)
SELECT
    c.MaDanhMuc,
    c.MaDanhMucCha,
    c.Cap,
    REPEAT('    ', c.Cap - 1) || CASE WHEN c.Cap > 1 THEN '|-- ' ELSE '' END || c.TenDanhMuc AS TenHienThi,
    c.DuongDan,
    COUNT(sp.MaSanPham)                                       AS SoMauGiayTrucTiep,
    c.MoTa
FROM cay_danh_muc c
LEFT JOIN SANPHAM sp ON c.MaDanhMuc = sp.MaDanhMuc
GROUP BY c.MaDanhMuc, c.MaDanhMucCha, c.Cap, c.TenDanhMuc, c.DuongDan, c.ThuTuCay, c.MoTa
ORDER BY c.ThuTuCay;
