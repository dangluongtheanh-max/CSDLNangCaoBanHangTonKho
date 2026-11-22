-- =============================================================================
-- ĐỒ ÁN CƠ SỞ DỮ LIỆU NÂNG CAO - ĐỀ TÀI 2
-- Hệ thống Quản lý Bán hàng & Tồn kho Đa kho
-- Hệ quản trị CSDL: PostgreSQL 17
-- File: 01_create_tables.sql
-- Mô tả: Khởi tạo 18 bảng và các ràng buộc PK, FK, UNIQUE, CHECK, DEFAULT.
-- Lưu ý triển khai:
--   - Không chứa DROP TABLE (reset CSDL thực hiện ở file riêng nếu cần).
--   - Không tạo thêm INDEX phụ ngoài index tự sinh từ PK/UNIQUE (dành cho 07_indexes.sql).
--   - Các quy tắc liên bảng (ISA rời rạc, chống chu trình danh mục, đổi cùng sản phẩm,
--     phiếu kho tối thiểu 1 chi tiết, cập nhật NgayCapNhat) triển khai ở 05_triggers.sql.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- NHÓM 1: DANH MỤC, THƯƠNG HIỆU & SẢN PHẨM
-- -----------------------------------------------------------------------------

-- 3.1 Danh mục sản phẩm (đa cấp tự tham chiếu)
CREATE TABLE DANHMUC_SP (
    MaDanhMuc    VARCHAR(20)  NOT NULL,
    MaDanhMucCha VARCHAR(20)  NULL,
    TenDanhMuc   VARCHAR(100) NOT NULL,
    MoTa         TEXT         NULL,

    CONSTRAINT pk_danhmuc_sp PRIMARY KEY (MaDanhMuc),
    CONSTRAINT fk_danhmuc_sp_cha FOREIGN KEY (MaDanhMucCha)
        REFERENCES DANHMUC_SP (MaDanhMuc)
        ON DELETE RESTRICT,
    CONSTRAINT chk_danhmuc_sp_khong_tu_lam_cha
        CHECK (MaDanhMucCha IS NULL OR MaDanhMucCha <> MaDanhMuc)
);

-- 3.2 Thương hiệu
CREATE TABLE THUONGHIEU (
    MaThuongHieu  VARCHAR(20)  NOT NULL,
    TenThuongHieu VARCHAR(120) NOT NULL,
    QuocGia       VARCHAR(100) NULL,
    Website       VARCHAR(255) NULL,

    CONSTRAINT pk_thuonghieu PRIMARY KEY (MaThuongHieu)
);

-- 3.3 Sản phẩm (cấp mẫu/model)
CREATE TABLE SANPHAM (
    MaSanPham    VARCHAR(20)    NOT NULL,
    TenSanPham   VARCHAR(200)   NOT NULL,
    MoTa         TEXT           NULL,
    GiaNiemYet   NUMERIC(14, 2) NOT NULL,
    TrangThai    VARCHAR(30)    NOT NULL,
    MaDanhMuc    VARCHAR(20)    NOT NULL,
    MaThuongHieu VARCHAR(20)    NOT NULL,

    CONSTRAINT pk_sanpham PRIMARY KEY (MaSanPham),
    CONSTRAINT fk_sanpham_danhmuc FOREIGN KEY (MaDanhMuc)
        REFERENCES DANHMUC_SP (MaDanhMuc)
        ON DELETE RESTRICT,
    CONSTRAINT fk_sanpham_thuonghieu FOREIGN KEY (MaThuongHieu)
        REFERENCES THUONGHIEU (MaThuongHieu)
        ON DELETE RESTRICT,
    CONSTRAINT chk_sanpham_gia_niem_yet CHECK (GiaNiemYet >= 0),
    CONSTRAINT chk_sanpham_trang_thai
        CHECK (TrangThai IN ('DANG_KINH_DOANH', 'TAM_NGUNG', 'NGUNG_KINH_DOANH'))
);

-- 3.4 Chi tiết sản phẩm (biến thể theo size/màu)
CREATE TABLE CHITIET_SP (
    MaChiTietSP VARCHAR(20) NOT NULL,
    SKU         VARCHAR(50) NOT NULL,
    Size        VARCHAR(20) NULL,
    MauSac      VARCHAR(50) NULL,
    TrangThai   VARCHAR(30) NOT NULL,
    MaSanPham   VARCHAR(20) NOT NULL,

    CONSTRAINT pk_chitiet_sp PRIMARY KEY (MaChiTietSP),
    CONSTRAINT uq_chitiet_sp_sku UNIQUE (SKU),
    CONSTRAINT fk_chitiet_sp_sanpham FOREIGN KEY (MaSanPham)
        REFERENCES SANPHAM (MaSanPham)
        ON DELETE RESTRICT,
    CONSTRAINT chk_chitiet_sp_trang_thai
        CHECK (TrangThai IN ('DANG_BAN', 'TAM_NGUNG', 'NGUNG_BAN'))
);

-- -----------------------------------------------------------------------------
-- NHÓM 2: NHÀ CUNG CẤP & CUNG ỨNG
-- -----------------------------------------------------------------------------

-- 3.5 Nhà cung cấp
CREATE TABLE NHACUNGCAP (
    MaNCC    VARCHAR(20)  NOT NULL,
    TenNCC   VARCHAR(200) NOT NULL,
    SDT      VARCHAR(20)  NULL,
    Email    VARCHAR(254) NULL,
    DiaChi   VARCHAR(255) NULL,
    MaSoThue VARCHAR(30)  NULL,

    CONSTRAINT pk_nhacungcap PRIMARY KEY (MaNCC),
    CONSTRAINT uq_nhacungcap_ma_so_thue UNIQUE (MaSoThue)
);

-- 3.6 Cung cấp (quan hệ N:N giữa biến thể sản phẩm và nhà cung cấp)
CREATE TABLE CUNGCAP (
    MaChiTietSP             VARCHAR(20)    NOT NULL,
    MaNCC                   VARCHAR(20)    NOT NULL,
    GiaNhap                 NUMERIC(14, 2) NOT NULL,
    ThoiGianGiaoHangDuKien  INTEGER        NULL,

    CONSTRAINT pk_cungcap PRIMARY KEY (MaChiTietSP, MaNCC),
    CONSTRAINT fk_cungcap_chitiet_sp FOREIGN KEY (MaChiTietSP)
        REFERENCES CHITIET_SP (MaChiTietSP)
        ON DELETE RESTRICT,
    CONSTRAINT fk_cungcap_nhacungcap FOREIGN KEY (MaNCC)
        REFERENCES NHACUNGCAP (MaNCC)
        ON DELETE RESTRICT,
    CONSTRAINT chk_cungcap_gia_nhap CHECK (GiaNhap >= 0),
    CONSTRAINT chk_cungcap_tg_giao_hang
        CHECK (ThoiGianGiaoHangDuKien IS NULL OR ThoiGianGiaoHangDuKien >= 0)
);

-- -----------------------------------------------------------------------------
-- NHÓM 3: KHO & TỒN KHO
-- -----------------------------------------------------------------------------

-- 3.7 Kho
CREATE TABLE KHO (
    MaKho     VARCHAR(20)  NOT NULL,
    TenKho    VARCHAR(120) NOT NULL,
    DiaChi    VARCHAR(255) NOT NULL,
    SDT       VARCHAR(20)  NULL,
    TrangThai VARCHAR(30)  NOT NULL,

    CONSTRAINT pk_kho PRIMARY KEY (MaKho),
    CONSTRAINT chk_kho_trang_thai
        CHECK (TrangThai IN ('HOAT_DONG', 'TAM_DONG', 'NGUNG_HOAT_DONG'))
);

-- 3.8 Tồn kho (theo từng kho và biến thể sản phẩm)
CREATE TABLE TONKHO (
    MaKho         VARCHAR(20) NOT NULL,
    MaChiTietSP   VARCHAR(20) NOT NULL,
    SoLuongThucTe INTEGER     NOT NULL DEFAULT 0,
    SoLuongDaGiu  INTEGER     NOT NULL DEFAULT 0,
    NgayCapNhat   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_tonkho PRIMARY KEY (MaKho, MaChiTietSP),
    CONSTRAINT fk_tonkho_kho FOREIGN KEY (MaKho)
        REFERENCES KHO (MaKho)
        ON DELETE RESTRICT,
    CONSTRAINT fk_tonkho_chitiet_sp FOREIGN KEY (MaChiTietSP)
        REFERENCES CHITIET_SP (MaChiTietSP)
        ON DELETE RESTRICT,
    CONSTRAINT chk_tonkho_sl_thuc_te CHECK (SoLuongThucTe >= 0),
    CONSTRAINT chk_tonkho_sl_da_giu CHECK (SoLuongDaGiu >= 0),
    CONSTRAINT chk_tonkho_da_giu_hop_le CHECK (SoLuongDaGiu <= SoLuongThucTe)
);

-- -----------------------------------------------------------------------------
-- NHÓM 4: KHÁCH HÀNG, ĐƠN HÀNG & TRẢ/ĐỔI HÀNG
-- -----------------------------------------------------------------------------

-- 3.11 Khách hàng
CREATE TABLE KHACHHANG (
    MaKH       VARCHAR(20)  NOT NULL,
    HoTen      VARCHAR(150) NOT NULL,
    SDT        VARCHAR(20)  NOT NULL,
    Email      VARCHAR(254) NULL,
    DiaChi     VARCHAR(255) NULL,
    NgayDangKy TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_khachhang PRIMARY KEY (MaKH)
);

-- 3.9 Đơn hàng
CREATE TABLE DONHANG (
    MaDonHang           VARCHAR(20)  NOT NULL,
    NgayDat             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    TrangThai           VARCHAR(30)  NOT NULL,
    DiaChiGiaoHang      VARCHAR(255) NOT NULL,
    PhuongThucThanhToan VARCHAR(50)  NOT NULL,
    GhiChu              TEXT         NULL,
    MaKH                VARCHAR(20)  NOT NULL,
    MaKho               VARCHAR(20)  NOT NULL,

    CONSTRAINT pk_donhang PRIMARY KEY (MaDonHang),
    CONSTRAINT fk_donhang_khachhang FOREIGN KEY (MaKH)
        REFERENCES KHACHHANG (MaKH)
        ON DELETE RESTRICT,
    CONSTRAINT fk_donhang_kho FOREIGN KEY (MaKho)
        REFERENCES KHO (MaKho)
        ON DELETE RESTRICT,
    CONSTRAINT chk_donhang_trang_thai
        CHECK (TrangThai IN ('CHO_XAC_NHAN', 'DA_XAC_NHAN', 'DANG_XU_LY', 'DANG_GIAO', 'HOAN_TAT', 'DA_HUY')),
    CONSTRAINT chk_donhang_pttt
        CHECK (PhuongThucThanhToan IN ('COD', 'CHUYEN_KHOAN', 'THE', 'VI_DIEN_TU', 'TIEN_MAT'))
);

-- 3.10 Chi tiết đơn hàng (ChietKhau lưu theo tỷ lệ phần trăm 0..100)
CREATE TABLE CHITIET_DONHANG (
    MaCTDonHang VARCHAR(20)    NOT NULL,
    MaDonHang   VARCHAR(20)    NOT NULL,
    MaChiTietSP VARCHAR(20)    NOT NULL,
    SoLuong     INTEGER        NOT NULL,
    DonGiaBan   NUMERIC(14, 2) NOT NULL,
    ChietKhau   NUMERIC(5, 2)  NOT NULL DEFAULT 0,

    CONSTRAINT pk_chitiet_donhang PRIMARY KEY (MaCTDonHang),
    CONSTRAINT uq_chitiet_donhang_dh_ctsp UNIQUE (MaDonHang, MaChiTietSP),
    CONSTRAINT fk_chitiet_donhang_donhang FOREIGN KEY (MaDonHang)
        REFERENCES DONHANG (MaDonHang)
        ON DELETE RESTRICT,
    CONSTRAINT fk_chitiet_donhang_chitiet_sp FOREIGN KEY (MaChiTietSP)
        REFERENCES CHITIET_SP (MaChiTietSP)
        ON DELETE RESTRICT,
    CONSTRAINT chk_chitiet_donhang_so_luong CHECK (SoLuong > 0),
    CONSTRAINT chk_chitiet_donhang_don_gia_ban CHECK (DonGiaBan >= 0),
    CONSTRAINT chk_chitiet_donhang_chiet_khau CHECK (ChietKhau >= 0 AND ChietKhau <= 100)
);

-- 3.12 Phiếu trả hàng
CREATE TABLE PHIEUTRAHANG (
    MaPhieuTra   VARCHAR(20) NOT NULL,
    MaDonHang    VARCHAR(20) NOT NULL,
    NgayTiepNhan TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    LyDo         TEXT        NOT NULL,
    TrangThai    VARCHAR(30) NOT NULL,
    GhiChu       TEXT        NULL,

    CONSTRAINT pk_phieutrahang PRIMARY KEY (MaPhieuTra),
    CONSTRAINT fk_phieutrahang_donhang FOREIGN KEY (MaDonHang)
        REFERENCES DONHANG (MaDonHang)
        ON DELETE RESTRICT,
    CONSTRAINT chk_phieutrahang_trang_thai
        CHECK (TrangThai IN ('CHO_XU_LY', 'DANG_XU_LY', 'HOAN_TAT', 'TU_CHOI', 'DA_HUY'))
);

-- 3.13 Chi tiết trả hàng
CREATE TABLE CHITIET_TRAHANG (
    MaCTTraHang      VARCHAR(20)    NOT NULL,
    MaPhieuTra       VARCHAR(20)    NOT NULL,
    MaCTDonHang      VARCHAR(20)    NOT NULL,
    SoLuongTra       INTEGER        NOT NULL,
    TinhTrangSanPham VARCHAR(30)    NOT NULL,
    HinhThucXuLy     VARCHAR(30)    NOT NULL,
    DonGiaHoan       NUMERIC(14, 2) NOT NULL,
    MaChiTietSPDoi   VARCHAR(20)    NULL,

    CONSTRAINT pk_chitiet_trahang PRIMARY KEY (MaCTTraHang),
    CONSTRAINT fk_chitiet_trahang_phieutra FOREIGN KEY (MaPhieuTra)
        REFERENCES PHIEUTRAHANG (MaPhieuTra)
        ON DELETE RESTRICT,
    CONSTRAINT fk_chitiet_trahang_ct_donhang FOREIGN KEY (MaCTDonHang)
        REFERENCES CHITIET_DONHANG (MaCTDonHang)
        ON DELETE RESTRICT,
    CONSTRAINT fk_chitiet_trahang_ctsp_doi FOREIGN KEY (MaChiTietSPDoi)
        REFERENCES CHITIET_SP (MaChiTietSP)
        ON DELETE RESTRICT,
    CONSTRAINT chk_chitiet_trahang_so_luong_tra CHECK (SoLuongTra > 0),
    CONSTRAINT chk_chitiet_trahang_tinh_trang
        CHECK (TinhTrangSanPham IN ('CON_MOI', 'DA_SU_DUNG')),
    CONSTRAINT chk_chitiet_trahang_hinh_thuc
        CHECK (HinhThucXuLy IN ('HOAN_TIEN', 'DOI_HANG', 'TU_CHOI_DONG')),
    CONSTRAINT chk_chitiet_trahang_don_gia_hoan CHECK (DonGiaHoan >= 0)
);

-- -----------------------------------------------------------------------------
-- NHÓM 5: PHIẾU KHO (MÔ HÌNH CHUYÊN BIỆT HÓA ISA DISJOINT) & CHI TIẾT
-- -----------------------------------------------------------------------------

-- 3.14 Phiếu kho (bảng cha)
CREATE TABLE PHIEUKHO (
    MaPhieu   VARCHAR(20) NOT NULL,
    LoaiPhieu VARCHAR(10) NOT NULL,
    NgayLap   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    TrangThai VARCHAR(30) NOT NULL,
    GhiChu    TEXT        NULL,

    CONSTRAINT pk_phieukho PRIMARY KEY (MaPhieu),
    CONSTRAINT chk_phieukho_loai_phieu CHECK (LoaiPhieu IN ('NHAP', 'XUAT', 'CHUYEN')),
    CONSTRAINT chk_phieukho_trang_thai
        CHECK (TrangThai IN ('BAN_NHAP', 'DANG_XU_LY', 'DANG_VAN_CHUYEN', 'HOAN_TAT', 'DA_HUY'))
);

-- 3.15 Phiếu nhập kho (bảng con của PHIEUKHO: nhập từ NCC hoặc nhập hàng khách trả)
CREATE TABLE PHIEUNHAPKHO (
    MaPhieu    VARCHAR(20) NOT NULL,
    MaNCC      VARCHAR(20) NULL,
    MaPhieuTra VARCHAR(20) NULL,
    KhoDich    VARCHAR(20) NOT NULL,

    CONSTRAINT pk_phieunhapkho PRIMARY KEY (MaPhieu),
    CONSTRAINT uq_phieunhapkho_phieu_tra UNIQUE (MaPhieuTra),
    CONSTRAINT fk_phieunhapkho_phieukho FOREIGN KEY (MaPhieu)
        REFERENCES PHIEUKHO (MaPhieu)
        ON DELETE RESTRICT,
    CONSTRAINT fk_phieunhapkho_nhacungcap FOREIGN KEY (MaNCC)
        REFERENCES NHACUNGCAP (MaNCC)
        ON DELETE RESTRICT,
    CONSTRAINT fk_phieunhapkho_phieutra FOREIGN KEY (MaPhieuTra)
        REFERENCES PHIEUTRAHANG (MaPhieuTra)
        ON DELETE RESTRICT,
    CONSTRAINT fk_phieunhapkho_kho_dich FOREIGN KEY (KhoDich)
        REFERENCES KHO (MaKho)
        ON DELETE RESTRICT,
    CONSTRAINT chk_phieunhapkho_xor_nguon_nhap CHECK (
        (MaNCC IS NOT NULL AND MaPhieuTra IS NULL)
        OR (MaNCC IS NULL AND MaPhieuTra IS NOT NULL)
    )
);

-- 3.16 Phiếu xuất kho (bảng con của PHIEUKHO: xuất giao đơn hoặc xuất hàng đổi)
CREATE TABLE PHIEUXUATKHO (
    MaPhieu    VARCHAR(20) NOT NULL,
    MaDonHang  VARCHAR(20) NULL,
    MaPhieuTra VARCHAR(20) NULL,
    KhoNguon   VARCHAR(20) NOT NULL,

    CONSTRAINT pk_phieuxuatkho PRIMARY KEY (MaPhieu),
    CONSTRAINT uq_phieuxuatkho_don_hang UNIQUE (MaDonHang),
    CONSTRAINT uq_phieuxuatkho_phieu_tra UNIQUE (MaPhieuTra),
    CONSTRAINT fk_phieuxuatkho_phieukho FOREIGN KEY (MaPhieu)
        REFERENCES PHIEUKHO (MaPhieu)
        ON DELETE RESTRICT,
    CONSTRAINT fk_phieuxuatkho_donhang FOREIGN KEY (MaDonHang)
        REFERENCES DONHANG (MaDonHang)
        ON DELETE RESTRICT,
    CONSTRAINT fk_phieuxuatkho_phieutra FOREIGN KEY (MaPhieuTra)
        REFERENCES PHIEUTRAHANG (MaPhieuTra)
        ON DELETE RESTRICT,
    CONSTRAINT fk_phieuxuatkho_kho_nguon FOREIGN KEY (KhoNguon)
        REFERENCES KHO (MaKho)
        ON DELETE RESTRICT,
    CONSTRAINT chk_phieuxuatkho_xor_muc_dich CHECK (
        (MaDonHang IS NOT NULL AND MaPhieuTra IS NULL)
        OR (MaDonHang IS NULL AND MaPhieuTra IS NOT NULL)
    )
);

-- 3.17 Phiếu chuyển kho (bảng con của PHIEUKHO: chuyển giữa hai kho khác nhau)
CREATE TABLE PHIEUCHUYENKHO (
    MaPhieu  VARCHAR(20) NOT NULL,
    KhoNguon VARCHAR(20) NOT NULL,
    KhoDich  VARCHAR(20) NOT NULL,

    CONSTRAINT pk_phieuchuyenkho PRIMARY KEY (MaPhieu),
    CONSTRAINT fk_phieuchuyenkho_phieukho FOREIGN KEY (MaPhieu)
        REFERENCES PHIEUKHO (MaPhieu)
        ON DELETE RESTRICT,
    CONSTRAINT fk_phieuchuyenkho_kho_nguon FOREIGN KEY (KhoNguon)
        REFERENCES KHO (MaKho)
        ON DELETE RESTRICT,
    CONSTRAINT fk_phieuchuyenkho_kho_dich FOREIGN KEY (KhoDich)
        REFERENCES KHO (MaKho)
        ON DELETE RESTRICT,
    CONSTRAINT chk_phieuchuyenkho_khac_kho CHECK (KhoNguon <> KhoDich)
);

-- 3.18 Chi tiết phiếu kho
CREATE TABLE CHITIET_PHIEUKHO (
    MaCTPhieuKho VARCHAR(20)    NOT NULL,
    MaPhieu      VARCHAR(20)    NOT NULL,
    MaChiTietSP  VARCHAR(20)    NOT NULL,
    SoLuong      INTEGER        NOT NULL,
    DonGia       NUMERIC(14, 2) NULL,

    CONSTRAINT pk_chitiet_phieukho PRIMARY KEY (MaCTPhieuKho),
    CONSTRAINT uq_chitiet_phieukho_pk_ctsp UNIQUE (MaPhieu, MaChiTietSP),
    CONSTRAINT fk_chitiet_phieukho_phieukho FOREIGN KEY (MaPhieu)
        REFERENCES PHIEUKHO (MaPhieu)
        ON DELETE RESTRICT,
    CONSTRAINT fk_chitiet_phieukho_chitiet_sp FOREIGN KEY (MaChiTietSP)
        REFERENCES CHITIET_SP (MaChiTietSP)
        ON DELETE RESTRICT,
    CONSTRAINT chk_chitiet_phieukho_so_luong CHECK (SoLuong > 0),
    CONSTRAINT chk_chitiet_phieukho_don_gia CHECK (DonGia IS NULL OR DonGia >= 0)
);
