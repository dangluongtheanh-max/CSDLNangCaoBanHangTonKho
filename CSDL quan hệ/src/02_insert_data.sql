-- =============================================================================
-- ĐỒ ÁN CƠ SỞ DỮ LIỆU NÂNG CAO - ĐỀ TÀI 2
-- Hệ thống Quản lý Bán hàng & Tồn kho Đa kho (Chuyên ngành Giày)
-- Hệ quản trị CSDL: PostgreSQL 17
-- File: 02_insert_data.sql
-- Mô tả: Dữ liệu mẫu sạch, đồng bộ 100% về sản phẩm giày cho 18 bảng:
--   - Cây danh mục giày đa cấp 3 tầng (phục vụ Recursive CTE)
--   - Đủ các trạng thái sản phẩm, biến thể, kho, đơn hàng, phiếu kho, trả/đổi hàng
--   - Đối chiếu khớp số học giữa Phiếu kho, Đơn hàng, Trả hàng và Tồn kho
--   - Bao gồm kịch bản giá đặt cũ khác giá niêm yết, hàng sắp hết
--     và hàng đang vận chuyển giữa các kho
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. DANHMUC_SP (11 danh mục giày - cây 3 cấp)
-- -----------------------------------------------------------------------------
INSERT INTO DANHMUC_SP (MaDanhMuc, MaDanhMucCha, TenDanhMuc, MoTa) VALUES
('DM01', NULL,   'Giày thể thao',          'Nhóm danh mục gốc cho các dòng giày tập luyện và thi đấu thể thao'),
('DM02', NULL,   'Giày thời trang',        'Nhóm danh mục gốc cho giày đi lại hằng ngày và dạo phố'),
('DM03', 'DM01', 'Giày chạy bộ',           'Giày thiết kế chuyên biệt cho hoạt động chạy bộ'),
('DM04', 'DM01', 'Giày thi đấu sân',       'Giày cho các môn thể thao sân cứng và sân cỏ'),
('DM05', 'DM02', 'Giày Sneaker',           'Giày sneaker năng động phối trang phục hằng ngày'),
('DM06', 'DM02', 'Giày Lười & Slip-on',    'Giày không dây xỏ nhanh tiện lợi'),
('DM07', 'DM03', 'Giày chạy đường trường', 'Giày chạy bộ trên mặt đường nhựa, đệm êm và phản hồi lực'),
('DM08', 'DM03', 'Giày chạy địa hình',     'Giày chạy trail đế gai bám chắc trên địa hình gồ ghề'),
('DM09', 'DM04', 'Giày bóng rổ',           'Giày cổ cao/trung hỗ trợ bật nhảy và ổn định cổ chân'),
('DM10', 'DM04', 'Giày đá bóng',           'Giày đinh TF/FG chuyên dụng cho sân cỏ nhân tạo và tự nhiên'),
('DM11', 'DM05', 'Giày Sneaker cổ thấp',   'Giày sneaker form thấp gọn nhẹ dễ phối đồ');

-- -----------------------------------------------------------------------------
-- 2. THUONGHIEU (5 thương hiệu giày)
-- -----------------------------------------------------------------------------
INSERT INTO THUONGHIEU (MaThuongHieu, TenThuongHieu, QuocGia, Website) VALUES
('TH01', 'Nike',          'Mỹ',       'https://www.nike.com'),
('TH02', 'Adidas',        'Đức',      'https://www.adidas.com'),
('TH03', 'Puma',          'Đức',      'https://www.puma.com'),
('TH04', 'Biti''s Hunter','Việt Nam', 'https://bitis.com.vn'),
('TH05', 'New Balance',   'Mỹ',       NULL);

-- -----------------------------------------------------------------------------
-- 3. SANPHAM (10 mẫu giày)
-- -----------------------------------------------------------------------------
INSERT INTO SANPHAM (MaSanPham, TenSanPham, MoTa, GiaNiemYet, TrangThai, MaDanhMuc, MaThuongHieu) VALUES
('SP01', 'Nike Pegasus 41',              'Giày chạy đường trường đệm Air Zoom thế hệ mới',        3200000.00, 'DANG_KINH_DOANH',  'DM07', 'TH01'),
('SP02', 'Adidas Ultraboost Light',      'Giày chạy đường trường đế Boost siêu nhẹ',             3800000.00, 'DANG_KINH_DOANH',  'DM07', 'TH02'),
('SP03', 'Nike LeBron Witness 8',        'Giày bóng rổ bám sân và ổn định cổ chân',              2900000.00, 'DANG_KINH_DOANH',  'DM09', 'TH01'),
('SP04', 'Biti''s Hunter Street',        'Giày sneaker cổ thấp phong cách đường phố',            950000.00, 'DANG_KINH_DOANH',  'DM11', 'TH04'),
('SP05', 'Adidas Predator League TF',    'Giày đá bóng sân cỏ nhân tạo kiểm soát bóng tốt',     2150000.00, 'DANG_KINH_DOANH',  'DM10', 'TH02'),
('SP06', 'New Balance Fresh Foam Hierro','Giày chạy địa hình đế Vibram chống trượt',            3450000.00, 'DANG_KINH_DOANH',  'DM08', 'TH05'),
('SP07', 'Puma Suede Classic',           'Giày sneaker cổ thấp chất liệu da lộn cổ điển',       1950000.00, 'DANG_KINH_DOANH',  'DM11', 'TH03'),
('SP08', 'Biti''s Hunter Core Slip-on',  'Giày lười thể thao không dây thoáng khí nhẹ chân',     850000.00, 'DANG_KINH_DOANH',  'DM06', 'TH04'),
('SP09', 'Puma Velocity Nitro 2',        'Giày chạy bộ đệm Nitro tạm ngưng nhập lô mới',        2750000.00, 'TAM_NGUNG',        'DM03', 'TH03'),
('SP10', 'Nike Air Max Classic',         'Giày sneaker cổ điển đã ngừng sản xuất mẫu mới',      2600000.00, 'NGUNG_KINH_DOANH', 'DM05', 'TH01');

-- -----------------------------------------------------------------------------
-- 4. CHITIET_SP (20 biến thể giày theo size và màu sắc)
-- -----------------------------------------------------------------------------
INSERT INTO CHITIET_SP (MaChiTietSP, SKU, Size, MauSac, TrangThai, MaSanPham) VALUES
('CTSP01', 'NK-PEG41-DEN-41', '41', 'Đen',        'DANG_BAN',  'SP01'),
('CTSP02', 'NK-PEG41-DEN-42', '42', 'Đen',        'DANG_BAN',  'SP01'),
('CTSP03', 'NK-PEG41-TRG-42', '42', 'Trắng',      'DANG_BAN',  'SP01'),
('CTSP04', 'AD-UBL-TRG-41',   '41', 'Trắng',      'DANG_BAN',  'SP02'),
('CTSP05', 'AD-UBL-XNH-42',   '42', 'Xanh navy',  'DANG_BAN',  'SP02'),
('CTSP06', 'NK-LBW8-DO-42',   '42', 'Đỏ',         'DANG_BAN',  'SP03'),
('CTSP07', 'NK-LBW8-DO-43',   '43', 'Đỏ',         'DANG_BAN',  'SP03'),
('CTSP08', 'BT-HST-XAM-40',   '40', 'Xám',        'DANG_BAN',  'SP04'),
('CTSP09', 'BT-HST-XAM-41',   '41', 'Xám',        'DANG_BAN',  'SP04'),
('CTSP10', 'AD-PRD-DEN-40',   '40', 'Đen',        'DANG_BAN',  'SP05'),
('CTSP11', 'AD-PRD-DEN-41',   '41', 'Đen',        'DANG_BAN',  'SP05'),
('CTSP12', 'NB-FFH-REU-41',   '41', 'Xanh rêu',   'DANG_BAN',  'SP06'),
('CTSP13', 'NB-FFH-REU-42',   '42', 'Xanh rêu',   'DANG_BAN',  'SP06'),
('CTSP14', 'NB-FFH-CAM-41',   '41', 'Cam đất',    'DANG_BAN',  'SP06'),
('CTSP15', 'PM-SDE-XNH-40',   '40', 'Xanh dương', 'DANG_BAN',  'SP07'),
('CTSP16', 'PM-SDE-XNH-41',   '41', 'Xanh dương', 'DANG_BAN',  'SP07'),
('CTSP17', 'BT-SLP-DEN-39',   '39', 'Đen',        'DANG_BAN',  'SP08'),
('CTSP18', 'BT-SLP-XAM-40',   '40', 'Xám',        'DANG_BAN',  'SP08'),
('CTSP19', 'PM-VN2-BAC-41',   '41', 'Bạc',        'TAM_NGUNG', 'SP09'),
('CTSP20', 'NK-AMC-TRG-41',   '41', 'Trắng',      'NGUNG_BAN', 'SP10');

-- -----------------------------------------------------------------------------
-- 5. NHACUNGCAP (5 nhà cung cấp giày; 2 bản ghi MaSoThue NULL kiểm chứng UNIQUE)
-- -----------------------------------------------------------------------------
INSERT INTO NHACUNGCAP (MaNCC, TenNCC, SDT, Email, DiaChi, MaSoThue) VALUES
('NCC01', 'Công ty TNHH Giày Thể Thao Việt',        '02838123456', 'sales@giaythethaoviet.vn', '120 Nguyễn Văn Linh, Q.7, TP.HCM',       '0312345678'),
('NCC02', 'Công ty CP Phân Phối Giày Đông Á',       '02439234567', 'contact@donga-shoes.vn',   '45 Giải Phóng, Hai Bà Trưng, Hà Nội',   '0109876543'),
('NCC03', 'Công ty TNHH Giày Quốc Tế Minh Long',    '02838456789', 'minhlong@shoes.vn',        '88 Cộng Hòa, Tân Bình, TP.HCM',         '0304567891'),
('NCC04', 'Cơ Sở Gia Công Giày Hoàng Hải',          '0912345678',  NULL,                       '15 Lê Duẩn, Hải Châu, Đà Nẵng',          NULL),
('NCC05', 'Công ty TNHH Nhập Khẩu Sneaker Thành Công','02837654321','thanhcong@sneaker.vn',    'KCN Tân Bình, Q. Tân Phú, TP.HCM',       NULL);

-- -----------------------------------------------------------------------------
-- 6. CUNGCAP (42 liên kết N:N; khớp hoàn toàn với các phiếu nhập từ NCC)
-- -----------------------------------------------------------------------------
INSERT INTO CUNGCAP (MaChiTietSP, MaNCC, GiaNhap, ThoiGianGiaoHangDuKien) VALUES
('CTSP01', 'NCC01', 2200000.00, 3),
('CTSP02', 'NCC01', 2200000.00, 3),
('CTSP03', 'NCC01', 2250000.00, 4),
('CTSP04', 'NCC01', 2650000.00, 3),
('CTSP05', 'NCC01', 2650000.00, 3),
('CTSP06', 'NCC01', 2000000.00, 5),
('CTSP07', 'NCC01', 2000000.00, 5),
('CTSP08', 'NCC01',  620000.00, 3),
('CTSP09', 'NCC01',  620000.00, 3),
('CTSP10', 'NCC01', 1480000.00, 2),
('CTSP11', 'NCC01', 1450000.00, 2),
('CTSP12', 'NCC01', 2380000.00, 3),
('CTSP13', 'NCC01', 2380000.00, 3),
('CTSP14', 'NCC01', 2400000.00, 3),
('CTSP15', 'NCC01', 1320000.00, 2),
('CTSP16', 'NCC01', 1340000.00, 2),
('CTSP17', 'NCC01',  560000.00, 2),
('CTSP18', 'NCC01',  560000.00, 2),

('CTSP01', 'NCC02', 2230000.00, 2),
('CTSP02', 'NCC02', 2240000.00, 2),
('CTSP03', 'NCC02', 2260000.00, 3),
('CTSP04', 'NCC02', 2600000.00, 4),
('CTSP05', 'NCC02', 2620000.00, 4),
('CTSP06', 'NCC02', 2050000.00, 3),
('CTSP07', 'NCC02', 2050000.00, 3),
('CTSP08', 'NCC02',  630000.00, 2),
('CTSP12', 'NCC02', 2420000.00, 2),
('CTSP13', 'NCC02', 2420000.00, 2),
('CTSP15', 'NCC02', 1320000.00, 2),
('CTSP16', 'NCC02', 1340000.00, 2),
('CTSP17', 'NCC02',  565000.00, 2),

('CTSP01', 'NCC03', 2280000.00, 5),
('CTSP08', 'NCC03',  620000.00, 2),
('CTSP09', 'NCC03',  620000.00, 2),
('CTSP10', 'NCC03', 1450000.00, 3),
('CTSP11', 'NCC03', 1450000.00, 3),
('CTSP12', 'NCC03', 2380000.00, 4),

('CTSP17', 'NCC04',  550000.00, 4),
('CTSP19', 'NCC04', 1900000.00, 5),

('CTSP12', 'NCC05', 2350000.00, 3),
('CTSP15', 'NCC05', 1300000.00, 3),
('CTSP19', 'NCC05', 1880000.00, 4);

-- -----------------------------------------------------------------------------
-- 7. KHO (4 kho: 3 kho hoạt động tại 3 miền, 1 kho tạm đóng)
-- -----------------------------------------------------------------------------
INSERT INTO KHO (MaKho, TenKho, DiaChi, SDT, TrangThai) VALUES
('KHO01', 'Kho Trung Tâm TP.HCM',    '18 Võ Văn Kiệt, Q.1, TP.HCM',           '02838111222', 'HOAT_DONG'),
('KHO02', 'Kho Miền Bắc Hà Nội',     '99 Phạm Hùng, Nam Từ Liêm, Hà Nội',     '02437333444', 'HOAT_DONG'),
('KHO03', 'Kho Miền Trung Đà Nẵng',  '210 Nguyễn Hữu Thọ, Cẩm Lệ, Đà Nẵng',   '02363555666', 'HOAT_DONG'),
('KHO04', 'Kho Dự Phòng Bình Dương', 'KCN VSIP 1, Thuận An, Bình Dương',      '02743777888', 'TAM_DONG');

-- -----------------------------------------------------------------------------
-- 8. KHACHHANG (8 khách hàng)
-- -----------------------------------------------------------------------------
INSERT INTO KHACHHANG (MaKH, HoTen, SDT, Email, DiaChi, NgayDangKy) VALUES
('KH01', 'Nguyễn Minh Tuấn', '0903111222', 'tuan.nguyen@gmail.com',  '25 Lê Lợi, Q.1, TP.HCM',               '2025-11-15 09:30:00'),
('KH02', 'Trần Thị Thu Hà',  '0912222333', 'thuha.tran@gmail.com',   '14 Trần Duy Hưng, Cầu Giấy, Hà Nội',   '2026-01-10 14:15:00'),
('KH03', 'Lê Hoàng Nam',     '0988333444', 'nam.le@outlook.com',     '302 Cách Mạng Tháng 8, Q.3, TP.HCM',   '2026-03-05 10:00:00'),
('KH04', 'Phạm Quốc Bảo',    '0935444555', NULL,                     '52 Bạch Đằng, Hải Châu, Đà Nẵng',      '2026-05-12 16:45:00'),
('KH05', 'Võ Ngọc Lan Anh',  '0909555666', 'lananh.vo@gmail.com',    '19 Nguyễn Thị Minh Khai, Q.1, TP.HCM', '2026-07-20 11:20:00'),
('KH06', 'Đặng Văn Hùng',    '0977666777', 'hung.dang@yahoo.com',    '78 Phan Xích Long, Phú Nhuận, TP.HCM', '2026-08-18 08:50:00'),
('KH07', 'Hoàng Thùy Linh',  '0966777888', NULL,                     '105 Kim Mã, Ba Đình, Hà Nội',          '2026-09-02 19:10:00'),
('KH08', 'Bùi Đức Thịnh',    '0944888999', 'thinh.bui@gmail.com',    NULL,                                   '2026-10-01 13:05:00');

-- -----------------------------------------------------------------------------
-- 9. DONHANG (10 đơn hàng qua nhiều tháng & đủ 6 trạng thái)
-- -----------------------------------------------------------------------------
INSERT INTO DONHANG (MaDonHang, NgayDat, TrangThai, DiaChiGiaoHang, PhuongThucThanhToan, GhiChu, MaKH, MaKho) VALUES
('DH01', '2026-09-20 10:15:00', 'HOAN_TAT',     '25 Lê Lợi, Q.1, TP.HCM',               'CHUYEN_KHOAN', 'Đặt theo giá ưu đãi tháng 9', 'KH01', 'KHO01'),
('DH02', CURRENT_TIMESTAMP - INTERVAL '5 days', 'HOAN_TAT',     '14 Trần Duy Hưng, Cầu Giấy, Hà Nội',   'COD',          NULL,                          'KH02', 'KHO02'),
('DH03', CURRENT_TIMESTAMP - INTERVAL '3 days', 'HOAN_TAT',     '302 Cách Mạng Tháng 8, Q.3, TP.HCM',   'THE',          'Giao giờ hành chính',         'KH03', 'KHO01'),
('DH04', '2026-10-18 14:20:00', 'HOAN_TAT',     '52 Bạch Đằng, Hải Châu, Đà Nẵng',      'VI_DIEN_TU',   NULL,                          'KH04', 'KHO03'),
('DH05', '2026-10-25 11:05:00', 'HOAN_TAT',     '19 Nguyễn Thị Minh Khai, Q.1, TP.HCM', 'COD',          NULL,                          'KH05', 'KHO01'),
('DH06', '2026-11-05 16:00:00', 'DANG_GIAO',    '142 Cầu Giấy, Cầu Giấy, Hà Nội',       'CHUYEN_KHOAN', 'Giao cho người thân nhận hộ', 'KH01', 'KHO02'),
('DH07', '2026-11-08 10:30:00', 'DA_XAC_NHAN',  '78 Phan Xích Long, Phú Nhuận, TP.HCM', 'COD',          'Đã giữ hàng tại Kho TP.HCM',  'KH06', 'KHO01'),
('DH08', '2026-11-09 09:15:00', 'DANG_XU_LY',   '105 Kim Mã, Ba Đình, Hà Nội',          'VI_DIEN_TU',   'Đang đóng gói tại Kho HN',    'KH07', 'KHO02'),
('DH09', '2026-11-10 08:45:00', 'CHO_XAC_NHAN', '33 Nguyễn Văn Linh, Thanh Khê, ĐN',    'TIEN_MAT',     'Khách đặt nhận tại quầy ĐN',  'KH08', 'KHO03'),
('DH10', '2026-11-02 17:20:00', 'DA_HUY',       '302 Cách Mạng Tháng 8, Q.3, TP.HCM',   'COD',          'Khách đổi ý trước khi giao',  'KH03', 'KHO01');

-- -----------------------------------------------------------------------------
-- 10. CHITIET_DONHANG (18 dòng chi tiết; ChietKhau là %)
--     Lưu ý DH01 (CTSP01 = 3.100.000) & DH03 (CTSP06 = 2.850.000) khác giá niêm
--     yết hiện tại để minh họa việc chốt đơn giá bán tại thời điểm đặt hàng.
-- -----------------------------------------------------------------------------
INSERT INTO CHITIET_DONHANG (MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau) VALUES
('CTDH01', 'DH01', 'CTSP01', 2, 3100000.00,  5.00),
('CTDH02', 'DH01', 'CTSP12', 3, 3450000.00,  0.00),
('CTDH03', 'DH02', 'CTSP04', 2, 3800000.00,  0.00),
('CTDH04', 'DH02', 'CTSP15', 4, 1950000.00, 10.00),
('CTDH05', 'DH03', 'CTSP06', 1, 2850000.00,  0.00),
('CTDH06', 'DH03', 'CTSP17', 2,  850000.00,  5.00),
('CTDH07', 'DH04', 'CTSP08', 2,  950000.00,  0.00),
('CTDH08', 'DH04', 'CTSP10', 2, 2150000.00,  0.00),
('CTDH09', 'DH05', 'CTSP02', 1, 3200000.00, 10.00),
('CTDH10', 'DH05', 'CTSP13', 2, 3450000.00,  0.00),
('CTDH11', 'DH06', 'CTSP05', 2, 3800000.00,  0.00),
('CTDH12', 'DH06', 'CTSP17', 1,  850000.00,  0.00),
('CTDH13', 'DH07', 'CTSP01', 2, 3200000.00,  0.00),
('CTDH14', 'DH07', 'CTSP14', 3, 3450000.00,  5.00),
('CTDH15', 'DH08', 'CTSP01', 1, 3200000.00,  0.00),
('CTDH16', 'DH08', 'CTSP12', 4, 3450000.00,  0.00),
('CTDH17', 'DH09', 'CTSP02', 1, 3200000.00,  0.00),
('CTDH18', 'DH10', 'CTSP03', 1, 3200000.00,  0.00);

-- -----------------------------------------------------------------------------
-- 11. PHIEUTRAHANG (4 phiếu trả hàng gắn với các đơn HOAN_TAT)
-- -----------------------------------------------------------------------------
INSERT INTO PHIEUTRAHANG (MaPhieuTra, MaDonHang, NgayTiepNhan, LyDo, TrangThai, GhiChu) VALUES
('PTH01', 'DH01', '2026-09-25 10:00:00', 'Đổi giày Pegasus sang size 42 và trả 1 đôi giày trail còn nguyên hộp do đặt dư', 'HOAN_TAT', 'Đã nhập kho hàng trả còn mới và xuất đổi size'),
('PTH02', 'DH02', CURRENT_TIMESTAMP - INTERVAL '2 days', 'Khách đặt nhầm 2 đôi Ultraboost cùng màu, trả lại 1 đôi',                         'HOAN_TAT', 'Hàng còn nguyên tem hộp, đã hoàn tiền'),
('PTH03', 'DH04', '2026-10-22 11:15:00', 'Khách yêu cầu trả giày đá bóng sau khi đã sử dụng ngoài sân cỏ',                 'TU_CHOI',  'Không đạt điều kiện còn mới để đổi trả'),
('PTH04', 'DH05', '2026-11-08 15:40:00', 'Khách mang hơi chật/rộng, yêu cầu đổi giày từ size 42 sang 41',                  'DANG_XU_LY', 'Đang chờ nhận hàng khách gửi về kho');

-- -----------------------------------------------------------------------------
-- 12. CHITIET_TRAHANG (5 dòng chi tiết trả/đổi giày)
-- -----------------------------------------------------------------------------
INSERT INTO CHITIET_TRAHANG (
    MaCTTraHang, MaPhieuTra, MaCTDonHang, SoLuongTra,
    TinhTrangSanPham, HinhThucXuLy, DonGiaHoan, MaChiTietSPDoi
) VALUES
('CTTH01', 'PTH01', 'CTDH01', 1, 'CON_MOI',    'DOI_HANG',     0.00,       'CTSP02'),
('CTTH02', 'PTH01', 'CTDH02', 1, 'CON_MOI',    'HOAN_TIEN',    3450000.00, NULL),
('CTTH03', 'PTH02', 'CTDH03', 1, 'CON_MOI',    'HOAN_TIEN',    3800000.00, NULL),
('CTTH04', 'PTH03', 'CTDH08', 1, 'DA_SU_DUNG', 'TU_CHOI_DONG', 0.00,       NULL),
('CTTH05', 'PTH04', 'CTDH10', 1, 'CON_MOI',    'DOI_HANG',     0.00,       'CTSP12');

-- -----------------------------------------------------------------------------
-- 13. PHIEUKHO (18 phiếu kho: đủ NHAP / XUAT / CHUYEN và các trạng thái)
-- -----------------------------------------------------------------------------
INSERT INTO PHIEUKHO (MaPhieu, LoaiPhieu, NgayLap, TrangThai, GhiChu) VALUES
('PK01', 'NHAP',   '2026-09-01 08:00:00', 'HOAN_TAT',        'Nhập lô giày đợt đầu từ NCC01 về Kho TP.HCM'),
('PK02', 'NHAP',   '2026-09-05 08:30:00', 'HOAN_TAT',        'Nhập lô giày từ NCC02 về Kho Hà Nội'),
('PK03', 'NHAP',   '2026-09-10 09:00:00', 'HOAN_TAT',        'Nhập giày sneaker và giày đá bóng từ NCC03 về Kho Đà Nẵng'),
('PK04', 'NHAP',   '2026-10-01 09:30:00', 'HOAN_TAT',        'Nhập giày lưu kho dự phòng Bình Dương từ NCC05'),
('PK05', 'CHUYEN', '2026-09-15 10:00:00', 'HOAN_TAT',        'Điều chuyển giày từ TP.HCM ra Đà Nẵng'),
('PK06', 'CHUYEN', '2026-11-08 11:00:00', 'DANG_VAN_CHUYEN', 'Đang vận chuyển giày từ TP.HCM ra Hà Nội, chưa nhập kho đích'),
('PK07', 'XUAT',   '2026-09-21 09:00:00', 'HOAN_TAT',        'Xuất kho giao đơn hàng DH01'),
('PK08', 'XUAT',   CURRENT_TIMESTAMP - INTERVAL '4 days', 'HOAN_TAT',        'Xuất kho giao đơn hàng DH02'),
('PK09', 'XUAT',   CURRENT_TIMESTAMP - INTERVAL '2 days', 'HOAN_TAT',        'Xuất kho giao đơn hàng DH03'),
('PK10', 'XUAT',   '2026-10-19 10:15:00', 'HOAN_TAT',        'Xuất kho giao đơn hàng DH04'),
('PK11', 'XUAT',   '2026-10-26 09:20:00', 'HOAN_TAT',        'Xuất kho giao đơn hàng DH05'),
('PK12', 'XUAT',   '2026-11-06 08:40:00', 'HOAN_TAT',        'Xuất kho giao đơn hàng DH06'),
('PK13', 'NHAP',   '2026-11-08 16:00:00', 'DANG_XU_LY',      'Đang kiểm đếm lô giày nhập bổ sung tại Kho Hà Nội'),
('PK14', 'NHAP',   '2026-11-09 14:00:00', 'BAN_NHAP',        'Phiếu nháp dự kiến nhập thêm giày về Kho TP.HCM'),
('PK15', 'CHUYEN', '2026-11-09 15:00:00', 'DA_HUY',          'Hủy kế hoạch chuyển giày từ Hà Nội sang Đà Nẵng'),
('PK16', 'NHAP',   '2026-09-26 10:30:00', 'HOAN_TAT',        'Nhập lại 2 đôi giày còn mới từ phiếu trả PTH01 vào kho bán'),
('PK17', 'XUAT',   '2026-09-26 11:00:00', 'HOAN_TAT',        'Xuất kho đổi size giày cho phiếu trả PTH01'),
('PK18', 'NHAP',   CURRENT_TIMESTAMP - INTERVAL '1 day', 'HOAN_TAT',        'Nhập lại 1 đôi Adidas Ultraboost từ phiếu trả PTH02');

-- -----------------------------------------------------------------------------
-- 14. PHIEUNHAPKHO (8 bản ghi khớp với 8 phiếu LoaiPhieu = 'NHAP')
-- -----------------------------------------------------------------------------
INSERT INTO PHIEUNHAPKHO (MaPhieu, MaNCC, MaPhieuTra, KhoDich) VALUES
('PK01', 'NCC01', NULL,    'KHO01'),
('PK02', 'NCC02', NULL,    'KHO02'),
('PK03', 'NCC03', NULL,    'KHO03'),
('PK04', 'NCC05', NULL,    'KHO04'),
('PK13', 'NCC02', NULL,    'KHO02'),
('PK14', 'NCC01', NULL,    'KHO01'),
('PK16', NULL,    'PTH01', 'KHO01'),
('PK18', NULL,    'PTH02', 'KHO02');

-- -----------------------------------------------------------------------------
-- 15. PHIEUXUATKHO (7 bản ghi khớp với 7 phiếu LoaiPhieu = 'XUAT')
-- -----------------------------------------------------------------------------
INSERT INTO PHIEUXUATKHO (MaPhieu, MaDonHang, MaPhieuTra, KhoNguon) VALUES
('PK07', 'DH01', NULL,    'KHO01'),
('PK08', 'DH02', NULL,    'KHO02'),
('PK09', 'DH03', NULL,    'KHO01'),
('PK10', 'DH04', NULL,    'KHO03'),
('PK11', 'DH05', NULL,    'KHO01'),
('PK12', 'DH06', NULL,    'KHO02'),
('PK17', NULL,   'PTH01', 'KHO01');

-- -----------------------------------------------------------------------------
-- 16. PHIEUCHUYENKHO (3 bản ghi khớp với 3 phiếu LoaiPhieu = 'CHUYEN')
-- -----------------------------------------------------------------------------
INSERT INTO PHIEUCHUYENKHO (MaPhieu, KhoNguon, KhoDich) VALUES
('PK05', 'KHO01', 'KHO03'),
('PK06', 'KHO01', 'KHO02'),
('PK15', 'KHO02', 'KHO03');

-- -----------------------------------------------------------------------------
-- 17. CHITIET_PHIEUKHO (61 dòng chi tiết, mỗi phiếu kho >= 1 dòng)
-- -----------------------------------------------------------------------------
INSERT INTO CHITIET_PHIEUKHO (MaCTPhieuKho, MaPhieu, MaChiTietSP, SoLuong, DonGia) VALUES
-- PK01: Nhập NCC01 vào KHO01
('CTPK01', 'PK01', 'CTSP01', 30, 2200000.00),
('CTPK02', 'PK01', 'CTSP02', 35, 2200000.00),
('CTPK03', 'PK01', 'CTSP03',  4, 2250000.00),
('CTPK04', 'PK01', 'CTSP04', 25, 2650000.00),
('CTPK05', 'PK01', 'CTSP05', 20, 2650000.00),
('CTPK06', 'PK01', 'CTSP06', 18, 2000000.00),
('CTPK07', 'PK01', 'CTSP07', 15, 2000000.00),
('CTPK08', 'PK01', 'CTSP08', 25,  620000.00),
('CTPK09', 'PK01', 'CTSP09', 20,  620000.00),
('CTPK10', 'PK01', 'CTSP10', 30, 1480000.00),
('CTPK11', 'PK01', 'CTSP11', 25, 1450000.00),
('CTPK12', 'PK01', 'CTSP12', 40, 2380000.00),
('CTPK13', 'PK01', 'CTSP13', 35, 2380000.00),
('CTPK14', 'PK01', 'CTSP14', 30, 2400000.00),
('CTPK15', 'PK01', 'CTSP15', 35, 1320000.00),
('CTPK16', 'PK01', 'CTSP16', 25, 1340000.00),
('CTPK17', 'PK01', 'CTSP17', 20,  560000.00),
('CTPK18', 'PK01', 'CTSP18',  2,  560000.00),

-- PK02: Nhập NCC02 vào KHO02
('CTPK19', 'PK02', 'CTSP01', 20, 2230000.00),
('CTPK20', 'PK02', 'CTSP02', 18, 2240000.00),
('CTPK21', 'PK02', 'CTSP04', 15, 2600000.00),
('CTPK22', 'PK02', 'CTSP05', 16, 2620000.00),
('CTPK23', 'PK02', 'CTSP06', 12, 2050000.00),
('CTPK24', 'PK02', 'CTSP08', 15,  630000.00),
('CTPK25', 'PK02', 'CTSP12', 25, 2420000.00),
('CTPK26', 'PK02', 'CTSP13', 20, 2420000.00),
('CTPK27', 'PK02', 'CTSP15', 22, 1320000.00),
('CTPK28', 'PK02', 'CTSP17', 14,  565000.00),

-- PK03: Nhập NCC03 vào KHO03
('CTPK29', 'PK03', 'CTSP01', 10, 2280000.00),
('CTPK30', 'PK03', 'CTSP08', 12,  620000.00),
('CTPK31', 'PK03', 'CTSP09',  3,  620000.00),
('CTPK32', 'PK03', 'CTSP10', 15, 1450000.00),
('CTPK33', 'PK03', 'CTSP11', 10, 1450000.00),
('CTPK34', 'PK03', 'CTSP12', 15, 2380000.00),

-- PK04: Nhập NCC05 vào KHO04
('CTPK35', 'PK04', 'CTSP12', 10, 2350000.00),
('CTPK36', 'PK04', 'CTSP15', 10, 1300000.00),
('CTPK37', 'PK04', 'CTSP19',  8, 1880000.00),

-- PK05: Chuyển hoàn tất KHO01 -> KHO03
('CTPK38', 'PK05', 'CTSP02', 10, NULL),
('CTPK39', 'PK05', 'CTSP15', 15, NULL),

-- PK06: Đang vận chuyển KHO01 -> KHO02
('CTPK40', 'PK06', 'CTSP04',  5, NULL),
('CTPK41', 'PK06', 'CTSP16', 10, NULL),

-- PK07..PK12: Xuất giao đơn DH01..DH06
('CTPK42', 'PK07', 'CTSP01',  2, 3100000.00),
('CTPK43', 'PK07', 'CTSP12',  3, 3450000.00),
('CTPK44', 'PK08', 'CTSP04',  2, 3800000.00),
('CTPK45', 'PK08', 'CTSP15',  4, 1950000.00),
('CTPK46', 'PK09', 'CTSP06',  1, 2850000.00),
('CTPK47', 'PK09', 'CTSP17',  2,  850000.00),
('CTPK48', 'PK10', 'CTSP08',  2,  950000.00),
('CTPK49', 'PK10', 'CTSP10',  2, 2150000.00),
('CTPK50', 'PK11', 'CTSP02',  1, 3200000.00),
('CTPK51', 'PK11', 'CTSP13',  2, 3450000.00),
('CTPK52', 'PK12', 'CTSP05',  2, 3800000.00),
('CTPK53', 'PK12', 'CTSP17',  1,  850000.00),

-- PK13..PK15: Phiếu chưa hoàn tất / đã hủy
('CTPK54', 'PK13', 'CTSP03', 10, 2260000.00),
('CTPK55', 'PK13', 'CTSP07',  8, 2050000.00),
('CTPK56', 'PK14', 'CTSP03', 12, 2250000.00),
('CTPK57', 'PK15', 'CTSP08',  5, NULL),

-- PK16..PK18: Phiếu nhập trả / xuất đổi hàng
('CTPK58', 'PK16', 'CTSP01',  1, NULL),
('CTPK59', 'PK16', 'CTSP12',  1, NULL),
('CTPK60', 'PK17', 'CTSP02',  1, NULL),
('CTPK61', 'PK18', 'CTSP04',  1, NULL);

-- -----------------------------------------------------------------------------
-- 18. TONKHO (40 dòng tồn kho khớp 100% với lịch sử phiếu kho & đơn hàng)
--     - CTSP03 tại KHO01: Khả dụng = 4 (cảnh báo sắp hết hàng)
--     - CTSP09 tại KHO03: Khả dụng = 3 (cảnh báo sắp hết hàng)
--     - CTSP18 tại KHO01: Khả dụng = 2 (phù hợp test cạnh tranh đồng thời)
--     - CTSP20 tại KHO01: Thực tế = 0 (giày ngừng bán, hết hàng)
-- -----------------------------------------------------------------------------
INSERT INTO TONKHO (MaKho, MaChiTietSP, SoLuongThucTe, SoLuongDaGiu, NgayCapNhat) VALUES
('KHO01', 'CTSP01', 29, 2, '2026-11-08 10:30:00'),
('KHO01', 'CTSP02', 23, 0, '2026-10-26 09:20:00'),
('KHO01', 'CTSP03',  4, 0, '2026-09-01 08:00:00'),
('KHO01', 'CTSP04', 20, 0, '2026-11-08 11:00:00'),
('KHO01', 'CTSP05', 20, 0, '2026-09-01 08:00:00'),
('KHO01', 'CTSP06', 17, 0, '2026-10-11 08:45:00'),
('KHO01', 'CTSP07', 15, 0, '2026-09-01 08:00:00'),
('KHO01', 'CTSP08', 25, 0, '2026-09-01 08:00:00'),
('KHO01', 'CTSP09', 20, 0, '2026-09-01 08:00:00'),
('KHO01', 'CTSP10', 30, 0, '2026-09-01 08:00:00'),
('KHO01', 'CTSP11', 25, 0, '2026-09-01 08:00:00'),
('KHO01', 'CTSP12', 38, 0, '2026-09-26 10:30:00'),
('KHO01', 'CTSP13', 33, 0, '2026-10-26 09:20:00'),
('KHO01', 'CTSP14', 30, 3, '2026-11-08 10:30:00'),
('KHO01', 'CTSP15', 20, 0, '2026-09-15 10:00:00'),
('KHO01', 'CTSP16', 15, 0, '2026-11-08 11:00:00'),
('KHO01', 'CTSP17', 18, 0, '2026-10-11 08:45:00'),
('KHO01', 'CTSP18',  2, 0, '2026-09-01 08:00:00'),
('KHO01', 'CTSP20',  0, 0, '2026-09-01 08:00:00'),

('KHO02', 'CTSP01', 20, 1, '2026-11-09 09:15:00'),
('KHO02', 'CTSP02', 18, 0, '2026-09-05 08:30:00'),
('KHO02', 'CTSP04', 14, 0, '2026-10-03 10:00:00'),
('KHO02', 'CTSP05', 14, 0, '2026-11-06 08:40:00'),
('KHO02', 'CTSP06', 12, 0, '2026-09-05 08:30:00'),
('KHO02', 'CTSP08', 15, 0, '2026-09-05 08:30:00'),
('KHO02', 'CTSP12', 25, 4, '2026-11-09 09:15:00'),
('KHO02', 'CTSP13', 20, 0, '2026-09-05 08:30:00'),
('KHO02', 'CTSP15', 18, 0, '2026-09-26 09:30:00'),
('KHO02', 'CTSP17', 13, 0, '2026-11-06 08:40:00'),

('KHO03', 'CTSP01', 10, 0, '2026-09-10 09:00:00'),
('KHO03', 'CTSP02', 10, 0, '2026-09-15 10:00:00'),
('KHO03', 'CTSP08', 10, 0, '2026-10-19 10:15:00'),
('KHO03', 'CTSP09',  3, 0, '2026-09-10 09:00:00'),
('KHO03', 'CTSP10', 13, 0, '2026-10-19 10:15:00'),
('KHO03', 'CTSP11', 10, 0, '2026-09-10 09:00:00'),
('KHO03', 'CTSP12', 15, 0, '2026-09-10 09:00:00'),
('KHO03', 'CTSP15', 15, 0, '2026-09-15 10:00:00'),

('KHO04', 'CTSP12', 10, 0, '2026-10-01 09:30:00'),
('KHO04', 'CTSP15', 10, 0, '2026-10-01 09:30:00'),
('KHO04', 'CTSP19',  8, 0, '2026-10-01 09:30:00');

COMMIT;
