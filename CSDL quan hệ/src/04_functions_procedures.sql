-- =============================================================================
-- ĐỒ ÁN CƠ SỞ DỮ LIỆU NÂNG CAO - ĐỀ TÀI 2
-- Hệ thống Quản lý Bán hàng & Tồn kho Đa kho (Chuyên ngành Giày)
-- Hệ quản trị CSDL: PostgreSQL 17
-- File: 04_functions_procedures.sql
-- Mô tả: Các Function, Stored Procedure và Dynamic SQL nghiệp vụ:
--   1. fn_tinh_ton_kho_kha_dung      - Tính tồn kho khả dụng tại một kho
--   2. fn_tinh_tong_tien_don_hang    - Tính tổng giá trị của một đơn hàng
--   3. sp_tao_don_hang               - Tạo đơn hàng, chi tiết & giữ tồn kho
--   4. sp_huy_don_hang               - Hủy đơn hàng & giải phóng hàng đã giữ
--   5. sp_chuyen_kho                 - Chuyển hàng từ kho nguồn sang kho đích
--      (+ sp_xac_nhan_nhan_chuyen_kho hỗ trợ hoàn tất phiếu đang vận chuyển)
--   6. fn_bao_cao_doanh_thu_dong     - Dynamic SQL lọc doanh thu theo kho, ngày, danh mục
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Function tính tồn kho khả dụng tại một kho
--    Công thức: SoLuongThucTe - SoLuongDaGiu
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_tinh_ton_kho_kha_dung(
    p_ma_kho        VARCHAR(20),
    p_ma_chitiet_sp VARCHAR(20)
)
RETURNS INTEGER
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_kha_dung INTEGER;
BEGIN
    SELECT (SoLuongThucTe - SoLuongDaGiu)
    INTO v_kha_dung
    FROM TONKHO
    WHERE MaKho = p_ma_kho
      AND MaChiTietSP = p_ma_chitiet_sp;

    RETURN COALESCE(v_kha_dung, 0);
END;
$$;

-- -----------------------------------------------------------------------------
-- 2. Function tính tổng tiền đơn hàng
--    Công thức dòng: SoLuong * DonGiaBan * (1 - ChietKhau / 100.0)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_tinh_tong_tien_don_hang(
    p_ma_don_hang VARCHAR(20)
)
RETURNS NUMERIC(14, 2)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_tong_tien NUMERIC(14, 2);
BEGIN
    IF NOT EXISTS (SELECT 1 FROM DONHANG WHERE MaDonHang = p_ma_don_hang) THEN
        RAISE EXCEPTION 'Đơn hàng % không tồn tại.', p_ma_don_hang;
    END IF;

    SELECT COALESCE(
        ROUND(SUM(SoLuong * DonGiaBan * (1 - ChietKhau / 100.0)), 2),
        0.00
    )
    INTO v_tong_tien
    FROM CHITIET_DONHANG
    WHERE MaDonHang = p_ma_don_hang;

    RETURN v_tong_tien;
END;
$$;

-- -----------------------------------------------------------------------------
-- 3. Procedure tạo đơn hàng
--    p_chi_tiet là JSONB array:
--      [{"ma_ctsp": "CTSP01", "so_luong": 2, "chiet_khau": 5.0}, ...]
--      (Có thể truyền thêm "don_gia_ban" và "ma_ct_don_hang"; nếu bỏ qua sẽ tự
--       lấy GiaNiemYet của SANPHAM và tự sinh mã dòng chi tiết).
--    Khóa dòng TONKHO theo thứ tự MaChiTietSP tăng dần (FOR UPDATE) để tránh deadlock.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_tao_don_hang(
    p_ma_don_hang VARCHAR(20),
    p_ma_kh       VARCHAR(20),
    p_ma_kho      VARCHAR(20),
    p_dia_chi     VARCHAR(255),
    p_pttt        VARCHAR(50),
    p_chi_tiet    JSONB,
    p_ghi_chu     TEXT DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_trang_thai_kho VARCHAR(30);
    v_item           RECORD;
    v_tt_ctsp        VARCHAR(30);
    v_tt_sp          VARCHAR(30);
    v_gia_niem_yet   NUMERIC(14, 2);
    v_don_gia_ban    NUMERIC(14, 2);
    v_kha_dung       INTEGER;
    v_ma_ct          VARCHAR(20);
    v_idx            INTEGER := 0;
BEGIN
    IF EXISTS (SELECT 1 FROM DONHANG WHERE MaDonHang = p_ma_don_hang) THEN -- kiểm tra đơn hàng đã tồn tại chưa 
        RAISE EXCEPTION 'Mã đơn hàng % đã tồn tại.', p_ma_don_hang;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM KHACHHANG WHERE MaKH = p_ma_kh) THEN -- kiểm tra khách hàng có tồn tại không
        RAISE EXCEPTION 'Khách hàng % không tồn tại.', p_ma_kh; 
    END IF;

    SELECT TrangThai INTO v_trang_thai_kho -- 
    FROM KHO
    WHERE MaKho = p_ma_kho;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Kho % không tồn tại.', p_ma_kho;
    ELSIF v_trang_thai_kho <> 'HOAT_DONG' THEN
        RAISE EXCEPTION 'Kho % đang ở trạng thái %, không thể tiếp nhận đơn hàng mới.', p_ma_kho, v_trang_thai_kho;
    END IF;

    IF p_chi_tiet IS NULL OR jsonb_typeof(p_chi_tiet) <> 'array' OR jsonb_array_length(p_chi_tiet) = 0 THEN
        RAISE EXCEPTION 'Đơn hàng % phải có ít nhất một dòng chi tiết.', p_ma_don_hang;
    END IF;

    -- Khởi tạo đơn ở trạng thái DA_XAC_NHAN để giữ hàng (SoLuongDaGiu)
    INSERT INTO DONHANG (
        MaDonHang, NgayDat, TrangThai, DiaChiGiaoHang,
        PhuongThucThanhToan, GhiChu, MaKH, MaKho
    ) VALUES (
        p_ma_don_hang, CURRENT_TIMESTAMP, 'DA_XAC_NHAN', p_dia_chi,
        p_pttt, p_ghi_chu, p_ma_kh, p_ma_kho
    );

    -- Duyệt theo thứ tự MaChiTietSP để tránh deadlock khi giao dịch đồng thời
    FOR v_item IN
        SELECT
            elem->>'ma_ctsp'                         AS ma_ctsp,
            (elem->>'so_luong')::INTEGER             AS so_luong,
            COALESCE((elem->>'chiet_khau')::NUMERIC(5, 2), 0.00) AS chiet_khau,
            (elem->>'don_gia_ban')::NUMERIC(14, 2)   AS don_gia_ban,
            elem->>'ma_ct_don_hang'                  AS ma_ct_don_hang
        FROM jsonb_array_elements(p_chi_tiet) AS elem
        ORDER BY elem->>'ma_ctsp'
    LOOP
        v_idx := v_idx + 1;

        IF v_item.ma_ctsp IS NULL OR v_item.so_luong IS NULL OR v_item.so_luong <= 0 THEN
            RAISE EXCEPTION 'Chi tiết thứ % không hợp lệ (mã biến thể hoặc số lượng <= 0).', v_idx;
        END IF;

        SELECT ctsp.TrangThai, sp.TrangThai, sp.GiaNiemYet
        INTO v_tt_ctsp, v_tt_sp, v_gia_niem_yet
        FROM CHITIET_SP ctsp
        JOIN SANPHAM sp ON ctsp.MaSanPham = sp.MaSanPham
        WHERE ctsp.MaChiTietSP = v_item.ma_ctsp;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Biến thể sản phẩm % không tồn tại.', v_item.ma_ctsp;
        ELSIF v_tt_ctsp <> 'DANG_BAN' OR v_tt_sp <> 'DANG_KINH_DOANH' THEN
            RAISE EXCEPTION 'Biến thể % hiện không mở bán (trạng thái SP: %, biến thể: %).',
                v_item.ma_ctsp, v_tt_sp, v_tt_ctsp;
        END IF;

        -- Khóa dòng tồn kho trước khi kiểm tra khả dụng
        SELECT (SoLuongThucTe - SoLuongDaGiu)
        INTO v_kha_dung
        FROM TONKHO
        WHERE MaKho = p_ma_kho
          AND MaChiTietSP = v_item.ma_ctsp
        FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Biến thể % chưa có tồn kho tại kho %.', v_item.ma_ctsp, p_ma_kho;
        ELSIF v_kha_dung < v_item.so_luong THEN
            RAISE EXCEPTION 'Không đủ tồn khả dụng cho % tại kho % (cần %, còn %).',
                v_item.ma_ctsp, p_ma_kho, v_item.so_luong, v_kha_dung;
        END IF;

        v_don_gia_ban := COALESCE(v_item.don_gia_ban, v_gia_niem_yet);
        v_ma_ct := COALESCE(
            v_item.ma_ct_don_hang,
            LEFT(p_ma_don_hang, 14) || '_CT' || LPAD(v_idx::TEXT, 3, '0')
        );

        INSERT INTO CHITIET_DONHANG (
            MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau
        ) VALUES (
            v_ma_ct, p_ma_don_hang, v_item.ma_ctsp, v_item.so_luong, v_don_gia_ban, v_item.chiet_khau
        );

        UPDATE TONKHO
        SET SoLuongDaGiu = SoLuongDaGiu + v_item.so_luong,
            NgayCapNhat  = CURRENT_TIMESTAMP
        WHERE MaKho = p_ma_kho
          AND MaChiTietSP = v_item.ma_ctsp;
    END LOOP;
END;
$$;

-- -----------------------------------------------------------------------------
-- 4. Procedure hủy đơn hàng và giải phóng số lượng hàng đã giữ
--    Chỉ cho phép hủy khi đơn chưa xuất kho (CHO_XAC_NHAN, DA_XAC_NHAN, DANG_XU_LY).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_huy_don_hang(
    p_ma_don_hang VARCHAR(20),
    p_ly_do       TEXT DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_trang_thai VARCHAR(30);
    v_ma_kho     VARCHAR(20);
    v_ct         RECORD;
BEGIN
    SELECT TrangThai, MaKho
    INTO v_trang_thai, v_ma_kho
    FROM DONHANG
    WHERE MaDonHang = p_ma_don_hang
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Đơn hàng % không tồn tại.', p_ma_don_hang;
    END IF;

    IF v_trang_thai = 'DA_HUY' THEN
        RAISE EXCEPTION 'Đơn hàng % đã được hủy trước đó.', p_ma_don_hang;
    ELSIF v_trang_thai IN ('DANG_GIAO', 'HOAN_TAT') THEN
        RAISE EXCEPTION 'Không thể hủy đơn hàng % ở trạng thái %; vui lòng lập phiếu trả hàng.',
            p_ma_don_hang, v_trang_thai;
    END IF;

    -- Chỉ giải phóng SoLuongDaGiu nếu đơn đã qua bước giữ hàng
    IF v_trang_thai IN ('DA_XAC_NHAN', 'DANG_XU_LY') THEN
        FOR v_ct IN
            SELECT MaChiTietSP, SoLuong
            FROM CHITIET_DONHANG
            WHERE MaDonHang = p_ma_don_hang
            ORDER BY MaChiTietSP
        LOOP
            PERFORM 1
            FROM TONKHO
            WHERE MaKho = v_ma_kho
              AND MaChiTietSP = v_ct.MaChiTietSP
            FOR UPDATE;

            UPDATE TONKHO
            SET SoLuongDaGiu = SoLuongDaGiu - v_ct.SoLuong,
                NgayCapNhat  = CURRENT_TIMESTAMP
            WHERE MaKho = v_ma_kho
              AND MaChiTietSP = v_ct.MaChiTietSP;
        END LOOP;
    END IF;

    UPDATE DONHANG
    SET TrangThai = 'DA_HUY',
        GhiChu    = CASE
                        WHEN p_ly_do IS NULL OR BTRIM(p_ly_do) = '' THEN GhiChu
                        WHEN GhiChu IS NULL OR BTRIM(GhiChu) = '' THEN 'Hủy đơn: ' || p_ly_do
                        ELSE GhiChu || ' | Hủy đơn: ' || p_ly_do
                    END
    WHERE MaDonHang = p_ma_don_hang;
END;
$$;

-- -----------------------------------------------------------------------------
-- 5. Procedure chuyển kho từ kho nguồn sang kho đích
--    p_chi_tiet là JSONB array:
--      [{"ma_ctsp": "CTSP01", "so_luong": 5, "don_gia": null}, ...]
--    p_hoan_tat = TRUE : chuyển hoàn tất ngay (trừ kho nguồn & cộng kho đích).
--    p_hoan_tat = FALSE: lập phiếu DANG_VAN_CHUYEN (chỉ trừ kho nguồn; khi kho đích
--                        nhận hàng sẽ gọi sp_xac_nhan_nhan_chuyen_kho).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_chuyen_kho(
    p_ma_phieu  VARCHAR(20),
    p_kho_nguon VARCHAR(20),
    p_kho_dich  VARCHAR(20),
    p_chi_tiet  JSONB,
    p_hoan_tat  BOOLEAN DEFAULT TRUE,
    p_ghi_chu   TEXT DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_tt_nguon VARCHAR(30);
    v_tt_dich  VARCHAR(30);
    v_item     RECORD;
    v_kha_dung INTEGER;
    v_ma_ctpk  VARCHAR(20);
    v_idx      INTEGER := 0;
BEGIN
    IF p_kho_nguon = p_kho_dich THEN
        RAISE EXCEPTION 'Kho nguồn và kho đích không được trùng nhau (%).', p_kho_nguon;
    END IF;

    IF EXISTS (SELECT 1 FROM PHIEUKHO WHERE MaPhieu = p_ma_phieu) THEN
        RAISE EXCEPTION 'Mã phiếu kho % đã tồn tại.', p_ma_phieu;
    END IF;

    SELECT TrangThai INTO v_tt_nguon FROM KHO WHERE MaKho = p_kho_nguon;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Kho nguồn % không tồn tại.', p_kho_nguon;
    ELSIF v_tt_nguon <> 'HOAT_DONG' THEN
        RAISE EXCEPTION 'Kho nguồn % đang ở trạng thái %, không thể xuất chuyển.', p_kho_nguon, v_tt_nguon;
    END IF;

    SELECT TrangThai INTO v_tt_dich FROM KHO WHERE MaKho = p_kho_dich;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Kho đích % không tồn tại.', p_kho_dich;
    ELSIF v_tt_dich <> 'HOAT_DONG' THEN
        RAISE EXCEPTION 'Kho đích % đang ở trạng thái %, không thể nhận chuyển.', p_kho_dich, v_tt_dich;
    END IF;

    IF p_chi_tiet IS NULL OR jsonb_typeof(p_chi_tiet) <> 'array' OR jsonb_array_length(p_chi_tiet) = 0 THEN
        RAISE EXCEPTION 'Phiếu chuyển kho % phải có ít nhất một dòng chi tiết.', p_ma_phieu;
    END IF;

    INSERT INTO PHIEUKHO (MaPhieu, LoaiPhieu, NgayLap, TrangThai, GhiChu)
    VALUES (
        p_ma_phieu,
        'CHUYEN',
        CURRENT_TIMESTAMP,
        CASE WHEN p_hoan_tat THEN 'HOAN_TAT' ELSE 'DANG_VAN_CHUYEN' END,
        p_ghi_chu
    );

    INSERT INTO PHIEUCHUYENKHO (MaPhieu, KhoNguon, KhoDich)
    VALUES (p_ma_phieu, p_kho_nguon, p_kho_dich);

    FOR v_item IN
        SELECT
            elem->>'ma_ctsp'                       AS ma_ctsp,
            (elem->>'so_luong')::INTEGER           AS so_luong,
            (elem->>'don_gia')::NUMERIC(14, 2)     AS don_gia,
            elem->>'ma_ct_phieu_kho'               AS ma_ct_phieu_kho
        FROM jsonb_array_elements(p_chi_tiet) AS elem
        ORDER BY elem->>'ma_ctsp'
    LOOP
        v_idx := v_idx + 1;

        IF v_item.ma_ctsp IS NULL OR v_item.so_luong IS NULL OR v_item.so_luong <= 0 THEN
            RAISE EXCEPTION 'Dòng chuyển kho thứ % không hợp lệ.', v_idx;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM CHITIET_SP WHERE MaChiTietSP = v_item.ma_ctsp) THEN
            RAISE EXCEPTION 'Biến thể % không tồn tại.', v_item.ma_ctsp;
        END IF;

        SELECT (SoLuongThucTe - SoLuongDaGiu)
        INTO v_kha_dung
        FROM TONKHO
        WHERE MaKho = p_kho_nguon
          AND MaChiTietSP = v_item.ma_ctsp
        FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Biến thể % chưa có tồn kho tại kho nguồn %.', v_item.ma_ctsp, p_kho_nguon;
        ELSIF v_kha_dung < v_item.so_luong THEN
            RAISE EXCEPTION 'Kho nguồn % không đủ tồn khả dụng cho % (cần %, còn %).',
                p_kho_nguon, v_item.ma_ctsp, v_item.so_luong, v_kha_dung;
        END IF;

        UPDATE TONKHO
        SET SoLuongThucTe = SoLuongThucTe - v_item.so_luong,
            NgayCapNhat   = CURRENT_TIMESTAMP
        WHERE MaKho = p_kho_nguon
          AND MaChiTietSP = v_item.ma_ctsp;

        v_ma_ctpk := COALESCE(
            v_item.ma_ct_phieu_kho,
            LEFT(p_ma_phieu, 13) || '_CT' || LPAD(v_idx::TEXT, 3, '0')
        );

        INSERT INTO CHITIET_PHIEUKHO (MaCTPhieuKho, MaPhieu, MaChiTietSP, SoLuong, DonGia)
        VALUES (v_ma_ctpk, p_ma_phieu, v_item.ma_ctsp, v_item.so_luong, v_item.don_gia);

        IF p_hoan_tat THEN
            INSERT INTO TONKHO (MaKho, MaChiTietSP, SoLuongThucTe, SoLuongDaGiu, NgayCapNhat)
            VALUES (p_kho_dich, v_item.ma_ctsp, v_item.so_luong, 0, CURRENT_TIMESTAMP)
            ON CONFLICT (MaKho, MaChiTietSP) DO UPDATE
            SET SoLuongThucTe = TONKHO.SoLuongThucTe + EXCLUDED.SoLuongThucTe,
                NgayCapNhat   = CURRENT_TIMESTAMP;
        END IF;
    END LOOP;
END;
$$;

-- Thủ tục bổ trợ: Xác nhận kho đích đã nhận hàng cho phiếu chuyển DANG_VAN_CHUYEN
CREATE OR REPLACE PROCEDURE sp_xac_nhan_nhan_chuyen_kho(
    p_ma_phieu VARCHAR(20)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_loai       VARCHAR(10);
    v_trang_thai VARCHAR(30);
    v_kho_dich   VARCHAR(20);
    v_ct         RECORD;
BEGIN
    SELECT pk.LoaiPhieu, pk.TrangThai, pck.KhoDich
    INTO v_loai, v_trang_thai, v_kho_dich
    FROM PHIEUKHO pk
    JOIN PHIEUCHUYENKHO pck ON pk.MaPhieu = pck.MaPhieu
    WHERE pk.MaPhieu = p_ma_phieu
    FOR UPDATE OF pk;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Phiếu chuyển kho % không tồn tại.', p_ma_phieu;
    ELSIF v_loai <> 'CHUYEN' OR v_trang_thai <> 'DANG_VAN_CHUYEN' THEN
        RAISE EXCEPTION 'Phiếu % không ở trạng thái DANG_VAN_CHUYEN (hiện tại: % / %).',
            p_ma_phieu, v_loai, v_trang_thai;
    END IF;

    FOR v_ct IN
        SELECT MaChiTietSP, SoLuong
        FROM CHITIET_PHIEUKHO
        WHERE MaPhieu = p_ma_phieu
        ORDER BY MaChiTietSP
    LOOP
        INSERT INTO TONKHO (MaKho, MaChiTietSP, SoLuongThucTe, SoLuongDaGiu, NgayCapNhat)
        VALUES (v_kho_dich, v_ct.MaChiTietSP, v_ct.SoLuong, 0, CURRENT_TIMESTAMP)
        ON CONFLICT (MaKho, MaChiTietSP) DO UPDATE
        SET SoLuongThucTe = TONKHO.SoLuongThucTe + EXCLUDED.SoLuongThucTe,
            NgayCapNhat   = CURRENT_TIMESTAMP;
    END LOOP;

    UPDATE PHIEUKHO
    SET TrangThai = 'HOAN_TAT'
    WHERE MaPhieu = p_ma_phieu;
END;
$$;

-- -----------------------------------------------------------------------------
-- 6. Dynamic SQL báo cáo doanh thu
--    Cho phép lọc linh hoạt theo kho, khoảng thời gian và danh mục sản phẩm
--    (khi lọc theo danh mục cha, tự động bao gồm cả các danh mục con/cháu qua Recursive CTE).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_bao_cao_doanh_thu_dong(
    p_ma_kho      VARCHAR(20) DEFAULT NULL,
    p_tu_ngay     TIMESTAMP   DEFAULT NULL,
    p_den_ngay    TIMESTAMP   DEFAULT NULL,
    p_ma_danh_muc VARCHAR(20) DEFAULT NULL
)
RETURNS TABLE (
    MaKho          VARCHAR(20),
    TenKho         VARCHAR(120),
    MaDanhMuc      VARCHAR(20),
    TenDanhMuc     VARCHAR(100),
    SoDonHang      BIGINT,
    TongSoLuongBan BIGINT,
    DoanhThu       NUMERIC(14, 2)
)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_sql   TEXT;
    v_where TEXT := ' WHERE dh.TrangThai = ''HOAN_TAT''';
BEGIN
    IF p_ma_kho IS NOT NULL THEN
        v_where := v_where || ' AND dh.MaKho = $1';
    END IF;

    IF p_tu_ngay IS NOT NULL THEN
        v_where := v_where || ' AND dh.NgayDat >= $2';
    END IF;

    IF p_den_ngay IS NOT NULL THEN
        v_where := v_where || ' AND dh.NgayDat <= $3';
    END IF;

    IF p_ma_danh_muc IS NOT NULL THEN
        v_where := v_where || ' AND sp.MaDanhMuc IN (SELECT MaDanhMuc FROM dm_loc)';
    END IF;

    v_sql := '
        WITH RECURSIVE dm_loc AS (
            SELECT MaDanhMuc
            FROM DANHMUC_SP
            WHERE MaDanhMuc = $4
            UNION ALL
            SELECT dm.MaDanhMuc
            FROM DANHMUC_SP dm
            JOIN dm_loc l ON dm.MaDanhMucCha = l.MaDanhMuc
        )
        SELECT
            k.MaKho,
            k.TenKho,
            dm.MaDanhMuc,
            dm.TenDanhMuc,
            COUNT(DISTINCT dh.MaDonHang)::BIGINT                      AS SoDonHang,
            COALESCE(SUM(ctdh.SoLuong), 0)::BIGINT                    AS TongSoLuongBan,
            COALESCE(
                ROUND(SUM(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0)), 2),
                0.00
            )::NUMERIC(14, 2)                                         AS DoanhThu
        FROM DONHANG dh
        JOIN KHO k                ON dh.MaKho = k.MaKho
        JOIN CHITIET_DONHANG ctdh ON dh.MaDonHang = ctdh.MaDonHang
        JOIN CHITIET_SP ctsp      ON ctdh.MaChiTietSP = ctsp.MaChiTietSP
        JOIN SANPHAM sp           ON ctsp.MaSanPham = sp.MaSanPham
        JOIN DANHMUC_SP dm        ON sp.MaDanhMuc = dm.MaDanhMuc
        ' || v_where || '
        GROUP BY k.MaKho, k.TenKho, dm.MaDanhMuc, dm.TenDanhMuc
        ORDER BY DoanhThu DESC, k.MaKho, dm.MaDanhMuc';

    RETURN QUERY EXECUTE v_sql
        USING p_ma_kho, p_tu_ngay, p_den_ngay, p_ma_danh_muc;
END;
$$;
