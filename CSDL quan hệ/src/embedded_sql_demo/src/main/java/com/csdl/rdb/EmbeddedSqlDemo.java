package com.csdl.rdb;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Embedded SQL & Multi-thread Concurrency Demo (PostgreSQL JDBC)
 * De tai 2: Quan ly Ban hang & Ton kho Da kho (Chuyen nganh Giay)
 */
public class EmbeddedSqlDemo {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final StringBuilder LOG_BUFFER = new StringBuilder();

    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;

    public EmbeddedSqlDemo(String jdbcUrl, String dbUser, String dbPassword) {
        this.jdbcUrl = jdbcUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public static void main(String[] args) throws Exception {
        String url = resolveConfig(args, 0, "PG_URL", "jdbc:postgresql://localhost:5433/ql_banhang");
        String user = resolveConfig(args, 1, "PG_USER", "postgres");
        String pass = resolveConfig(args, 2, "PG_PASS", "123456");

        EmbeddedSqlDemo app = new EmbeddedSqlDemo(url, user, pass);
        app.runAllDemos();
    }

    private static String resolveConfig(String[] args, int index, String envName, String defaultValue) {
        if (args != null && args.length > index && args[index] != null && !args[index].isBlank()) {
            return args[index].trim();
        }
        String envVal = System.getenv(envName);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        return defaultValue;
    }

    public void runAllDemos() throws Exception {
        log("================================================================================");
        log("EMBEDDED SQL & CONCURRENCY DEMO (PostgreSQL JDBC) - DE TAI 2 (NGANH GIAY)");
        log("Ket noi: " + jdbcUrl + " | User: " + dbUser);
        log("================================================================================");

        try (Connection conn = openConnection()) {
            cleanupDemoData(conn);

            demoParameterizedSql(conn);
            demoDynamicSqlReport(conn);
            demoTransactionCommitAndRollback(conn);
            demoTwoThreadConcurrency();

            log("\n[CLEANUP] Khoi phuc du lieu mau goc...");
            cleanupDemoData(conn);
            verifyBaselineState(conn);
        } finally {
            writeOutputLog();
        }
    }

    private Connection openConnection() throws SQLException {
        // Windows JDK thuong dat timezone mac dinh la "Asia/Saigon", bi PostgreSQL 17 tren Linux Docker tu choi.
        if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        }
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
    }

    /**
     * Demo 1: Parameterized SQL (PreparedStatement) chong SQL Injection & tra cuu ton kho.
     */
    private void demoParameterizedSql(Connection conn) throws SQLException {
        log("\n--------------------------------------------------------------------------------");
        log("[DEMO 1] EMBEDDED PARAMETERIZED SQL (PreparedStatement & Stored Function)");
        log("--------------------------------------------------------------------------------");

        String sql = """
                SELECT tk.MaKho, tk.MaChiTietSP, ct.SKU, sp.TenSanPham,
                       tk.SoLuongThucTe, tk.SoLuongDaGiu,
                       fn_tinh_ton_kho_kha_dung(tk.MaKho, tk.MaChiTietSP) AS KhaDung
                FROM TONKHO tk
                JOIN CHITIET_SP ct ON tk.MaChiTietSP = ct.MaChiTietSP
                JOIN SANPHAM sp    ON ct.MaSanPham = sp.MaSanPham
                WHERE tk.MaKho = ? AND tk.MaChiTietSP IN (?, ?)
                ORDER BY tk.MaChiTietSP
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "KHO01");
            ps.setString(2, "CTSP02");
            ps.setString(3, "CTSP18");

            try (ResultSet rs = ps.executeQuery()) {
                log(String.format("%-6s | %-7s | %-20s | %-24s | %6s | %6s | %7s",
                        "Kho", "CTSP", "SKU", "SanPham", "ThucTe", "DaGiu", "KhaDung"));
                while (rs.next()) {
                    log(String.format("%-6s | %-7s | %-20s | %-24s | %6d | %6d | %7d",
                            rs.getString("MaKho"),
                            rs.getString("MaChiTietSP"),
                            rs.getString("SKU"),
                            truncate(rs.getString("TenSanPham"), 24),
                            rs.getInt("SoLuongThucTe"),
                            rs.getInt("SoLuongDaGiu"),
                            rs.getInt("KhaDung")));
                }
            }
        }

        // Thu nghiem truyen chuoi SQL Injection qua PreparedStatement
        String maliciousInput = "KH01' OR '1'='1";
        String checkCustomerSql = "SELECT COUNT(*) FROM KHACHHANG WHERE MaKH = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkCustomerSql)) {
            ps.setString(1, maliciousInput);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                log("Kiem thu chong SQL Injection voi input [\"" + maliciousInput + "\"]: so dong tra ve = "
                        + rs.getInt(1) + " (an toan tuyet doi).");
            }
        }
    }

    /**
     * Demo 2: Goi Dynamic SQL Stored Function fn_bao_cao_doanh_thu_dong voi tham so tuy chon.
     */
    private void demoDynamicSqlReport(Connection conn) throws SQLException {
        log("\n--------------------------------------------------------------------------------");
        log("[DEMO 2] DYNAMIC SQL REPORT (fn_bao_cao_doanh_thu_dong)");
        log("--------------------------------------------------------------------------------");

        log("-> Truong hop A: Loc theo kho KHO01, tat ca danh muc (p_ma_danh_muc = NULL)");
        executeRevenueReport(conn, "KHO01", null);

        log("-> Truong hop B: Loc theo danh muc cha DM01 (Recursive CTE), tat ca kho (p_ma_kho = NULL)");
        executeRevenueReport(conn, null, "DM01");
    }

    private void executeRevenueReport(Connection conn, String maKho, String maDanhMuc) throws SQLException {
        String sql = "SELECT MaKho, TenKho, MaDanhMuc, TenDanhMuc, SoDonHang, TongSoLuongBan, DoanhThu "
                + "FROM fn_bao_cao_doanh_thu_dong(?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (maKho != null) {
                ps.setString(1, maKho);
            } else {
                ps.setNull(1, Types.VARCHAR);
            }
            ps.setNull(2, Types.TIMESTAMP);
            ps.setNull(3, Types.TIMESTAMP);
            if (maDanhMuc != null) {
                ps.setString(4, maDanhMuc);
            } else {
                ps.setNull(4, Types.VARCHAR);
            }

            try (ResultSet rs = ps.executeQuery()) {
                log(String.format("   %-6s | %-24s | %-6s | %-20s | %5s | %5s | %14s",
                        "Kho", "TenKho", "MaDM", "TenDanhMuc", "SoDH", "SoBan", "DoanhThu"));
                while (rs.next()) {
                    BigDecimal doanhThu = rs.getBigDecimal("DoanhThu");
                    log(String.format("   %-6s | %-24s | %-6s | %-20s | %5d | %5d | %,14.2f",
                            rs.getString("MaKho"),
                            truncate(rs.getString("TenKho"), 24),
                            rs.getString("MaDanhMuc"),
                            truncate(rs.getString("TenDanhMuc"), 20),
                            rs.getLong("SoDonHang"),
                            rs.getLong("TongSoLuongBan"),
                            doanhThu));
                }
            }
        }
    }

    /**
     * Demo 3: Transaction Integrity (COMMIT hop le & ROLLBACK khi vuot ton kho kha dung).
     */
    private void demoTransactionCommitAndRollback(Connection conn) throws SQLException {
        log("\n--------------------------------------------------------------------------------");
        log("[DEMO 3] TRANSACTION INTEGRITY: COMMIT HOP LE & ROLLBACK CHONG AM TON");
        log("--------------------------------------------------------------------------------");

        int beforeOk = getAvailableStock(conn, "KHO01", "CTSP02");
        log("Ton kha dung (KHO01, CTSP02) truoc COMMIT = " + beforeOk);

        // Case A: COMMIT thanh cong
        conn.setAutoCommit(false);
        try {
            callCreateOrder(conn, "DH_DEMO_OK", "KH01", "KHO01", "123 Nguyen Hue, Q1, TP.HCM",
                    "CHUYEN_KHOAN", "[{\"ma_ctsp\":\"CTSP02\",\"so_luong\":1,\"chiet_khau\":0}]",
                    "Demo COMMIT tu JDBC");
            conn.commit();
            int afterOk = getAvailableStock(conn, "KHO01", "CTSP02");
            log("[COMMIT THANH CONG] Tao DH_DEMO_OK (1 doi CTSP02). Ton kha dung: " + beforeOk + " -> " + afterOk);
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }

        // Case B: ROLLBACK khi dat vuot ton kho (yeu cau 999 doi CTSP18 khi chi con 2)
        int beforeFail = getAvailableStock(conn, "KHO01", "CTSP18");
        log("Ton kha dung (KHO01, CTSP18) truoc ROLLBACK test = " + beforeFail);

        conn.setAutoCommit(false);
        try {
            callCreateOrder(conn, "DH_DEMO_FAIL", "KH02", "KHO01", "45 Le Loi, Q1, TP.HCM",
                    "TIEN_MAT", "[{\"ma_ctsp\":\"CTSP18\",\"so_luong\":999,\"chiet_khau\":0}]",
                    "Demo vuot ton kho");
            conn.commit();
        } catch (SQLException ex) {
            log("[SQL EXCEPTION] SQLState=" + ex.getSQLState() + " | Message=" + firstLine(ex.getMessage()));
            conn.rollback();
            log("[ROLLBACK THANH CONG] Da huy toan bo giao dich DH_DEMO_FAIL.");
        } finally {
            conn.setAutoCommit(true);
        }

        int afterFail = getAvailableStock(conn, "KHO01", "CTSP18");
        boolean orderExists = existsOrder(conn, "DH_DEMO_FAIL");
        log("Kiem tra sau ROLLBACK: DH_DEMO_FAIL ton tai? " + orderExists
                + " | Ton kha dung (KHO01, CTSP18) van la " + afterFail);
    }

    /**
     * Demo 4: Hai Thread song song tranh mua 2 doi cuoi cung cua (KHO01, CTSP18).
     * Thread A khoa dong TONKHO bang FOR UPDATE, giu lock 1500ms roi COMMIT.
     * Thread B block cho lock cua Thread A, khi tinh day duoc du lieu moi (kha dung = 0)
     * thi bi procedure/trigger chan lai va ROLLBACK.
     */
    private void demoTwoThreadConcurrency() throws Exception {
        log("\n--------------------------------------------------------------------------------");
        log("[DEMO 4] TWO-THREAD CONCURRENCY (2 JDBC Connections, READ COMMITTED, FOR UPDATE)");
        log("--------------------------------------------------------------------------------");

        try (Connection checkConn = openConnection()) {
            log("Trang thai ban dau (KHO01, CTSP18): KhaDung = "
                    + getAvailableStock(checkConn, "KHO01", "CTSP18"));
        }

        CountDownLatch threadALockedLatch = new CountDownLatch(1);
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();

        Thread threadA = new Thread(() -> {
            try (Connection connA = openConnection()) {
                connA.setAutoCommit(false);
                connA.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                logThread("Thread-A", "Bat dau giao dich, dat mua 2 doi CTSP18 (DH_CONC_A)...");
                callCreateOrder(connA, "DH_CONC_A", "KH01", "KHO01", "10 Hai Ba Trung, Q1",
                        "VI_DIEN_TU", "[{\"ma_ctsp\":\"CTSP18\",\"so_luong\":2,\"chiet_khau\":0}]",
                        "Concurrency Thread A");
                logThread("Thread-A", "Da giu lock FOR UPDATE tren TONKHO(KHO01, CTSP18). Giu lock 1500ms...");
                threadALockedLatch.countDown();

                Thread.sleep(1500);
                connA.commit();
                logThread("Thread-A", "Da COMMIT DH_CONC_A! (Giai phong row lock).");
            } catch (Throwable t) {
                threadALockedLatch.countDown();
                unexpectedError.compareAndSet(null, t);
            }
        }, "Thread-A");

        Thread threadB = new Thread(() -> {
            try (Connection connB = openConnection()) {
                connB.setAutoCommit(false);
                connB.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                if (!threadALockedLatch.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Het thoi gian cho Thread-A lay lock.");
                }
                // Nho giup Thread-B goi ngay sau khi Thread-A da giu lock
                Thread.sleep(100);

                long startNanos = System.nanoTime();
                logThread("Thread-B", "Bat dau giao dich, co dat mua 1 doi CTSP18 (DH_CONC_B)...");
                try {
                    callCreateOrder(connB, "DH_CONC_B", "KH02", "KHO01", "22 Ly Tu Trong, Q1",
                            "TIEN_MAT", "[{\"ma_ctsp\":\"CTSP18\",\"so_luong\":1,\"chiet_khau\":0}]",
                            "Concurrency Thread B");
                    connB.commit();
                    logThread("Thread-B", "CANH BAO: Thread-B khong nen thanh cong!");
                } catch (SQLException ex) {
                    long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                    logThread("Thread-B", "Bi BLOCK cho lock trong " + waitedMs
                            + " ms -> nhan loi tu PostgreSQL: " + firstLine(ex.getMessage()));
                    connB.rollback();
                    logThread("Thread-B", "Da ROLLBACK giao dich DH_CONC_B (Chong overselling thanh cong).");
                }
            } catch (Throwable t) {
                unexpectedError.compareAndSet(null, t);
            }
        }, "Thread-B");

        threadA.start();
        threadB.start();
        threadA.join(15_000);
        threadB.join(15_000);

        if (unexpectedError.get() != null) {
            throw new RuntimeException("Loi khong mong muon trong concurrency test", unexpectedError.get());
        }

        try (Connection verifyConn = openConnection()) {
            int finalAvail = getAvailableStock(verifyConn, "KHO01", "CTSP18");
            boolean hasA = existsOrder(verifyConn, "DH_CONC_A");
            boolean hasB = existsOrder(verifyConn, "DH_CONC_B");
            log(String.format("Ket qua sau 2 Thread: DH_CONC_A=%s | DH_CONC_B=%s | Ton kha dung CTSP18=%d (khong am!)",
                    hasA, hasB, finalAvail));
        }
    }

    private void callCreateOrder(
            Connection conn,
            String maDonHang,
            String maKh,
            String maKho,
            String diaChi,
            String pttt,
            String chiTietJson,
            String ghiChu) throws SQLException {
        String callSql = "CALL sp_tao_don_hang(?, ?, ?, ?, ?, ?::jsonb, ?)";
        try (PreparedStatement ps = conn.prepareStatement(callSql)) {
            ps.setString(1, maDonHang);
            ps.setString(2, maKh);
            ps.setString(3, maKho);
            ps.setString(4, diaChi);
            ps.setString(5, pttt);
            ps.setString(6, chiTietJson);
            ps.setString(7, ghiChu);
            ps.execute();
        }
    }

    private int getAvailableStock(Connection conn, String maKho, String maCtsp) throws SQLException {
        String sql = "SELECT fn_tinh_ton_kho_kha_dung(?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, maKho);
            ps.setString(2, maCtsp);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private boolean existsOrder(Connection conn, String maDonHang) throws SQLException {
        String sql = "SELECT 1 FROM DONHANG WHERE MaDonHang = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, maDonHang);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void cleanupDemoData(Connection conn) throws SQLException {
        List<String> ordersToClean = new ArrayList<>();
        String findSql = "SELECT MaDonHang, TrangThai FROM DONHANG WHERE MaDonHang LIKE 'DH_DEMO_%' OR MaDonHang LIKE 'DH_CONC_%'";
        try (PreparedStatement ps = conn.prepareStatement(findSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String maDh = rs.getString("MaDonHang");
                String tt = rs.getString("TrangThai");
                ordersToClean.add(maDh + ":" + tt);
            }
        }

        if (ordersToClean.isEmpty()) {
            return;
        }

        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (String entry : ordersToClean) {
                String[] parts = entry.split(":");
                String maDh = parts[0];
                String tt = parts[1];
                if ("DA_XAC_NHAN".equals(tt) || "DANG_XU_LY".equals(tt)) {
                    try (PreparedStatement cancelPs = conn.prepareStatement("CALL sp_huy_don_hang(?, ?)")) {
                        cancelPs.setString(1, maDh);
                        cancelPs.setString(2, "Cleanup demo JDBC");
                        cancelPs.execute();
                    }
                }
                try (PreparedStatement delDetail = conn.prepareStatement("DELETE FROM CHITIET_DONHANG WHERE MaDonHang = ?")) {
                    delDetail.setString(1, maDh);
                    delDetail.executeUpdate();
                }
                try (PreparedStatement delOrder = conn.prepareStatement("DELETE FROM DONHANG WHERE MaDonHang = ?")) {
                    delOrder.setString(1, maDh);
                    delOrder.executeUpdate();
                }
            }
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private void verifyBaselineState(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT (SELECT COUNT(*) FROM DONHANG) AS so_dh, (SELECT COUNT(*) FROM TONKHO) AS so_tk");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            log(String.format("Trang thai sau cleanup: DONHANG=%d dong, TONKHO=%d dong | (KHO01,CTSP02)=%d | (KHO01,CTSP18)=%d",
                    rs.getInt("so_dh"),
                    rs.getInt("so_tk"),
                    getAvailableStock(conn, "KHO01", "CTSP02"),
                    getAvailableStock(conn, "KHO01", "CTSP18")));
        }
    }

    private static synchronized void log(String message) {
        System.out.println(message);
        LOG_BUFFER.append(message).append(System.lineSeparator());
    }

    private static void logThread(String threadName, String message) {
        String ts = LocalTime.now().format(TIME_FMT);
        log(String.format("[%s] [%-8s] %s", ts, threadName, message));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    private static String firstLine(String msg) {
        if (msg == null) {
            return "";
        }
        int idx = msg.indexOf('\n');
        return idx >= 0 ? msg.substring(0, idx).trim() : msg.trim();
    }

    private void writeOutputLog() throws IOException {
        Path outDir = Path.of("target");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("embedded_sql_output.txt");
        Files.writeString(outFile, LOG_BUFFER.toString(), StandardCharsets.UTF_8);
    }
}

