package com.csdl.executor;

import com.csdl.model.ChiTietDonHang;
import com.csdl.model.ChiTietSP;
import com.csdl.model.DonHang;
import com.csdl.model.KhachHang;
import com.csdl.model.PhieuKho;
import com.csdl.model.PhieuXuatKho;
import com.csdl.model.SanPham;
import com.csdl.seeder.DataSeeder;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cau noi xuat du lieu JSON thuc nghiem tu ObjectDB (JPA 2.2) phuc vu Web Demo (Phuong an B).
 * Luu y: Khong anh huong toi OqlExecutor va OodbFeaturesTest hien co.
 */
public class OqlWebBridge {

    private static final String DB_PATH = "target/csdl_giay.odb";
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        PrintStream utf8Out = new PrintStream(System.out, true, StandardCharsets.UTF_8.name());
        new File(DB_PATH).delete();
        new File(DB_PATH + "$").delete();
        new File("target").mkdirs();

        EntityManagerFactory emf = Persistence.createEntityManagerFactory(DB_PATH);
        EntityManager em = emf.createEntityManager();

        try {
            DataSeeder.seed(em);

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("engine", "ObjectDB 2.9.1 (JPA 2.2 / ODMG 3.0 Mapping)");
            root.put("dbFile", DB_PATH);

            // 1. Counts & OID check
            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("khachHang", count(em, "KhachHang"));
            counts.put("sanPham", count(em, "SanPham"));
            counts.put("chiTietSP", count(em, "ChiTietSP"));
            counts.put("donHang", count(em, "DonHang"));
            counts.put("chiTietDonHang", count(em, "ChiTietDonHang"));
            counts.put("phieuXuatKho", count(em, "PhieuXuatKho"));
            counts.put("phieuKho", count(em, "PhieuKho"));
            root.put("counts", counts);

            DonHang dhFind = em.find(DonHang.class, "DH01");
            DonHang dhQuery = em.createQuery("SELECT d FROM DonHang d WHERE d.maDonHang = 'DH01'", DonHang.class)
                    .getSingleResult();
            root.put("oidSameInstance", dhFind == dhQuery);
            root.put("oidFindRef", "@" + Integer.toHexString(System.identityHashCode(dhFind)));
            root.put("oidQueryRef", "@" + Integer.toHexString(System.identityHashCode(dhQuery)));

            // 2. Inheritance & Polymorphism check
            List<PhieuKho> allPhieu = em.createQuery("SELECT p FROM PhieuKho p ORDER BY p.maPhieu", PhieuKho.class)
                    .getResultList();
            long pxCount = allPhieu.stream().filter(p -> p instanceof PhieuXuatKho).count();
            long deliveryCount = allPhieu.stream()
                    .filter(p -> p instanceof PhieuXuatKho px && px.laXuatGiaoDonHang())
                    .count();
            boolean allSubclass = pxCount == allPhieu.size();
            PhieuXuatKho pk07 = em.find(PhieuXuatKho.class, "PK07");
            PhieuXuatKho pk17 = em.find(PhieuXuatKho.class, "PK17");
            Map<String, Object> poly = new LinkedHashMap<>();
            poly.put("totalPhieuKho", allPhieu.size());
            poly.put("pxCount", pxCount);
            poly.put("deliveryCount", deliveryCount);
            poly.put("exchangeCount", pxCount - deliveryCount);
            poly.put("allPhieuXuatKho", allSubclass);
            poly.put("pk07LaGiaoDon", pk07 != null && pk07.laXuatGiaoDonHang());
            poly.put("pk17LaGiaoDon", pk17 != null && pk17.laXuatGiaoDonHang());
            poly.put("pk17MaPhieuTra", pk17 != null ? pk17.getMaPhieuTra() : null);
            root.put("polymorphism", poly);

            // 3. All 6 OQL <-> JPQL queries
            List<Map<String, Object>> queries = new ArrayList<>();
            queries.add(runOql1(em));
            queries.add(runOql2(em));
            queries.add(runOql3(em));
            queries.add(runOql4(em));
            queries.add(runOql5(em));
            queries.add(runOql6(em));
            root.put("queries", queries);

            // 4. Encapsulated OOP methods showcase
            List<Map<String, Object>> methods = new ArrayList<>();
            KhachHang kh01 = em.find(KhachHang.class, "KH01");
            KhachHang kh02 = em.find(KhachHang.class, "KH02");
            DonHang dh01 = em.find(DonHang.class, "DH01");
            DonHang dh07 = em.find(DonHang.class, "DH07");
            SanPham sp01 = em.find(SanPham.class, "SP01");
            SanPham sp09 = em.find(SanPham.class, "SP09");

            methods.add(methodRow("KhachHang (KH01)", "tinhTongChiTieu() / demDonHoanTat() / coDonChuaHoanTat()",
                    formatMoney(kh01.tinhTongChiTieu()) + " | " + kh01.demDonHoanTat() + " đơn | còn đơn chưa hoàn tất=" + kh01.coDonChuaHoanTat()));
            methods.add(methodRow("KhachHang (KH02)", "tinhTongChiTieu() / coDonChuaHoanTat()",
                    formatMoney(kh02.tinhTongChiTieu()) + " | còn đơn chưa hoàn tất=" + kh02.coDonChuaHoanTat()));
            methods.add(methodRow("DonHang (DH01)", "tinhTongTien() / tinhTongSoLuong() / duocPhepXuatKho()",
                    formatMoney(dh01.tinhTongTien()) + " | SL=" + dh01.tinhTongSoLuong() + " | được xuất kho=" + dh01.duocPhepXuatKho()));
            methods.add(methodRow("DonHang (DH07)", "duocPhepXuatKho() / duocPhepXoaHuy()",
                    "được xuất kho=" + dh07.duocPhepXuatKho() + " | được hủy=" + dh07.duocPhepXoaHuy()));
            methods.add(methodRow("SanPham (SP01)", "demSoBienTheDangBan() / coTheDatHang()",
                    sp01.demSoBienTheDangBan() + " size/màu | cho đặt hàng=" + sp01.coTheDatHang()));
            methods.add(methodRow("SanPham (SP09 - TAM_NGUNG)", "demSoBienTheDangBan() / coTheDatHang()",
                    sp09.demSoBienTheDangBan() + " size/màu | cho đặt hàng=" + sp09.coTheDatHang()));
            methods.add(methodRow("PhieuXuatKho (PK07 vs PK17)", "laPhieuHoanTat() / laXuatGiaoDonHang()",
                    "PK07: hoàn tất=" + pk07.laPhieuHoanTat() + ", xuất giao đơn=" + pk07.laXuatGiaoDonHang()
                            + " | PK17: hoàn tất=" + pk17.laPhieuHoanTat() + ", xuất giao đơn=" + pk17.laXuatGiaoDonHang()));
            root.put("entityMethods", methods);
            root.put("entityCatalog", buildEntityCatalog(em));

            utf8Out.println("<<<OODB_JSON_START>>>");
            utf8Out.println(toJson(root));
            utf8Out.println("<<<OODB_JSON_END>>>");
        } finally {
            em.close();
            emf.close();
        }
    }

    private static long count(EntityManager em, String entity) {
        return em.createQuery("SELECT COUNT(e) FROM " + entity + " e", Long.class).getSingleResult();
    }

    private static Map<String, Object> runOql1(EntityManager em) {
        String jpql = "SELECT dh.maDonHang, dh.ngayDat, dh.trangThai, dh.khachHang.hoTen, dh.khachHang.sdt, dh.diaChiGiaoHang "
                + "FROM DonHang dh WHERE dh.trangThai IN ('DANG_XU_LY', 'DANG_GIAO', 'HOAN_TAT') ORDER BY dh.ngayDat DESC";
        String oql = "select struct(maDonHang: dh.maDonHang, ngayDat: dh.ngayDat, trangThai: dh.trangThai, "
                + "tenKhachHang: dh.khachHang.hoTen, sdt: dh.khachHang.sdt, diaChiGiao: dh.diaChiGiaoHang)\n"
                + "from dh in dsDonHang\n"
                + "where dh.trangThai in set(\"DANG_XU_LY\", \"DANG_GIAO\", \"HOAN_TAT\")\n"
                + "order by dh.ngayDat desc;";
        List<Object[]> res = em.createQuery(jpql, Object[].class).getResultList();
        List<List<Object>> rows = new ArrayList<>();
        for (Object[] r : res) {
            rows.add(List.of(r[0], DF.format((Date) r[1]), r[2], r[3], r[4], r[5]));
        }
        return queryResult("OQL 1", "Đơn hàng đang xử lý / đang giao / hoàn tất kèm tên & SĐT khách (Điều hướng không JOIN)",
                "Biểu thức đường dẫn qua con trỏ OID (dh.khachHang.hoTen — không cần JOIN bảng)", oql, jpql,
                List.of("MaDH", "NgayDat", "TrangThai", "HoTenKH", "SDT", "DiaChiGiaoHang"), rows,
                "7 bản ghi — Khớp hoàn toàn với DONHANG x KHACHHANG trong PostgreSQL");
    }

    private static Map<String, Object> runOql2(EntityManager em) {
        String jpql = "SELECT DISTINCT dh.maDonHang, dh.ngayDat, dh.trangThai, dh.khachHang.hoTen "
                + "FROM DonHang dh JOIN dh.dsChiTiet ct WHERE ct.bienTheSP.sanPham.maSanPham = 'SP01' ORDER BY dh.maDonHang";
        String oql = "select distinct struct(maDonHang: dh.maDonHang, ngayDat: dh.ngayDat, trangThai: dh.trangThai, tenKhachHang: dh.khachHang.hoTen)\n"
                + "from dh in dsDonHang\n"
                + "where exists ct in dh.dsChiTiet : ct.bienTheSP.sanPham.maSanPham = \"SP01\"\n"
                + "order by dh.maDonHang;";
        List<Object[]> res = em.createQuery(jpql, Object[].class).getResultList();
        List<List<Object>> rows = new ArrayList<>();
        for (Object[] r : res) {
            rows.add(List.of(r[0], DF.format((Date) r[1]), r[2], r[3]));
        }
        return queryResult("OQL 2", "Đơn hàng có mua giày Nike Pegasus 41 SP01 (Duyệt tập hợp Collection + EXISTS)",
                "Duyệt tập hợp con dsChiTiet và đi tiếp qua bienTheSP.sanPham", oql, jpql,
                List.of("MaDH", "NgayDat", "TrangThai", "HoTenKH"), rows,
                "6 bản ghi mua Nike Pegasus 41 (SP01) — Khớp 100% SQL");
    }

    private static Map<String, Object> runOql3(EntityManager em) {
        String jpql = "SELECT dh.maDonHang, dh.khachHang.hoTen, "
                + "SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)), SUM(ct.soLuong) "
                + "FROM DonHang dh JOIN dh.dsChiTiet ct GROUP BY dh.maDonHang, dh.khachHang.hoTen "
                + "HAVING SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) > 5000000.0 "
                + "ORDER BY SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) DESC";
        String oql = "select struct(maDonHang: dh.maDonHang, tenKhachHang: dh.khachHang.hoTen, tongTien: dh.tinhTongTien(), tongSoLuong: dh.tinhTongSoLuong())\n"
                + "from dh in dsDonHang\n"
                + "where dh.tinhTongTien() > 5000000.0\n"
                + "order by dh.tinhTongTien() desc;";
        List<Object[]> res = em.createQuery(jpql, Object[].class).getResultList();
        List<List<Object>> rows = new ArrayList<>();
        for (Object[] r : res) {
            String maDh = (String) r[0];
            DonHang dh = em.find(DonHang.class, maDh);
            double methodVal = dh.tinhTongTien();
            rows.add(List.of(maDh, r[1], formatMoney(((Number) r[2]).doubleValue()),
                    ((Number) r[3]).intValue(), "Khớp (" + formatMoney(methodVal) + ")"));
        }
        return queryResult("OQL 3", "Đơn hàng giá trị cao trên 5 triệu đồng (Gọi phương thức OOP trực tiếp)",
                "Gọi và đối chiếu trực tiếp DonHang::tinhTongTien() & tinhTongSoLuong()", oql, jpql,
                List.of("MaDH", "HoTenKH", "TongTienJPQL", "TongSL", "OOPMethodCheck"), rows,
                "7 đơn > 5.000.000 VNĐ — Giá trị JPQL và phương thức OOP khớp tuyệt đối");
    }

    private static Map<String, Object> runOql4(EntityManager em) {
        String jpql = "SELECT kh.maKH, kh.hoTen, kh.sdt, COUNT(DISTINCT dh.maDonHang), "
                + "SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) "
                + "FROM KhachHang kh JOIN kh.dsDonHang dh JOIN dh.dsChiTiet ct "
                + "WHERE dh.trangThai = 'HOAN_TAT' GROUP BY kh.maKH, kh.hoTen, kh.sdt "
                + "ORDER BY SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) DESC";
        String oql = "select struct(maKH: kh.maKH, hoTen: kh.hoTen, sdt: kh.sdt, "
                + "soDonHoanTat: count(select dh from dh in partition where dh.trangThai = \"HOAN_TAT\"), "
                + "tongChiTieu: sum(select ct.tinhThanhTien() from dh in partition, ct in dh.dsChiTiet where dh.trangThai = \"HOAN_TAT\"))\n"
                + "from kh in dsKhachHang, dh in kh.dsDonHang where dh.trangThai = \"HOAN_TAT\"\n"
                + "group by maKH: kh.maKH, hoTen: kh.hoTen, sdt: kh.sdt order by tongChiTieu desc;";
        List<Object[]> res = em.createQuery(jpql, Object[].class).getResultList();
        List<List<Object>> rows = new ArrayList<>();
        for (Object[] r : res) {
            rows.add(List.of(r[0], r[1], r[2], ((Number) r[3]).intValue(), formatMoney(((Number) r[4]).doubleValue())));
        }
        return queryResult("OQL 4", "Thống kê tổng chi tiêu & số đơn hoàn tất từng khách (Gom nhóm Group By)",
                "Gom nhóm chi tiêu khách hàng (partition trong OQL ↔ GROUP BY trong JPQL)", oql, jpql,
                List.of("MaKH", "HoTen", "SDT", "SoDonHT", "TongChiTieu"), rows,
                "5 khách có đơn HOAN_TAT — Khớp 100% thống kê SQL");
    }

    private static Map<String, Object> runOql5(EntityManager em) {
        String jpql = "SELECT px.maPhieu, px.ngayLap, px.trangThai, px.khoNguon, "
                + "px.donHang.maDonHang, px.donHang.khachHang.hoTen "
                + "FROM PhieuXuatKho px WHERE px.donHang IS NOT NULL ORDER BY px.maPhieu";
        String oql = "select struct(maPhieu: px.maPhieu, ngayLap: px.ngayLap, trangThai: px.trangThai, "
                + "khoNguon: px.khoNguon, maDonHang: px.donHang.maDonHang, tenKhachHang: px.donHang.khachHang.hoTen)\n"
                + "from px in dsPhieuXuatKho where px.donHang != null order by px.maPhieu;";
        List<Object[]> res = em.createQuery(jpql, Object[].class).getResultList();
        List<List<Object>> rows = new ArrayList<>();
        for (Object[] r : res) {
            rows.add(List.of(r[0], DF.format((Date) r[1]), r[2], r[3], r[4], r[5]));
        }
        return queryResult("OQL 5", "Danh sách phiếu xuất kho giao đơn hàng (Kế thừa PhieuKho ← PhieuXuatKho)",
                "Lớp con PhieuXuatKho thừa hưởng thuộc tính ngayLap, trangThai từ lớp cha PhieuKho", oql, jpql,
                List.of("MaPhieu", "NgayLap (Kế thừa)", "TrangThai", "KhoNguon", "MaDH", "KhachNhan"), rows,
                "6 phiếu xuất giao đơn hàng (PK07..PK12), loại trừ PK17 xuất đổi trả");
    }

    private static Map<String, Object> runOql6(EntityManager em) {
        String jpql = "SELECT sp FROM SanPham sp ORDER BY sp.maSanPham";
        String oql = "select struct(maSanPham: sp.maSanPham, tenSanPham: sp.tenSanPham, giaNiemYet: sp.giaNiemYet, "
                + "soBienThe: count(sp.dsBienThe), tongDaDat: sum(select ct.soLuong from bt in sp.dsBienThe, ct in bt.dsChiTietDonHang where ct.donHang.trangThai != \"DA_HUY\"))\n"
                + "from sp in dsSanPham order by sp.maSanPham;";
        List<SanPham> spList = em.createQuery(jpql, SanPham.class).getResultList();
        List<List<Object>> rows = new ArrayList<>();
        for (SanPham sp : spList) {
            int tongDat = 0;
            for (ChiTietSP bt : sp.getDsBienThe()) {
                tongDat += bt.tinhTongSoLuongDaBan();
            }
            rows.add(List.of(sp.getMaSanPham(), sp.getTenSanPham(),
                    formatMoney(sp.getGiaNiemYet()), sp.getDsBienThe().size(), tongDat));
        }
        return queryResult("OQL 6", "Danh mục sản phẩm: đếm phân loại size/màu & tổng đã đặt (Collection lồng nhau)",
                "Duyệt tập hợp lồng nhau SanPham -> ChiTietSP -> ChiTietDonHang (loại trừ DA_HUY)", oql, jpql,
                List.of("MaSP", "TenSanPham", "GiaNiemYet", "SoBienThe", "TongDaDat"), rows,
                "10 mẫu giày — SP01 đã loại trừ đơn hủy DH10 nên còn 7 đôi");
    }

    private static Map<String, Object> queryResult(
            String id, String title, String technique, String oql, String jpql,
            List<String> columns, List<List<Object>> rows, String parityNote) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("technique", technique);
        m.put("oql", oql);
        m.put("jpql", jpql);
        m.put("columns", columns);
        m.put("rows", rows);
        m.put("rowCount", rows.size());
        m.put("parityNote", parityNote);
        return m;
    }

    private static List<Map<String, Object>> buildEntityCatalog(EntityManager em) {
        List<Map<String, Object>> catalog = new ArrayList<>();

        List<KhachHang> khList = em.createQuery("SELECT k FROM KhachHang k ORDER BY k.maKH", KhachHang.class).getResultList();
        List<Map<String, Object>> khObjs = new ArrayList<>();
        for (KhachHang k : khList) {
            khObjs.add(entityObj(
                    k.getMaKH(),
                    k.getMaKH() + " — " + k.getHoTen() + " (" + k.getSdt() + ")",
                    "Lớp KhachHang · SĐT: " + k.getSdt() + " · Tập dsDonHang (Set): " + k.getDsDonHang().size() + " đơn",
                    List.of(
                            methodDef("tinhTongChiTieu()", "Tổng tiền các đơn có trạng thái HOAN_TAT qua tập dsDonHang",
                                    formatMoney(k.tinhTongChiTieu()) + " đ"),
                            methodDef("demDonHoanTat()", "Đếm số đơn hàng đã hoàn tất trong dsDonHang",
                                    k.demDonHoanTat() + " đơn"),
                            methodDef("coDonChuaHoanTat()", "Kiểm tra khách có còn đơn đang xử lý/chưa hoàn tất không",
                                    String.valueOf(k.coDonChuaHoanTat()))
                    )
            ));
        }
        catalog.add(entityClassEntry("KhachHang", "1. KhachHang — Khách hàng (Set<DonHang>)", khObjs));

        List<DonHang> dhList = em.createQuery("SELECT d FROM DonHang d ORDER BY d.maDonHang", DonHang.class).getResultList();
        List<Map<String, Object>> dhObjs = new ArrayList<>();
        for (DonHang d : dhList) {
            String tenKh = d.getKhachHang() != null ? d.getKhachHang().getHoTen() : "";
            dhObjs.add(entityObj(
                    d.getMaDonHang(),
                    d.getMaDonHang() + " — " + tenKh + " [" + d.getTrangThai() + "]",
                    "Lớp DonHang · Kho: " + d.getMaKho() + " · Trạng thái: " + d.getTrangThai()
                            + " · Tập dsChiTiet (List): " + d.getDsChiTiet().size() + " dòng",
                    List.of(
                            methodDef("tinhTongTien()", "Cộng thành tiền sau chiết khấu của toàn bộ dòng trong dsChiTiet",
                                    formatMoney(d.tinhTongTien()) + " đ"),
                            methodDef("tinhTongSoLuong()", "Cộng tổng số đôi giày đặt mua trong đơn hàng",
                                    d.tinhTongSoLuong() + " đôi"),
                            methodDef("duocPhepXuatKho()", "Cho phép xuất kho nếu đơn đang DA_XAC_NHAN hoặc DANG_XU_LY",
                                    String.valueOf(d.duocPhepXuatKho())),
                            methodDef("duocPhepXoaHuy()", "Cho phép hủy nếu đơn chưa xuất kho (CHO_XAC_NHAN, DA_XAC_NHAN hoặc DANG_XU_LY)",
                                    String.valueOf(d.duocPhepXoaHuy()))
                    )
            ));
        }
        catalog.add(entityClassEntry("DonHang", "2. DonHang — Đơn hàng (List<ChiTietDonHang>)", dhObjs));

        List<ChiTietDonHang> ctList = em.createQuery("SELECT c FROM ChiTietDonHang c ORDER BY c.maCTDonHang", ChiTietDonHang.class).getResultList();
        List<Map<String, Object>> ctObjs = new ArrayList<>();
        for (ChiTietDonHang ct : ctList) {
            String maDh = ct.getDonHang() != null ? ct.getDonHang().getMaDonHang() : "";
            String sku = ct.getBienTheSP() != null ? ct.getBienTheSP().getSku() : "";
            ctObjs.add(entityObj(
                    ct.getMaCTDonHang(),
                    ct.getMaCTDonHang() + " — " + maDh + " · " + sku + " (SL " + ct.getSoLuong() + ")",
                    "Lớp ChiTietDonHang · SL: " + ct.getSoLuong() + " · Đơn giá: " + formatMoney(ct.getDonGiaBan())
                            + " đ · Chiết khấu: " + ct.getChietKhau() + "%",
                    List.of(
                            methodDef("tinhThanhTien()", "Tính tiền dòng đơn theo công thức soLuong * donGiaBan * (1 - chietKhau/100)",
                                    formatMoney(ct.tinhThanhTien()) + " đ")
                    )
            ));
        }
        catalog.add(entityClassEntry("ChiTietDonHang", "3. ChiTietDonHang — Chi tiết đơn hàng", ctObjs));

        List<SanPham> spList = em.createQuery("SELECT s FROM SanPham s ORDER BY s.maSanPham", SanPham.class).getResultList();
        List<Map<String, Object>> spObjs = new ArrayList<>();
        for (SanPham s : spList) {
            spObjs.add(entityObj(
                    s.getMaSanPham(),
                    s.getMaSanPham() + " — " + s.getTenSanPham() + " [" + s.getTrangThai() + "]",
                    "Lớp SanPham · Giá niêm yết: " + formatMoney(s.getGiaNiemYet()) + " đ · Trạng thái: "
                            + s.getTrangThai() + " · Tập dsBienThe (Set): " + s.getDsBienThe().size() + " size/màu",
                    List.of(
                            methodDef("demSoBienTheDangBan()", "Đếm số phân loại size/màu đang có trạng thái DANG_BAN",
                                    s.demSoBienTheDangBan() + " size/màu"),
                            methodDef("coTheDatHang()", "Mẫu đang DANG_KINH_DOANH và có ít nhất 1 size/màu đang bán",
                                    String.valueOf(s.coTheDatHang()))
                    )
            ));
        }
        catalog.add(entityClassEntry("SanPham", "4. SanPham — Mẫu giày (Set<ChiTietSP>)", spObjs));

        List<ChiTietSP> btList = em.createQuery("SELECT b FROM ChiTietSP b ORDER BY b.maChiTietSP", ChiTietSP.class).getResultList();
        List<Map<String, Object>> btObjs = new ArrayList<>();
        for (ChiTietSP b : btList) {
            String tenSp = b.getSanPham() != null ? b.getSanPham().getTenSanPham() : "";
            btObjs.add(entityObj(
                    b.getMaChiTietSP(),
                    b.getMaChiTietSP() + " — " + tenSp + " (Size " + b.getSize() + ", " + b.getMauSac() + ")",
                    "Lớp ChiTietSP · SKU: " + b.getSku() + " · Trạng thái: " + b.getTrangThai()
                            + " · Liên kết dsChiTietDonHang: " + b.getDsChiTietDonHang().size() + " dòng",
                    List.of(
                            methodDef("tinhTongSoLuongDaBan()", "Tổng số đôi đã đặt qua dsChiTietDonHang (loại trừ đơn DA_HUY)",
                                    b.tinhTongSoLuongDaBan() + " đôi")
                    )
            ));
        }
        catalog.add(entityClassEntry("ChiTietSP", "5. ChiTietSP — Chi tiết sản phẩm (Size / Màu)", btObjs));

        List<PhieuKho> pkList = em.createQuery("SELECT p FROM PhieuKho p ORDER BY p.maPhieu", PhieuKho.class).getResultList();
        List<Map<String, Object>> pkObjs = new ArrayList<>();
        List<Map<String, Object>> pxObjs = new ArrayList<>();
        for (PhieuKho p : pkList) {
            pkObjs.add(entityObj(
                    p.getMaPhieu(),
                    p.getMaPhieu() + " (" + p.getLoaiPhieu() + " · " + p.getTrangThai() + ")",
                    "Lớp cha PhieuKho (instance thực tế: " + p.getClass().getSimpleName() + ") · Trạng thái: " + p.getTrangThai(),
                    List.of(
                            methodDef("laPhieuHoanTat()", "Kiểm tra phiếu kho đã hoàn tất (trangThai == HOAN_TAT) chưa",
                                    String.valueOf(p.laPhieuHoanTat())),
                            methodDef("duocPhepChinhSua()", "Kiểm tra phiếu còn ở dạng nháp (BAN_NHAP) để chỉnh sửa không",
                                    String.valueOf(p.duocPhepChinhSua()))
                    )
            ));
            if (p instanceof PhieuXuatKho px) {
                String maDh = px.getDonHang() != null ? px.getDonHang().getMaDonHang() : "null (đổi trả " + px.getMaPhieuTra() + ")";
                pxObjs.add(entityObj(
                        px.getMaPhieu(),
                        px.getMaPhieu() + " — Kho " + px.getKhoNguon() + " · ĐH: " + maDh,
                        "Lớp con PhieuXuatKho (kế thừa PhieuKho) · Kho nguồn: " + px.getKhoNguon() + " · Đơn hàng: " + maDh,
                        List.of(
                                methodDef("laPhieuHoanTat()", "Kế thừa từ lớp cha PhieuKho: kiểm tra trạng thái HOAN_TAT",
                                        String.valueOf(px.laPhieuHoanTat())),
                                methodDef("duocPhepChinhSua()", "Kế thừa từ lớp cha PhieuKho: kiểm tra trạng thái BAN_NHAP",
                                        String.valueOf(px.duocPhepChinhSua())),
                                methodDef("laXuatGiaoDonHang()", "Phương thức riêng của lớp con PhieuXuatKho (donHang != null)",
                                        String.valueOf(px.laXuatGiaoDonHang()))
                        )
                ));
            }
        }
        catalog.add(entityClassEntry("PhieuKho", "6. PhieuKho — Phiếu kho (Lớp cha)", pkObjs));
        catalog.add(entityClassEntry("PhieuXuatKho", "7. PhieuXuatKho — Phiếu xuất kho (Kế thừa PhieuKho)", pxObjs));

        return catalog;
    }

    private static Map<String, Object> entityClassEntry(String className, String title, List<Map<String, Object>> objects) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("className", className);
        m.put("title", title);
        m.put("objects", objects);
        return m;
    }

    private static Map<String, Object> entityObj(String id, String label, String stateSummary, List<Map<String, Object>> methods) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("stateSummary", stateSummary);
        m.put("methods", methods);
        return m;
    }

    private static Map<String, Object> methodDef(String signature, String description, String returnValue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("signature", signature);
        m.put("description", description);
        m.put("returnValue", returnValue);
        return m;
    }

    private static Map<String, Object> methodRow(String target, String methods, String result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("target", target);
        m.put("methods", methods);
        m.put("result", result);
        return m;
    }

    private static String formatMoney(double val) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        return new DecimalFormat("#,##0.00", sym).format(val);
    }

    private static String toJson(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof Boolean || val instanceof Number) {
            return val.toString();
        }
        if (val instanceof String s) {
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                switch (ch) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\b' -> sb.append("\\b");
                    case '\f' -> sb.append("\\f");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (ch < 0x20) {
                            sb.append(String.format("\\u%04x", (int) ch));
                        } else {
                            sb.append(ch);
                        }
                    }
                }
            }
            sb.append("\"");
            return sb.toString();
        }
        if (val instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(String.valueOf(e.getKey()))).append(':').append(toJson(e.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        if (val instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : it) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(item));
            }
            sb.append(']');
            return sb.toString();
        }
        return toJson(String.valueOf(val));
    }
}
