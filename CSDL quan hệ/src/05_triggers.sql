-- =============================================================================
-- ĐỒ ÁN CƠ SỞ DỮ LIỆU NÂNG CAO - ĐỀ TÀI 2
-- Hệ thống Quản lý Bán hàng & Tồn kho Đa kho (Chuyên ngành Giày)
-- Hệ quản trị CSDL: PostgreSQL 17
-- File: 05_triggers.sql
-- Mô tả: Toàn bộ 8 nhóm Trigger bảo vệ tính toàn vẹn dữ liệu & tự động hóa:
--   1. Kiểm tra số lượng tồn kho không được âm (TONKHO)
--   2. Kiểm tra số lượng hàng đã giữ / bị khóa không vượt quá thực tế (TONKHO)
--   3. Tự động cập nhật thời gian cập nhật tồn kho NgayCapNhat (TONKHO)
--   4. Ngăn chặn việc xóa dữ liệu cha đang được tham chiếu nghiệp vụ (11 bảng)
--   5. Kiểm tra chuyên biệt hóa rời rạc (ISA Disjoint) giữa PHIEUKHO và 3 bảng con
--   6. Chống chu trình phân cấp trên danh mục đa cấp DANHMUC_SP
--   7. Kiểm tra hợp lệ CHITIET_TRAHANG (đúng đơn gốc, không trả lố, đổi cùng SP)
--   8. Bảo đảm mỗi PHIEUKHO phải có ít nhất 1 dòng chi tiết CHITIET_PHIEUKHO
--
-- GHI CHÚ CƠ CHẾ KÍCH HOẠT (HÀM/THỦ TỤC NÀO GỌI CÁC TRIGGER NÀY?):
--   Trong PostgreSQL, Trigger chạy TỰ ĐỘNG khi có lệnh DML (INSERT/UPDATE/DELETE)
--   lên bảng được gắn Trigger. Mối liên hệ giữa Trigger <-> Hàm Trigger <-> Thủ tục gọi:
--   + Nhóm 1, 2, 3 (trên TONKHO):
--       - Hàm Trigger: fn_trg_tonkho_khong_am(), fn_trg_tonkho_kiem_tra_da_giu(),
--                      fn_trg_tonkho_cap_nhat_thoi_gian()
--       - Thủ tục/Script kích hoạt: sp_tao_don_hang(), sp_huy_don_hang(),
--         sp_chuyen_kho(), sp_xac_nhan_nhan_chuyen_kho() [04_functions_procedures.sql];
--         02_insert_data.sql; các kịch bản 1, 2, 3, 4, 5 trong 06_transactions.sql.
--   + Nhóm 4 (trên 11 bảng cha):
--       - Hàm Trigger: fn_trg_ngan_xoa_tham_chieu()
--       - Kích hoạt bởi: Mọi lệnh DELETE trên các bảng cha khi còn dữ liệu con
--         (ví dụ: nếu xóa DONHANG trước CHITIET_DONHANG ở 06_transactions.sql).
--   + Nhóm 5 & Nhóm 8 (trên PHIEUKHO, PHIEUNHAPKHO, PHIEUXUATKHO, PHIEUCHUYENKHO, CHITIET_PHIEUKHO):
--       - Hàm Trigger: fn_trg_phieukho_kiem_tra_isa_con(), fn_trg_phieukho_kiem_tra_isa_cha(),
--                      fn_trg_phieukho_toi_thieu_chi_tiet()
--       - Thủ tục/Script kích hoạt: sp_chuyen_kho(), sp_xac_nhan_nhan_chuyen_kho()
--         [04_functions_procedures.sql]; 02_insert_data.sql; Kịch bản 4 (PK19, PK20)
--         và khối cleanup trong 06_transactions.sql.
--   + Nhóm 6 (trên DANHMUC_SP):
--       - Hàm Trigger: fn_trg_danhmuc_chong_chu_trinh()
--       - Kích hoạt bởi: INSERT/UPDATE trên DANHMUC_SP (02_insert_data.sql), bảo vệ
--         cây danh mục cho truy vấn đệ quy Câu 12 [03_queries.sql] và
--         fn_bao_cao_doanh_thu_dong() [04_functions_procedures.sql].
--   + Nhóm 7 (trên PHIEUTRAHANG & CHITIET_TRAHANG):
--       - Hàm Trigger: fn_trg_phieutrahang_don_hoan_tat(), fn_trg_chitiet_trahang_hop_le()
--       - Kích hoạt bởi: INSERT/UPDATE trên PHIEUTRAHANG, CHITIET_TRAHANG
--         (02_insert_data.sql và Kịch bản 4 PT05/CTTH06 trong 06_transactions.sql).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Trigger 1: Kiểm tra các số lượng tồn kho không được âm
-- Hàm Trigger: fn_trg_tonkho_khong_am()
-- Kích hoạt khi INSERT/UPDATE trên TONKHO từ: sp_tao_don_hang, sp_huy_don_hang,
-- sp_chuyen_kho, sp_xac_nhan_nhan_chuyen_kho, 02_insert_data.sql, 06_transactions.sql.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_trg_tonkho_khong_am()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.SoLuongThucTe < 0 THEN
        RAISE EXCEPTION 'Lỗi tồn kho tại (%, %): SoLuongThucTe (%) không được âm.',
            NEW.MaKho, NEW.MaChiTietSP, NEW.SoLuongThucTe
            USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.SoLuongDaGiu < 0 THEN
        RAISE EXCEPTION 'Lỗi tồn kho tại (%, %): SoLuongDaGiu (%) không được âm.',
            NEW.MaKho, NEW.MaChiTietSP, NEW.SoLuongDaGiu
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_01_tonkho_khong_am
    BEFORE INSERT OR UPDATE ON TONKHO
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_tonkho_khong_am();

-- -----------------------------------------------------------------------------
-- Trigger 2: Kiểm tra số lượng hàng đã giữ không vượt quá số lượng thực tế
-- Hàm Trigger: fn_trg_tonkho_kiem_tra_da_giu()
-- Kích hoạt khi INSERT/UPDATE trên TONKHO từ: sp_tao_don_hang, sp_huy_don_hang,
-- sp_chuyen_kho, sp_xac_nhan_nhan_chuyen_kho, 02_insert_data.sql,
-- Kịch bản 2 (bẫy lỗi ROLLBACK) và Kịch bản 5 (chặn overselling) trong 06_transactions.sql.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_trg_tonkho_kiem_tra_da_giu()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.SoLuongDaGiu > NEW.SoLuongThucTe THEN
        RAISE EXCEPTION 'Lỗi tồn kho tại (%, %): SoLuongDaGiu (%) vượt quá SoLuongThucTe (%).',
            NEW.MaKho, NEW.MaChiTietSP, NEW.SoLuongDaGiu, NEW.SoLuongThucTe
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_02_tonkho_kiem_tra_da_giu
    BEFORE INSERT OR UPDATE ON TONKHO
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_tonkho_kiem_tra_da_giu();

-- -----------------------------------------------------------------------------
-- Trigger 3: Tự động cập nhật thời gian cập nhật tồn kho (NgayCapNhat)
-- Hàm Trigger: fn_trg_tonkho_cap_nhat_thoi_gian()
-- Kích hoạt tự động khi INSERT/UPDATE số lượng trên TONKHO (bao gồm mọi lần
-- sp_tao_don_hang, sp_huy_don_hang, sp_chuyen_kho, sp_xac_nhan_nhan_chuyen_kho
-- và các kịch bản trong 06_transactions.sql cập nhật tồn kho).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_trg_tonkho_cap_nhat_thoi_gian()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.NgayCapNhat IS NULL THEN
            NEW.NgayCapNhat := CURRENT_TIMESTAMP;
        END IF;
    ELSIF TG_OP = 'UPDATE' THEN
        IF NEW.SoLuongThucTe IS DISTINCT FROM OLD.SoLuongThucTe
           OR NEW.SoLuongDaGiu IS DISTINCT FROM OLD.SoLuongDaGiu THEN
            NEW.NgayCapNhat := CURRENT_TIMESTAMP;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_03_tonkho_cap_nhat_thoi_gian
    BEFORE INSERT OR UPDATE ON TONKHO
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_tonkho_cap_nhat_thoi_gian();

-- -----------------------------------------------------------------------------
-- Trigger 4: Ngăn chặn xóa dữ liệu đang được tham chiếu bởi nghiệp vụ khác
--            (Hoạt động kết hợp cùng các khóa ngoại ON DELETE RESTRICT để trả
--             về thông báo nghiệp vụ rõ ràng theo bảng cha/con từ pg_constraint)
-- Hàm Trigger: fn_trg_ngan_xoa_tham_chieu()
-- Kích hoạt khi chạy DELETE trên 11 bảng cha (DANHMUC_SP, THUONGHIEU, SANPHAM,
-- CHITIET_SP, NHACUNGCAP, KHO, KHACHHANG, DONHANG, CHITIET_DONHANG,
-- PHIEUTRAHANG, PHIEUKHO) nếu vẫn còn bản ghi con tham chiếu.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_trg_ngan_xoa_tham_chieu()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_fk          RECORD;
    v_old_json    JSONB := to_jsonb(OLD);
    v_old_val     TEXT;
    v_dang_dung   BOOLEAN;
BEGIN
    FOR v_fk IN
        SELECT
            con.conname,
            con.conrelid::regclass AS child_table,
            a_child.attname        AS child_col,
            a_parent.attname       AS parent_col
        FROM pg_constraint con
        JOIN pg_attribute a_child
            ON a_child.attrelid = con.conrelid
           AND a_child.attnum = con.conkey[1]
        JOIN pg_attribute a_parent
            ON a_parent.attrelid = con.confrelid
           AND a_parent.attnum = con.confkey[1]
        WHERE con.contype = 'f'
          AND con.confrelid = TG_RELID
        ORDER BY con.conrelid::regclass::TEXT, a_child.attname
    LOOP
        v_old_val := v_old_json ->> v_fk.parent_col;

        IF v_old_val IS NOT NULL THEN
            EXECUTE format(
                'SELECT EXISTS (SELECT 1 FROM %s WHERE %I::TEXT = $1)',
                v_fk.child_table,
                v_fk.child_col
            )
            INTO v_dang_dung
            USING v_old_val;

            IF v_dang_dung THEN
                RAISE EXCEPTION
                    'Không thể xóa bản ghi (% = %) ở bảng % vì đang được tham chiếu tại % (%).',
                    v_fk.parent_col, v_old_val, TG_TABLE_NAME, v_fk.child_table, v_fk.child_col
                    USING ERRCODE = 'foreign_key_violation';
            END IF;
        END IF;
    END LOOP;

    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_04_chan_xoa_danhmuc_sp
    BEFORE DELETE ON DANHMUC_SP FOR EACH ROW
    EXECUTE FUNCTION fn_trg_ngan_xoa_tham_chieu();

CREATE TRIGGER trg_04_chan_xoa_thuonghieu
    BEFORE DELETE ON THUONGHIEU FOR EACH ROW
    EXECUTE FUNCTION fn_trg_ngan_xoa_tham_chieu();

CREATE TRIGGER trg_04_chan_xoa_sanpham
    BEFORE DELETE ON SANPHAM FOR EACH ROW
    EXECUTE FUNCTION fn_trg_ngan_xoa_tham_chieu();

CREATE TRIGGER trg_04_chan_xoa_chitiet_sp
    BEFORE DELETE ON CHITIET_SP FOR EACH ROW
    EXECUTE FUNCTION fn_trg_ngan_xoa_tham_chieu();

CREATE TRIGGER trg_04_chan_xoa_nhacungcap
    BEFORE DELETE ON NHACUNGCAP FOR EACH ROW
    EXECUTE FUNCTION fn_trg_ngan_xoa_tham_chieu();

CREATE TRIGGER trg_04_chan_xoa_kho
    BEFORE DELETE ON KHO FOR EACH ROW
    EXECUTE FUNCTION fn_trg_ngan_xoa_tham_chieu();

CREATE TRIGGER trg_04_chan_xoa_khachhang
    BEFORE DELETE ON KHACHHANG FOR EACH ROW
    EXECUTE FUNCTION fn_trg_ngan_xoa_tham_chieu();

CREATE TRIGGER trg_04_chan_xoa_donhang
    BEFORE DELETE ON DONHANG FOR EACH ROW
    EXECUTE FUNCTION fn_trg_ngan_xoa_tham_chieu();

CREATE TRIGGER trg_04_chan_xoa_chitiet_donhang
    BEFORE DELETE ON CHITIET_DONHANG FOR EACH ROW
    EXECUTE FUNCTION fn_trg_ngan_xoa_tham_chieu();

CREATE TRIGGER trg_04_chan_xoa_phieutrahang
    BEFORE DELETE ON PHIEUTRAHANG FOR EACH ROW
    EXECUTE FUNCTION fn_trg_ngan_xoa_tham_chieu();

CREATE TRIGGER trg_04_chan_xoa_phieukho
    BEFORE DELETE ON PHIEUKHO FOR EACH ROW
    EXECUTE FUNCTION fn_trg_ngan_xoa_tham_chieu();

-- -----------------------------------------------------------------------------
-- Trigger 5: Kiểm tra chuyên biệt hóa rời rạc (ISA Disjoint) cho PHIEUKHO
--            Mỗi PHIEUKHO có LoaiPhieu IN ('NHAP', 'XUAT', 'CHUYEN') phải khớp
--            đúng 1 bảng con tương ứng và không được tồn tại ở 2 bảng con còn lại.
-- Hàm Trigger:
--   - fn_trg_phieukho_kiem_tra_isa_con(): kiểm tra tức thời khi INSERT/UPDATE
--     vào 3 bảng con (PHIEUNHAPKHO, PHIEUXUATKHO, PHIEUCHUYENKHO).
--   - fn_trg_phieukho_kiem_tra_isa_cha(): CONSTRAINT TRIGGER (DEFERRABLE
--     INITIALLY DEFERRED) kiểm tra lúc COMMIT để đảm bảo PHIEUKHO không bị mồ côi
--     bảng con (hoặc bị xóa mất bảng con mà bảng cha vẫn còn).
-- Thủ tục/Script kích hoạt: sp_chuyen_kho(), sp_xac_nhan_nhan_chuyen_kho()
-- [04_functions_procedures.sql]; 02_insert_data.sql; Kịch bản 4 (PK19, PK20)
-- và khối cleanup trong 06_transactions.sql.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_trg_phieukho_kiem_tra_isa_con()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_loai_yeu_cau VARCHAR(10) := TG_ARGV[0];
    v_loai_phieu   VARCHAR(10);
BEGIN
    SELECT LoaiPhieu INTO v_loai_phieu
    FROM PHIEUKHO
    WHERE MaPhieu = NEW.MaPhieu;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Phiếu kho % không tồn tại trong bảng cha PHIEUKHO.', NEW.MaPhieu
            USING ERRCODE = 'foreign_key_violation';
    ELSIF v_loai_phieu <> v_loai_yeu_cau THEN
        RAISE EXCEPTION
            'Vi phạm phân loại ISA: Phiếu % có LoaiPhieu = % không thể lưu vào bảng con % (yêu cầu %).',
            NEW.MaPhieu, v_loai_phieu, TG_TABLE_NAME, v_loai_yeu_cau
            USING ERRCODE = 'check_violation';
    END IF;

    -- Bảo đảm rời rạc (Disjoint): một MaPhieu chỉ xuất hiện ở đúng 1 bảng con
    IF v_loai_yeu_cau <> 'NHAP'
       AND EXISTS (SELECT 1 FROM PHIEUNHAPKHO WHERE MaPhieu = NEW.MaPhieu) THEN
        RAISE EXCEPTION 'Vi phạm ISA rời rạc: Phiếu % đã tồn tại ở PHIEUNHAPKHO.', NEW.MaPhieu
            USING ERRCODE = 'check_violation';
    END IF;

    IF v_loai_yeu_cau <> 'XUAT'
       AND EXISTS (SELECT 1 FROM PHIEUXUATKHO WHERE MaPhieu = NEW.MaPhieu) THEN
        RAISE EXCEPTION 'Vi phạm ISA rời rạc: Phiếu % đã tồn tại ở PHIEUXUATKHO.', NEW.MaPhieu
            USING ERRCODE = 'check_violation';
    END IF;

    IF v_loai_yeu_cau <> 'CHUYEN'
       AND EXISTS (SELECT 1 FROM PHIEUCHUYENKHO WHERE MaPhieu = NEW.MaPhieu) THEN
        RAISE EXCEPTION 'Vi phạm ISA rời rạc: Phiếu % đã tồn tại ở PHIEUCHUYENKHO.', NEW.MaPhieu
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_05_isa_phieunhapkho
    BEFORE INSERT OR UPDATE ON PHIEUNHAPKHO
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_phieukho_kiem_tra_isa_con('NHAP');

CREATE TRIGGER trg_05_isa_phieuxuatkho
    BEFORE INSERT OR UPDATE ON PHIEUXUATKHO
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_phieukho_kiem_tra_isa_con('XUAT');

CREATE TRIGGER trg_05_isa_phieuchuyenkho
    BEFORE INSERT OR UPDATE ON PHIEUCHUYENKHO
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_phieukho_kiem_tra_isa_con('CHUYEN');

CREATE OR REPLACE FUNCTION fn_trg_phieukho_kiem_tra_isa_cha()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_ma_phieu   VARCHAR(20) := COALESCE(NEW.MaPhieu, OLD.MaPhieu);
    v_loai_phieu VARCHAR(10);
    v_co_nhap    BOOLEAN;
    v_co_xuat    BOOLEAN;
    v_co_chuyen  BOOLEAN;
    v_so_bang    INTEGER;
BEGIN
    SELECT LoaiPhieu INTO v_loai_phieu
    FROM PHIEUKHO
    WHERE MaPhieu = v_ma_phieu;

    -- Nếu bản ghi cha đã bị xóa trước khi COMMIT (vd: cleanup) thì bỏ qua
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    v_co_nhap   := EXISTS (SELECT 1 FROM PHIEUNHAPKHO   WHERE MaPhieu = v_ma_phieu);
    v_co_xuat   := EXISTS (SELECT 1 FROM PHIEUXUATKHO   WHERE MaPhieu = v_ma_phieu);
    v_co_chuyen := EXISTS (SELECT 1 FROM PHIEUCHUYENKHO WHERE MaPhieu = v_ma_phieu);

    v_so_bang := (CASE WHEN v_co_nhap THEN 1 ELSE 0 END)
               + (CASE WHEN v_co_xuat THEN 1 ELSE 0 END)
               + (CASE WHEN v_co_chuyen THEN 1 ELSE 0 END);

    IF v_so_bang <> 1 THEN
        RAISE EXCEPTION
            'Vi phạm ISA rời rạc: Phiếu kho % (%) phải tồn tại ở đúng 1 bảng con tương ứng (hiện tại: % bảng).',
            v_ma_phieu, v_loai_phieu, v_so_bang
            USING ERRCODE = 'check_violation';
    END IF;

    IF (v_loai_phieu = 'NHAP' AND NOT v_co_nhap)
       OR (v_loai_phieu = 'XUAT' AND NOT v_co_xuat)
       OR (v_loai_phieu = 'CHUYEN' AND NOT v_co_chuyen) THEN
        RAISE EXCEPTION
            'Vi phạm phân loại ISA: Phiếu kho % có LoaiPhieu = % không khớp bảng con hiện có.',
            v_ma_phieu, v_loai_phieu
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_05_isa_phieukho_cha
    AFTER INSERT OR UPDATE ON PHIEUKHO
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_phieukho_kiem_tra_isa_cha();

CREATE CONSTRAINT TRIGGER trg_05_isa_phieunhapkho_xoa
    AFTER DELETE ON PHIEUNHAPKHO
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_phieukho_kiem_tra_isa_cha();

CREATE CONSTRAINT TRIGGER trg_05_isa_phieuxuatkho_xoa
    AFTER DELETE ON PHIEUXUATKHO
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_phieukho_kiem_tra_isa_cha();

CREATE CONSTRAINT TRIGGER trg_05_isa_phieuchuyenkho_xoa
    AFTER DELETE ON PHIEUCHUYENKHO
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_phieukho_kiem_tra_isa_cha();

-- -----------------------------------------------------------------------------
-- Trigger 6: Chống chu trình phân cấp trên danh mục đa cấp DANHMUC_SP
--            (Ngăn tình trạng A -> B -> ... -> A khi INSERT hoặc UPDATE MaDanhMucCha)
-- Hàm Trigger: fn_trg_danhmuc_chong_chu_trinh()
-- Kích hoạt khi INSERT/UPDATE trên DANHMUC_SP (02_insert_data.sql) để bảo vệ
-- cấu trúc cây cho truy vấn đệ quy Câu 12 [03_queries.sql] và
-- fn_bao_cao_doanh_thu_dong() [04_functions_procedures.sql].
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_trg_danhmuc_chong_chu_trinh()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_co_chu_trinh BOOLEAN;
BEGIN
    IF NEW.MaDanhMucCha IS NULL THEN
        RETURN NEW;
    END IF;

    IF NEW.MaDanhMucCha = NEW.MaDanhMuc THEN
        RAISE EXCEPTION 'Danh mục % không thể tự làm danh mục cha của chính nó.', NEW.MaDanhMuc
            USING ERRCODE = 'check_violation';
    END IF;

    WITH RECURSIVE to_tien AS (
        SELECT dm.MaDanhMuc, dm.MaDanhMucCha
        FROM DANHMUC_SP dm
        WHERE dm.MaDanhMuc = NEW.MaDanhMucCha

        UNION ALL

        SELECT cha.MaDanhMuc, cha.MaDanhMucCha
        FROM DANHMUC_SP cha
        JOIN to_tien t ON cha.MaDanhMuc = t.MaDanhMucCha
        WHERE cha.MaDanhMuc <> NEW.MaDanhMucCha
    )
    SELECT EXISTS (
        SELECT 1 FROM to_tien WHERE MaDanhMuc = NEW.MaDanhMuc
    )
    INTO v_co_chu_trinh;

    IF v_co_chu_trinh THEN
        RAISE EXCEPTION
            'Vi phạm cây danh mục: Gán cha % cho danh mục % sẽ tạo thành chu trình phân cấp.',
            NEW.MaDanhMucCha, NEW.MaDanhMuc
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_06_danhmuc_chong_chu_trinh
    BEFORE INSERT OR UPDATE OF MaDanhMuc, MaDanhMucCha ON DANHMUC_SP
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_danhmuc_chong_chu_trinh();

-- -----------------------------------------------------------------------------
-- Trigger 7: Kiểm tra quy tắc trả hàng & đổi hàng cùng sản phẩm
--   - Chỉ đơn hàng ở trạng thái HOAN_TAT mới được lập PHIEUTRAHANG.
--   - CHITIET_TRAHANG.MaCTDonHang phải thuộc đúng MaDonHang của PHIEUTRAHANG.
--   - Tổng số lượng trả không vượt quá số lượng đã mua trên CHITIET_DONHANG.
--   - Nếu HinhThucXuLy = 'DOI_HANG': bắt buộc có MaChiTietSPDoi và phải cùng
--     MaSanPham với biến thể gốc; nếu không phải DOI_HANG thì MaChiTietSPDoi phải NULL.
-- Hàm Trigger:
--   - fn_trg_phieutrahang_don_hoan_tat()
--   - fn_trg_chitiet_trahang_hop_le()
-- Kích hoạt khi INSERT/UPDATE trên PHIEUTRAHANG, CHITIET_TRAHANG từ:
-- 02_insert_data.sql (PTH01..04) và Kịch bản 4 (PT05, CTTH06) trong 06_transactions.sql.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_trg_phieutrahang_don_hoan_tat()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_tt_donhang VARCHAR(30);
BEGIN
    SELECT TrangThai INTO v_tt_donhang
    FROM DONHANG
    WHERE MaDonHang = NEW.MaDonHang;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Đơn hàng % không tồn tại.', NEW.MaDonHang
            USING ERRCODE = 'foreign_key_violation';
    ELSIF v_tt_donhang <> 'HOAN_TAT' THEN
        RAISE EXCEPTION
            'Không thể lập phiếu trả % cho đơn hàng % đang ở trạng thái % (chỉ nhận đơn HOAN_TAT).',
            NEW.MaPhieuTra, NEW.MaDonHang, v_tt_donhang
            USING ERRCODE = 'check_violation';
    END IF;

    IF TG_OP = 'UPDATE' AND NEW.MaDonHang IS DISTINCT FROM OLD.MaDonHang THEN
        IF EXISTS (SELECT 1 FROM CHITIET_TRAHANG WHERE MaPhieuTra = NEW.MaPhieuTra) THEN
            RAISE EXCEPTION
                'Không thể đổi MaDonHang của phiếu trả % khi đã có chi tiết trả hàng.',
                NEW.MaPhieuTra
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_07_phieutrahang_don_hoan_tat
    BEFORE INSERT OR UPDATE OF MaDonHang ON PHIEUTRAHANG
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_phieutrahang_don_hoan_tat();

CREATE OR REPLACE FUNCTION fn_trg_chitiet_trahang_hop_le()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_ma_dh_tra     VARCHAR(20);
    v_tt_phieu_tra  VARCHAR(30);
    v_ma_dh_goc     VARCHAR(20);
    v_ctsp_goc      VARCHAR(20);
    v_sl_mua        INTEGER;
    v_tong_da_tra   INTEGER;
    v_sp_goc        VARCHAR(20);
    v_sp_doi        VARCHAR(20);
BEGIN
    SELECT MaDonHang, TrangThai
    INTO v_ma_dh_tra, v_tt_phieu_tra
    FROM PHIEUTRAHANG
    WHERE MaPhieuTra = NEW.MaPhieuTra;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Phiếu trả hàng % không tồn tại.', NEW.MaPhieuTra
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    SELECT MaDonHang, MaChiTietSP, SoLuong
    INTO v_ma_dh_goc, v_ctsp_goc, v_sl_mua
    FROM CHITIET_DONHANG
    WHERE MaCTDonHang = NEW.MaCTDonHang;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Chi tiết đơn hàng % không tồn tại.', NEW.MaCTDonHang
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    -- 1. Kiểm tra dòng đơn hàng phải thuộc đúng đơn hàng của phiếu trả
    IF v_ma_dh_tra <> v_ma_dh_goc THEN
        RAISE EXCEPTION
            'Chi tiết đơn hàng % thuộc đơn %, không thuộc đơn % của phiếu trả %.',
            NEW.MaCTDonHang, v_ma_dh_goc, v_ma_dh_tra, NEW.MaPhieuTra
            USING ERRCODE = 'check_violation';
    END IF;

    -- 2. Kiểm tra tổng số lượng trả không vượt quá số lượng đã mua
    IF NEW.SoLuongTra > v_sl_mua THEN
        RAISE EXCEPTION
            'Số lượng trả (%) cho chi tiết đơn % vượt quá số lượng đã mua (%).',
            NEW.SoLuongTra, NEW.MaCTDonHang, v_sl_mua
            USING ERRCODE = 'check_violation';
    END IF;

    IF v_tt_phieu_tra NOT IN ('DA_HUY', 'TU_CHOI') AND NEW.HinhThucXuLy <> 'TU_CHOI_DONG' THEN
        SELECT COALESCE(SUM(ctth.SoLuongTra), 0)
        INTO v_tong_da_tra
        FROM CHITIET_TRAHANG ctth
        JOIN PHIEUTRAHANG pth ON ctth.MaPhieuTra = pth.MaPhieuTra
        WHERE ctth.MaCTDonHang = NEW.MaCTDonHang
          AND ctth.MaCTTraHang <> NEW.MaCTTraHang
          AND pth.TrangThai NOT IN ('DA_HUY', 'TU_CHOI')
          AND ctth.HinhThucXuLy <> 'TU_CHOI_DONG';

        IF (v_tong_da_tra + NEW.SoLuongTra) > v_sl_mua THEN
            RAISE EXCEPTION
                'Tổng số lượng trả (% + % = %) cho chi tiết đơn % vượt quá số lượng đã mua (%).',
                v_tong_da_tra, NEW.SoLuongTra, (v_tong_da_tra + NEW.SoLuongTra), NEW.MaCTDonHang, v_sl_mua
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    -- 3. Kiểm tra biến thể đổi phải cùng MaSanPham với biến thể gốc
    IF NEW.HinhThucXuLy = 'DOI_HANG' THEN
        IF NEW.MaChiTietSPDoi IS NULL THEN
            RAISE EXCEPTION
                'Chi tiết trả hàng % xử lý DOI_HANG bắt buộc phải chỉ định MaChiTietSPDoi.',
                NEW.MaCTTraHang
                USING ERRCODE = 'check_violation';
        END IF;

        SELECT MaSanPham INTO v_sp_goc FROM CHITIET_SP WHERE MaChiTietSP = v_ctsp_goc;
        SELECT MaSanPham INTO v_sp_doi FROM CHITIET_SP WHERE MaChiTietSP = NEW.MaChiTietSPDoi;

        IF v_sp_doi IS NULL THEN
            RAISE EXCEPTION 'Biến thể đổi % không tồn tại.', NEW.MaChiTietSPDoi
                USING ERRCODE = 'foreign_key_violation';
        ELSIF v_sp_goc <> v_sp_doi THEN
            RAISE EXCEPTION
                'Vi phạm quy tắc đổi hàng: Biến thể đổi % (%) phải cùng sản phẩm với biến thể gốc % (%).',
                NEW.MaChiTietSPDoi, v_sp_doi, v_ctsp_goc, v_sp_goc
                USING ERRCODE = 'check_violation';
        END IF;
    ELSIF NEW.MaChiTietSPDoi IS NOT NULL THEN
        RAISE EXCEPTION
            'Chỉ được nhập MaChiTietSPDoi khi HinhThucXuLy là DOI_HANG (hiện tại: %).',
            NEW.HinhThucXuLy
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_07_chitiet_trahang_hop_le
    BEFORE INSERT OR UPDATE ON CHITIET_TRAHANG
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_chitiet_trahang_hop_le();

-- -----------------------------------------------------------------------------
-- Trigger 8: Bảo đảm mỗi PHIEUKHO phải có ít nhất 1 dòng chi tiết CHITIET_PHIEUKHO
--            Sử dụng CONSTRAINT TRIGGER (DEFERRABLE INITIALLY DEFERRED) để cho phép
--            INSERT bảng cha PHIEUKHO trước rồi INSERT CHITIET_PHIEUKHO ngay sau đó
--            trong cùng một Transaction, và tự động kiểm tra tại thời điểm COMMIT.
-- Hàm Trigger: fn_trg_phieukho_toi_thieu_chi_tiet()
-- Thủ tục/Script kích hoạt: sp_chuyen_kho() [04_functions_procedures.sql];
-- 02_insert_data.sql; Kịch bản 4 (PK19, PK20) và khối cleanup trong 06_transactions.sql.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_trg_phieukho_toi_thieu_chi_tiet()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_ma_phieu VARCHAR(20);
BEGIN
    IF TG_TABLE_NAME = 'phieukho' THEN
        v_ma_phieu := NEW.MaPhieu;
    ELSE
        v_ma_phieu := OLD.MaPhieu;
    END IF;

    -- Chỉ kiểm tra nếu phiếu kho cha vẫn còn tồn tại lúc COMMIT
    IF EXISTS (SELECT 1 FROM PHIEUKHO WHERE MaPhieu = v_ma_phieu) THEN
        IF NOT EXISTS (SELECT 1 FROM CHITIET_PHIEUKHO WHERE MaPhieu = v_ma_phieu) THEN
            RAISE EXCEPTION
                'Phiếu kho % phải có ít nhất một dòng chi tiết trong CHITIET_PHIEUKHO.',
                v_ma_phieu
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_08_phieukho_co_chi_tiet
    AFTER INSERT OR UPDATE ON PHIEUKHO
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_phieukho_toi_thieu_chi_tiet();

CREATE CONSTRAINT TRIGGER trg_08_chitiet_phieukho_giu_toi_thieu
    AFTER DELETE OR UPDATE OF MaPhieu ON CHITIET_PHIEUKHO
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_phieukho_toi_thieu_chi_tiet();
