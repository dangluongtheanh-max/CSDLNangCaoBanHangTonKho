package com.csdl.seeder;

import com.csdl.model.*;

import javax.persistence.EntityManager;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Nạp bộ dữ liệu đối tượng mẫu vào CSDL ObjectDB, khớp 100% với 02_insert_data.sql.
 */
public class DataSeeder {

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static Date parseDate(String s) {
        try {
            return DF.parse(s);
        } catch (ParseException e) {
            return new Date();
        }
    }

    public static void seed(EntityManager em) {
        em.getTransaction().begin();

        // 1. KHACHHANG (KH01 - KH08)
        Map<String, KhachHang> khMap = new HashMap<>();
        khMap.put("KH01", new KhachHang("KH01", "Nguyễn Minh Tuấn", "0903111222", "tuan.nguyen@gmail.com", "25 Lê Lợi, Q.1, TP.HCM", parseDate("2025-11-15 09:30:00")));
        khMap.put("KH02", new KhachHang("KH02", "Trần Thị Thu Hà",  "0912222333", "thuha.tran@gmail.com",  "14 Trần Duy Hưng, Cầu Giấy, Hà Nội", parseDate("2026-01-10 14:15:00")));
        khMap.put("KH03", new KhachHang("KH03", "Lê Hoàng Nam",     "0988333444", "nam.le@outlook.com",    "302 Cách Mạng Tháng 8, Q.3, TP.HCM", parseDate("2026-03-05 10:00:00")));
        khMap.put("KH04", new KhachHang("KH04", "Phạm Quốc Bảo",    "0935444555", null,                    "52 Bạch Đằng, Hải Châu, Đà Nẵng", parseDate("2026-05-12 16:45:00")));
        khMap.put("KH05", new KhachHang("KH05", "Võ Ngọc Lan Anh",  "0909555666", "lananh.vo@gmail.com",   "19 Nguyễn Thị Minh Khai, Q.1, TP.HCM", parseDate("2026-07-20 11:20:00")));
        khMap.put("KH06", new KhachHang("KH06", "Đặng Văn Hùng",    "0977666777", "hung.dang@yahoo.com",   "78 Phan Xích Long, Phú Nhuận, TP.HCM", parseDate("2026-08-18 08:50:00")));
        khMap.put("KH07", new KhachHang("KH07", "Hoàng Thùy Linh",  "0966777888", null,                    "105 Kim Mã, Ba Đình, Hà Nội", parseDate("2026-09-02 19:10:00")));
        khMap.put("KH08", new KhachHang("KH08", "Bùi Đức Thịnh",    "0944888999", "thinh.bui@gmail.com",   null, parseDate("2026-10-01 13:05:00")));

        for (KhachHang kh : khMap.values()) {
            em.persist(kh);
        }

        // 2. SANPHAM (SP01 - SP10)
        Map<String, SanPham> spMap = new HashMap<>();
        spMap.put("SP01", new SanPham("SP01", "Nike Pegasus 41",               "Giày chạy đường trường đệm Air Zoom thế hệ mới", 3200000.0, "DANG_KINH_DOANH",  "DM07", "TH01"));
        spMap.put("SP02", new SanPham("SP02", "Adidas Ultraboost Light",       "Giày chạy đường trường đế Boost siêu nhẹ",      3800000.0, "DANG_KINH_DOANH",  "DM07", "TH02"));
        spMap.put("SP03", new SanPham("SP03", "Nike LeBron Witness 8",         "Giày bóng rổ bám sân và ổn định cổ chân",       2900000.0, "DANG_KINH_DOANH",  "DM09", "TH01"));
        spMap.put("SP04", new SanPham("SP04", "Biti's Hunter Street",         "Giày sneaker cổ thấp phong cách đường phố",      950000.0, "DANG_KINH_DOANH",  "DM11", "TH04"));
        spMap.put("SP05", new SanPham("SP05", "Adidas Predator League TF",     "Giày đá bóng sân cỏ nhân tạo kiểm soát bóng tốt", 2150000.0, "DANG_KINH_DOANH",  "DM10", "TH02"));
        spMap.put("SP06", new SanPham("SP06", "New Balance Fresh Foam Hierro", "Giày chạy địa hình đế Vibram chống trượt",     3450000.0, "DANG_KINH_DOANH",  "DM08", "TH05"));
        spMap.put("SP07", new SanPham("SP07", "Puma Suede Classic",            "Giày sneaker cổ thấp chất liệu da lộn cổ điển", 1950000.0, "DANG_KINH_DOANH",  "DM11", "TH03"));
        spMap.put("SP08", new SanPham("SP08", "Biti's Hunter Core Slip-on",   "Giày lười thể thao không dây thoáng khí",        850000.0, "DANG_KINH_DOANH",  "DM06", "TH04"));
        spMap.put("SP09", new SanPham("SP09", "Puma Velocity Nitro 2",         "Giày chạy bộ đệm Nitro tạm ngưng nhập lô mới", 2750000.0, "TAM_NGUNG",         "DM03", "TH03"));
        spMap.put("SP10", new SanPham("SP10", "Nike Air Max Classic",          "Giày sneaker cổ điển đã ngừng sản xuất mẫu mới", 2600000.0, "NGUNG_KINH_DOANH", "DM05", "TH01"));

        for (SanPham sp : spMap.values()) {
            em.persist(sp);
        }

        // 3. CHITIET_SP (CTSP01 - CTSP20)
        Map<String, ChiTietSP> ctspMap = new HashMap<>();
        Object[][] ctspData = {
            {"CTSP01", "NK-PEG41-DEN-41", "41", "Đen",        "DANG_BAN", "SP01"},
            {"CTSP02", "NK-PEG41-DEN-42", "42", "Đen",        "DANG_BAN", "SP01"},
            {"CTSP03", "NK-PEG41-TRG-42", "42", "Trắng",      "DANG_BAN", "SP01"},
            {"CTSP04", "AD-UBL-TRG-41",   "41", "Trắng",      "DANG_BAN", "SP02"},
            {"CTSP05", "AD-UBL-XNH-42",   "42", "Xanh navy",  "DANG_BAN", "SP02"},
            {"CTSP06", "NK-LBW8-DO-42",   "42", "Đỏ",         "DANG_BAN", "SP03"},
            {"CTSP07", "NK-LBW8-DO-43",   "43", "Đỏ",         "DANG_BAN", "SP03"},
            {"CTSP08", "BT-HST-XAM-40",   "40", "Xám",        "DANG_BAN", "SP04"},
            {"CTSP09", "BT-HST-XAM-41",   "41", "Xám",        "DANG_BAN", "SP04"},
            {"CTSP10", "AD-PRD-DEN-40",   "40", "Đen",        "DANG_BAN", "SP05"},
            {"CTSP11", "AD-PRD-DEN-41",   "41", "Đen",        "DANG_BAN", "SP05"},
            {"CTSP12", "NB-FFH-REU-41",   "41", "Xanh rêu",   "DANG_BAN", "SP06"},
            {"CTSP13", "NB-FFH-REU-42",   "42", "Xanh rêu",   "DANG_BAN", "SP06"},
            {"CTSP14", "NB-FFH-CAM-41",   "41", "Cam đất",    "DANG_BAN",  "SP06"},
            {"CTSP15", "PM-SDE-XNH-40",   "40", "Xanh dương", "DANG_BAN",  "SP07"},
            {"CTSP16", "PM-SDE-XNH-41",   "41", "Xanh dương", "DANG_BAN",  "SP07"},
            {"CTSP17", "BT-SLP-DEN-39",   "39", "Đen",        "DANG_BAN",  "SP08"},
            {"CTSP18", "BT-SLP-XAM-40",   "40", "Xám",        "DANG_BAN",  "SP08"},
            {"CTSP19", "PM-VN2-BAC-41",   "41", "Bạc",        "TAM_NGUNG", "SP09"},
            {"CTSP20", "NK-AMC-TRG-41",   "41", "Trắng",      "NGUNG_BAN", "SP10"}
        };

        for (Object[] r : ctspData) {
            String ma = (String) r[0];
            String sku = (String) r[1];
            String size = (String) r[2];
            String mau = (String) r[3];
            String tt = (String) r[4];
            SanPham sp = spMap.get((String) r[5]);

            ChiTietSP ctsp = new ChiTietSP(ma, sku, size, mau, tt, sp);
            sp.getDsBienThe().add(ctsp);
            ctspMap.put(ma, ctsp);
            em.persist(ctsp);
        }

        // 4. DONHANG (DH01 - DH10)
        Map<String, DonHang> dhMap = new HashMap<>();
        Object[][] dhData = {
            {"DH01", "2026-09-20 10:15:00", "HOAN_TAT",     "25 Lê Lợi, Q.1, TP.HCM",               "CHUYEN_KHOAN", "Đặt theo giá ưu đãi tháng 9", "KHO01", "KH01"},
            {"DH02", "2026-09-25 15:30:00", "HOAN_TAT",     "14 Trần Duy Hưng, Cầu Giấy, Hà Nội",   "COD",          null,                           "KHO02", "KH02"},
            {"DH03", "2026-10-10 09:40:00", "HOAN_TAT",     "302 Cách Mạng Tháng 8, Q.3, TP.HCM",   "THE",          "Giao giờ hành chính",          "KHO01", "KH03"},
            {"DH04", "2026-10-18 14:20:00", "HOAN_TAT",     "52 Bạch Đằng, Hải Châu, Đà Nẵng",      "VI_DIEN_TU",   null,                           "KHO03", "KH04"},
            {"DH05", "2026-10-25 11:05:00", "HOAN_TAT",     "19 Nguyễn Thị Minh Khai, Q.1, TP.HCM", "COD",          null,                           "KHO01", "KH05"},
            {"DH06", "2026-11-05 16:00:00", "DANG_GIAO",    "142 Cầu Giấy, Cầu Giấy, Hà Nội",       "CHUYEN_KHOAN", "Giao cho người thân nhận hộ",  "KHO02", "KH01"},
            {"DH07", "2026-11-08 10:30:00", "DA_XAC_NHAN",  "78 Phan Xích Long, Phú Nhuận, TP.HCM", "COD",          "Đã giữ hàng tại Kho TP.HCM",   "KHO01", "KH06"},
            {"DH08", "2026-11-09 09:15:00", "DANG_XU_LY",   "105 Kim Mã, Ba Đình, Hà Nội",          "VI_DIEN_TU",   "Đang đóng gói tại Kho HN",     "KHO02", "KH07"},
            {"DH09", "2026-11-10 08:45:00", "CHO_XAC_NHAN", "33 Nguyễn Văn Linh, Thanh Khê, ĐN",    "TIEN_MAT",     "Khách đặt nhận tại quầy ĐN",   "KHO03", "KH08"},
            {"DH10", "2026-11-02 17:20:00", "DA_HUY",       "302 Cách Mạng Tháng 8, Q.3, TP.HCM",   "COD",          "Khách đổi ý trước khi giao",   "KHO01", "KH03"}
        };

        for (Object[] r : dhData) {
            String ma = (String) r[0];
            Date ngay = parseDate((String) r[1]);
            String tt = (String) r[2];
            String dc = (String) r[3];
            String pttt = (String) r[4];
            String gc = (String) r[5];
            String kho = (String) r[6];
            KhachHang kh = khMap.get((String) r[7]);

            DonHang dh = new DonHang(ma, ngay, tt, dc, pttt, gc, kho, kh);
            kh.getDsDonHang().add(dh);
            dhMap.put(ma, dh);
            em.persist(dh);
        }

        // 5. CHITIET_DONHANG (CTDH01 - CTDH18)
        Object[][] ctdhData = {
            {"CTDH01", "DH01", "CTSP01", 2, 3100000.0,  5.0},
            {"CTDH02", "DH01", "CTSP12", 3, 3450000.0,  0.0},
            {"CTDH03", "DH02", "CTSP04", 2, 3800000.0,  0.0},
            {"CTDH04", "DH02", "CTSP15", 4, 1950000.0, 10.0},
            {"CTDH05", "DH03", "CTSP06", 1, 2850000.0,  0.0},
            {"CTDH06", "DH03", "CTSP17", 2,  850000.0,  5.0},
            {"CTDH07", "DH04", "CTSP08", 2,  950000.0,  0.0},
            {"CTDH08", "DH04", "CTSP10", 2, 2150000.0,  0.0},
            {"CTDH09", "DH05", "CTSP02", 1, 3200000.0, 10.0},
            {"CTDH10", "DH05", "CTSP13", 2, 3450000.0,  0.0},
            {"CTDH11", "DH06", "CTSP05", 2, 3800000.0,  0.0},
            {"CTDH12", "DH06", "CTSP17", 1,  850000.0,  0.0},
            {"CTDH13", "DH07", "CTSP01", 2, 3200000.0,  0.0},
            {"CTDH14", "DH07", "CTSP14", 3, 3450000.0,  5.0},
            {"CTDH15", "DH08", "CTSP01", 1, 3200000.0,  0.0},
            {"CTDH16", "DH08", "CTSP12", 4, 3450000.0,  0.0},
            {"CTDH17", "DH09", "CTSP02", 1, 3200000.0,  0.0},
            {"CTDH18", "DH10", "CTSP03", 1, 3200000.0,  0.0}
        };

        for (Object[] r : ctdhData) {
            String ma = (String) r[0];
            DonHang dh = dhMap.get((String) r[1]);
            ChiTietSP ctsp = ctspMap.get((String) r[2]);
            int sl = (Integer) r[3];
            double dg = (Double) r[4];
            double ck = (Double) r[5];

            ChiTietDonHang ctdh = new ChiTietDonHang(ma, sl, dg, ck, dh, ctsp);
            dh.getDsChiTiet().add(ctdh);
            ctsp.getDsChiTietDonHang().add(ctdh);
            em.persist(ctdh);
        }

        // 6. PHIEUXUATKHO (PK07 - PK12, PK17) kế thừa PhieuKho
        Object[][] pxData = {
            {"PK07", "2026-09-21 09:00:00", "HOAN_TAT", "Xuất kho giao đơn hàng DH01", "KHO01", null, "DH01"},
            {"PK08", "2026-09-26 09:30:00", "HOAN_TAT", "Xuất kho giao đơn hàng DH02", "KHO02", null, "DH02"},
            {"PK09", "2026-10-11 08:45:00", "HOAN_TAT", "Xuất kho giao đơn hàng DH03", "KHO01", null, "DH03"},
            {"PK10", "2026-10-19 10:15:00", "HOAN_TAT", "Xuất kho giao đơn hàng DH04", "KHO03", null, "DH04"},
            {"PK11", "2026-10-26 09:20:00", "HOAN_TAT", "Xuất kho giao đơn hàng DH05", "KHO01", null, "DH05"},
            {"PK12", "2026-11-06 08:40:00", "HOAN_TAT", "Xuất kho giao đơn hàng DH06", "KHO02", null, "DH06"},
            {"PK17", "2026-09-26 11:00:00", "HOAN_TAT", "Xuất kho đổi size giày cho phiếu trả PTH01", "KHO01", "PTH01", null}
        };

        for (Object[] r : pxData) {
            String ma = (String) r[0];
            Date ngay = parseDate((String) r[1]);
            String tt = (String) r[2];
            String gc = (String) r[3];
            String kho = (String) r[4];
            String pt = (String) r[5];
            DonHang dh = r[6] != null ? dhMap.get((String) r[6]) : null;

            PhieuXuatKho px = new PhieuXuatKho(ma, ngay, tt, gc, kho, pt, dh);
            if (dh != null) {
                dh.setPhieuXuat(px);
            }
            em.persist(px);
        }

        em.getTransaction().commit();
    }
}

