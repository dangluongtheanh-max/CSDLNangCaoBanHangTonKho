package com.csdl.rdb;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bo kiem thu tu dong JUnit 5 cho toan bo chuc nang CSDL Quan he (PostgreSQL):
 * - Cau truc 18 bang, du lieu mau goc & 18 Index (01, 02, 07)
 * - 12 truy van nghiep vu tu file 03_queries.sql (bao gom Recursive CTE)
 * - Tat ca 3 Function & 4 Stored Procedure + Dynamic SQL
 * (04_functions_procedures.sql)
 * - Tat ca 8 nhom Trigger tich cuc & bay loi (05_triggers.sql)
 * - Giao dich SAVEPOINT & Concurrency 2 luong song song chong overselling (06,
 * JDBC)
 *
 * Moi test don luong dung rollback() de bao toan 100% du lieu mau ban dau.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DatabaseFeaturesTest {

    private static String jdbcUrl;
    private static String dbUser;
    private static String dbPassword;

    private Connection txConn;

    @FunctionalInterface
    interface SqlExecutable {
        void execute() throws SQLException;
    }

    @BeforeAll
    static void initConfigAndCleanLeftovers() throws SQLException {
        normalizePostgresCompatibleTimeZone();
        jdbcUrl = resolveSetting("pg.url", "PG_URL", "jdbc:postgresql://localhost:5433/ql_banhang");
        dbUser = resolveSetting("pg.user", "PG_USER", "postgres");
        dbPassword = resolveSetting("pg.pass", "PG_PASS", "123456");

        try (Connection c = openNewConnection()) {
            cleanupCommittedTestArtifacts(c);
            assertBaselineSampleData(c);
        }
    }

    @BeforeEach
    void openTransaction() throws SQLException {
        txConn = openNewConnection();
        txConn.setAutoCommit(false);
    }

    @AfterEach
    void rollbackTransaction() throws SQLException {
        if (txConn != null) {
            try {
                if (!txConn.getAutoCommit()) {
                    txConn.rollback();
                }
            } finally {
                txConn.close();
            }
        }
    }

    @AfterAll
    static void verifyDatabaseRemainsClean() throws SQLException {
        try (Connection c = openNewConnection()) {
            cleanupCommittedTestArtifacts(c);
            assertBaselineSampleData(c);
        }
    }

    // -------------------------------------------------------------------------
    // 1. KIEM TRA CAU TRUC BANG, DU LIEU MAU & 18 INDEX (01, 02, 07)
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    void testSchemaTablesBaselineAnd18Indexes() throws SQLException {
        int tableCount = queryInt(txConn,
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'");
        assertEquals(18, tableCount, "Phai co du 18 bang trong schema public");

        int customIndexCount = queryInt(txConn,
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname LIKE 'idx_%'");
        assertEquals(18, customIndexCount, "Phai co du 18 Index bo sung tu 07_indexes.sql");

        assertBaselineSampleData(txConn);
    }

    // -------------------------------------------------------------------------
    // 2. KIEM TRA TOAN BO 12 TRUY VAN TU FILE 03_queries.sql
    // -------------------------------------------------------------------------

    @Test
    @Order(2)
    void testAll12BusinessQueriesFromSqlFile() throws Exception {
        Path queriesPath = locateSqlFile("03_queries.sql");
        String rawSql = Files.readString(queriesPath, StandardCharsets.UTF_8);
        List<String> queries = parseSqlStatements(rawSql);

        assertEquals(12, queries.size(), "03_queries.sql phai co dung 12 cau truy van SELECT");

        int[] expectedRows = { 10, 18, 3, 4, 3, 5, 8, 3, 2, 4, 6, 11 };
        for (int i = 0; i < queries.size(); i++) {
            try (Statement st = txConn.createStatement();
                    ResultSet rs = st.executeQuery(queries.get(i))) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                assertEquals(expectedRows[i], count,
                        "Sai so dong ket qua o Cau " + (i + 1) + " trong 03_queries.sql");
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. KIEM TRA 3 FUNCTION & 4 STORED PROCEDURE (04_functions_procedures.sql)
    // -------------------------------------------------------------------------

    @Test
    @Order(3)
    void testFunctions_TonKhoKhaDungAndTongTienDonHang() throws SQLException {
        assertEquals(27, getAvailableStock(txConn, "KHO01", "CTSP01"));
        assertEquals(38, getAvailableStock(txConn, "KHO01", "CTSP12"));
        assertEquals(0, getAvailableStock(txConn, "KHO01", "CTSP_KHONG_TON_TAI"));

        BigDecimal dh01Total = queryBigDecimal(txConn, "SELECT fn_tinh_tong_tien_don_hang(?)", "DH01");
        assertEquals(0, new BigDecimal("16240000.00").compareTo(dh01Total));

        assertThrowsSqlAndRollbackSavepoint(txConn,
                () -> queryBigDecimal(txConn, "SELECT fn_tinh_tong_tien_don_hang(?)", "DH_KHONG_TON_TAI"),
                "P0001", "không tồn tại");
    }

    @Test
    @Order(4)
    void testProcedures_TaoDonHangAndHuyDonHang() throws SQLException {
        // Tao don hop le gom 2 dong CTSP01 (2 doi) va CTSP07 (1 doi)
        callCreateOrder(txConn, "DH_TEST_01", "KH01", "KHO01", "25 Le Loi, Q.1, TP.HCM",
                "CHUYEN_KHOAN",
                "[{\"ma_ctsp\":\"CTSP01\",\"so_luong\":2,\"chiet_khau\":5},{\"ma_ctsp\":\"CTSP07\",\"so_luong\":1}]",
                "Unit test tao don");

        assertEquals(25, getAvailableStock(txConn, "KHO01", "CTSP01")); // 27 -> 25
        assertEquals(14, getAvailableStock(txConn, "KHO01", "CTSP07")); // 15 -> 14

        // Huy don vua tao -> giai phong SoLuongDaGiu ve nhu cu
        try (PreparedStatement ps = txConn.prepareStatement("CALL sp_huy_don_hang(?, ?)")) {
            ps.setString(1, "DH_TEST_01");
            ps.setString(2, "Khach huy unit test");
            ps.execute();
        }
        assertEquals("DA_HUY", queryString(txConn, "SELECT TrangThai FROM DONHANG WHERE MaDonHang = ?", "DH_TEST_01"));
        assertEquals(27, getAvailableStock(txConn, "KHO01", "CTSP01"));
        assertEquals(15, getAvailableStock(txConn, "KHO01", "CTSP07"));

        // Cac nhanh bao loi cua sp_tao_don_hang & sp_huy_don_hang
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> {
            try (PreparedStatement ps = txConn.prepareStatement("CALL sp_huy_don_hang(?, ?)")) {
                ps.setString(1, "DH_TEST_01");
                ps.setString(2, "Huy lan 2");
                ps.execute();
            }
        }, "P0001", "đã được hủy trước đó");

        assertThrowsSqlAndRollbackSavepoint(txConn, () -> {
            try (PreparedStatement ps = txConn.prepareStatement("CALL sp_huy_don_hang(?, ?)")) {
                ps.setString(1, "DH01");
                ps.setString(2, "Thu huy don hoan tat");
                ps.execute();
            }
        }, "P0001", "Không thể hủy đơn hàng");

        assertThrowsSqlAndRollbackSavepoint(txConn,
                () -> callCreateOrder(txConn, "DH_TEST_BAD_KHO", "KH01", "KHO04", "BD", "COD",
                        "[{\"ma_ctsp\":\"CTSP12\",\"so_luong\":1}]", null),
                "P0001", "không thể tiếp nhận đơn hàng mới");

        assertThrowsSqlAndRollbackSavepoint(txConn,
                () -> callCreateOrder(txConn, "DH_TEST_NGUNG_BAN", "KH01", "KHO01", "HCM", "COD",
                        "[{\"ma_ctsp\":\"CTSP19\",\"so_luong\":1}]", null),
                "P0001", "hiện không mở bán");

        assertThrowsSqlAndRollbackSavepoint(txConn,
                () -> callCreateOrder(txConn, "DH_TEST_OVER", "KH01", "KHO01", "HCM", "COD",
                        "[{\"ma_ctsp\":\"CTSP18\",\"so_luong\":5}]", null),
                "P0001", "Không đủ tồn khả dụng");
    }

    @Test
    @Order(5)
    void testProcedures_ChuyenKhoAndXacNhanNhanChuyenKho() throws SQLException {
        // Che do 1: Chuyen hoan tat ngay (p_hoan_tat = TRUE): KHO01 -> KHO02 (CTSP07
        // chưa có ở KHO02)
        try (PreparedStatement ps = txConn.prepareStatement("CALL sp_chuyen_kho(?, ?, ?, ?::jsonb, ?, ?)")) {
            ps.setString(1, "PK_TEST_CH1");
            ps.setString(2, "KHO01");
            ps.setString(3, "KHO02");
            ps.setString(4, "[{\"ma_ctsp\":\"CTSP07\",\"so_luong\":3}]");
            ps.setBoolean(5, true);
            ps.setString(6, "Chuyen kho hoan tat ngay");
            ps.execute();
        }
        assertEquals("HOAN_TAT",
                queryString(txConn, "SELECT TrangThai FROM PHIEUKHO WHERE MaPhieu = ?", "PK_TEST_CH1"));
        assertEquals(12, getAvailableStock(txConn, "KHO01", "CTSP07")); // 15 -> 12
        assertEquals(3, getAvailableStock(txConn, "KHO02", "CTSP07")); // 0 -> 3 (ON CONFLICT INSERT)

        // Che do 2: Chuyen 2 buoc (p_hoan_tat = FALSE -> DANG_VAN_CHUYEN ->
        // sp_xac_nhan_nhan_chuyen_kho)
        try (PreparedStatement ps = txConn.prepareStatement("CALL sp_chuyen_kho(?, ?, ?, ?::jsonb, ?, ?)")) {
            ps.setString(1, "PK_TEST_CH2");
            ps.setString(2, "KHO01");
            ps.setString(3, "KHO03");
            ps.setString(4, "[{\"ma_ctsp\":\"CTSP07\",\"so_luong\":2}]");
            ps.setBoolean(5, false);
            ps.setString(6, "Chuyen kho dang van chuyen");
            ps.execute();
        }
        assertEquals("DANG_VAN_CHUYEN",
                queryString(txConn, "SELECT TrangThai FROM PHIEUKHO WHERE MaPhieu = ?", "PK_TEST_CH2"));
        assertEquals(10, getAvailableStock(txConn, "KHO01", "CTSP07")); // 12 -> 10
        assertEquals(0, getAvailableStock(txConn, "KHO03", "CTSP07")); // Chua nhan hang

        try (PreparedStatement ps = txConn.prepareStatement("CALL sp_xac_nhan_nhan_chuyen_kho(?)")) {
            ps.setString(1, "PK_TEST_CH2");
            ps.execute();
        }
        assertEquals("HOAN_TAT",
                queryString(txConn, "SELECT TrangThai FROM PHIEUKHO WHERE MaPhieu = ?", "PK_TEST_CH2"));
        assertEquals(2, getAvailableStock(txConn, "KHO03", "CTSP07")); // Da cong kho dich

        // Xac nhan nhan phieu mau PK06 dang o trang thai DANG_VAN_CHUYEN (KHO01 ->
        // KHO02: CTSP04=5, CTSP16=10)
        try (PreparedStatement ps = txConn.prepareStatement("CALL sp_xac_nhan_nhan_chuyen_kho(?)")) {
            ps.setString(1, "PK06");
            ps.execute();
        }
        assertEquals(19, getAvailableStock(txConn, "KHO02", "CTSP04")); // 14 + 5
        assertEquals(10, getAvailableStock(txConn, "KHO02", "CTSP16")); // 0 + 10

        // Cac tinh huong loi cua sp_chuyen_kho & sp_xac_nhan_nhan_chuyen_kho
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> {
            try (PreparedStatement ps = txConn.prepareStatement("CALL sp_chuyen_kho(?, ?, ?, ?::jsonb, ?, ?)")) {
                ps.setString(1, "PK_TEST_SAME");
                ps.setString(2, "KHO01");
                ps.setString(3, "KHO01");
                ps.setString(4, "[{\"ma_ctsp\":\"CTSP07\",\"so_luong\":1}]");
                ps.setBoolean(5, true);
                ps.setNull(6, Types.VARCHAR);
                ps.execute();
            }
        }, "P0001", "không được trùng nhau");

        assertThrowsSqlAndRollbackSavepoint(txConn, () -> {
            try (PreparedStatement ps = txConn.prepareStatement("CALL sp_chuyen_kho(?, ?, ?, ?::jsonb, ?, ?)")) {
                ps.setString(1, "PK_TEST_CLOSED");
                ps.setString(2, "KHO01");
                ps.setString(3, "KHO04");
                ps.setString(4, "[{\"ma_ctsp\":\"CTSP07\",\"so_luong\":1}]");
                ps.setBoolean(5, true);
                ps.setNull(6, Types.VARCHAR);
                ps.execute();
            }
        }, "P0001", "không thể nhận chuyển");

        assertThrowsSqlAndRollbackSavepoint(txConn, () -> {
            try (PreparedStatement ps = txConn.prepareStatement("CALL sp_xac_nhan_nhan_chuyen_kho(?)")) {
                ps.setString(1, "PK05");
                ps.execute();
            }
        }, "P0001", "DANG_VAN_CHUYEN");
    }

    @Test
    @Order(6)
    void testDynamicSql_FnBaoCaoDoanhThuDongWithDateAndRecursiveCategory() throws SQLException {
        // Loc theo KHO01 -> tong = 30,485,000.00
        BigDecimal kho01Revenue = sumDynamicReport(txConn, "KHO01", null, null, null);
        assertEquals(0, new BigDecimal("30485000.00").compareTo(kho01Revenue));

        // Loc theo danh muc cha DM01 (Recursive CTE gom DM07, DM08, DM09, DM10)
        BigDecimal dm01Revenue = sumDynamicReport(txConn, null, null, null, "DM01");
        assertEquals(0, new BigDecimal("40770000.00").compareTo(dm01Revenue));

        // Loc theo khoang thoi gian thang 10/2026 (DH04, DH05) -> 15,980,000.00
        BigDecimal octRevenue = sumDynamicReport(txConn, null,
                Timestamp.valueOf("2026-10-01 00:00:00"),
                Timestamp.valueOf("2026-10-31 23:59:59"),
                null);
        assertEquals(0, new BigDecimal("15980000.00").compareTo(octRevenue));

        // Loc theo 7 ngay gan nhat (DH02 + DH03) -> 19,085,000.00
        long nowMs = System.currentTimeMillis();
        BigDecimal recentRevenue = sumDynamicReport(txConn, null,
                new Timestamp(nowMs - 7L * 24 * 3600 * 1000),
                new Timestamp(nowMs + 24L * 3600 * 1000),
                null);
        assertEquals(0, new BigDecimal("19085000.00").compareTo(recentRevenue));

        // Chong SQL Injection qua tham so Dynamic SQL
        BigDecimal sqliRevenue = sumDynamicReport(txConn, "KHO01' OR '1'='1", null, null, null);
        assertEquals(0, BigDecimal.ZERO.compareTo(sqliRevenue));
    }

    // -------------------------------------------------------------------------
    // 4. KIEM TRA DAY DU 8 NHOM TRIGGER (05_triggers.sql)
    // -------------------------------------------------------------------------

    @Test
    @Order(7)
    void testTrigger1And2_TonKhoNonNegativeAndNotOverReserved() throws SQLException {
        // Trigger 1: khong am SoLuongThucTe / SoLuongDaGiu
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> executeUpdate(txConn,
                "UPDATE TONKHO SET SoLuongThucTe = -1 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP02'"),
                "23514", "không được âm");

        assertThrowsSqlAndRollbackSavepoint(txConn, () -> executeUpdate(txConn,
                "UPDATE TONKHO SET SoLuongDaGiu = -1 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP02'"),
                "23514", "không được âm");

        // Trigger 2: SoLuongDaGiu <= SoLuongThucTe (CTSP18 chi co 2)
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> executeUpdate(txConn,
                "UPDATE TONKHO SET SoLuongDaGiu = 3 WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP18'"),
                "23514", "vượt quá SoLuongThucTe");
    }

    @Test
    @Order(8)
    void testTrigger3_TonKhoAutoUpdatesNgayCapNhat() throws SQLException {
        executeUpdate(txConn,
                "UPDATE TONKHO SET SoLuongDaGiu = 1, NgayCapNhat = TIMESTAMP '2020-01-01 00:00:00' "
                        + "WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP02'");

        Timestamp updatedAt;
        try (PreparedStatement ps = txConn.prepareStatement(
                "SELECT NgayCapNhat FROM TONKHO WHERE MaKho = 'KHO01' AND MaChiTietSP = 'CTSP02'");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            updatedAt = rs.getTimestamp(1);
        }
        assertTrue(updatedAt.after(Timestamp.valueOf("2025-01-01 00:00:00")),
                "Trigger 3 phai tu dong ghi de NgayCapNhat thanh CURRENT_TIMESTAMP");
    }

    @Test
    @Order(9)
    void testTrigger4_PreventDeleteReferencedParentRecords() throws SQLException {
        assertThrowsSqlAndRollbackSavepoint(txConn,
                () -> executeUpdate(txConn, "DELETE FROM SANPHAM WHERE MaSanPham = 'SP01'"),
                "23503", "Không thể xóa bản ghi");

        assertThrowsSqlAndRollbackSavepoint(txConn,
                () -> executeUpdate(txConn, "DELETE FROM KHACHHANG WHERE MaKH = 'KH01'"),
                "23503", "Không thể xóa bản ghi");

        assertThrowsSqlAndRollbackSavepoint(txConn,
                () -> executeUpdate(txConn, "DELETE FROM DONHANG WHERE MaDonHang = 'DH01'"),
                "23503", "Không thể xóa bản ghi");
    }

    @Test
    @Order(10)
    void testTrigger5_PhieuKhoIsaDisjointImmediateAndDeferred() throws SQLException {
        // Phan A: Immediate BEFORE INSERT trigger chan luu sai bang con ISA
        executeUpdate(txConn,
                "INSERT INTO PHIEUKHO (MaPhieu, LoaiPhieu, TrangThai) VALUES ('PK_TEST_ISA1', 'NHAP', 'BAN_NHAP')");
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> executeUpdate(txConn,
                "INSERT INTO PHIEUXUATKHO (MaPhieu, MaDonHang, MaPhieuTra, KhoNguon) "
                        + "VALUES ('PK_TEST_ISA1', 'DH08', NULL, 'KHO01')"),
                "23514", "Vi phạm phân loại ISA");

        // Phan B: Deferred CONSTRAINT TRIGGER luc COMMIT chan PHIEUKHO mo coi (khong co
        // bang con)
        try (Connection c = openNewConnection()) {
            c.setAutoCommit(false);
            try {
                executeUpdate(c,
                        "INSERT INTO PHIEUKHO (MaPhieu, LoaiPhieu, TrangThai) VALUES ('PK_TEST_ISA2', 'NHAP', 'BAN_NHAP')");
                executeUpdate(c,
                        "INSERT INTO CHITIET_PHIEUKHO (MaCTPhieuKho, MaPhieu, MaChiTietSP, SoLuong, DonGia) "
                                + "VALUES ('CTPK_T_ISA2', 'PK_TEST_ISA2', 'CTSP01', 1, 1000.00)");

                SQLException ex = assertThrows(SQLException.class, c::commit);
                assertEquals("23514", ex.getSQLState());
                assertTrue(ex.getMessage().contains("Vi phạm ISA rời rạc"));
            } finally {
                quietRollback(c);
                cleanupCommittedTestArtifacts(c);
            }
        }
    }

    @Test
    @Order(11)
    void testTrigger6_DanhMucAntiCycle() throws SQLException {
        // Tu lam cha chinh minh
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> executeUpdate(txConn,
                "UPDATE DANHMUC_SP SET MaDanhMucCha = 'DM01' WHERE MaDanhMuc = 'DM01'"),
                "23514", "không thể tự làm danh mục cha");

        // Tao chu trinh 3 cap: DM01 -> DM03 -> DM07 -> DM01
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> executeUpdate(txConn,
                "UPDATE DANHMUC_SP SET MaDanhMucCha = 'DM07' WHERE MaDanhMuc = 'DM01'"),
                "23514", "chu trình phân cấp");
    }

    @Test
    @Order(12)
    void testTrigger7_PhieuTraHangAndChiTietTraHangValidation() throws SQLException {
        // 1. Khong duoc lap phieu tra cho don chua HOAN_TAT (DH07 dang DA_XAC_NHAN)
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> executeUpdate(txConn,
                "INSERT INTO PHIEUTRAHANG (MaPhieuTra, MaDonHang, LyDo, TrangThai) "
                        + "VALUES ('PTH_TEST_1', 'DH07', 'Test don chua hoan tat', 'CHO_XU_LY')"),
                "23514", "HOAN_TAT");

        // 2. Chi tiet tra hang khong thuoc dung don hang cua phieu tra (PTH01 thuoc
        // DH01, CTDH03 thuoc DH02)
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> executeUpdate(txConn,
                "INSERT INTO CHITIET_TRAHANG (MaCTTraHang, MaPhieuTra, MaCTDonHang, SoLuongTra, TinhTrangSanPham, HinhThucXuLy, DonGiaHoan, MaChiTietSPDoi) "
                        + "VALUES ('CTTH_T1', 'PTH01', 'CTDH03', 1, 'CON_MOI', 'HOAN_TIEN', 1000.00, NULL)"),
                "23514", "không thuộc đơn DH01");

        // 3. Tra lo tong so luong da mua (CTDH01 mua 2, CTTH01 da tra 1 -> tra them 2
        // se vuot)
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> executeUpdate(txConn,
                "INSERT INTO CHITIET_TRAHANG (MaCTTraHang, MaPhieuTra, MaCTDonHang, SoLuongTra, TinhTrangSanPham, HinhThucXuLy, DonGiaHoan, MaChiTietSPDoi) "
                        + "VALUES ('CTTH_T2', 'PTH01', 'CTDH01', 2, 'CON_MOI', 'HOAN_TIEN', 1000.00, NULL)"),
                "23514", "vượt quá số lượng đã mua");

        // 4. Doi hang khac MaSanPham (CTDH01 la CTSP01 cua SP01, doi sang CTSP04 cua
        // SP02)
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> executeUpdate(txConn,
                "INSERT INTO CHITIET_TRAHANG (MaCTTraHang, MaPhieuTra, MaCTDonHang, SoLuongTra, TinhTrangSanPham, HinhThucXuLy, DonGiaHoan, MaChiTietSPDoi) "
                        + "VALUES ('CTTH_T3', 'PTH01', 'CTDH01', 1, 'CON_MOI', 'DOI_HANG', 0.00, 'CTSP04')"),
                "23514", "phải cùng sản phẩm");

        // 5. Khong phai DOI_HANG nhung nhap MaChiTietSPDoi
        assertThrowsSqlAndRollbackSavepoint(txConn, () -> executeUpdate(txConn,
                "INSERT INTO CHITIET_TRAHANG (MaCTTraHang, MaPhieuTra, MaCTDonHang, SoLuongTra, TinhTrangSanPham, HinhThucXuLy, DonGiaHoan, MaChiTietSPDoi) "
                        + "VALUES ('CTTH_T4', 'PTH01', 'CTDH01', 1, 'CON_MOI', 'HOAN_TIEN', 1000.00, 'CTSP02')"),
                "23514", "DOI_HANG");
    }

    @Test
    @Order(13)
    void testTrigger8_PhieuKhoMustHaveAtLeastOneDetailOnCommit() throws SQLException {
        try (Connection c = openNewConnection()) {
            c.setAutoCommit(false);
            try {
                executeUpdate(c,
                        "INSERT INTO PHIEUKHO (MaPhieu, LoaiPhieu, TrangThai) VALUES ('PK_TEST_NODET', 'NHAP', 'BAN_NHAP')");
                executeUpdate(c,
                        "INSERT INTO PHIEUNHAPKHO (MaPhieu, MaNCC, MaPhieuTra, KhoDich) VALUES ('PK_TEST_NODET', 'NCC01', NULL, 'KHO01')");

                SQLException ex = assertThrows(SQLException.class, c::commit);
                assertEquals("23514", ex.getSQLState());
                assertTrue(ex.getMessage().contains("ít nhất một dòng chi tiết"));
            } finally {
                quietRollback(c);
                cleanupCommittedTestArtifacts(c);
            }
        }
    }

    // -------------------------------------------------------------------------
    // 5. KIEM TRA SAVEPOINT & DONG THOI 2 LUONG SONG SONG THAT (06, JDBC)
    // -------------------------------------------------------------------------

    @Test
    @Order(14)
    void testTransactionSavepointPartialRollback() throws SQLException {
        callCreateOrder(txConn, "DH_TEST_SP", "KH04", "KHO01", "52 Bach Dang", "THE",
                "[{\"ma_ctsp\":\"CTSP03\",\"so_luong\":1}]", "Don Savepoint");
        assertEquals(3, getAvailableStock(txConn, "KHO01", "CTSP03"));

        Savepoint spAfterFirstLine = txConn.setSavepoint("sp_sau_dong_1");
        executeUpdate(txConn,
                "INSERT INTO CHITIET_DONHANG (MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau) "
                        + "VALUES ('CTDH_TEST_WRONG', 'DH_TEST_SP', 'CTSP01', 1, 3200000.00, 0.00)");
        assertEquals(1, queryInt(txConn,
                "SELECT COUNT(*) FROM CHITIET_DONHANG WHERE MaCTDonHang = 'CTDH_TEST_WRONG'"));

        // Hoan tac rieng phan sau SAVEPOINT -> giu nguyen DH_TEST_SP va dong CTSP03
        txConn.rollback(spAfterFirstLine);
        assertEquals(0, queryInt(txConn,
                "SELECT COUNT(*) FROM CHITIET_DONHANG WHERE MaCTDonHang = 'CTDH_TEST_WRONG'"));
        assertEquals(3, getAvailableStock(txConn, "KHO01", "CTSP03"));
        assertEquals("DA_XAC_NHAN", queryString(txConn,
                "SELECT TrangThai FROM DONHANG WHERE MaDonHang = ?", "DH_TEST_SP"));
    }

    @Test
    @Order(15)
    void testTwoThreadConcurrencyPreventsOverselling() throws Exception {
        assertEquals(2, getAvailableStock(txConn, "KHO01", "CTSP18"));

        CountDownLatch threadALockedLatch = new CountDownLatch(1);
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();
        AtomicReference<SQLException> threadBError = new AtomicReference<>();
        AtomicReference<Long> threadBWaitMs = new AtomicReference<>(0L);

        Thread threadA = new Thread(() -> {
            try (Connection connA = openNewConnection()) {
                connA.setAutoCommit(false);
                connA.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                callCreateOrder(connA, "DH_TEST_CONC_A", "KH01", "KHO01", "10 Hai Ba Trung",
                        "VI_DIEN_TU", "[{\"ma_ctsp\":\"CTSP18\",\"so_luong\":2}]", "JUnit Thread A");
                threadALockedLatch.countDown();

                Thread.sleep(800);
                connA.commit();
            } catch (Throwable t) {
                threadALockedLatch.countDown();
                unexpectedError.compareAndSet(null, t);
            }
        }, "JUnit-Thread-A");

        Thread threadB = new Thread(() -> {
            try (Connection connB = openNewConnection()) {
                connB.setAutoCommit(false);
                connB.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                assertTrue(threadALockedLatch.await(10, TimeUnit.SECONDS));
                Thread.sleep(100);

                long start = System.nanoTime();
                try {
                    callCreateOrder(connB, "DH_TEST_CONC_B", "KH02", "KHO01", "22 Ly Tu Trong",
                            "TIEN_MAT", "[{\"ma_ctsp\":\"CTSP18\",\"so_luong\":1}]", "JUnit Thread B");
                    connB.commit();
                } catch (SQLException ex) {
                    threadBWaitMs.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                    threadBError.set(ex);
                    connB.rollback();
                }
            } catch (Throwable t) {
                unexpectedError.compareAndSet(null, t);
            }
        }, "JUnit-Thread-B");

        try {
            threadA.start();
            threadB.start();
            threadA.join(15_000);
            threadB.join(15_000);

            assertNull(unexpectedError.get(), "Khong duoc phat sinh loi he thong ngoai du kien");
            assertNotNull(threadBError.get(), "Thread-B phai bi chan khong cho overselling");
            assertTrue(threadBError.get().getMessage().contains("Không đủ tồn khả dụng"));
            assertTrue(threadBWaitMs.get() >= 400, "Thread-B phai thuc su cho row lock FOR UPDATE tu Thread-A");

            try (Connection verifyConn = openNewConnection()) {
                assertEquals(1,
                        queryInt(verifyConn, "SELECT COUNT(*) FROM DONHANG WHERE MaDonHang = 'DH_TEST_CONC_A'"));
                assertEquals(0,
                        queryInt(verifyConn, "SELECT COUNT(*) FROM DONHANG WHERE MaDonHang = 'DH_TEST_CONC_B'"));
                assertEquals(0, getAvailableStock(verifyConn, "KHO01", "CTSP18"));
            }
        } finally {
            try (Connection cleanupConn = openNewConnection()) {
                cleanupCommittedTestArtifacts(cleanupConn);
            }
        }
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private static Connection openNewConnection() throws SQLException {
        normalizePostgresCompatibleTimeZone();
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
    }

    private static void normalizePostgresCompatibleTimeZone() {
        // Windows JDK thuong dat timezone mac dinh la "Asia/Saigon", bi PostgreSQL 17
        // tren Linux Docker tu choi.
        if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        }
    }

    private static String resolveSetting(String propKey, String envKey, String defaultVal) {
        String prop = System.getProperty(propKey);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return defaultVal;
    }

    private static void assertBaselineSampleData(Connection c) throws SQLException {
        assertEquals(10, queryInt(c, "SELECT COUNT(*) FROM DONHANG"), "DONHANG phai giu nguyen 10 dong mau");
        assertEquals(18, queryInt(c, "SELECT COUNT(*) FROM CHITIET_DONHANG"),
                "CHITIET_DONHANG phai giu nguyen 18 dong mau");
        assertEquals(40, queryInt(c, "SELECT COUNT(*) FROM TONKHO"), "TONKHO phai giu nguyen 40 dong mau");
        assertEquals(23, getAvailableStock(c, "KHO01", "CTSP02"));
        assertEquals(2, getAvailableStock(c, "KHO01", "CTSP18"));
    }

    private static void cleanupCommittedTestArtifacts(Connection c) throws SQLException {
        List<String> orders = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT MaDonHang, TrangThai FROM DONHANG WHERE MaDonHang LIKE 'DH_TEST_%' OR MaDonHang LIKE 'DH_DEMO_%' OR MaDonHang LIKE 'DH_CONC_%'");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                orders.add(rs.getString(1) + ":" + rs.getString(2));
            }
        }

        boolean prevAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            for (String item : orders) {
                String[] parts = item.split(":");
                String maDh = parts[0];
                String tt = parts[1];
                if ("DA_XAC_NHAN".equals(tt) || "DANG_XU_LY".equals(tt)) {
                    try (PreparedStatement cancelPs = c.prepareStatement("CALL sp_huy_don_hang(?, ?)")) {
                        cancelPs.setString(1, maDh);
                        cancelPs.setString(2, "Cleanup JUnit");
                        cancelPs.execute();
                    }
                }
                try (PreparedStatement delCt = c.prepareStatement("DELETE FROM CHITIET_DONHANG WHERE MaDonHang = ?")) {
                    delCt.setString(1, maDh);
                    delCt.executeUpdate();
                }
                try (PreparedStatement delDh = c.prepareStatement("DELETE FROM DONHANG WHERE MaDonHang = ?")) {
                    delDh.setString(1, maDh);
                    delDh.executeUpdate();
                }
            }

            executeUpdate(c,
                    "DELETE FROM CHITIET_PHIEUKHO WHERE MaCTPhieuKho LIKE 'CTPK_T_%' OR MaPhieu LIKE 'PK_TEST_%'");
            executeUpdate(c, "DELETE FROM PHIEUNHAPKHO WHERE MaPhieu LIKE 'PK_TEST_%'");
            executeUpdate(c, "DELETE FROM PHIEUXUATKHO WHERE MaPhieu LIKE 'PK_TEST_%'");
            executeUpdate(c, "DELETE FROM PHIEUCHUYENKHO WHERE MaPhieu LIKE 'PK_TEST_%'");
            executeUpdate(c, "DELETE FROM PHIEUKHO WHERE MaPhieu LIKE 'PK_TEST_%'");
            executeUpdate(c,
                    "DELETE FROM CHITIET_TRAHANG WHERE MaCTTraHang LIKE 'CTTH_T%' OR MaPhieuTra LIKE 'PTH_TEST_%'");
            executeUpdate(c, "DELETE FROM PHIEUTRAHANG WHERE MaPhieuTra LIKE 'PTH_TEST_%'");

            c.commit();
        } catch (SQLException ex) {
            quietRollback(c);
            throw ex;
        } finally {
            c.setAutoCommit(prevAuto);
        }
    }

    private static void assertThrowsSqlAndRollbackSavepoint(
            Connection c,
            SqlExecutable action,
            String expectedSqlState,
            String expectedMsgPart) throws SQLException {
        Savepoint sp = c.setSavepoint();
        SQLException ex = assertThrows(SQLException.class, action::execute);
        c.rollback(sp);
        c.releaseSavepoint(sp);

        if (expectedSqlState != null) {
            assertEquals(expectedSqlState, ex.getSQLState(),
                    "SQLState khong khop; thong bao: " + ex.getMessage());
        }
        if (expectedMsgPart != null) {
            assertTrue(ex.getMessage().toLowerCase().contains(expectedMsgPart.toLowerCase()),
                    "Thong bao loi thuc te [" + ex.getMessage() + "] khong chua [" + expectedMsgPart + "]");
        }
    }

    private static void callCreateOrder(
            Connection c, String maDh, String maKh, String maKho,
            String diaChi, String pttt, String json, String ghiChu) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("CALL sp_tao_don_hang(?, ?, ?, ?, ?, ?::jsonb, ?)")) {
            ps.setString(1, maDh);
            ps.setString(2, maKh);
            ps.setString(3, maKho);
            ps.setString(4, diaChi);
            ps.setString(5, pttt);
            ps.setString(6, json);
            if (ghiChu != null) {
                ps.setString(7, ghiChu);
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            ps.execute();
        }
    }

    private static int getAvailableStock(Connection c, String maKho, String maCtsp) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT fn_tinh_ton_kho_kha_dung(?, ?)")) {
            ps.setString(1, maKho);
            ps.setString(2, maCtsp);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static BigDecimal sumDynamicReport(
            Connection c, String maKho, Timestamp tuNgay, Timestamp denNgay, String maDm) throws SQLException {
        BigDecimal total = BigDecimal.ZERO;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT DoanhThu FROM fn_bao_cao_doanh_thu_dong(?, ?, ?, ?)")) {
            setNullableString(ps, 1, maKho);
            setNullableTimestamp(ps, 2, tuNgay);
            setNullableTimestamp(ps, 3, denNgay);
            setNullableString(ps, 4, maDm);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    total = total.add(rs.getBigDecimal(1));
                }
            }
        }
        return total;
    }

    private static void setNullableString(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val != null) {
            ps.setString(idx, val);
        } else {
            ps.setNull(idx, Types.VARCHAR);
        }
    }

    private static void setNullableTimestamp(PreparedStatement ps, int idx, Timestamp val) throws SQLException {
        if (val != null) {
            ps.setTimestamp(idx, val);
        } else {
            ps.setNull(idx, Types.TIMESTAMP);
        }
    }

    private static int queryInt(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String queryString(Connection c, String sql, String param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static BigDecimal queryBigDecimal(Connection c, String sql, String param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private static void executeUpdate(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static void quietRollback(Connection c) {
        try {
            if (c != null && !c.getAutoCommit()) {
                c.rollback();
            }
        } catch (SQLException ignored) {
            // Giao dich da bi PostgreSQL tu rollback khi deferred trigger that bai
        }
    }

    private static Path locateSqlFile(String fileName) {
        Path[] candidates = {
                Path.of("..", fileName),
                Path.of("CSDL quan hệ", "src", fileName),
                Path.of(fileName)
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        throw new IllegalStateException("Khong tim thay file SQL: " + fileName);
    }

    private static List<String> parseSqlStatements(String rawSql) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : rawSql.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }
        List<String> out = new ArrayList<>();
        for (String chunk : cleaned.toString().split(";")) {
            String trimmed = chunk.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
