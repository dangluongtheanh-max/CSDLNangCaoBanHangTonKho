package com.csdl.oodb;

import com.csdl.model.ChiTietDonHang;
import com.csdl.model.ChiTietSP;
import com.csdl.model.DonHang;
import com.csdl.model.KhachHang;
import com.csdl.model.PhieuKho;
import com.csdl.model.PhieuXuatKho;
import com.csdl.model.SanPham;
import com.csdl.seeder.DataSeeder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bo kiem thu tu dong JUnit 5 cho phan he CSDL Huong doi tuong (ObjectDB / JPA 2.2):
 *   - Nap du lieu mau & danh tinh OID (DataSeeder)
 *   - 06 truy van OQL <-> JPQL trong 03_oql_queries.oql
 *   - Tinh ke thua & da hinh (PhieuXuatKho extends PhieuKho)
 *   - Lien ket 2 chieu (Bidirectional Inverse) & Collection (Set / ordered List)
 *   - Toan bo phuong thuc dong goi tren 7 lop Entity & giao dich ACID rollback
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OodbFeaturesTest {

    private static final String TEST_DB_PATH = "target/test_csdl_giay.odb";
    private static EntityManagerFactory emf;
    private EntityManager em;

    @BeforeAll
    static void initObjectDb() {
        new File("target").mkdirs();
        deleteTestDbFiles();

        emf = Persistence.createEntityManagerFactory(TEST_DB_PATH);
        EntityManager seedEm = emf.createEntityManager();
        try {
            DataSeeder.seed(seedEm);
        } finally {
            seedEm.close();
        }
    }

    @BeforeEach
    void openEntityManager() {
        em = emf.createEntityManager();
    }

    @AfterEach
    void closeEntityManager() {
        if (em != null) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            if (em.isOpen()) {
                em.close();
            }
        }
    }

    @AfterAll
    static void shutdownObjectDb() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
        deleteTestDbFiles();
    }

    private static void deleteTestDbFiles() {
        new File(TEST_DB_PATH).delete();
        new File(TEST_DB_PATH + "$").delete();
    }

    @Test
    @Order(1)
    void testSeededObjectGraphCountsAndOidIdentity() {
        assertEquals(8L, countEntity(KhachHang.class), "Phai co 8 KhachHang");
        assertEquals(10L, countEntity(SanPham.class), "Phai co 10 SanPham");
        assertEquals(20L, countEntity(ChiTietSP.class), "Phai co 20 bien the ChiTietSP");
        assertEquals(10L, countEntity(DonHang.class), "Phai co 10 DonHang");
        assertEquals(18L, countEntity(ChiTietDonHang.class), "Phai co 18 ChiTietDonHang");
        assertEquals(7L, countEntity(PhieuXuatKho.class), "Phai co 7 PhieuXuatKho");
        assertEquals(7L, countEntity(PhieuKho.class), "Tong so PhieuKho phai bang 7");

        // Kiem chung dinh danh OID trong Persistence Context: 2 lan tim cung khoa tra ve cung 1 instance
        DonHang dhA = em.find(DonHang.class, "DH01");
        DonHang dhB = em.createQuery("SELECT d FROM DonHang d WHERE d.maDonHang = 'DH01'", DonHang.class).getSingleResult();
        assertNotNull(dhA);
        assertSame(dhA, dhB, "Truy xuat cung OID trong cung EntityManager phai tra ve cung tham chieu doi tuong");
    }

    @Test
    @Order(2)
    void testOql1_PathExpressionWithoutJoin() {
        String jpql = """
                SELECT dh.maDonHang, dh.ngayDat, dh.trangThai, dh.khachHang.hoTen, dh.khachHang.sdt, dh.diaChiGiaoHang
                FROM DonHang dh
                WHERE dh.trangThai IN ('DANG_XU_LY', 'DANG_GIAO', 'HOAN_TAT')
                ORDER BY dh.ngayDat DESC
                """;
        List<Object[]> rows = em.createQuery(jpql, Object[].class).getResultList();

        assertEquals(7, rows.size(), "OQL 1 phai tra ve dung 7 don hang");
        List<String> actualIds = rows.stream().map(r -> (String) r[0]).collect(Collectors.toList());
        assertEquals(Arrays.asList("DH08", "DH06", "DH05", "DH04", "DH03", "DH02", "DH01"), actualIds);

        assertEquals("Hoàng Thùy Linh", rows.get(0)[3]);
        assertEquals("0966777888", rows.get(0)[4]);
        assertEquals("Nguyễn Minh Tuấn", rows.get(6)[3]);
    }

    @Test
    @Order(3)
    void testOql2_CollectionTraversalDistinct() {
        String jpql = """
                SELECT DISTINCT dh.maDonHang, dh.ngayDat, dh.trangThai, dh.khachHang.hoTen
                FROM DonHang dh JOIN dh.dsChiTiet ct
                WHERE ct.bienTheSP.sanPham.maSanPham = 'SP01'
                ORDER BY dh.maDonHang
                """;
        List<Object[]> rows = em.createQuery(jpql, Object[].class).getResultList();

        assertEquals(6, rows.size(), "OQL 2 phai tra ve 6 don hang dat mua Nike Pegasus 41 (SP01)");
        List<String> actualIds = rows.stream().map(r -> (String) r[0]).collect(Collectors.toList());
        assertEquals(Arrays.asList("DH01", "DH05", "DH07", "DH08", "DH09", "DH10"), actualIds);
    }

    @Test
    @Order(4)
    void testOql3_HighValueOrdersAndMethodInvocationParity() {
        String jpql = """
                SELECT dh.maDonHang, dh.khachHang.hoTen,
                       SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)),
                       SUM(ct.soLuong)
                FROM DonHang dh JOIN dh.dsChiTiet ct
                GROUP BY dh.maDonHang, dh.khachHang.hoTen
                HAVING SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) > 5000000.0
                ORDER BY SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) DESC
                """;
        List<Object[]> rows = em.createQuery(jpql, Object[].class).getResultList();

        assertEquals(7, rows.size(), "OQL 3 phai tra ve dung 7 don hang gia tri > 5,000,000");

        String[] expectedIds = {"DH08", "DH01", "DH07", "DH02", "DH05", "DH06", "DH04"};
        double[] expectedTotals = {17000000.0, 16240000.0, 16232500.0, 14620000.0, 9780000.0, 8450000.0, 6200000.0};
        int[] expectedQtys = {5, 5, 5, 6, 3, 3, 4};

        for (int i = 0; i < expectedIds.length; i++) {
            String maDh = (String) rows.get(i)[0];
            double jpqlTotal = ((Number) rows.get(i)[2]).doubleValue();
            int jpqlQty = ((Number) rows.get(i)[3]).intValue();

            assertEquals(expectedIds[i], maDh);
            assertEquals(expectedTotals[i], jpqlTotal, 0.01);
            assertEquals(expectedQtys[i], jpqlQty);

            // Doi chieu truc tiep voi phuong thuc OOP dong goi tren Entity DonHang
            DonHang dh = em.find(DonHang.class, maDh);
            assertEquals(jpqlTotal, dh.tinhTongTien(), 0.01, "DonHang.tinhTongTien() phai khop JPQL cho " + maDh);
            assertEquals(jpqlQty, dh.tinhTongSoLuong(), "DonHang.tinhTongSoLuong() phai khop JPQL cho " + maDh);
        }
    }

    @Test
    @Order(5)
    void testOql4_CustomerSpendGroupByAndCustomerMethods() {
        String jpql = """
                SELECT kh.maKH, kh.hoTen, kh.sdt, COUNT(DISTINCT dh.maDonHang),
                       SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0))
                FROM KhachHang kh JOIN kh.dsDonHang dh JOIN dh.dsChiTiet ct
                WHERE dh.trangThai = 'HOAN_TAT'
                GROUP BY kh.maKH, kh.hoTen, kh.sdt
                ORDER BY SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) DESC
                """;
        List<Object[]> rows = em.createQuery(jpql, Object[].class).getResultList();

        assertEquals(5, rows.size(), "OQL 4 phai tra ve dung 5 khach hang co don HOAN_TAT");

        String[] expectedKhIds = {"KH01", "KH02", "KH05", "KH04", "KH03"};
        double[] expectedSpends = {16240000.0, 14620000.0, 9780000.0, 6200000.0, 4465000.0};

        for (int i = 0; i < expectedKhIds.length; i++) {
            String maKh = (String) rows.get(i)[0];
            assertEquals(expectedKhIds[i], maKh);
            assertEquals(1, ((Number) rows.get(i)[3]).intValue());
            assertEquals(expectedSpends[i], ((Number) rows.get(i)[4]).doubleValue(), 0.01);

            KhachHang kh = em.find(KhachHang.class, maKh);
            assertEquals(1, kh.demDonHoanTat(), "demDonHoanTat() khong khop cho " + maKh);
            assertEquals(expectedSpends[i], kh.tinhTongChiTieu(), 0.01, "tinhTongChiTieu() khong khop cho " + maKh);
        }

        // Kiem tra coDonChuaHoanTat()
        assertTrue(em.find(KhachHang.class, "KH01").coDonChuaHoanTat(), "KH01 con DH06 DANG_GIAO");
        assertFalse(em.find(KhachHang.class, "KH02").coDonChuaHoanTat(), "KH02 chi co DH02 HOAN_TAT");
        KhachHang kh06 = em.find(KhachHang.class, "KH06");
        assertEquals(0, kh06.demDonHoanTat());
        assertEquals(0.0, kh06.tinhTongChiTieu(), 0.01);
        assertTrue(kh06.coDonChuaHoanTat());
    }

    @Test
    @Order(6)
    void testOql5_InheritanceAndPolymorphism() {
        String jpql = """
                SELECT px.maPhieu, px.ngayLap, px.trangThai, px.khoNguon,
                       px.donHang.maDonHang, px.donHang.khachHang.hoTen
                FROM PhieuXuatKho px
                WHERE px.donHang IS NOT NULL
                ORDER BY px.maPhieu
                """;
        List<Object[]> rows = em.createQuery(jpql, Object[].class).getResultList();

        assertEquals(6, rows.size(), "OQL 5 phai tra ve 6 phieu xuat giao don hang");
        List<String> actualPxIds = rows.stream().map(r -> (String) r[0]).collect(Collectors.toList());
        assertEquals(Arrays.asList("PK07", "PK08", "PK09", "PK10", "PK11", "PK12"), actualPxIds);

        // Da hinh tren lop cha PhieuKho -> tra ve ca PhieuXuatKho (ke ca PK17 xuat doi tra)
        List<PhieuKho> allPhieu = em.createQuery("SELECT p FROM PhieuKho p ORDER BY p.maPhieu", PhieuKho.class).getResultList();
        assertEquals(7, allPhieu.size());
        for (PhieuKho p : allPhieu) {
            assertInstanceOf(PhieuXuatKho.class, p);
            assertEquals("XUAT", p.getLoaiPhieu());
            assertTrue(p.laPhieuHoanTat());
            assertFalse(p.duocPhepChinhSua());
        }

        PhieuXuatKho pk07 = em.find(PhieuXuatKho.class, "PK07");
        PhieuXuatKho pk17 = em.find(PhieuXuatKho.class, "PK17");
        assertTrue(pk07.laXuatGiaoDonHang());
        assertFalse(pk17.laXuatGiaoDonHang());
        assertEquals("PTH01", pk17.getMaPhieuTra());
    }

    @Test
    @Order(7)
    void testOql6_NestedCollectionAndProductVariantMethods() {
        List<SanPham> products = em.createQuery("SELECT sp FROM SanPham sp ORDER BY sp.maSanPham", SanPham.class)
                .getResultList();
        assertEquals(10, products.size(), "OQL 6 phai tra ve du 10 SanPham");

        int[] expectedVariants = {3, 2, 2, 2, 2, 3, 2, 2, 1, 1};
        int[] expectedActiveVariants = {3, 2, 2, 2, 2, 3, 2, 2, 0, 0};
        int[] expectedSold = {7, 4, 1, 2, 2, 12, 4, 3, 0, 0};

        for (int i = 0; i < products.size(); i++) {
            SanPham sp = products.get(i);
            assertEquals(expectedVariants[i], sp.getDsBienThe().size(), "So bien the sai tai " + sp.getMaSanPham());
            assertEquals(expectedActiveVariants[i], sp.demSoBienTheDangBan(), "demSoBienTheDangBan() sai tai " + sp.getMaSanPham());

            int totalSold = 0;
            for (ChiTietSP bt : sp.getDsBienThe()) {
                totalSold += bt.tinhTongSoLuongDaBan();
            }
            assertEquals(expectedSold[i], totalSold,
                    "Tong so luong da dat (loai tru DA_HUY) sai tai " + sp.getMaSanPham());
        }

        assertTrue(em.find(SanPham.class, "SP01").coTheDatHang());
        assertFalse(em.find(SanPham.class, "SP09").coTheDatHang(), "SP09 TAM_NGUNG khong duoc dat hang");
        assertFalse(em.find(SanPham.class, "SP10").coTheDatHang(), "SP10 NGUNG_KINH_DOANH khong duoc dat hang");
    }

    @Test
    @Order(8)
    void testBidirectionalInverseLinksAndOrderStateMethods() {
        KhachHang kh01 = em.find(KhachHang.class, "KH01");
        Map<String, DonHang> kh01Orders = kh01.getDsDonHang().stream()
                .collect(Collectors.toMap(DonHang::getMaDonHang, d -> d));
        assertEquals(2, kh01Orders.size());
        assertTrue(kh01Orders.containsKey("DH01"));
        assertTrue(kh01Orders.containsKey("DH06"));

        DonHang dh01 = em.find(DonHang.class, "DH01");
        assertSame(kh01, dh01.getKhachHang());
        assertEquals(2, dh01.getDsChiTiet().size());
        assertEquals("CTDH01", dh01.getDsChiTiet().get(0).getMaCTDonHang());
        assertEquals("CTDH02", dh01.getDsChiTiet().get(1).getMaCTDonHang());
        assertEquals(5890000.0, dh01.getDsChiTiet().get(0).tinhThanhTien(), 0.01);
        assertEquals(10350000.0, dh01.getDsChiTiet().get(1).tinhThanhTien(), 0.01);

        assertNotNull(dh01.getPhieuXuat());
        assertEquals("PK07", dh01.getPhieuXuat().getMaPhieu());
        assertSame(dh01, dh01.getPhieuXuat().getDonHang());

        // Kiem tra trang thai DonHang
        DonHang dh06 = em.find(DonHang.class, "DH06");
        DonHang dh07 = em.find(DonHang.class, "DH07");
        DonHang dh08 = em.find(DonHang.class, "DH08");
        DonHang dh09 = em.find(DonHang.class, "DH09");

        assertFalse(dh01.duocPhepXuatKho());
        assertTrue(dh07.duocPhepXuatKho());
        assertTrue(dh08.duocPhepXuatKho());

        assertFalse(dh06.duocPhepXoaHuy());
        assertTrue(dh07.duocPhepXoaHuy());
        assertTrue(dh08.duocPhepXoaHuy());
        assertTrue(dh09.duocPhepXoaHuy());
    }

    @Test
    @Order(9)
    void testObjectDbAcidTransactionRollback() {
        em.getTransaction().begin();
        KhachHang tempKh = new KhachHang("KH_TEST", "Khách Kiểm Thử", "0900000000", null, "Test Address", new java.util.Date());
        em.persist(tempKh);
        em.flush();
        assertEquals(9L, countEntity(KhachHang.class), "Trong giao dich chua commit phai thay 9 KhachHang");
        em.getTransaction().rollback();

        em.clear();
        assertNull(em.find(KhachHang.class, "KH_TEST"), "Sau ROLLBACK doi tuong tam khong duoc ton tai");
        assertEquals(8L, countEntity(KhachHang.class), "Sau ROLLBACK phai giu nguyen dung 8 KhachHang");
    }

    private long countEntity(Class<?> entityClass) {
        return em.createQuery("SELECT COUNT(e) FROM " + entityClass.getSimpleName() + " e", Long.class)
                .getSingleResult();
    }
}

