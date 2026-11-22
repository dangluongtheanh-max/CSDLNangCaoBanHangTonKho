-- =============================================================================
-- ĐỒ ÁN CƠ SỞ DỮ LIỆU NÂNG CAO - ĐỀ TÀI 2
-- Hệ thống Quản lý Bán hàng & Tồn kho Đa kho (Chuyên ngành Giày)
-- Hệ quản trị CSDL: PostgreSQL 17
-- File: 06_transactions.sql
-- Mô tả: Thiết kế và kiểm thực nghiệm các kịch bản Giao dịch (Transaction) &
--        Điều khiển đồng thời (Concurrency Control) theo Hướng dẫn đồ án:
--   1. Kịch bản 1 (COMMIT): Giao dịch đặt đơn hàng nhiều mặt hàng thành công.
--   2. Kịch bản 2 (ROLLBACK): Giao dịch lỗi giữa chừng (vượt tồn khả dụng ở
--      dòng hàng thứ 2) -> Tự động hoàn tác toàn bộ, bảo toàn tính Nguyên tử.
--   3. Kịch bản 3 (SAVEPOINT): Hoàn tác một phần dòng hàng hết tồn kho bằng
--      SAVEPOINT và vẫn COMMIT đơn hàng hợp lệ.
--   4. Kịch bản 4 (COMMIT phức hợp): Giao dịch Đổi size hàng còn mới
--      (Phiếu trả + Chi tiết trả + Phiếu nhập kho hoàn + Phiếu xuất đổi + Tồn kho).
--   5. Kịch bản 5 (CONCURRENCY): Hai phiên giao dịch đồng thời cùng tranh mua
--      lượng giày cuối cùng trong kho (Chống bán lố / Overselling).
--   6. Kịch bản 6 (DEADLOCK PREVENTION): Kỹ thuật sắp xếp thứ tự khóa
--      (Lock Ordering) ngăn chặn Deadlock khi đặt nhiều đôi giày cùng lúc.
-- =============================================================================


-- =============================================================================
-- BƯỚC 0: ĐẢM BẢO TRẠNG THÁI DỮ LIỆU GỐC SẠCH TRƯỚC KHI CHẠY KỊCH BẢN (IDEMPOTENT)
-- =============================================================================
BEGIN;
    UPDATE TONKHO SET SoLuongThucTe = 29, SoLuongDaGiu = 2 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP01';
    UPDATE TONKHO SET SoLuongThucTe = 23, SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP02';
    UPDATE TONKHO SET SoLuongThucTe = 4,  SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP03';
    UPDATE TONKHO SET SoLuongThucTe = 15, SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP07';
    UPDATE TONKHO SET SoLuongThucTe = 25, SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP08';
    UPDATE TONKHO SET SoLuongThucTe = 38, SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP12';
    UPDATE TONKHO SET SoLuongThucTe = 33, SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP13';
    UPDATE TONKHO SET SoLuongThucTe = 2,  SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP18';

    DELETE FROM CHITIET_PHIEUKHO WHERE MaPhieu IN ('PK19', 'PK20');
    DELETE FROM PHIEUNHAPKHO WHERE MaPhieu = 'PK19';
    DELETE FROM PHIEUXUATKHO WHERE MaPhieu = 'PK20';
    DELETE FROM PHIEUKHO WHERE MaPhieu IN ('PK19', 'PK20');

    DELETE FROM CHITIET_TRAHANG WHERE MaPhieuTra = 'PT05';
    DELETE FROM PHIEUTRAHANG WHERE MaPhieuTra = 'PT05';

    DELETE FROM CHITIET_DONHANG WHERE MaDonHang IN ('DH11', 'DH12', 'DH13', 'DH14_SESSION_A', 'DH15_SESSION_B');
    DELETE FROM DONHANG WHERE MaDonHang IN ('DH11', 'DH12', 'DH13', 'DH14_SESSION_A', 'DH15_SESSION_B');
COMMIT;


-- =============================================================================
-- KỊCH BẢN 1: GIAO DỊCH THÀNH CÔNG (COMMIT)
-- Nghiệp vụ:
--   Gộp chuỗi thao tác tạo đơn hàng (DH11), tạo 2 dòng chi tiết đơn hàng
--   (CTSP01: 1 đôi, CTSP07: 1 đôi) và cập nhật giữ hàng tồn kho tại KHO01
--   thành một giao dịch (Transaction) duy nhất.
-- =============================================================================

-- [Bước 1.0] Kiểm tra trạng thái tồn kho KHO01 trước khi chạy Giao dịch 1
SELECT
    tk.MaKho,
    tk.MaChiTietSP,
    ct.SKU,
    tk.SoLuongThucTe,
    tk.SoLuongDaGiu,
    (tk.SoLuongThucTe - tk.SoLuongDaGiu) AS SoLuongKhaDung_TruocGD1
FROM TONKHO tk
JOIN CHITIET_SP ct ON tk.MaChiTietSP = ct.MaChiTietSP
WHERE tk.MaKho = 'KHO01'
  AND tk.MaChiTietSP IN ('CTSP01', 'CTSP07')
ORDER BY tk.MaChiTietSP;

-- [Bước 1.1] Thực thi Giao dịch 1 (BEGIN ... COMMIT)
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;

    -- 1. Khóa bi quan (Pessimistic Lock) các dòng tồn kho theo thứ tự chuẩn
    SELECT MaKho, MaChiTietSP, SoLuongThucTe, SoLuongDaGiu
    FROM TONKHO
    WHERE MaKho = 'KHO01'
      AND MaChiTietSP IN ('CTSP01', 'CTSP07')
    ORDER BY MaKho, MaChiTietSP
    FOR UPDATE;

    -- 2. Tạo bản ghi đầu đơn hàng DH11
    INSERT INTO DONHANG (
        MaDonHang, MaKH, MaKho, NgayDat,
        TrangThai, PhuongThucThanhToan, DiaChiGiaoHang, GhiChu
    )
    VALUES (
        'DH11', 'KH04', 'KHO01', CURRENT_TIMESTAMP,
        'DA_XAC_NHAN', 'CHUYEN_KHOAN',
        '52 Bạch Đằng, Hải Châu, Đà Nẵng',
        'Kịch bản 1: Giao dịch đặt hàng thành công (COMMIT)'
    );

    -- 3. Thêm 2 dòng chi tiết đơn hàng
    INSERT INTO CHITIET_DONHANG (
        MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau
    )
    VALUES
        ('CTDH19', 'DH11', 'CTSP01', 1, 3200000.00, 0.00),
        ('CTDH20', 'DH11', 'CTSP07', 1, 2850000.00, 5.00);

    -- 4. Cập nhật tăng số lượng đã giữ (SoLuongDaGiu) tại KHO01
    UPDATE TONKHO
    SET SoLuongDaGiu = SoLuongDaGiu + 1
    WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP01';

    UPDATE TONKHO
    SET SoLuongDaGiu = SoLuongDaGiu + 1
    WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP07';

COMMIT;

-- [Bước 1.2] Kiểm tra kết quả sau khi COMMIT Giao dịch 1
SELECT
    dh.MaDonHang,
    dh.MaKH,
    dh.MaKho,
    dh.TrangThai,
    fn_tinh_tong_tien_don_hang(dh.MaDonHang) AS TongTienDonHang
FROM DONHANG dh
WHERE dh.MaDonHang = 'DH11';

SELECT
    tk.MaKho,
    tk.MaChiTietSP,
    ct.SKU,
    tk.SoLuongThucTe,
    tk.SoLuongDaGiu AS SoLuongDaGiu_SauGD1,
    (tk.SoLuongThucTe - tk.SoLuongDaGiu) AS SoLuongKhaDung_SauGD1
FROM TONKHO tk
JOIN CHITIET_SP ct ON tk.MaChiTietSP = ct.MaChiTietSP
WHERE tk.MaKho = 'KHO01'
  AND tk.MaChiTietSP IN ('CTSP01', 'CTSP07')
ORDER BY tk.MaChiTietSP;


-- =============================================================================
-- KỊCH BẢN 2: GIAO DỊCH LỖI GIỮA CHỪNG & HOÀN TÁC TOÀN BỘ (ROLLBACK)
-- Mục tiêu minh chứng:
--   - Khách hàng KH05 đặt đơn hàng DH12 tại KHO01 gồm 2 dòng sản phẩm:
--       + Dòng 1 (CTSP02 - Nike Pegasus 41 Đen Size 42): Đặt 2 đôi -> HỢP LỆ
--         (Tồn khả dụng tại KHO01 đang có 23 đôi). Hệ thống đã INSERT đơn DH12,
--         INSERT dòng CTDH21 và UPDATE tăng SoLuongDaGiu của CTSP02 thêm 2.
--       + Dòng 2 (CTSP18 - Biti's Hunter Core Slip-on Xám Size 40): Cố ý đặt
--         10 đôi, trong khi tồn khả dụng tại KHO01 chỉ còn 2 đôi!
--   - Khi cập nhật tồn kho dòng 2, Trigger trg_02_tonkho_kiem_tra_da_giu lập tức
--     chặn lại và phát sinh ngoại lệ.
--   - Toàn bộ giao dịch được ROLLBACK: Đơn hàng DH12 không bị tạo dở dang,
--     dòng 1 (CTSP02) không bị giữ hàng ảo.
-- =============================================================================

DO $$
DECLARE
    v_da_giu_truoc INTEGER;
    v_da_giu_giua  INTEGER;
BEGIN
    SELECT SoLuongDaGiu INTO v_da_giu_truoc
    FROM TONKHO
    WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP02';

    RAISE NOTICE '------------------------------------------------------------';
    RAISE NOTICE '[KỊCH BẢN 2 - TRƯỚC GIAO DỊCH] CTSP02 tại KHO01 có SoLuongDaGiu = %', v_da_giu_truoc;

    -- Khối BEGIN ... EXCEPTION tương đương SAVEPOINT / Subtransaction trong PL/pgSQL:
    -- Mọi thay đổi bên trong khối BEGIN sẽ tự động ROLLBACK khi gặp lỗi ở bước 4.
    BEGIN
        -- Bước 1: Tạo đơn hàng DH12 (Thành công tạm thời trong giao dịch)
        INSERT INTO DONHANG (
            MaDonHang, MaKH, MaKho, NgayDat,
            TrangThai, PhuongThucThanhToan, DiaChiGiaoHang, GhiChu
        )
        VALUES (
            'DH12', 'KH05', 'KHO01', CURRENT_TIMESTAMP,
            'CHO_XAC_NHAN', 'COD',
            '19 Nguyễn Thị Minh Khai, Q.1, TP.HCM',
            'Kịch bản 2: Đơn hàng lỗi ở dòng chi tiết thứ 2'
        );

        -- Bước 2: Thêm dòng chi tiết 1 (CTSP02 - 2 đôi) & giữ hàng (Thành công tạm thời)
        INSERT INTO CHITIET_DONHANG (
            MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau
        )
        VALUES ('CTDH21', 'DH12', 'CTSP02', 2, 3200000.00, 0.00);

        UPDATE TONKHO
        SET SoLuongDaGiu = SoLuongDaGiu + 2
        WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP02';

        SELECT SoLuongDaGiu INTO v_da_giu_giua
        FROM TONKHO
        WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP02';

        RAISE NOTICE '[KỊCH BẢN 2 - GIỮA GIAO DỊCH] Đã tạo DH12 & giữ 2 đôi CTSP02 (SoLuongDaGiu tạm thời tăng lên = %)', v_da_giu_giua;

        -- Bước 3: Thêm dòng chi tiết 2 (CTSP18 - cố ý đặt 10 đôi vượt quá 2 đôi tồn thực tế)
        INSERT INTO CHITIET_DONHANG (
            MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau
        )
        VALUES ('CTDH22', 'DH12', 'CTSP18', 10, 850000.00, 0.00);

        -- Bước 4: Cập nhật giữ 10 đôi CTSP18 -> Sẽ bị Trigger trg_02_tonkho_kiem_tra_da_giu chặn!
        UPDATE TONKHO
        SET SoLuongDaGiu = SoLuongDaGiu + 10
        WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP18';

    EXCEPTION
        WHEN OTHERS THEN
            RAISE NOTICE '[KỊCH BẢN 2 - PHÁT HIỆN LỖI & ROLLBACK] Hệ thống chặn lỗi: %', SQLERRM;
            RAISE NOTICE '[KỊCH BẢN 2 - ĐÃ ROLLBACK] Toàn bộ thao tác tạo DH12, CTDH21 và giữ 2 đôi CTSP02 đã được hoàn tác!';
    END;
END;
$$ LANGUAGE plpgsql;

-- [Minh chứng sau ROLLBACK Kịch bản 2]:
-- 1. Kiểm tra đơn hàng DH12 và chi tiết CTDH21 hoàn toàn không tồn tại (số dòng = 0)
SELECT
    (SELECT COUNT(*) FROM DONHANG WHERE MaDonHang = 'DH12') AS SoDonHang_DH12_TonTai,
    (SELECT COUNT(*) FROM CHITIET_DONHANG WHERE MaDonHang = 'DH12') AS SoChiTiet_DH12_TonTai;

-- 2. Kiểm tra tồn kho của CTSP02 và CTSP18 tại KHO01 vẫn bảo toàn nguyên vẹn
SELECT
    tk.MaKho,
    tk.MaChiTietSP,
    ct.SKU,
    tk.SoLuongThucTe,
    tk.SoLuongDaGiu AS SoLuongDaGiu_SauRollback,
    (tk.SoLuongThucTe - tk.SoLuongDaGiu) AS SoLuongKhaDung_SauRollback
FROM TONKHO tk
JOIN CHITIET_SP ct ON tk.MaChiTietSP = ct.MaChiTietSP
WHERE tk.MaKho = 'KHO01'
  AND tk.MaChiTietSP IN ('CTSP02', 'CTSP18')
ORDER BY tk.MaChiTietSP;

/*
-- [Mẫu câu lệnh SQL thuần dùng để chạy từng bước trong DBeaver với Ctrl + Enter]:
BEGIN;
    INSERT INTO DONHANG (MaDonHang, MaKH, MaKho, TrangThai, PhuongThucThanhToan, DiaChiGiaoHang)
    VALUES ('DH12', 'KH05', 'KHO01', 'CHO_XAC_NHAN', 'COD', '19 Nguyễn Thị Minh Khai, Q.1, TP.HCM');

    INSERT INTO CHITIET_DONHANG (MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau)
    VALUES ('CTDH21', 'DH12', 'CTSP02', 2, 3200000.00, 0.00);

    UPDATE TONKHO SET SoLuongDaGiu = SoLuongDaGiu + 2
    WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP02';

    -- Câu lệnh dưới đây gây lỗi vượt tồn kho khả dụng (chỉ còn 2 đôi CTSP18 tại KHO01):
    UPDATE TONKHO SET SoLuongDaGiu = SoLuongDaGiu + 10
    WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP18';

ROLLBACK;
*/


-- =============================================================================
-- KỊCH BẢN 3: SỬ DỤNG SAVEPOINT & ROLLBACK TO SAVEPOINT (HOÀN TÁC MỘT PHẦN)
-- Nghiệp vụ:
--   Khách hàng KH06 đặt đơn DH13 tại KHO01 gồm:
--     - Dòng 1: CTSP03 (Nike Pegasus 41 Trắng Size 42) - 1 đôi (Còn hàng)
--     - Đặt SAVEPOINT sp_them_dong_2 trước khi thêm dòng 2
--     - Dòng 2: Khách thử đặt CTSP18 với số lượng lớn nhưng không đủ tồn kho
--       -> Hệ thống ROLLBACK TO SAVEPOINT sp_them_dong_2 (chỉ hủy dòng 2),
--          sau đó thay bằng CTSP08 (Biti's Hunter Street Xám Size 40) - 1 đôi
--          và COMMIT đơn hàng DH13 thành công.
-- =============================================================================

BEGIN;

    -- 1. Tạo đơn hàng DH13 và dòng chi tiết 1 (CTSP03 - 1 đôi)
    INSERT INTO DONHANG (
        MaDonHang, MaKH, MaKho, NgayDat,
        TrangThai, PhuongThucThanhToan, DiaChiGiaoHang, GhiChu
    )
    VALUES (
        'DH13', 'KH06', 'KHO01', CURRENT_TIMESTAMP,
        'DA_XAC_NHAN', 'THE',
        '78 Phan Xích Long, Phú Nhuận, TP.HCM',
        'Kịch bản 3: Minh họa SAVEPOINT & ROLLBACK TO SAVEPOINT'
    );

    INSERT INTO CHITIET_DONHANG (
        MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau
    )
    VALUES ('CTDH23', 'DH13', 'CTSP03', 1, 3200000.00, 0.00);

    UPDATE TONKHO
    SET SoLuongDaGiu = SoLuongDaGiu + 1
    WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP03';

    -- 2. Đánh dấu điểm lưu SAVEPOINT trước khi thử thêm mặt hàng thứ 2
    SAVEPOINT sp_them_dong_2;

    -- Giả sử khách chọn nhầm CTSP18 (tạm thời thêm vào đơn nhưng sau đó phát hiện không đủ số lượng yêu cầu)
    INSERT INTO CHITIET_DONHANG (
        MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau
    )
    VALUES ('CTDH24_LOI', 'DH13', 'CTSP18', 2, 850000.00, 0.00);

    -- 3. Hoàn tác riêng phần thử thêm dòng 2 về SAVEPOINT mà KHÔNG làm mất đơn DH13 và dòng 1
    ROLLBACK TO SAVEPOINT sp_them_dong_2;

    -- 4. Thay thế bằng dòng hàng hợp lệ CTSP08 (1 đôi) và COMMIT
    INSERT INTO CHITIET_DONHANG (
        MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau
    )
    VALUES ('CTDH24', 'DH13', 'CTSP08', 1, 950000.00, 0.00);

    UPDATE TONKHO
    SET SoLuongDaGiu = SoLuongDaGiu + 1
    WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP08';

    RELEASE SAVEPOINT sp_them_dong_2;

COMMIT;

-- Kiểm tra đơn hàng DH13 sau khi dùng SAVEPOINT: Chỉ có CTDH23 và CTDH24 (không có CTDH24_LOI)
SELECT
    ctdh.MaDonHang,
    ctdh.MaCTDonHang,
    ctdh.MaChiTietSP,
    ct.SKU,
    ctdh.SoLuong,
    ctdh.DonGiaBan
FROM CHITIET_DONHANG ctdh
JOIN CHITIET_SP ct ON ctdh.MaChiTietSP = ct.MaChiTietSP
WHERE ctdh.MaDonHang = 'DH13'
ORDER BY ctdh.MaCTDonHang;


-- =============================================================================
-- KỊCH BẢN 4: GIAO DỊCH PHỨC HỢP ĐỔI SIZE HÀNG CÒN MỚI (THEO DATA DICTIONARY)
-- Quy tắc nghiệp vụ:
--   "Tạo phiếu trả, chi tiết trả, phiếu nhập hàng trả, phiếu xuất hàng đổi và
--    cập nhật tồn kho phải nằm trong cùng transaction khi cùng thuộc một nghiệp vụ."
--   "Biến thể thay thế phải cùng sản phẩm/model với biến thể ban đầu."
--
-- Tình huống:
--   - Đơn hàng gốc DH05 (của KH05 tại KHO01, trạng thái HOAN_TAT) có dòng CTDH10
--     mua 2 đôi CTSP13 (New Balance Fresh Foam Hierro - Xanh rêu - Size 42).
--   - Khách yêu cầu đổi 1 đôi CTSP13 còn nguyên mới (CON_MOI) sang 1 đôi
--     CTSP12 (New Balance Fresh Foam Hierro - Xanh rêu - Size 41, cùng mã SP06).
-- =============================================================================

BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;

    -- 1. Khóa dòng tồn kho của biến thể trả (CTSP13) và biến thể đổi (CTSP12) tại KHO01
    SELECT MaKho, MaChiTietSP, SoLuongThucTe, SoLuongDaGiu
    FROM TONKHO
    WHERE MaKho = 'KHO01'
      AND MaChiTietSP IN ('CTSP12', 'CTSP13')
    ORDER BY MaKho, MaChiTietSP
    FOR UPDATE;

    -- 2. Tạo Phiếu trả hàng PT05 tham chiếu đơn gốc DH05
    INSERT INTO PHIEUTRAHANG (
        MaPhieuTra, MaDonHang, NgayTiepNhan, LyDo, TrangThai, GhiChu
    )
    VALUES (
        'PT05', 'DH05', CURRENT_TIMESTAMP,
        'Đổi 1 đôi New Balance Fresh Foam Hierro từ Size 42 sang Size 41 do mang không vừa',
        'HOAN_TAT',
        'Kịch bản 4: Giao dịch phức hợp đổi size hàng còn mới'
    );

    -- 3. Tạo Chi tiết trả hàng CTTH06 (Trả CTSP13 còn mới, đổi sang CTSP12 cùng SP06)
    INSERT INTO CHITIET_TRAHANG (
        MaCTTraHang, MaPhieuTra, MaCTDonHang, SoLuongTra,
        TinhTrangSanPham, HinhThucXuLy, DonGiaHoan, MaChiTietSPDoi
    )
    VALUES (
        'CTTH06', 'PT05', 'CTDH10', 1,
        'CON_MOI', 'DOI_HANG', 0.00, 'CTSP12'
    );

    -- 4. Lập Phiếu nhập kho PK19 nhận lại 1 đôi giày còn mới CTSP13 vào KHO01
    INSERT INTO PHIEUKHO (MaPhieu, LoaiPhieu, NgayLap, TrangThai, GhiChu)
    VALUES ('PK19', 'NHAP', CURRENT_TIMESTAMP, 'HOAN_TAT', 'Nhập lại giày còn mới từ phiếu trả PT05 vào kho bán');

    INSERT INTO PHIEUNHAPKHO (MaPhieu, MaNCC, MaPhieuTra, KhoDich)
    VALUES ('PK19', NULL, 'PT05', 'KHO01');

    INSERT INTO CHITIET_PHIEUKHO (MaCTPhieuKho, MaPhieu, MaChiTietSP, SoLuong, DonGia)
    VALUES ('CTPK62', 'PK19', 'CTSP13', 1, 3450000.00);

    -- Cập nhật tồn kho KHO01 cho CTSP13: Tăng SoLuongThucTe (+1) để bán tiếp
    UPDATE TONKHO
    SET SoLuongThucTe = SoLuongThucTe + 1
    WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP13';

    -- 5. Lập Phiếu xuất kho PK20 xuất 1 đôi giày CTSP12 (Size 41) giao đổi cho khách
    INSERT INTO PHIEUKHO (MaPhieu, LoaiPhieu, NgayLap, TrangThai, GhiChu)
    VALUES ('PK20', 'XUAT', CURRENT_TIMESTAMP, 'HOAN_TAT', 'Xuất giày đổi Size 41 cho phiếu trả PT05');

    INSERT INTO PHIEUXUATKHO (MaPhieu, MaDonHang, MaPhieuTra, KhoNguon)
    VALUES ('PK20', NULL, 'PT05', 'KHO01');

    INSERT INTO CHITIET_PHIEUKHO (MaCTPhieuKho, MaPhieu, MaChiTietSP, SoLuong, DonGia)
    VALUES ('CTPK63', 'PK20', 'CTSP12', 1, 3450000.00);

    -- Cập nhật tồn kho KHO01 cho CTSP12: Giảm SoLuongThucTe đi 1 đôi xuất đổi
    UPDATE TONKHO
    SET SoLuongThucTe = SoLuongThucTe - 1
    WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP12';

COMMIT;

-- Kiểm tra kết quả Giao dịch 4 (Tồn kho CTSP12 giảm 1 khả dụng; CTSP13 tăng 1 khả dụng bán lại)
SELECT
    tk.MaKho,
    tk.MaChiTietSP,
    ct.SKU,
    ct.Size,
    tk.SoLuongThucTe,
    tk.SoLuongDaGiu,
    (tk.SoLuongThucTe - tk.SoLuongDaGiu) AS SoLuongKhaDung
FROM TONKHO tk
JOIN CHITIET_SP ct ON tk.MaChiTietSP = ct.MaChiTietSP
WHERE tk.MaKho = 'KHO01'
  AND tk.MaChiTietSP IN ('CTSP12', 'CTSP13')
ORDER BY tk.MaChiTietSP;


-- =============================================================================
-- KỊCH BẢN 5: CẠNH TRANH ĐỒNG THỜI (CONCURRENCY) - TRANH MUA ĐÔI GIÀY CUỐI CÙNG
-- Bài toán thực nghiệm (Mục III.Bước 5 - Hướng dẫn đồ án):
--   "Thiết lập hai phiên giao dịch đồng thời cùng mua lượng hàng cuối cùng trong kho."
--   - Tại KHO01, biến thể CTSP18 (Biti's Hunter Core Slip-on Xám Size 40)
--     chỉ còn đúng 2 đôi cuối cùng (SoLuongThucTe = 2, SoLuongDaGiu = 0, Khả dụng = 2).
--   - Phiên 1 (Khách KH01) đặt mua 2 đôi CTSP18 tại KHO01.
--   - Phiên 2 (Khách KH02) cùng lúc cũng đặt mua 1 đôi CTSP18 tại KHO01.
--   - Tổng cầu = 3 đôi > Tồn khả dụng (2 đôi). Hệ thống phải đảm bảo chỉ Phiên 1
--     thành công, Phiên 2 bị từ chối và KHÔNG BAO GIỜ âm tồn kho khả dụng.
-- =============================================================================

-- [Phần 5.1] Mô phỏng thực thi tự động chuỗi cạnh tranh ngay trong 1 Script:
DO $$
BEGIN
    RAISE NOTICE '------------------------------------------------------------';
    RAISE NOTICE '[KỊCH BẢN 5 - TRƯỚC CẠNH TRANH] Tồn khả dụng CTSP18 tại KHO01 = % đôi',
        fn_tinh_ton_kho_kha_dung('KHO01', 'CTSP18');

    -- Giao dịch A (Khách KH01) gọi sp_tao_don_hang đặt mua 2 đôi cuối cùng của CTSP18 tại KHO01
    CALL sp_tao_don_hang(
        'DH14_SESSION_A', 'KH01', 'KHO01',
        '25 Lê Lợi, Q.1, TP.HCM', 'CHUYEN_KHOAN',
        '[{"ma_ctsp": "CTSP18", "so_luong": 2, "chiet_khau": 0}]'::JSONB,
        'Phiên A: Mua 2 đôi CTSP18 cuối cùng tại KHO01'
    );

    RAISE NOTICE '[KỊCH BẢN 5 - SAU PHIÊN A] Phiên A đặt thành công 2 đôi! Tồn khả dụng CTSP18 tại KHO01 còn = % đôi',
        fn_tinh_ton_kho_kha_dung('KHO01', 'CTSP18');

    -- Giao dịch B (Khách KH02) tiếp tục cố đặt mua 1 đôi CTSP18 tại KHO01 khi tồn khả dụng đã về 0
    BEGIN
        CALL sp_tao_don_hang(
            'DH15_SESSION_B', 'KH02', 'KHO01',
            '14 Trần Duy Hưng, Cầu Giấy, Hà Nội', 'COD',
            '[{"ma_ctsp": "CTSP18", "so_luong": 1, "chiet_khau": 0}]'::JSONB,
            'Phiên B: Cạnh tranh mua 1 đôi CTSP18 tại KHO01'
        );
    EXCEPTION
        WHEN OTHERS THEN
            RAISE NOTICE '[KỊCH BẢN 5 - PHIÊN B BỊ CHẶN AN TOÀN] %', SQLERRM;
    END;
END;
$$ LANGUAGE plpgsql;

-- Kiểm tra trạng thái CTSP18 tại KHO01 sau cạnh tranh:
-- SoLuongThucTe = 2, SoLuongDaGiu = 2, SoLuongKhaDung = 0 (Không bị âm!)
SELECT
    tk.MaKho,
    tk.MaChiTietSP,
    ct.SKU,
    tk.SoLuongThucTe,
    tk.SoLuongDaGiu,
    (tk.SoLuongThucTe - tk.SoLuongDaGiu) AS SoLuongKhaDung
FROM TONKHO tk
JOIN CHITIET_SP ct ON tk.MaChiTietSP = ct.MaChiTietSP
WHERE tk.MaKho = 'KHO01'
  AND tk.MaChiTietSP = 'CTSP18';

/*
-- =============================================================================
-- [Phần 5.2] HƯỚNG DẪN CHẠY THỰC NGHIỆM 2 CỬA SỔ ĐỒNG THỜI TRONG DBEAVER
-- (Dành cho phần demo trực tiếp 2 phiên song song khi bảo vệ đồ án)
-- Cách mở 2 phiên độc lập trong DBeaver:
--   Chuột phải vào kết nối "ql_banhang (localhost:5433)" -> SQL Editor -> New SQL Script
--   (Mở 2 tab SQL Script riêng biệt: Tab 1 là SESSION A, Tab 2 là SESSION B).
-- =============================================================================

-- ---------------- [TAB 1 - SESSION A (Chạy trước)] ----------------
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- Bước A1: Khóa dòng tồn kho CTSP18 tại KHO01 để kiểm tra và giữ 2 đôi cuối
SELECT MaKho, MaChiTietSP, SoLuongThucTe, SoLuongDaGiu,
       (SoLuongThucTe - SoLuongDaGiu) AS SoLuongKhaDung
FROM TONKHO
WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP18'
FOR UPDATE;

-- Bước A2: Tạo đơn hàng và giữ 2 đôi cuối cùng (CHƯA COMMIT ngay để chuyển sang Tab 2)
INSERT INTO DONHANG (MaDonHang, MaKH, MaKho, TrangThai, PhuongThucThanhToan, DiaChiGiaoHang)
VALUES ('DH_CONC_A', 'KH01', 'KHO01', 'DA_XAC_NHAN', 'CHUYEN_KHOAN', '25 Lê Lợi, Q.1, TP.HCM');

INSERT INTO CHITIET_DONHANG (MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau)
VALUES ('CTDH_CONC_A', 'DH_CONC_A', 'CTSP18', 2, 850000.00, 0.00);

UPDATE TONKHO
SET SoLuongDaGiu = SoLuongDaGiu + 2
WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP18';

-- (Lúc này chuyển sang TAB 2 chạy Bước B1 -> Tab 2 sẽ đứng chờ khóa! Sau đó quay lại Tab 1 chạy Bước A3):
-- Bước A3:
COMMIT;


-- ---------------- [TAB 2 - SESSION B (Chạy đồng thời)] ----------------
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- Bước B1: Cố gắng khóa và đặt mua 1 đôi CTSP18 tại KHO01
-- -> Câu lệnh này sẽ BỊ CHẶN (WAITING) cho tới khi Tab 1 nhấn COMMIT!
SELECT MaKho, MaChiTietSP, SoLuongThucTe, SoLuongDaGiu,
       (SoLuongThucTe - SoLuongDaGiu) AS SoLuongKhaDung
FROM TONKHO
WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP18'
FOR UPDATE;

-- Bước B2: Ngay khi Tab 1 COMMIT, Tab 2 đọc được SoLuongDaGiu = 2 (SoLuongKhaDung = 0).
-- Nếu Tab 2 cố tình UPDATE tăng SoLuongDaGiu thêm 1 -> Trigger trg_02_tonkho_kiem_tra_da_giu
-- lập tức báo lỗi và chặn đứng, buộc Tab 2 phải ROLLBACK:
UPDATE TONKHO
SET SoLuongDaGiu = SoLuongDaGiu + 1
WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP18';

ROLLBACK;
*/


-- =============================================================================
-- KỊCH BẢN 6: PHÒNG CHỐNG DEADLOCK BẰNG QUY TẮC SẮP XẾP KHÓA (LOCK ORDERING)
-- Lý thuyết & Giải pháp trong hệ thống:
--   - Nguyên nhân Deadlock (SQLSTATE 40P01):
--       + Phiên 1 mua (CTSP01, CTSP02): Khóa CTSP01 trước -> chờ khóa CTSP02.
--       + Phiên 2 mua (CTSP02, CTSP01): Khóa CTSP02 trước -> chờ khóa CTSP01.
--   - Giải pháp đã áp dụng trong sp_tao_don_hang và sp_chuyen_kho:
--       + Dù mảng JSON đầu vào truyền thứ tự sản phẩm bất kỳ, hệ thống luôn
--         gom nhóm và khóa các dòng TONKHO theo thứ tự tăng dần cố định:
--         ORDER BY MaKho ASC, MaChiTietSP ASC FOR UPDATE.
-- =============================================================================

-- Minh chứng truy vấn khóa theo thứ tự chuẩn dù danh sách đầu vào bị đảo ngược:
BEGIN;

    SELECT
        tk.MaKho,
        tk.MaChiTietSP,
        ct.SKU,
        (tk.SoLuongThucTe - tk.SoLuongDaGiu) AS SoLuongKhaDung
    FROM TONKHO tk
    JOIN CHITIET_SP ct ON tk.MaChiTietSP = ct.MaChiTietSP
    WHERE tk.MaKho = 'KHO01'
      AND tk.MaChiTietSP IN ('CTSP07', 'CTSP01', 'CTSP03') -- Thứ tự đầu vào lộn xộn
    ORDER BY tk.MaKho ASC, tk.MaChiTietSP ASC              -- Luôn khóa theo thứ tự: CTSP01 -> CTSP03 -> CTSP07
    FOR UPDATE;

COMMIT;


-- =============================================================================
-- KHỐI DỌN DẸP & KHÔI PHỤC DỮ LIỆU GỐC (REPRODUCIBILITY CLEANUP)
-- Mô tả:
--   Hoàn trả các bản ghi thử nghiệm phát sinh trong Kịch bản 1, 3, 4, 5 về đúng
--   trạng thái chuẩn của 02_insert_data.sql để script có thể chạy lại nhiều lần
--   (Reproducible) mà không làm sai lệch số liệu báo cáo của Phần 3.
-- =============================================================================

BEGIN;

    -- 1. Khôi phục tồn kho KHO01 về trạng thái chuẩn sau 02_insert_data.sql
    UPDATE TONKHO SET SoLuongThucTe = 29, SoLuongDaGiu = 2 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP01';
    UPDATE TONKHO SET SoLuongThucTe = 23, SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP02';
    UPDATE TONKHO SET SoLuongThucTe = 4,  SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP03';
    UPDATE TONKHO SET SoLuongThucTe = 15, SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP07';
    UPDATE TONKHO SET SoLuongThucTe = 25, SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP08';
    UPDATE TONKHO SET SoLuongThucTe = 38, SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP12';
    UPDATE TONKHO SET SoLuongThucTe = 33, SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP13';
    UPDATE TONKHO SET SoLuongThucTe = 2,  SoLuongDaGiu = 0 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP18';

    -- 2. Xóa các bản ghi thử nghiệm phát sinh trong Kịch bản 1, 3, 4, 5
    DELETE FROM CHITIET_PHIEUKHO WHERE MaPhieu IN ('PK19', 'PK20');
    DELETE FROM PHIEUNHAPKHO WHERE MaPhieu = 'PK19';
    DELETE FROM PHIEUXUATKHO WHERE MaPhieu = 'PK20';
    DELETE FROM PHIEUKHO WHERE MaPhieu IN ('PK19', 'PK20');

    DELETE FROM CHITIET_TRAHANG WHERE MaPhieuTra = 'PT05';
    DELETE FROM PHIEUTRAHANG WHERE MaPhieuTra = 'PT05';

    DELETE FROM CHITIET_DONHANG WHERE MaDonHang IN ('DH11', 'DH13', 'DH14_SESSION_A');
    DELETE FROM DONHANG WHERE MaDonHang IN ('DH11', 'DH13', 'DH14_SESSION_A');

COMMIT;
