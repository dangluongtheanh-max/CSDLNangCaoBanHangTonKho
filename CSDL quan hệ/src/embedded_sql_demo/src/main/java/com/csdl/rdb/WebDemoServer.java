package com.csdl.rdb;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mini Web Demo Server (JDK built-in HttpServer, khong can framework ngoai)
 * Phuc vu truc quan hoa toan bo chuc nang CSDL Quan he (PostgreSQL 17)
 * va doi chieu truc tiep voi CSDL Huong doi tuong (ObjectDB - Phuong an B).
 */
public class WebDemoServer {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Object CONCURRENCY_LOCK = new Object();
    private static final Object BENCHMARK_LOCK = new Object();
    private static final Object OODB_LOCK = new Object();
    private static final AtomicInteger ORDER_SEQ = new AtomicInteger(1);
    private static final AtomicInteger TRANSFER_SEQ = new AtomicInteger(1);
    private static final AtomicInteger RETURN_SEQ = new AtomicInteger(1);
    private static final AtomicInteger DETAIL_SEQ = new AtomicInteger(1);

    private static final List<String> SUPPLEMENTAL_INDEX_NAMES = List.of(
            "idx_donhang_kh_ngaydat",
            "idx_donhang_trangthai_ngaydat",
            "idx_donhang_kho_trangthai",
            "idx_ctdh_machitietsp_cover",
            "idx_chitiet_sp_masanpham",
            "idx_sanpham_danhmuc_thuonghieu",
            "idx_danhmuc_sp_cha",
            "idx_tonkho_machitietsp_kho",
            "idx_khachhang_ngaydangky",
            "idx_cungcap_mancc_ctsp",
            "idx_khachhang_sdt",
            "idx_phieutrahang_madonhang",
            "idx_chitiet_trahang_mactdh",
            "idx_phieukho_loai_trangthai_ngay",
            "idx_chitiet_phieukho_ctsp",
            "idx_phieunhapkho_mancc_khodich",
            "idx_phieuxuatkho_khonguon",
            "idx_phieuchuyenkho_kho"
    );

    private static final List<String> SUPPLEMENTAL_INDEX_DDL = List.of(
            "CREATE INDEX IF NOT EXISTS idx_donhang_kh_ngaydat ON DONHANG (MaKH, NgayDat DESC)",
            "CREATE INDEX IF NOT EXISTS idx_donhang_trangthai_ngaydat ON DONHANG (TrangThai, NgayDat DESC)",
            "CREATE INDEX IF NOT EXISTS idx_donhang_kho_trangthai ON DONHANG (MaKho, TrangThai, NgayDat DESC)",
            "CREATE INDEX IF NOT EXISTS idx_ctdh_machitietsp_cover ON CHITIET_DONHANG (MaChiTietSP) INCLUDE (MaDonHang, SoLuong, DonGiaBan, ChietKhau)",
            "CREATE INDEX IF NOT EXISTS idx_chitiet_sp_masanpham ON CHITIET_SP (MaSanPham, TrangThai)",
            "CREATE INDEX IF NOT EXISTS idx_sanpham_danhmuc_thuonghieu ON SANPHAM (MaDanhMuc, MaThuongHieu)",
            "CREATE INDEX IF NOT EXISTS idx_danhmuc_sp_cha ON DANHMUC_SP (MaDanhMucCha)",
            "CREATE INDEX IF NOT EXISTS idx_tonkho_machitietsp_kho ON TONKHO (MaChiTietSP, MaKho)",
            "CREATE INDEX IF NOT EXISTS idx_khachhang_ngaydangky ON KHACHHANG (NgayDangKy DESC)",
            "CREATE INDEX IF NOT EXISTS idx_cungcap_mancc_ctsp ON CUNGCAP (MaNCC, MaChiTietSP)",
            "CREATE INDEX IF NOT EXISTS idx_khachhang_sdt ON KHACHHANG (SDT)",
            "CREATE INDEX IF NOT EXISTS idx_phieutrahang_madonhang ON PHIEUTRAHANG (MaDonHang, TrangThai)",
            "CREATE INDEX IF NOT EXISTS idx_chitiet_trahang_mactdh ON CHITIET_TRAHANG (MaCTDonHang, MaPhieuTra)",
            "CREATE INDEX IF NOT EXISTS idx_phieukho_loai_trangthai_ngay ON PHIEUKHO (LoaiPhieu, TrangThai, NgayLap DESC)",
            "CREATE INDEX IF NOT EXISTS idx_chitiet_phieukho_ctsp ON CHITIET_PHIEUKHO (MaChiTietSP, MaPhieu)",
            "CREATE INDEX IF NOT EXISTS idx_phieunhapkho_mancc_khodich ON PHIEUNHAPKHO (MaNCC, KhoDich)",
            "CREATE INDEX IF NOT EXISTS idx_phieuxuatkho_khonguon ON PHIEUXUATKHO (KhoNguon)",
            "CREATE INDEX IF NOT EXISTS idx_phieuchuyenkho_kho ON PHIEUCHUYENKHO (KhoNguon, KhoDich)"
    );

    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;
    private final int port;
    private HttpServer server;
    private final ScheduledExecutorService shutdownScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "web-demo-shutdown");
        t.setDaemon(true);
        return t;
    });
    private final AtomicReference<ScheduledFuture<?>> pendingShutdown = new AtomicReference<>();
    private final AtomicLong lastActivityNanos = new AtomicLong(System.nanoTime());
    private final Map<String, InteractiveTxSession> txSessions = Map.of(
            "A", new InteractiveTxSession("A"),
            "B", new InteractiveTxSession("B")
    );

    public WebDemoServer(String jdbcUrl, String dbUser, String dbPassword, int port) {
        this.jdbcUrl = jdbcUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.port = port;
    }

    public static void main(String[] args) throws Exception {
        String url = resolveSetting("pg.url", "PG_URL", "jdbc:postgresql://localhost:5433/ql_banhang");
        String user = resolveSetting("pg.user", "PG_USER", "postgres");
        String pass = resolveSetting("pg.pass", "PG_PASS", "123456");
        int port = Integer.parseInt(resolveSetting("web.port", "WEB_PORT", "8080"));
        boolean openBrowser = !"1".equals(System.getenv("WEB_NO_BROWSER"))
                && !Boolean.getBoolean("web.noBrowser");

        WebDemoServer app = new WebDemoServer(url, user, pass, port);
        try {
            app.start();
        } catch (java.net.BindException be) {
            System.err.println("[LUU Y] Cong " + port + " dang duoc su dung (WebDemoServer co the dang chay san).");
            System.err.println("Hay mo trinh duyet tai http://127.0.0.1:" + port + " (dong tab web hoac nhan Ctrl + Alt + M de tat server cu).");
            return;
        }

        String webUrl = "http://127.0.0.1:" + port;
        System.out.println("==========================================================================");
        System.out.println("DO AN CSDL NANG CAO - MINI WEB DEMO (POSTGRESQL 17 + OBJECTDB)");
        System.out.println("Mo trinh duyet tai: " + webUrl);
        System.out.println("Ket noi PostgreSQL: " + url + " (User: " + user + ")");
        System.out.println("Dong tab trinh duyet (hoac nhan Ctrl + C / Stop trong IDE) de tu dong tat server.");
        System.out.println("==========================================================================");

        if (openBrowser && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(URI.create(webUrl));
            } catch (Exception ignored) {
                // Khong chan server neu khong mo duoc trinh duyet tu dong
            }
        }

        CountDownLatch keepAlive = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            keepAlive.countDown();
        }));
        keepAlive.await();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", handleStaticIndex());
        server.createContext("/api/health", wrapJson(this::handleHealth));
        server.createContext("/api/ping", wrapJson(ex -> Map.of("ok", true)));
        server.createContext("/api/overview", wrapJson(this::handleOverview));
        server.createContext("/api/orders/create", wrapJson(this::handleCreateOrder));
        server.createContext("/api/orders/complete", wrapJson(this::handleCompleteOrder));
        server.createContext("/api/orders/cancel", wrapJson(this::handleCancelOrder));
        server.createContext("/api/warehouse/transfer", wrapJson(this::handleTransferStock));
        server.createContext("/api/warehouse/confirm", wrapJson(this::handleConfirmTransfer));
        server.createContext("/api/warehouse/receive", wrapJson(this::handleReceiveStock));
        server.createContext("/api/returns/create", wrapJson(this::handleCreateReturn));
        server.createContext("/api/demo/concurrency", wrapJson(this::handleConcurrencyDemo));
        server.createContext("/api/demo/tx/status", wrapJson(this::handleInteractiveTxStatus));
        server.createContext("/api/demo/tx/start", wrapJson(this::handleInteractiveTxStart));
        server.createContext("/api/demo/tx/commit", wrapJson(this::handleInteractiveTxCommit));
        server.createContext("/api/demo/tx/rollback", wrapJson(this::handleInteractiveTxRollback));
        server.createContext("/api/demo/tx/reset", wrapJson(this::handleInteractiveTxReset));
        server.createContext("/api/reports/dynamic", wrapJson(this::handleDynamicReport));
        server.createContext("/api/queries/catalog", wrapJson(this::handleSqlQueryCatalog));
        server.createContext("/api/queries/run", wrapJson(this::handleRunSqlQuery));
        server.createContext("/api/benchmark/run", wrapJson(this::handleBenchmarkRun));
        server.createContext("/api/oodb/run", wrapJson(this::handleOodbRun));
        server.createContext("/api/reset", wrapJson(this::handleReset));
        server.createContext("/api/shutdown", wrapJson(ex -> {
            Map<String, String> q = parseQueryParams(ex.getRequestURI().getRawQuery());
            boolean auto = "1".equals(q.get("auto")) || "true".equalsIgnoreCase(q.get("auto"));
            long delayMs = auto ? 2000L : 150L;
            scheduleShutdown(delayMs, auto ? "Da dong tab trinh duyet" : "Nhan yeu cau tat truc tiep", auto);
            return Map.of(
                    "ok", true,
                    "auto", auto,
                    "delayMs", delayMs,
                    "message", auto ? "Cho xac nhan dong tab (2s)..." : "Server dang tat..."
            );
        }));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    private void scheduleShutdown(long delayMs, String reason, boolean cancellableByActivity) {
        long requestedAtNanos = System.nanoTime();
        ScheduledFuture<?> next = shutdownScheduler.schedule(() -> {
            if (cancellableByActivity && lastActivityNanos.get() > requestedAtNanos) {
                return;
            }
            System.out.println("[WebDemoServer] " + reason + " -> Dang tat server...");
            stop();
            System.exit(0);
        }, delayMs, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> prev = pendingShutdown.getAndSet(next);
        if (prev != null) {
            prev.cancel(false);
        }
    }

    private void recordActivity() {
        lastActivityNanos.set(System.nanoTime());
        ScheduledFuture<?> prev = pendingShutdown.getAndSet(null);
        if (prev != null) {
            prev.cancel(false);
        }
    }

    public void stop() {
        closeAllInteractiveSessionsQuietly();
        if (server != null) {
            server.stop(0);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP HANDLERS
    // -------------------------------------------------------------------------

    private HttpHandler handleStaticIndex() {
        return exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed");
                return;
            }
            recordActivity();
            byte[] html = loadIndexHtml();
            Headers h = exchange.getResponseHeaders();
            h.set("Content-Type", "text/html; charset=utf-8");
            h.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, html.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html);
            }
        };
    }

    @FunctionalInterface
    interface JsonRoute {
        Object handle(HttpExchange exchange) throws Exception;
    }

    private HttpHandler wrapJson(JsonRoute route) {
        return exchange -> {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, Map.of("ok", true));
                return;
            }
            if (!"/api/shutdown".equals(exchange.getRequestURI().getPath())) {
                recordActivity();
            }
            try {
                Object result = route.handle(exchange);
                sendJson(exchange, 200, result);
            } catch (Exception ex) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("ok", false);
                err.put("error", firstLine(ex.getMessage()));
                if (ex instanceof SQLException sqlEx) {
                    err.put("sqlState", sqlEx.getSQLState());
                }
                sendJson(exchange, 500, err);
            }
        };
    }

    private Object handleHealth(HttpExchange ex) throws SQLException {
        try (Connection c = openConnection()) {
            return Map.of("ok", true, "baseline", fetchBaseline(c));
        }
    }

    private Object handleOverview(HttpExchange ex) throws SQLException {
        try (Connection c = openConnection()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("baseline", fetchBaseline(c));
            out.put("warehouses", queryRowsAsMaps(c,
                    "SELECT MaKho, TenKho, TrangThai FROM KHO ORDER BY MaKho"));
            out.put("customers", queryRowsAsMaps(c,
                    "SELECT MaKH, HoTen, SDT FROM KHACHHANG ORDER BY MaKH"));
            out.put("brands", queryRowsAsMaps(c,
                    "SELECT MaThuongHieu, TenThuongHieu FROM THUONGHIEU ORDER BY MaThuongHieu"));
            out.put("categories", queryRowsAsMaps(c,
                    "SELECT MaDanhMuc, MaDanhMucCha, TenDanhMuc FROM DANHMUC_SP ORDER BY MaDanhMuc"));
            out.put("variants", queryRowsAsMaps(c,
                    "SELECT ct.MaChiTietSP, ct.SKU, sp.MaSanPham, sp.TenSanPham, ct.Size, ct.MauSac, "
                            + "sp.GiaNiemYet, ct.TrangThai AS TrangThaiCTSP, sp.TrangThai AS TrangThaiSP, "
                            + "sp.MaDanhMuc, dm.TenDanhMuc, sp.MaThuongHieu, th.TenThuongHieu "
                            + "FROM CHITIET_SP ct "
                            + "JOIN SANPHAM sp ON ct.MaSanPham = sp.MaSanPham "
                            + "JOIN DANHMUC_SP dm ON sp.MaDanhMuc = dm.MaDanhMuc "
                            + "JOIN THUONGHIEU th ON sp.MaThuongHieu = th.MaThuongHieu "
                            + "ORDER BY ct.MaChiTietSP"));
            out.put("inventory", queryRowsAsMaps(c,
                    "SELECT tk.MaKho, k.TenKho, tk.MaChiTietSP, ct.SKU, sp.TenSanPham, ct.Size, ct.MauSac, "
                            + "tk.SoLuongThucTe, tk.SoLuongDaGiu, "
                            + "fn_tinh_ton_kho_kha_dung(tk.MaKho, tk.MaChiTietSP) AS SoLuongKhaDung "
                            + "FROM TONKHO tk "
                            + "JOIN KHO k ON tk.MaKho = k.MaKho "
                            + "JOIN CHITIET_SP ct ON tk.MaChiTietSP = ct.MaChiTietSP "
                            + "JOIN SANPHAM sp ON ct.MaSanPham = sp.MaSanPham "
                            + "ORDER BY tk.MaKho, tk.MaChiTietSP"));
            out.put("orders", queryRowsAsMaps(c,
                    "SELECT dh.MaDonHang, TO_CHAR(dh.NgayDat, 'YYYY-MM-DD HH24:MI:SS') AS NgayDat, "
                            + "GREATEST(0, (CURRENT_DATE - dh.NgayDat::DATE))::INTEGER AS SoNgayTuNgayDat, "
                            + "dh.TrangThai, dh.MaKH, kh.HoTen AS TenKhachHang, dh.MaKho, dh.PhuongThucThanhToan, "
                            + "fn_tinh_tong_tien_don_hang(dh.MaDonHang) AS TongTien, COALESCE(dh.GhiChu, '') AS GhiChu, "
                            + "COUNT(ct.MaCTDonHang) AS SoMatHang, "
                            + "COALESCE(STRING_AGG(ct.MaChiTietSP || ' ×' || ct.SoLuong || "
                            + "CASE WHEN ct.ChietKhau > 0 THEN ' (-' || TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM ROUND(ct.ChietKhau, 2)::TEXT)) || '%)' ELSE '' END, "
                            + "', ' ORDER BY ct.MaChiTietSP), '') AS ChiTietTomTat "
                            + "FROM DONHANG dh "
                            + "JOIN KHACHHANG kh ON dh.MaKH = kh.MaKH "
                            + "LEFT JOIN CHITIET_DONHANG ct ON dh.MaDonHang = ct.MaDonHang "
                            + "GROUP BY dh.MaDonHang, dh.NgayDat, dh.TrangThai, dh.MaKH, kh.HoTen, dh.MaKho, dh.PhuongThucThanhToan, dh.GhiChu "
                            + "ORDER BY CASE WHEN dh.MaDonHang LIKE 'DH_WEB_%' OR dh.MaDonHang LIKE 'DH_CONC_%' THEN 0 ELSE 1 END, dh.NgayDat DESC, dh.MaDonHang"));
            out.put("transfers", queryRowsAsMaps(c,
                    "SELECT pk.MaPhieu, pk.TrangThai, TO_CHAR(pk.NgayLap, 'YYYY-MM-DD HH24:MI:SS') AS NgayLap, "
                            + "pck.KhoNguon, pck.KhoDich, COALESCE(pk.GhiChu, '') AS GhiChu, "
                            + "COALESCE(SUM(ct.SoLuong), 0) AS TongSoLuong "
                            + "FROM PHIEUKHO pk "
                            + "JOIN PHIEUCHUYENKHO pck ON pk.MaPhieu = pck.MaPhieu "
                            + "LEFT JOIN CHITIET_PHIEUKHO ct ON pk.MaPhieu = ct.MaPhieu "
                            + "GROUP BY pk.MaPhieu, pk.TrangThai, pk.NgayLap, pck.KhoNguon, pck.KhoDich, pk.GhiChu "
                            + "ORDER BY CASE WHEN pk.MaPhieu LIKE 'PK_WEB_%' THEN 0 ELSE 1 END, pk.NgayLap DESC, pk.MaPhieu"));
            out.put("suppliers", queryRowsAsMaps(c,
                    "SELECT MaNCC, TenNCC, SDT FROM NHACUNGCAP ORDER BY MaNCC"));
            out.put("supplierCatalog", queryRowsAsMaps(c,
                    "SELECT MaNCC, MaChiTietSP, GiaNhap, ThoiGianGiaoHangDuKien "
                            + "FROM CUNGCAP ORDER BY MaNCC, MaChiTietSP"));
            out.put("orderDetails", queryRowsAsMaps(c,
                    "SELECT ctdh.MaCTDonHang, ctdh.MaDonHang, ctdh.MaChiTietSP, ct.SKU, sp.MaSanPham, sp.TenSanPham, "
                            + "ct.Size, ct.MauSac, ctdh.SoLuong, ctdh.DonGiaBan, ctdh.ChietKhau, "
                            + "ROUND(ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0), 2) AS DonGiaThucTe, "
                            + "COALESCE(("
                            + "  SELECT SUM(ctth.SoLuongTra) "
                            + "  FROM CHITIET_TRAHANG ctth "
                            + "  JOIN PHIEUTRAHANG pth ON ctth.MaPhieuTra = pth.MaPhieuTra "
                            + "  WHERE ctth.MaCTDonHang = ctdh.MaCTDonHang "
                            + "    AND pth.TrangThai NOT IN ('DA_HUY', 'TU_CHOI') "
                            + "    AND ctth.HinhThucXuLy <> 'TU_CHOI_DONG'"
                            + "), 0) AS DaTra "
                            + "FROM CHITIET_DONHANG ctdh "
                            + "JOIN CHITIET_SP ct ON ctdh.MaChiTietSP = ct.MaChiTietSP "
                            + "JOIN SANPHAM sp ON ct.MaSanPham = sp.MaSanPham "
                            + "ORDER BY ctdh.MaDonHang, ctdh.MaCTDonHang"));
            out.put("returns", queryRowsAsMaps(c,
                    "SELECT pth.MaPhieuTra, pth.MaDonHang, dh.MaKH, kh.HoTen AS TenKhachHang, dh.MaKho, "
                            + "TO_CHAR(pth.NgayTiepNhan, 'YYYY-MM-DD HH24:MI:SS') AS NgayTiepNhan, "
                            + "pth.LyDo, pth.TrangThai, COALESCE(pth.GhiChu, '') AS GhiChu, "
                            + "COALESCE(SUM(ctth.SoLuongTra), 0) AS TongSoLuongTra, "
                            + "COALESCE(SUM(CASE WHEN ctth.HinhThucXuLy = 'HOAN_TIEN' THEN ctth.SoLuongTra * ctth.DonGiaHoan ELSE 0 END), 0) AS TongTienHoan, "
                            + "COALESCE(STRING_AGG("
                            + "  ctdh.MaChiTietSP || ' (' || ctth.HinhThucXuLy || ' ×' || ctth.SoLuongTra "
                            + "  || CASE WHEN ctth.MaChiTietSPDoi IS NOT NULL THEN ' → ' || ctth.MaChiTietSPDoi ELSE '' END || ')', "
                            + "  ', ' ORDER BY ctth.MaCTTraHang), '') AS ChiTietTomTat "
                            + "FROM PHIEUTRAHANG pth "
                            + "JOIN DONHANG dh ON pth.MaDonHang = dh.MaDonHang "
                            + "JOIN KHACHHANG kh ON dh.MaKH = kh.MaKH "
                            + "LEFT JOIN CHITIET_TRAHANG ctth ON pth.MaPhieuTra = ctth.MaPhieuTra "
                            + "LEFT JOIN CHITIET_DONHANG ctdh ON ctth.MaCTDonHang = ctdh.MaCTDonHang "
                            + "GROUP BY pth.MaPhieuTra, pth.MaDonHang, dh.MaKH, kh.HoTen, dh.MaKho, "
                            + "  pth.NgayTiepNhan, pth.LyDo, pth.TrangThai, pth.GhiChu "
                            + "ORDER BY CASE WHEN pth.MaPhieuTra LIKE 'PTH_WEB_%' THEN 0 ELSE 1 END, pth.NgayTiepNhan DESC, pth.MaPhieuTra"));
            out.put("receives", queryRowsAsMaps(c,
                    "SELECT pk.MaPhieu, pk.TrangThai, TO_CHAR(pk.NgayLap, 'YYYY-MM-DD HH24:MI:SS') AS NgayLap, "
                            + "pnk.KhoDich, pnk.MaNCC, COALESCE(ncc.TenNCC, '') AS TenNCC, "
                            + "COALESCE(pnk.MaPhieuTra, '') AS MaPhieuTra, COALESCE(pk.GhiChu, '') AS GhiChu, "
                            + "COALESCE(SUM(ct.SoLuong), 0) AS TongSoLuong, "
                            + "COALESCE(SUM(ct.SoLuong * COALESCE(ct.DonGia, 0)), 0) AS TongGiaTriNhap, "
                            + "COALESCE(STRING_AGG(ct.MaChiTietSP || ' ×' || ct.SoLuong, ', ' ORDER BY ct.MaChiTietSP), '') AS ChiTietTomTat "
                            + "FROM PHIEUKHO pk "
                            + "JOIN PHIEUNHAPKHO pnk ON pk.MaPhieu = pnk.MaPhieu "
                            + "LEFT JOIN NHACUNGCAP ncc ON pnk.MaNCC = ncc.MaNCC "
                            + "LEFT JOIN CHITIET_PHIEUKHO ct ON pk.MaPhieu = ct.MaPhieu "
                            + "GROUP BY pk.MaPhieu, pk.TrangThai, pk.NgayLap, pnk.KhoDich, pnk.MaNCC, ncc.TenNCC, pnk.MaPhieuTra, pk.GhiChu "
                            + "ORDER BY CASE WHEN pk.MaPhieu LIKE 'PK_WEB_%' THEN 0 ELSE 1 END, pk.NgayLap DESC, pk.MaPhieu"));
            return out;
        }
    }

    private Object handleCreateOrder(HttpExchange ex) throws Exception {
        Map<String, Object> body = readJsonBody(ex);
        String maKh = JsonHelper.asString(body, "maKh", "KH01");
        String maKho = JsonHelper.asString(body, "maKho", "KHO01");
        String pttt = JsonHelper.asString(body, "pttt", "CHUYEN_KHOAN");
        String diaChi = JsonHelper.asString(body, "diaChi", "25 Lê Lợi, Q.1, TP.HCM");
        String ghiChu = JsonHelper.asString(body, "ghiChu", "Tao tu Web Demo");

        List<Map<String, Object>> items = extractOrderItems(body);
        String maDonHang = nextOrderId();
        String itemJson = JsonHelper.toJson(items);

        try (Connection c = openConnection()) {
            Map<String, Integer> beforeByCtsp = new LinkedHashMap<>();
            for (Map<String, Object> it : items) {
                String ctspId = String.valueOf(it.get("ma_ctsp"));
                beforeByCtsp.putIfAbsent(ctspId, getAvailableStock(c, maKho, ctspId));
            }
            int firstBefore = beforeByCtsp.values().iterator().next();

            c.setAutoCommit(false);
            try {
                callCreateOrder(c, maDonHang, maKh, maKho, diaChi, pttt, itemJson, ghiChu);
                c.commit();
                c.setAutoCommit(true);

                List<Map<String, Object>> itemChanges = buildStockChanges(c, maKho, beforeByCtsp);
                int firstAfter = (Integer) itemChanges.get(0).get("afterAvail");
                BigDecimal tongTien = queryBigDecimal(c, "SELECT fn_tinh_tong_tien_don_hang(?)", maDonHang);

                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", true);
                res.put("maDonHang", maDonHang);
                res.put("tongTien", tongTien);
                res.put("itemCount", items.size());
                res.put("beforeAvail", firstBefore);
                res.put("afterAvail", firstAfter);
                res.put("itemChanges", itemChanges);
                res.put("baseline", fetchBaseline(c));
                return res;
            } catch (SQLException sqlEx) {
                quietRollback(c);
                c.setAutoCommit(true);
                List<Map<String, Object>> itemChanges = buildStockChanges(c, maKho, beforeByCtsp);
                int firstAfter = (Integer) itemChanges.get(0).get("afterAvail");
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", false);
                res.put("blockedByDb", true);
                res.put("rolledBack", true);
                res.put("maDonHang", maDonHang);
                res.put("sqlState", sqlEx.getSQLState());
                res.put("errorMessage", firstLine(sqlEx.getMessage()));
                res.put("itemCount", items.size());
                res.put("beforeAvail", firstBefore);
                res.put("afterAvail", firstAfter);
                res.put("itemChanges", itemChanges);
                res.put("baseline", fetchBaseline(c));
                return res;
            }
        }
    }

    private List<Map<String, Object>> extractOrderItems(Map<String, Object> body) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        Object rawItems = body.get("items");
        if (rawItems instanceof List<?> list && !list.isEmpty()) {
            for (Object el : list) {
                if (el instanceof Map<?, ?> rawMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) rawMap;
                    String maCtsp = JsonHelper.asString(m, "maCtsp", JsonHelper.asString(m, "ma_ctsp", ""));
                    int soLuong = JsonHelper.asInt(m, "soLuong", JsonHelper.asInt(m, "so_luong", 1));
                    double chietKhau = JsonHelper.asDouble(m, "chietKhau", JsonHelper.asDouble(m, "chiet_khau", 0.0));
                    if (!maCtsp.isEmpty()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("ma_ctsp", maCtsp);
                        row.put("so_luong", soLuong);
                        row.put("chiet_khau", chietKhau);
                        normalized.add(row);
                    }
                }
            }
        }
        if (normalized.isEmpty()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ma_ctsp", JsonHelper.asString(body, "maCtsp", "CTSP02"));
            row.put("so_luong", JsonHelper.asInt(body, "soLuong", 1));
            row.put("chiet_khau", JsonHelper.asDouble(body, "chietKhau", 0.0));
            normalized.add(row);
        }
        return normalized;
    }

    private List<Map<String, Object>> buildStockChanges(
            Connection c, String maKho, Map<String, Integer> beforeByCtsp) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>(beforeByCtsp.size());
        for (Map.Entry<String, Integer> e : beforeByCtsp.entrySet()) {
            String ctspId = e.getKey();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("maCtsp", ctspId);
            row.put("beforeAvail", e.getValue());
            row.put("afterAvail", getAvailableStock(c, maKho, ctspId));
            list.add(row);
        }
        return list;
    }

    private Object handleCompleteOrder(HttpExchange ex) throws Exception {
        Map<String, Object> body = readJsonBody(ex);
        String maDonHang = JsonHelper.asString(body, "maDonHang", "");
        if (maDonHang.isEmpty()) {
            return Map.of("ok", false, "blockedByDb", true, "errorMessage", "Thieu MaDonHang.");
        }
        if (!maDonHang.startsWith("DH_WEB_")) {
            return Map.of("ok", false, "blockedByDb", true,
                    "errorMessage", "Chi cho phep xac nhan hoan tat don DH_WEB_% tao tu Web Demo de giu nguyen du lieu mau.");
        }

        try (Connection c = openConnection()) {
            c.setAutoCommit(false);
            String maKho = null;
            String trangThai = null;
            Map<String, Integer> beforeByCtsp = new LinkedHashMap<>();
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT MaKho, TrangThai FROM DONHANG WHERE MaDonHang = ? FOR UPDATE")) {
                    ps.setString(1, maDonHang);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            maKho = rs.getString(1);
                            trangThai = rs.getString(2);
                        }
                    }
                }
                if (trangThai == null) {
                    throw new SQLException("Don hang " + maDonHang + " khong ton tai.", "P0001");
                }
                if ("HOAN_TAT".equals(trangThai) || "DA_HUY".equals(trangThai)) {
                    throw new SQLException("Don hang " + maDonHang + " dang o trang thai " + trangThai + ", khong the hoan tat lai.", "P0001");
                }

                List<Object[]> lines = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT MaChiTietSP, SoLuong, ROUND(DonGiaBan * (1 - ChietKhau / 100.0), 2) AS DonGiaThucTe "
                                + "FROM CHITIET_DONHANG WHERE MaDonHang = ? ORDER BY MaChiTietSP")) {
                    ps.setString(1, maDonHang);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String ctsp = rs.getString(1);
                            int sl = rs.getInt(2);
                            BigDecimal dg = rs.getBigDecimal(3);
                            lines.add(new Object[]{ctsp, sl, dg});
                            beforeByCtsp.put(ctsp, getAvailableStock(c, maKho, ctsp));
                        }
                    }
                }

                String maPhieuXuat = nextTransferId();
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO PHIEUKHO (MaPhieu, LoaiPhieu, NgayLap, TrangThai, GhiChu) "
                                + "VALUES (?, 'XUAT', CURRENT_TIMESTAMP, 'HOAN_TAT', ?)")) {
                    ps.setString(1, maPhieuXuat);
                    ps.setString(2, "Xuat kho giao hoan tat don " + maDonHang + " tu Web Demo");
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO PHIEUXUATKHO (MaPhieu, MaDonHang, MaPhieuTra, KhoNguon) VALUES (?, ?, NULL, ?)")) {
                    ps.setString(1, maPhieuXuat);
                    ps.setString(2, maDonHang);
                    ps.setString(3, maKho);
                    ps.executeUpdate();
                }

                for (Object[] line : lines) {
                    String ctsp = (String) line[0];
                    int sl = (Integer) line[1];
                    BigDecimal dg = (BigDecimal) line[2];

                    int thucTe = 0;
                    int daGiu = 0;
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT SoLuongThucTe, SoLuongDaGiu FROM TONKHO WHERE MaKho = ? AND MaChiTietSP = ? FOR UPDATE")) {
                        ps.setString(1, maKho);
                        ps.setString(2, ctsp);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                thucTe = rs.getInt(1);
                                daGiu = rs.getInt(2);
                            }
                        }
                    }
                    if (thucTe < sl || daGiu < sl) {
                        throw new SQLException("Ton kho " + maKho + "/" + ctsp + " khong du de xuat giao (ThucTe=" + thucTe + ", DaGiu=" + daGiu + ").", "P0001");
                    }

                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE TONKHO SET SoLuongThucTe = SoLuongThucTe - ?, SoLuongDaGiu = SoLuongDaGiu - ?, NgayCapNhat = CURRENT_TIMESTAMP "
                                    + "WHERE MaKho = ? AND MaChiTietSP = ?")) {
                        ps.setInt(1, sl);
                        ps.setInt(2, sl);
                        ps.setString(3, maKho);
                        ps.setString(4, ctsp);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO CHITIET_PHIEUKHO (MaCTPhieuKho, MaPhieu, MaChiTietSP, SoLuong, DonGia) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, nextDetailId("CTPK_W_"));
                        ps.setString(2, maPhieuXuat);
                        ps.setString(3, ctsp);
                        ps.setInt(4, sl);
                        ps.setBigDecimal(5, dg);
                        ps.executeUpdate();
                    }
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE DONHANG SET TrangThai = 'HOAN_TAT' WHERE MaDonHang = ?")) {
                    ps.setString(1, maDonHang);
                    ps.executeUpdate();
                }

                c.commit();
                c.setAutoCommit(true);

                List<Map<String, Object>> itemChanges = buildStockChanges(c, maKho, beforeByCtsp);
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", true);
                res.put("maDonHang", maDonHang);
                res.put("maKho", maKho);
                res.put("maPhieuXuat", maPhieuXuat);
                res.put("itemChanges", itemChanges);
                res.put("baseline", fetchBaseline(c));
                return res;
            } catch (SQLException sqlEx) {
                quietRollback(c);
                c.setAutoCommit(true);
                return Map.of(
                        "ok", false,
                        "blockedByDb", true,
                        "rolledBack", true,
                        "maDonHang", maDonHang,
                        "sqlState", sqlEx.getSQLState() == null ? "P0001" : sqlEx.getSQLState(),
                        "errorMessage", firstLine(sqlEx.getMessage()),
                        "baseline", fetchBaseline(c)
                );
            }
        }
    }

    private Object handleCancelOrder(HttpExchange ex) throws Exception {
        Map<String, Object> body = readJsonBody(ex);
        String maDonHang = JsonHelper.asString(body, "maDonHang", "");
        String lyDo = JsonHelper.asString(body, "lyDo", "Huy tu Web Demo");
        if (maDonHang.isEmpty()) {
            return Map.of("ok", false, "errorMessage", "Thieu MaDonHang");
        }

        try (Connection c = openConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("CALL sp_huy_don_hang(?, ?)")) {
                ps.setString(1, maDonHang);
                ps.setString(2, lyDo);
                ps.execute();
                c.commit();
                c.setAutoCommit(true);
                return Map.of("ok", true, "maDonHang", maDonHang, "baseline", fetchBaseline(c));
            } catch (SQLException sqlEx) {
                quietRollback(c);
                c.setAutoCommit(true);
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", false);
                res.put("blockedByDb", true);
                res.put("maDonHang", maDonHang);
                res.put("sqlState", sqlEx.getSQLState());
                res.put("errorMessage", firstLine(sqlEx.getMessage()));
                res.put("baseline", fetchBaseline(c));
                return res;
            }
        }
    }

    private Object handleTransferStock(HttpExchange ex) throws Exception {
        Map<String, Object> body = readJsonBody(ex);
        String khoNguon = JsonHelper.asString(body, "khoNguon", "KHO01");
        String khoDich = JsonHelper.asString(body, "khoDich", "KHO02");
        String maCtsp = JsonHelper.asString(body, "maCtsp", "CTSP02");
        int soLuong = JsonHelper.asInt(body, "soLuong", 2);
        boolean hoanTat = JsonHelper.asBoolean(body, "hoanTat", false);
        String ghiChu = JsonHelper.asString(body, "ghiChu", "Chuyen kho tu Web Demo");

        String maPhieu = nextTransferId();
        String itemJson = JsonHelper.toJson(List.of(Map.of(
                "ma_ctsp", maCtsp,
                "so_luong", soLuong
        )));

        try (Connection c = openConnection()) {
            int beforeSrc = getAvailableStock(c, khoNguon, maCtsp);
            int beforeDst = getAvailableStock(c, khoDich, maCtsp);
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("CALL sp_chuyen_kho(?, ?, ?, ?::jsonb, ?, ?)")) {
                ps.setString(1, maPhieu);
                ps.setString(2, khoNguon);
                ps.setString(3, khoDich);
                ps.setString(4, itemJson);
                ps.setBoolean(5, hoanTat);
                ps.setString(6, ghiChu);
                ps.execute();
                c.commit();
                c.setAutoCommit(true);

                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", true);
                res.put("maPhieu", maPhieu);
                res.put("hoanTat", hoanTat);
                res.put("beforeSrc", beforeSrc);
                res.put("afterSrc", getAvailableStock(c, khoNguon, maCtsp));
                res.put("beforeDst", beforeDst);
                res.put("afterDst", getAvailableStock(c, khoDich, maCtsp));
                res.put("baseline", fetchBaseline(c));
                return res;
            } catch (SQLException sqlEx) {
                quietRollback(c);
                c.setAutoCommit(true);
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", false);
                res.put("blockedByDb", true);
                res.put("sqlState", sqlEx.getSQLState());
                res.put("errorMessage", firstLine(sqlEx.getMessage()));
                res.put("baseline", fetchBaseline(c));
                return res;
            }
        }
    }

    private Object handleConfirmTransfer(HttpExchange ex) throws Exception {
        Map<String, Object> body = readJsonBody(ex);
        String maPhieu = JsonHelper.asString(body, "maPhieu", "");
        if (!maPhieu.startsWith("PK_WEB_")) {
            return Map.of("ok", false, "blockedByDb", true,
                    "errorMessage", "Chi cho phep xac nhan phieu PK_WEB_% tao tu Web Demo de giu nguyen du lieu mau PK06.");
        }

        try (Connection c = openConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("CALL sp_xac_nhan_nhan_chuyen_kho(?)")) {
                ps.setString(1, maPhieu);
                ps.execute();
                c.commit();
                c.setAutoCommit(true);
                return Map.of("ok", true, "maPhieu", maPhieu, "baseline", fetchBaseline(c));
            } catch (SQLException sqlEx) {
                quietRollback(c);
                c.setAutoCommit(true);
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", false);
                res.put("blockedByDb", true);
                res.put("sqlState", sqlEx.getSQLState());
                res.put("errorMessage", firstLine(sqlEx.getMessage()));
                return res;
            }
        }
    }

    private Object handleReceiveStock(HttpExchange ex) throws Exception {
        Map<String, Object> body = readJsonBody(ex);
        String maNcc = JsonHelper.asString(body, "maNcc", "NCC01");
        String khoDich = JsonHelper.asString(body, "khoDich", "KHO01");
        String maCtsp = JsonHelper.asString(body, "maCtsp", "CTSP01");
        int soLuong = JsonHelper.asInt(body, "soLuong", 5);
        double donGia = JsonHelper.asDouble(body, "donGia", 0.0);
        String ghiChu = JsonHelper.asString(body, "ghiChu", "Nhap hang tu NCC qua Web Demo");

        if (soLuong <= 0) {
            return Map.of("ok", false, "blockedByDb", true, "errorMessage", "So luong nhan hang phai lon hon 0.");
        }
        if (donGia < 0) {
            return Map.of("ok", false, "blockedByDb", true, "errorMessage", "Don gia nhap khong duoc am.");
        }

        String maPhieu = nextTransferId();
        String maCtpk = nextDetailId("CTPK_W_");
        BigDecimal donGiaBd = BigDecimal.valueOf(donGia);

        try (Connection c = openConnection()) {
            int beforeAvail = getAvailableStock(c, khoDich, maCtsp);
            c.setAutoCommit(false);
            try {
                String ttKho = null;
                try (PreparedStatement ps = c.prepareStatement("SELECT TrangThai FROM KHO WHERE MaKho = ?")) {
                    ps.setString(1, khoDich);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            ttKho = rs.getString(1);
                        }
                    }
                }
                if (ttKho == null) {
                    throw new SQLException("Kho dich " + khoDich + " khong ton tai.", "P0001");
                }
                if (!"HOAT_DONG".equals(ttKho)) {
                    throw new SQLException("Kho dich " + khoDich + " dang o trang thai " + ttKho + ", khong the nhan hang.", "P0001");
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT SoLuongThucTe, SoLuongDaGiu FROM TONKHO WHERE MaKho = ? AND MaChiTietSP = ? FOR UPDATE")) {
                    ps.setString(1, khoDich);
                    ps.setString(2, maCtsp);
                    ps.executeQuery();
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO PHIEUKHO (MaPhieu, LoaiPhieu, NgayLap, TrangThai, GhiChu) "
                                + "VALUES (?, 'NHAP', CURRENT_TIMESTAMP, 'HOAN_TAT', ?)")) {
                    ps.setString(1, maPhieu);
                    ps.setString(2, ghiChu);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO PHIEUNHAPKHO (MaPhieu, MaNCC, MaPhieuTra, KhoDich) VALUES (?, ?, NULL, ?)")) {
                    ps.setString(1, maPhieu);
                    ps.setString(2, maNcc);
                    ps.setString(3, khoDich);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO CHITIET_PHIEUKHO (MaCTPhieuKho, MaPhieu, MaChiTietSP, SoLuong, DonGia) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, maCtpk);
                    ps.setString(2, maPhieu);
                    ps.setString(3, maCtsp);
                    ps.setInt(4, soLuong);
                    ps.setBigDecimal(5, donGiaBd);
                    ps.executeUpdate();
                }

                upsertAddStock(c, khoDich, maCtsp, soLuong);
                c.commit();
                c.setAutoCommit(true);

                int afterAvail = getAvailableStock(c, khoDich, maCtsp);
                BigDecimal tongGiaTri = donGiaBd.multiply(BigDecimal.valueOf(soLuong));
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", true);
                res.put("maPhieu", maPhieu);
                res.put("maNcc", maNcc);
                res.put("khoDich", khoDich);
                res.put("maCtsp", maCtsp);
                res.put("soLuong", soLuong);
                res.put("tongGiaTri", tongGiaTri);
                res.put("beforeAvail", beforeAvail);
                res.put("afterAvail", afterAvail);
                res.put("baseline", fetchBaseline(c));
                return res;
            } catch (SQLException sqlEx) {
                quietRollback(c);
                c.setAutoCommit(true);
                int afterAvail = getAvailableStock(c, khoDich, maCtsp);
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", false);
                res.put("blockedByDb", true);
                res.put("rolledBack", true);
                res.put("maPhieu", maPhieu);
                res.put("sqlState", sqlEx.getSQLState());
                res.put("errorMessage", firstLine(sqlEx.getMessage()));
                res.put("beforeAvail", beforeAvail);
                res.put("afterAvail", afterAvail);
                res.put("baseline", fetchBaseline(c));
                return res;
            }
        }
    }

    private Object handleCreateReturn(HttpExchange ex) throws Exception {
        Map<String, Object> body = readJsonBody(ex);
        String maDonHang = JsonHelper.asString(body, "maDonHang", "");
        String maCtDonHang = JsonHelper.asString(body, "maCtDonHang", "");
        int soLuongTra = JsonHelper.asInt(body, "soLuongTra", 1);
        String tinhTrang = JsonHelper.asString(body, "tinhTrangSanPham", "CON_MOI");
        String hinhThuc = JsonHelper.asString(body, "hinhThucXuLy", "HOAN_TIEN");
        String maCtspDoi = blankToNull(JsonHelper.asString(body, "maChiTietSPDoi", ""));
        String lyDo = JsonHelper.asString(body, "lyDo", "Khach tra/doi hang tu Web Demo");

        if (maDonHang.isEmpty() || maCtDonHang.isEmpty()) {
            return Map.of("ok", false, "blockedByDb", true, "errorMessage", "Vui long chon don hang va dong CTSP can tra.");
        }
        if (soLuongTra <= 0) {
            return Map.of("ok", false, "blockedByDb", true, "errorMessage", "So luong tra phai lon hon 0.");
        }
        if ("DOI_HANG".equals(hinhThuc) && maCtspDoi == null) {
            return Map.of("ok", false, "blockedByDb", true, "errorMessage", "Vui long chon bien the CTSP muon doi.");
        }
        if (!"DOI_HANG".equals(hinhThuc)) {
            maCtspDoi = null;
        }

        String maPhieuTra = nextReturnId();
        String maCtTra = nextDetailId("CTTH_W_");

        try (Connection c = openConnection()) {
            String maKho = "KHO01";
            String ttDonHang = null;
            int soNgay = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT MaKho, TrangThai, GREATEST(0, (CURRENT_DATE - NgayDat::DATE))::INTEGER AS SoNgay "
                            + "FROM DONHANG WHERE MaDonHang = ?")) {
                ps.setString(1, maDonHang);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        maKho = rs.getString(1);
                        ttDonHang = rs.getString(2);
                        soNgay = rs.getInt(3);
                    }
                }
            }

            String maCtspGoc = null;
            BigDecimal donGiaThucTe = BigDecimal.ZERO;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT MaChiTietSP, ROUND(DonGiaBan * (1 - ChietKhau / 100.0), 2) AS DonGiaThucTe "
                            + "FROM CHITIET_DONHANG WHERE MaCTDonHang = ?")) {
                ps.setString(1, maCtDonHang);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        maCtspGoc = rs.getString(1);
                        donGiaThucTe = rs.getBigDecimal(2);
                    }
                }
            }

            Map<String, Integer> beforeByCtsp = new LinkedHashMap<>();
            if ("CON_MOI".equals(tinhTrang) && maCtspGoc != null) {
                beforeByCtsp.put(maCtspGoc, getAvailableStock(c, maKho, maCtspGoc));
            }
            if ("DOI_HANG".equals(hinhThuc) && maCtspDoi != null) {
                beforeByCtsp.putIfAbsent(maCtspDoi, getAvailableStock(c, maKho, maCtspDoi));
            }

            int refundRatePercent = "DOI_HANG".equals(hinhThuc)
                    ? 0
                    : ("DA_SU_DUNG".equals(tinhTrang) ? 50 : 100);
            BigDecimal donGiaHoan;
            if ("DOI_HANG".equals(hinhThuc)) {
                donGiaHoan = BigDecimal.ZERO;
            } else if ("DA_SU_DUNG".equals(tinhTrang)) {
                donGiaHoan = donGiaThucTe.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP);
            } else {
                donGiaHoan = donGiaThucTe;
            }

            c.setAutoCommit(false);
            try {
                // Nghiep vu 1: Don hang HOAN_TAT qua 14 ngay khong duoc hoan/tra
                if ("HOAN_TAT".equals(ttDonHang) && soNgay > 14) {
                    throw new SQLException(
                            "Don hang " + maDonHang + " da qua " + soNgay
                                    + " ngay (> 14 ngay ke tu ngay dat), khong duoc hoan/tra hang.",
                            "P0001");
                }

                // Nghiep vu 2: CTSP da qua su dung khong duoc doi ngang (chi hoan tien 50%)
                if ("DA_SU_DUNG".equals(tinhTrang) && "DOI_HANG".equals(hinhThuc)) {
                    throw new SQLException(
                            "CTSP da qua su dung (DA_SU_DUNG) khong ho tro doi hang (DOI_HANG), chi ap dung hoan tien 50%.",
                            "P0001");
                }

                // Khoa cac dong TONKHO theo thu tu tang dan MaChiTietSP truoc khi cap nhat
                List<String> lockTargets = new ArrayList<>(beforeByCtsp.keySet());
                Collections.sort(lockTargets);
                for (String ctspLock : lockTargets) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT SoLuongThucTe, SoLuongDaGiu FROM TONKHO WHERE MaKho = ? AND MaChiTietSP = ? FOR UPDATE")) {
                        ps.setString(1, maKho);
                        ps.setString(2, ctspLock);
                        ps.executeQuery();
                    }
                }

                // 1. Tao PHIEUTRAHANG (Trigger 7 kiem tra DONHANG phai HOAN_TAT)
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO PHIEUTRAHANG (MaPhieuTra, MaDonHang, NgayTiepNhan, LyDo, TrangThai, GhiChu) "
                                + "VALUES (?, ?, CURRENT_TIMESTAMP, ?, 'HOAN_TAT', 'Lap tu Web Demo')")) {
                    ps.setString(1, maPhieuTra);
                    ps.setString(2, maDonHang);
                    ps.setString(3, lyDo);
                    ps.executeUpdate();
                }

                // 2. Tao CHITIET_TRAHANG (Trigger 7 kiem tra dung don, khong tra lo, doi cung SP)
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO CHITIET_TRAHANG (MaCTTraHang, MaPhieuTra, MaCTDonHang, SoLuongTra, "
                                + "TinhTrangSanPham, HinhThucXuLy, DonGiaHoan, MaChiTietSPDoi) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, maCtTra);
                    ps.setString(2, maPhieuTra);
                    ps.setString(3, maCtDonHang);
                    ps.setInt(4, soLuongTra);
                    ps.setString(5, tinhTrang);
                    ps.setString(6, hinhThuc);
                    ps.setBigDecimal(7, donGiaHoan);
                    setNullableString(ps, 8, maCtspDoi);
                    ps.executeUpdate();
                }

                String maPhieuNhap = null;
                String maPhieuXuat = null;

                // 3. Neu hang CON_MOI -> nhap lai kho ban qua PHIEUKHO + PHIEUNHAPKHO
                if ("CON_MOI".equals(tinhTrang) && maCtspGoc != null) {
                    maPhieuNhap = nextTransferId();
                    String maCtpkNhap = nextDetailId("CTPK_W_");
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO PHIEUKHO (MaPhieu, LoaiPhieu, NgayLap, TrangThai, GhiChu) "
                                    + "VALUES (?, 'NHAP', CURRENT_TIMESTAMP, 'HOAN_TAT', ?)")) {
                        ps.setString(1, maPhieuNhap);
                        ps.setString(2, "Nhap kho hang tra tu phieu " + maPhieuTra);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO PHIEUNHAPKHO (MaPhieu, MaNCC, MaPhieuTra, KhoDich) VALUES (?, NULL, ?, ?)")) {
                        ps.setString(1, maPhieuNhap);
                        ps.setString(2, maPhieuTra);
                        ps.setString(3, maKho);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO CHITIET_PHIEUKHO (MaCTPhieuKho, MaPhieu, MaChiTietSP, SoLuong, DonGia) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, maCtpkNhap);
                        ps.setString(2, maPhieuNhap);
                        ps.setString(3, maCtspGoc);
                        ps.setInt(4, soLuongTra);
                        ps.setBigDecimal(5, donGiaThucTe);
                        ps.executeUpdate();
                    }
                    upsertAddStock(c, maKho, maCtspGoc, soLuongTra);
                }

                // 4. Neu DOI_HANG -> kiem tra ton kha dung bien the doi va xuat doi qua PHIEUXUATKHO
                if ("DOI_HANG".equals(hinhThuc) && maCtspDoi != null) {
                    int khaDungDoi = 0;
                    boolean hasRow = false;
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT (SoLuongThucTe - SoLuongDaGiu) FROM TONKHO WHERE MaKho = ? AND MaChiTietSP = ? FOR UPDATE")) {
                        ps.setString(1, maKho);
                        ps.setString(2, maCtspDoi);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                hasRow = true;
                                khaDungDoi = rs.getInt(1);
                            }
                        }
                    }
                    if (!hasRow || khaDungDoi < soLuongTra) {
                        throw new SQLException(
                                "Kho " + maKho + " khong du ton kha dung cho bien the doi " + maCtspDoi
                                        + " (can " + soLuongTra + ", con " + khaDungDoi + ").",
                                "P0001");
                    }

                    maPhieuXuat = nextTransferId();
                    String maCtpkXuat = nextDetailId("CTPK_W_");
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO PHIEUKHO (MaPhieu, LoaiPhieu, NgayLap, TrangThai, GhiChu) "
                                    + "VALUES (?, 'XUAT', CURRENT_TIMESTAMP, 'HOAN_TAT', ?)")) {
                        ps.setString(1, maPhieuXuat);
                        ps.setString(2, "Xuat kho doi hang cho phieu " + maPhieuTra);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO PHIEUXUATKHO (MaPhieu, MaDonHang, MaPhieuTra, KhoNguon) VALUES (?, NULL, ?, ?)")) {
                        ps.setString(1, maPhieuXuat);
                        ps.setString(2, maPhieuTra);
                        ps.setString(3, maKho);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO CHITIET_PHIEUKHO (MaCTPhieuKho, MaPhieu, MaChiTietSP, SoLuong, DonGia) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, maCtpkXuat);
                        ps.setString(2, maPhieuXuat);
                        ps.setString(3, maCtspDoi);
                        ps.setInt(4, soLuongTra);
                        ps.setBigDecimal(5, donGiaThucTe);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE TONKHO SET SoLuongThucTe = SoLuongThucTe - ?, NgayCapNhat = CURRENT_TIMESTAMP "
                                    + "WHERE MaKho = ? AND MaChiTietSP = ?")) {
                        ps.setInt(1, soLuongTra);
                        ps.setString(2, maKho);
                        ps.setString(3, maCtspDoi);
                        ps.executeUpdate();
                    }
                }

                c.commit();
                c.setAutoCommit(true);

                List<Map<String, Object>> itemChanges = buildStockChanges(c, maKho, beforeByCtsp);
                BigDecimal tongTienHoan = donGiaHoan.multiply(BigDecimal.valueOf(soLuongTra));
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", true);
                res.put("maPhieuTra", maPhieuTra);
                res.put("maDonHang", maDonHang);
                res.put("maKho", maKho);
                res.put("maCtspGoc", maCtspGoc);
                res.put("maCtspDoi", maCtspDoi);
                res.put("hinhThucXuLy", hinhThuc);
                res.put("tinhTrangSanPham", tinhTrang);
                res.put("soLuongTra", soLuongTra);
                res.put("refundRatePercent", refundRatePercent);
                res.put("soNgay", soNgay);
                res.put("donGiaHoan", donGiaHoan);
                res.put("tongTienHoan", tongTienHoan);
                res.put("maPhieuNhap", maPhieuNhap);
                res.put("maPhieuXuat", maPhieuXuat);
                res.put("itemChanges", itemChanges);
                res.put("baseline", fetchBaseline(c));
                return res;
            } catch (SQLException sqlEx) {
                quietRollback(c);
                c.setAutoCommit(true);
                List<Map<String, Object>> itemChanges = buildStockChanges(c, maKho, beforeByCtsp);
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", false);
                res.put("blockedByDb", true);
                res.put("rolledBack", true);
                res.put("maPhieuTra", maPhieuTra);
                res.put("maKho", maKho);
                res.put("sqlState", sqlEx.getSQLState());
                res.put("errorMessage", firstLine(sqlEx.getMessage()));
                res.put("itemChanges", itemChanges);
                res.put("baseline", fetchBaseline(c));
                return res;
            }
        }
    }

    private void upsertAddStock(Connection c, String maKho, String maCtsp, int soLuong) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO TONKHO (MaKho, MaChiTietSP, SoLuongThucTe, SoLuongDaGiu, NgayCapNhat) "
                        + "VALUES (?, ?, ?, 0, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (MaKho, MaChiTietSP) DO UPDATE "
                        + "SET SoLuongThucTe = TONKHO.SoLuongThucTe + EXCLUDED.SoLuongThucTe, "
                        + "NgayCapNhat = CURRENT_TIMESTAMP")) {
            ps.setString(1, maKho);
            ps.setString(2, maCtsp);
            ps.setInt(3, soLuong);
            ps.executeUpdate();
        }
    }

    private Object handleConcurrencyDemo(HttpExchange ex) throws Exception {
        Map<String, Object> body = readJsonBody(ex);
        int holdMs = Math.max(200, Math.min(4000, JsonHelper.asInt(body, "holdMs", 1200)));

        synchronized (CONCURRENCY_LOCK) {
            try (Connection resetConn = openConnection()) {
                cleanupWebAndDemoArtifacts(resetConn);
            }

            List<Map<String, Object>> timeline = new ArrayList<>();
            long t0 = System.nanoTime();
            CountDownLatch threadALocked = new CountDownLatch(1);
            AtomicReference<String> threadBError = new AtomicReference<>();
            AtomicReference<String> threadBSqlState = new AtomicReference<>();
            AtomicLong threadBWaitMs = new AtomicLong(0);
            AtomicReference<Throwable> unexpected = new AtomicReference<>();

            Thread threadA = new Thread(() -> {
                try (Connection connA = openConnection()) {
                    connA.setAutoCommit(false);
                    connA.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                    addTimeline(timeline, t0, "Thread-A (KH01)", "START",
                            "Mo giao dich READ COMMITTED, dat mua 2 doi cuoi (KHO01, CTSP18) -> DH_CONC_A");
                    callCreateOrder(connA, "DH_CONC_A", "KH01", "KHO01", "25 Le Loi, Q1", "CHUYEN_KHOAN",
                            "[{\"ma_ctsp\":\"CTSP18\",\"so_luong\":2,\"chiet_khau\":0}]",
                            "Web Demo Concurrency Thread-A");
                    addTimeline(timeline, t0, "Thread-A (KH01)", "LOCKED",
                            "Da giu Row Lock FOR UPDATE tren TONKHO(KHO01, CTSP18). Tam giu khoa " + holdMs + " ms...");
                    threadALocked.countDown();

                    Thread.sleep(holdMs);
                    connA.commit();
                    addTimeline(timeline, t0, "Thread-A (KH01)", "COMMIT",
                            "Da COMMIT DH_CONC_A thanh cong! Giai phong row lock (KhaDung = 0).");
                } catch (Throwable t) {
                    threadALocked.countDown();
                    unexpected.compareAndSet(null, t);
                }
            }, "WebConc-A");

            Thread threadB = new Thread(() -> {
                try (Connection connB = openConnection()) {
                    connB.setAutoCommit(false);
                    connB.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                    if (!threadALocked.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timeout cho Thread-A lay lock");
                    }
                    Thread.sleep(100);

                    long bStart = System.nanoTime();
                    addTimeline(timeline, t0, "Thread-B (KH02)", "WAIT",
                            "Bat dau giao dich, co dat mua 1 doi CTSP18 -> dang bi BLOCK cho Thread-A nha khoa...");
                    try {
                        callCreateOrder(connB, "DH_CONC_B", "KH02", "KHO01", "14 Tran Duy Hung, HN", "COD",
                                "[{\"ma_ctsp\":\"CTSP18\",\"so_luong\":1,\"chiet_khau\":0}]",
                                "Web Demo Concurrency Thread-B");
                        connB.commit();
                        addTimeline(timeline, t0, "Thread-B (KH02)", "UNEXPECTED", "Khong nen thanh cong!");
                    } catch (SQLException sqlEx) {
                        long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - bStart);
                        threadBWaitMs.set(waited);
                        threadBError.set(firstLine(sqlEx.getMessage()));
                        threadBSqlState.set(sqlEx.getSQLState());
                        connB.rollback();
                        addTimeline(timeline, t0, "Thread-B (KH02)", "BLOCKED_ROLLBACK",
                                "Bi block " + waited + " ms -> tinh day doc duoc KhaDung=0 -> PostgreSQL chan ["
                                        + firstLine(sqlEx.getMessage()) + "] & da ROLLBACK!");
                    }
                } catch (Throwable t) {
                    unexpected.compareAndSet(null, t);
                }
            }, "WebConc-B");

            threadA.start();
            threadB.start();
            threadA.join(15_000);
            threadB.join(15_000);

            if (unexpected.get() != null) {
                throw new RuntimeException("Loi concurrency demo", unexpected.get());
            }

            try (Connection verifyConn = openConnection()) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("initialAvail", 2);
                out.put("finalAvail", getAvailableStock(verifyConn, "KHO01", "CTSP18"));
                out.put("hasOrderA", queryInt(verifyConn, "SELECT COUNT(*) FROM DONHANG WHERE MaDonHang = 'DH_CONC_A'") == 1);
                out.put("hasOrderB", queryInt(verifyConn, "SELECT COUNT(*) FROM DONHANG WHERE MaDonHang = 'DH_CONC_B'") == 1);
                out.put("threadBWaitMs", threadBWaitMs.get());
                out.put("threadBSqlState", threadBSqlState.get());
                out.put("threadBError", threadBError.get());
                out.put("timeline", timeline);
                out.put("baseline", fetchBaseline(verifyConn));
                return out;
            }
        }
    }

    private static final class InteractiveTxSession {
        final String slot;
        final Object connLock = new Object();
        volatile Connection conn;
        volatile PreparedStatement activeStmt;
        volatile boolean abortRequested;
        volatile int backendPid;
        volatile String state = "IDLE";
        volatile String orderId;
        volatile String maKh = "";
        volatile String maKho = "KHO01";
        volatile String maCtsp = "CTSP18";
        volatile int soLuong;
        volatile long callStartedNanos;
        volatile long waitDurationMs;
        volatile String message = "Chưa mở giao dịch.";
        final List<String> logs = Collections.synchronizedList(new ArrayList<>());

        InteractiveTxSession(String slot) {
            this.slot = slot;
        }

        void addLog(String text) {
            String ts = LocalTime.now().format(TIME_FMT);
            logs.add(ts + " — " + text);
            if (logs.size() > 8) {
                logs.remove(0);
            }
        }

        void resetMetadata() {
            state = "IDLE";
            orderId = null;
            maKh = "";
            soLuong = 0;
            callStartedNanos = 0;
            waitDurationMs = 0;
            backendPid = 0;
            abortRequested = false;
            message = "Sẵn sàng mở giao dịch mới.";
            logs.clear();
        }
    }

    private InteractiveTxSession requireSlot(Map<String, Object> body) {
        String slot = JsonHelper.asString(body, "slot", "").trim().toUpperCase();
        InteractiveTxSession s = txSessions.get(slot);
        if (s == null) {
            throw new IllegalArgumentException("Phien giao dich khong hop le (chi nhan A hoac B).");
        }
        return s;
    }

    private Object handleInteractiveTxStatus(HttpExchange ex) throws SQLException {
        Map<String, String> q = parseQueryParams(ex.getRequestURI().getRawQuery());
        String watchKho = blankToNull(q.get("maKho"));
        String watchCtsp = blankToNull(q.get("maCtsp"));
        if (watchKho == null) watchKho = "KHO01";
        if (watchCtsp == null) watchCtsp = "CTSP18";

        InteractiveTxSession sa = txSessions.get("A");
        InteractiveTxSession sb = txSessions.get("B");

        try (Connection mon = openConnection()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("sessionA", txSnapshot(sa));
            out.put("sessionB", txSnapshot(sb));
            out.put("pgLocks", queryLivePgLocks(mon, sa.backendPid, sb.backendPid));
            out.put("watchedStock", queryWatchedStock(mon, watchKho, watchCtsp));
            out.put("baseline", fetchBaseline(mon));
            return out;
        }
    }

    private Object handleInteractiveTxStart(HttpExchange ex) throws Exception {
        Map<String, Object> body = readJsonBody(ex);
        InteractiveTxSession s = requireSlot(body);
        if (s.conn != null || "EXECUTING".equals(s.state) || "HOLDING_LOCK".equals(s.state)) {
            return Map.of(
                    "ok", false,
                    "errorMessage", "Phiên " + s.slot + " đang mở giao dịch (PID " + s.backendPid + "). Hãy COMMIT hoặc ROLLBACK trước."
            );
        }

        String maKh = JsonHelper.asString(body, "maKh", "A".equals(s.slot) ? "KH01" : "KH02");
        String maKho = JsonHelper.asString(body, "maKho", "KHO01");
        String maCtsp = JsonHelper.asString(body, "maCtsp", "CTSP18");
        int soLuong = JsonHelper.asInt(body, "soLuong", "A".equals(s.slot) ? 2 : 1);

        synchronized (s.connLock) {
            if (s.conn != null) {
                return Map.of("ok", false, "errorMessage", "Phiên " + s.slot + " đang mở giao dịch.");
            }
            s.abortRequested = false;
            String orderId = String.format("DH_WEB_%s%03d", s.slot, ORDER_SEQ.getAndIncrement() % 1000);
            String itemJson = JsonHelper.toJson(List.of(Map.of(
                    "ma_ctsp", maCtsp,
                    "so_luong", soLuong,
                    "chiet_khau", 0
            )));

            Connection conn = openConnection();
            int pid = queryInt(conn, "SELECT pg_backend_pid()");
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            s.conn = conn;
            s.backendPid = pid;
            s.orderId = orderId;
            s.maKh = maKh;
            s.maKho = maKho;
            s.maCtsp = maCtsp;
            s.soLuong = soLuong;
            s.waitDurationMs = 0;
            s.callStartedNanos = System.nanoTime();
            s.state = "EXECUTING";
            s.message = "Đang gọi sp_tao_don_hang(" + maKho + ", " + maCtsp + " x " + soLuong + ")...";
            s.addLog("Mở Transaction READ COMMITTED (PID " + pid + "), đặt " + maCtsp + " x " + soLuong + " tại " + maKho);

            try (PreparedStatement ps = conn.prepareStatement("CALL sp_tao_don_hang(?, ?, ?, ?, ?, ?::jsonb, ?)")) {
                s.activeStmt = ps;
                ps.setQueryTimeout(120);
                ps.setString(1, orderId);
                ps.setString(2, maKh);
                ps.setString(3, maKho);
                ps.setString(4, "25 Le Loi, Q1");
                ps.setString(5, "CHUYEN_KHOAN");
                ps.setString(6, itemJson);
                ps.setString(7, "Interactive Tx Phien " + s.slot);
                ps.execute();
                s.activeStmt = null;

                long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s.callStartedNanos);
                s.waitDurationMs = waited;

                if (s.abortRequested) {
                    quietRollback(conn);
                    closeQuietly(conn);
                    s.conn = null;
                    s.backendPid = 0;
                    s.state = "ROLLED_BACK";
                    s.message = "Đã hủy phiên theo yêu cầu Reset.";
                    return Map.of("ok", true, "slot", s.slot, "state", s.state, "waitedMs", waited);
                }

                s.state = "HOLDING_LOCK";
                s.message = "Đang GIỮ KHÓA dòng (" + maKho + ", " + maCtsp + ") cho đơn " + orderId
                        + (waited > 50 ? " (sau " + waited + " ms chờ khóa, chưa COMMIT!). " : " (chưa COMMIT!). ")
                        + "Hãy bấm Thanh toán (COMMIT) hoặc Hủy đơn (ROLLBACK).";
                s.addLog("Đã khóa dòng FOR UPDATE & giữ tạm " + soLuong + " đôi " + maCtsp + " (chưa COMMIT!)");
                try (Connection mon = openConnection()) {
                    return Map.of(
                            "ok", true,
                            "slot", s.slot,
                            "state", s.state,
                            "orderId", orderId,
                            "pid", pid,
                            "waitedMs", waited,
                            "baseline", fetchBaseline(mon)
                    );
                }
            } catch (SQLException sqlEx) {
                s.activeStmt = null;
                long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s.callStartedNanos);
                s.waitDurationMs = waited;
                quietRollback(conn);
                closeQuietly(conn);
                s.conn = null;
                s.backendPid = 0;

                if (s.abortRequested) {
                    s.state = "ROLLED_BACK";
                    s.message = "Đã hủy chờ khóa theo lệnh Reset.";
                    s.addLog("Ngắt lệnh đang chờ khóa & ROLLBACK do Reset.");
                } else {
                    s.state = "BLOCKED_ROLLBACK";
                    String err = firstLine(sqlEx.getMessage());
                    s.message = err + " (đã đợi " + waited + " ms & ROLLBACK sạch)";
                    s.addLog("Sau " + waited + " ms → bị chặn [" + err + "] & ROLLBACK!");
                }
                return Map.of(
                        "ok", true,
                        "slot", s.slot,
                        "state", s.state,
                        "blocked", !s.abortRequested,
                        "waitedMs", waited,
                        "errorMessage", s.message
                );
            }
        }
    }

    private Object handleInteractiveTxCommit(HttpExchange ex) throws Exception {
        return finishHeldSession(requireSlot(readJsonBody(ex)), true);
    }

    private Object handleInteractiveTxRollback(HttpExchange ex) throws Exception {
        return finishHeldSession(requireSlot(readJsonBody(ex)), false);
    }

    private Map<String, Object> finishHeldSession(InteractiveTxSession s, boolean commit) throws SQLException {
        synchronized (s.connLock) {
            if (s.conn == null || !"HOLDING_LOCK".equals(s.state)) {
                return Map.of("ok", false, "errorMessage", "Phiên " + s.slot + " hiện không giữ giao dịch mở nào.");
            }
            Connection conn = s.conn;
            try {
                if (commit) {
                    conn.commit();
                    s.state = "COMMITTED";
                    s.message = "Đã COMMIT đơn " + s.orderId + " và giải phóng Row Lock.";
                    s.addLog("COMMIT đơn " + s.orderId + " → nhả Row Lock!");
                } else {
                    conn.rollback();
                    s.state = "ROLLED_BACK";
                    s.message = "Đã ROLLBACK đơn " + s.orderId + " → nhả Row Lock và hoàn trả hàng.";
                    s.addLog("ROLLBACK đơn " + s.orderId + " → nhả Row Lock & trả lại hàng!");
                }
            } finally {
                closeQuietly(conn);
                s.conn = null;
                s.backendPid = 0;
                s.activeStmt = null;
            }
            try (Connection mon = openConnection()) {
                return Map.of("ok", true, "slot", s.slot, "state", s.state, "baseline", fetchBaseline(mon));
            }
        }
    }

    private Object handleInteractiveTxReset(HttpExchange ex) throws SQLException {
        closeAllInteractiveSessionsQuietly();
        try (Connection c = openConnection()) {
            cleanupWebAndDemoArtifacts(c);
            return Map.of(
                    "ok", true,
                    "message", "Da dong 2 phien va khoi phuc du lieu mau.",
                    "baseline", fetchBaseline(c)
            );
        }
    }

    private void closeAllInteractiveSessionsQuietly() {
        for (InteractiveTxSession s : List.of(txSessions.get("A"), txSessions.get("B"))) {
            s.abortRequested = true;
            PreparedStatement stmt = s.activeStmt;
            if (stmt != null) {
                try {
                    stmt.cancel();
                } catch (Exception ignored) {
                }
            }
            synchronized (s.connLock) {
                Connection c = s.conn;
                if (c != null) {
                    quietRollback(c);
                    closeQuietly(c);
                    s.conn = null;
                }
                s.activeStmt = null;
                s.resetMetadata();
            }
        }
    }

    private Map<String, Object> txSnapshot(InteractiveTxSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slot", s.slot);
        m.put("state", s.state);
        m.put("backendPid", s.backendPid);
        m.put("orderId", s.orderId == null ? "" : s.orderId);
        m.put("maKh", s.maKh);
        m.put("maKho", s.maKho);
        m.put("maCtsp", s.maCtsp);
        m.put("soLuong", s.soLuong);
        long liveWait = "EXECUTING".equals(s.state) && s.callStartedNanos > 0
                ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s.callStartedNanos)
                : s.waitDurationMs;
        m.put("waitMs", liveWait);
        m.put("message", s.message);
        synchronized (s.logs) {
            m.put("logs", new ArrayList<>(s.logs));
        }
        return m;
    }

    private List<Map<String, Object>> queryLivePgLocks(Connection mon, int pidA, int pidB) throws SQLException {
        if (pidA <= 0 && pidB <= 0) {
            return List.of();
        }
        String sql = "SELECT a.pid, a.state, "
                + "COALESCE(a.wait_event_type || ':' || a.wait_event, 'NONE') AS wait_info, "
                + "COALESCE(NULLIF(array_to_string(pg_blocking_pids(a.pid), ', '), ''), '-') AS blocked_by, "
                + "COALESCE(STRING_AGG(DISTINCT "
                + "  CASE WHEN l.relation IS NOT NULL THEN l.relation::regclass::text "
                + "       ELSE l.locktype END "
                + "  || ' (' || l.mode || ', ' || CASE WHEN l.granted THEN 'GIU_KHOA' ELSE 'DANG_CHO' END || ')', ', '), 'Không giữ khóa') AS locks "
                + "FROM pg_stat_activity a "
                + "LEFT JOIN pg_locks l ON l.pid = a.pid "
                + "  AND l.locktype IN ('relation', 'tuple', 'transactionid') "
                + "  AND COALESCE(l.relation::regclass::text, '') NOT LIKE 'pg_%' "
                + "WHERE a.pid IN (?, ?) "
                + "GROUP BY a.pid, a.state, a.wait_event_type, a.wait_event "
                + "ORDER BY a.pid";
        try (PreparedStatement ps = mon.prepareStatement(sql)) {
            ps.setInt(1, pidA > 0 ? pidA : -1);
            ps.setInt(2, pidB > 0 ? pidB : -1);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    int pid = rs.getInt("pid");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("slot", pid == pidA ? "Phiên A" : (pid == pidB ? "Phiên B" : "?"));
                    row.put("pid", pid);
                    row.put("state", normalizeJdbcValue(rs.getObject("state")));
                    row.put("waitInfo", normalizeJdbcValue(rs.getObject("wait_info")));
                    row.put("blockedBy", normalizeJdbcValue(rs.getObject("blocked_by")));
                    row.put("locks", normalizeJdbcValue(rs.getObject("locks")));
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private Map<String, Object> queryWatchedStock(Connection c, String maKho, String maCtsp) throws SQLException {
        String sql = "SELECT tk.MaKho, tk.MaChiTietSP, ct.SKU, sp.TenSanPham, ct.Size, ct.MauSac, "
                + "tk.SoLuongThucTe, tk.SoLuongDaGiu, "
                + "fn_tinh_ton_kho_kha_dung(tk.MaKho, tk.MaChiTietSP) AS SoLuongKhaDung "
                + "FROM TONKHO tk "
                + "JOIN CHITIET_SP ct ON tk.MaChiTietSP = ct.MaChiTietSP "
                + "JOIN SANPHAM sp ON ct.MaSanPham = sp.MaSanPham "
                + "WHERE tk.MaKho = ? AND tk.MaChiTietSP = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, maKho);
            ps.setString(2, maCtsp);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("exists", true);
                    m.put("maKho", rs.getString("MaKho"));
                    m.put("maCtsp", rs.getString("MaChiTietSP"));
                    m.put("tenSanPham", rs.getString("TenSanPham") + " (Size " + rs.getString("Size") + ")");
                    m.put("thucTe", rs.getInt("SoLuongThucTe"));
                    m.put("daGiu", rs.getInt("SoLuongDaGiu"));
                    m.put("khaDung", rs.getInt("SoLuongKhaDung"));
                    return m;
                }
            }
        }
        return Map.of("exists", false, "maKho", maKho, "maCtsp", maCtsp);
    }

    private static void closeQuietly(Connection c) {
        if (c != null) {
            try {
                c.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private Object handleDynamicReport(HttpExchange ex) throws SQLException {
        Map<String, String> q = parseQueryParams(ex.getRequestURI().getRawQuery());
        String maKho = blankToNull(q.get("maKho"));
        String maDanhMuc = blankToNull(q.get("maDanhMuc"));
        String tuNgay = blankToNull(q.get("tuNgay"));
        String denNgay = blankToNull(q.get("denNgay"));
        if (tuNgay == null || denNgay == null) {
            tuNgay = null;
            denNgay = null;
        }

        String sql = "SELECT MaKho, TenKho, MaDanhMuc, TenDanhMuc, SoDonHang, TongSoLuongBan, DoanhThu "
                + "FROM fn_bao_cao_doanh_thu_dong(?, ?, ?, ?)";

        StringBuilder refundWhere = new StringBuilder(" WHERE dh.TrangThai = 'HOAN_TAT'");
        if (maKho != null) refundWhere.append(" AND dh.MaKho = $1");
        if (tuNgay != null) refundWhere.append(" AND dh.NgayDat >= $2");
        if (denNgay != null) refundWhere.append(" AND dh.NgayDat <= $3");
        if (maDanhMuc != null) refundWhere.append(" AND sp.MaDanhMuc IN (SELECT MaDanhMuc FROM dm_loc)");

        String refundSql = "WITH RECURSIVE dm_loc AS ("
                + "  SELECT MaDanhMuc FROM DANHMUC_SP WHERE MaDanhMuc = $4 "
                + "  UNION ALL "
                + "  SELECT dm.MaDanhMuc FROM DANHMUC_SP dm JOIN dm_loc l ON dm.MaDanhMucCha = l.MaDanhMuc"
                + "), tra_theo_dong AS ("
                + "  SELECT ctth.MaCTDonHang, "
                + "         SUM(ctth.SoLuongTra)::BIGINT AS sl_hoan, "
                + "         ROUND(SUM(ctth.SoLuongTra * ctth.DonGiaHoan), 2) AS tien_hoan "
                + "  FROM CHITIET_TRAHANG ctth "
                + "  JOIN PHIEUTRAHANG pth ON ctth.MaPhieuTra = pth.MaPhieuTra "
                + "  WHERE pth.TrangThai = 'HOAN_TAT' AND ctth.HinhThucXuLy = 'HOAN_TIEN' "
                + "  GROUP BY ctth.MaCTDonHang"
                + ") "
                + "SELECT dh.MaKho, sp.MaDanhMuc, "
                + "       COALESCE(SUM(t.sl_hoan), 0)::BIGINT AS sl_hoan, "
                + "       COALESCE(SUM(t.tien_hoan), 0.00)::NUMERIC(14, 2) AS tien_hoan "
                + "FROM DONHANG dh "
                + "JOIN CHITIET_DONHANG ctdh ON dh.MaDonHang = ctdh.MaDonHang "
                + "JOIN tra_theo_dong t ON t.MaCTDonHang = ctdh.MaCTDonHang "
                + "JOIN CHITIET_SP ctsp ON ctdh.MaChiTietSP = ctsp.MaChiTietSP "
                + "JOIN SANPHAM sp ON ctsp.MaSanPham = sp.MaSanPham"
                + refundWhere
                + " GROUP BY dh.MaKho, sp.MaDanhMuc";

        try (Connection c = openConnection()) {
            Map<String, Object> rawTable;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                setNullableString(ps, 1, maKho);
                setNullableTimestamp(ps, 2, tuNgay, false);
                setNullableTimestamp(ps, 3, denNgay, true);
                setNullableString(ps, 4, maDanhMuc);
                try (ResultSet rs = ps.executeQuery()) {
                    rawTable = readResultSet(rs);
                }
            }

            Map<String, double[]> refundByGroup = new LinkedHashMap<>();
            // Dung PreparedStatement voi tham so positional khop tu 1..4 qua CTE wrapper
            String wrappedRefundSql = "WITH RECURSIVE params AS (SELECT ?::VARCHAR AS p1, ?::TIMESTAMP AS p2, ?::TIMESTAMP AS p3, ?::VARCHAR AS p4), "
                    + refundSql.substring("WITH RECURSIVE ".length())
                            .replace("$1", "(SELECT p1 FROM params)")
                            .replace("$2", "(SELECT p2 FROM params)")
                            .replace("$3", "(SELECT p3 FROM params)")
                            .replace("$4", "(SELECT p4 FROM params)");
            try (PreparedStatement ps = c.prepareStatement(wrappedRefundSql)) {
                setNullableString(ps, 1, maKho);
                setNullableTimestamp(ps, 2, tuNgay, false);
                setNullableTimestamp(ps, 3, denNgay, true);
                setNullableString(ps, 4, maDanhMuc);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString(1) + "|" + rs.getString(2);
                        refundByGroup.put(key, new double[]{rs.getLong(3), rs.getBigDecimal(4).doubleValue()});
                    }
                }
            }

            @SuppressWarnings("unchecked")
            List<List<Object>> rawRows = (List<List<Object>>) rawTable.get("rows");
            List<List<Object>> enrichedRows = new ArrayList<>(rawRows.size());
            double grossRev = 0.0;
            double refundRev = 0.0;
            long grossQty = 0L;
            long refundQty = 0L;
            for (List<Object> row : rawRows) {
                String k = String.valueOf(row.get(0)) + "|" + String.valueOf(row.get(2));
                double[] ref = refundByGroup.getOrDefault(k, new double[]{0.0, 0.0});
                long qBan = ((Number) row.get(5)).longValue();
                double dtGop = ((Number) row.get(6)).doubleValue();
                long qHoan = Math.round(ref[0]);
                double tienHoan = ref[1];
                double dtThuan = Math.round((dtGop - tienHoan) * 100.0) / 100.0;
                grossQty += qBan;
                refundQty += qHoan;
                grossRev += dtGop;
                refundRev += tienHoan;

                List<Object> nr = new ArrayList<>(row);
                nr.add(tienHoan);
                nr.add(dtThuan);
                enrichedRows.add(nr);
            }
            Map<String, Object> enrichedTable = Map.of(
                    "columns", List.of("MaKho", "TenKho", "MaDanhMuc", "TenDanhMuc", "SoDonHang",
                            "TongSoLuongBan", "DoanhThuGop", "TienHoanTra", "DoanhThuThuan"),
                    "rows", enrichedRows,
                    "rowCount", enrichedRows.size()
            );
            Map<String, Object> revSummary = Map.of(
                    "grossRevenue", Math.round(grossRev * 100.0) / 100.0,
                    "refundAmount", Math.round(refundRev * 100.0) / 100.0,
                    "netRevenue", Math.round((grossRev - refundRev) * 100.0) / 100.0,
                    "grossQty", grossQty,
                    "refundQty", refundQty,
                    "netQty", grossQty - refundQty
            );

            return Map.of(
                    "ok", true,
                    "filters", Map.of(
                            "maKho", maKho == null ? "TAT_CA" : maKho,
                            "maDanhMuc", maDanhMuc == null ? "TAT_CA" : maDanhMuc,
                            "tuNgay", tuNgay == null ? "" : tuNgay,
                            "denNgay", denNgay == null ? "" : denNgay
                    ),
                    "result", enrichedTable,
                    "revenueSummary", revSummary
            );
        }
    }

    private Object handleSqlQueryCatalog(HttpExchange ex) throws Exception {
        List<SqlQueryItem> queries = loadQueriesFrom03Sql();
        return Map.of(
                "ok", true,
                "defaultIndex", queries.size(),
                "catalog", buildSqlCatalog(queries)
        );
    }

    private Object handleRunSqlQuery(HttpExchange ex) throws Exception {
        Map<String, String> q = parseQueryParams(ex.getRequestURI().getRawQuery());
        int index = 12;
        if (q.containsKey("index")) {
            index = Integer.parseInt(q.get("index"));
        }

        List<SqlQueryItem> queries = loadQueriesFrom03Sql();
        if (index < 1 || index > queries.size()) {
            index = queries.size();
        }
        SqlQueryItem chosen = queries.get(index - 1);

        try (Connection c = openConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(chosen.sql)) {
            return Map.of(
                    "ok", true,
                    "selectedIndex", index,
                    "title", chosen.title,
                    "sql", chosen.sql,
                    "catalog", buildSqlCatalog(queries),
                    "result", readResultSet(rs)
            );
        }
    }

    private List<Map<String, Object>> buildSqlCatalog(List<SqlQueryItem> queries) {
        List<Map<String, Object>> catalog = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            SqlQueryItem item = queries.get(i);
            catalog.add(Map.of("index", i + 1, "title", item.title, "sql", item.sql));
        }
        return catalog;
    }

    private Object handleBenchmarkRun(HttpExchange ex) throws SQLException {
        synchronized (BENCHMARK_LOCK) {
            try (Connection c = openConnection()) {
                try {
                    dropSupplementalIndexes(c);
                    deleteBenchmarkRows(c);
                    seed50kBenchmarkData(c);

                    List<Map<String, Object>> beforeList = runFourBenchmarkExplains(c);

                    restoreSupplementalIndexes(c);
                    try (Statement st = c.createStatement()) {
                        st.execute("ANALYZE");
                    }

                    List<Map<String, Object>> afterList = runFourBenchmarkExplains(c);
                    List<Map<String, Object>> indexSizes = queryRowsAsMaps(c,
                            "SELECT tablename, indexname, pg_size_pretty(pg_relation_size(indexname::regclass)) AS index_size "
                                    + "FROM pg_indexes WHERE schemaname = 'public' AND indexname LIKE 'idx_%' ORDER BY tablename, indexname");

                    List<Map<String, Object>> comparisons = new ArrayList<>();
                    for (int i = 0; i < beforeList.size(); i++) {
                        Map<String, Object> b = beforeList.get(i);
                        Map<String, Object> a = afterList.get(i);
                        double tBefore = ((Number) b.get("execTimeMs")).doubleValue();
                        double tAfter = ((Number) a.get("execTimeMs")).doubleValue();
                        double speedup = tAfter > 0 ? Math.round((tBefore / tAfter) * 10.0) / 10.0 : 0.0;

                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("code", b.get("code"));
                        row.put("label", b.get("label"));
                        row.put("beforeTimeMs", tBefore);
                        row.put("afterTimeMs", tAfter);
                        row.put("speedup", speedup);
                        row.put("beforeBuffers", b.get("totalBuffers"));
                        row.put("afterBuffers", a.get("totalBuffers"));
                        row.put("beforeScan", b.get("scanSummary"));
                        row.put("afterScan", a.get("scanSummary"));
                        row.put("beforePlan", b.get("rawPlan"));
                        row.put("afterPlan", a.get("rawPlan"));
                        comparisons.add(row);
                    }

                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("ok", true);
                    out.put("scale", "30,000 KHACHHANG + 50,000 DONHANG + 100,000 CHITIET_DONHANG ('BM_%')");
                    out.put("comparisons", comparisons);
                    out.put("indexSizes", indexSizes);
                    return out;
                } finally {
                    restoreSupplementalIndexes(c);
                    deleteBenchmarkRows(c);
                    try (Statement st = c.createStatement()) {
                        st.execute("ANALYZE");
                    } catch (SQLException ignored) {
                    }
                }
            }
        }
    }

    private Object handleOodbRun(HttpExchange ex) throws Exception {
        synchronized (OODB_LOCK) {
            Path oodbDir = resolveOodbProjectDir();
            String jsonText = executeOqlWebBridge(oodbDir);
            Map<String, Object> parsed = JsonHelper.parseObject(jsonText);
            parsed.put("ok", true);
            parsed.put("projectPath", oodbDir.toAbsolutePath().toString());
            return parsed;
        }
    }

    private Object handleReset(HttpExchange ex) throws SQLException {
        closeAllInteractiveSessionsQuietly();
        try (Connection c = openConnection()) {
            cleanupWebAndDemoArtifacts(c);
            return Map.of(
                    "ok", true,
                    "message", "Da khoi phuc dung trang thai du lieu mau ban dau.",
                    "baseline", fetchBaseline(c)
            );
        }
    }

    // -------------------------------------------------------------------------
    // DATABASE & SUBPROCESS HELPERS
    // -------------------------------------------------------------------------

    private Connection openConnection() throws SQLException {
        if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        }
        // Khoi tao truc tiep org.postgresql.Driver de tranh loi DriverManager TCCL trong exec:java thread pool
        Properties props = new Properties();
        props.setProperty("user", dbUser);
        props.setProperty("password", dbPassword);
        Connection direct = new org.postgresql.Driver().connect(jdbcUrl, props);
        if (direct != null) {
            return direct;
        }
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
    }

    private Map<String, Object> fetchBaseline(Connection c) throws SQLException {
        int donHang = queryInt(c, "SELECT COUNT(*) FROM DONHANG");
        int ctdh = queryInt(c, "SELECT COUNT(*) FROM CHITIET_DONHANG");
        int tonKho = queryInt(c, "SELECT COUNT(*) FROM TONKHO");
        int phieuTra = queryInt(c, "SELECT COUNT(*) FROM PHIEUTRAHANG");
        int phieuKho = queryInt(c, "SELECT COUNT(*) FROM PHIEUKHO");
        int indexes = queryInt(c, "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname LIKE 'idx_%'");
        int triggers = queryInt(c,
                "SELECT COUNT(*) FROM pg_trigger t JOIN pg_class cl ON cl.oid = t.tgrelid "
                        + "JOIN pg_namespace n ON n.oid = cl.relnamespace WHERE n.nspname = 'public' AND NOT t.tgisinternal");
        int ctsp02Avail = getAvailableStock(c, "KHO01", "CTSP02");
        int ctsp18Avail = getAvailableStock(c, "KHO01", "CTSP18");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("donHang", donHang);
        m.put("chiTietDonHang", ctdh);
        m.put("tonKho", tonKho);
        m.put("phieuTraHang", phieuTra);
        m.put("phieuKho", phieuKho);
        m.put("indexes", indexes);
        m.put("triggers", triggers);
        m.put("ctsp02Avail", ctsp02Avail);
        m.put("ctsp18Avail", ctsp18Avail);
        m.put("isCleanBaseline", donHang == 10 && ctdh == 18 && tonKho == 40
                && phieuTra == 4 && phieuKho == 18 && ctsp02Avail == 23 && ctsp18Avail == 2);
        return m;
    }

    private void cleanupWebAndDemoArtifacts(Connection c) throws SQLException {
        boolean prevAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            // 1. Thu thap toan bo tac dong TONKHO cua cac phieu kho PK_WEB_% (chuyen / nhap / xuat doi)
            List<Object[]> transfers = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT pk.MaPhieu, pk.TrangThai, pck.KhoNguon, pck.KhoDich, ct.MaChiTietSP, ct.SoLuong "
                            + "FROM PHIEUKHO pk "
                            + "JOIN PHIEUCHUYENKHO pck ON pk.MaPhieu = pck.MaPhieu "
                            + "JOIN CHITIET_PHIEUKHO ct ON pk.MaPhieu = ct.MaPhieu "
                            + "WHERE pk.MaPhieu LIKE 'PK_WEB_%'");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    transfers.add(new Object[]{
                            rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getInt(6)
                    });
                }
            }

            List<Object[]> receives = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT pnk.KhoDich, ct.MaChiTietSP, ct.SoLuong "
                            + "FROM PHIEUNHAPKHO pnk "
                            + "JOIN CHITIET_PHIEUKHO ct ON pnk.MaPhieu = ct.MaPhieu "
                            + "WHERE pnk.MaPhieu LIKE 'PK_WEB_%'");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    receives.add(new Object[]{rs.getString(1), rs.getString(2), rs.getInt(3)});
                }
            }

            List<Object[]> returnExports = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT pxk.KhoNguon, ct.MaChiTietSP, ct.SoLuong "
                            + "FROM PHIEUXUATKHO pxk "
                            + "JOIN CHITIET_PHIEUKHO ct ON pxk.MaPhieu = ct.MaPhieu "
                            + "WHERE pxk.MaPhieu LIKE 'PK_WEB_%'");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    returnExports.add(new Object[]{rs.getString(1), rs.getString(2), rs.getInt(3)});
                }
            }

            // 2. Cong bu lai kho nguon truoc (tranh am ton kho)
            for (Object[] tr : transfers) {
                try (PreparedStatement addSrc = c.prepareStatement(
                        "UPDATE TONKHO SET SoLuongThucTe = SoLuongThucTe + ?, NgayCapNhat = CURRENT_TIMESTAMP "
                                + "WHERE MaKho = ? AND MaChiTietSP = ?")) {
                    addSrc.setInt(1, (Integer) tr[5]);
                    addSrc.setString(2, (String) tr[2]);
                    addSrc.setString(3, (String) tr[4]);
                    addSrc.executeUpdate();
                }
            }
            for (Object[] exRow : returnExports) {
                upsertAddStock(c, (String) exRow[0], (String) exRow[1], (Integer) exRow[2]);
            }

            // 3. Xoa phieu kho & phieu tra hang web theo thu tu khoa ngoai (child -> parent)
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM CHITIET_PHIEUKHO WHERE MaPhieu LIKE 'PK_WEB_%'");
                st.executeUpdate("DELETE FROM PHIEUCHUYENKHO WHERE MaPhieu LIKE 'PK_WEB_%'");
                st.executeUpdate("DELETE FROM PHIEUXUATKHO WHERE MaPhieu LIKE 'PK_WEB_%'");
                st.executeUpdate("DELETE FROM PHIEUNHAPKHO WHERE MaPhieu LIKE 'PK_WEB_%'");
                st.executeUpdate("DELETE FROM PHIEUKHO WHERE MaPhieu LIKE 'PK_WEB_%'");
                st.executeUpdate("DELETE FROM CHITIET_TRAHANG WHERE MaPhieuTra LIKE 'PTH_WEB_%' OR MaCTTraHang LIKE 'CTTH_W_%'");
                st.executeUpdate("DELETE FROM PHIEUTRAHANG WHERE MaPhieuTra LIKE 'PTH_WEB_%'");
            }

            // 4. Hoan tac & xoa cac don DH_WEB_% / DH_CONC_% / DH_DEMO_% (giai phong SoLuongDaGiu)
            List<String[]> orders = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT MaDonHang, TrangThai FROM DONHANG "
                            + "WHERE MaDonHang LIKE 'DH_WEB_%' OR MaDonHang LIKE 'DH_CONC_%' OR MaDonHang LIKE 'DH_DEMO_%'");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }
            for (String[] row : orders) {
                String maDh = row[0];
                String tt = row[1];
                if ("DA_XAC_NHAN".equals(tt) || "DANG_XU_LY".equals(tt)) {
                    try (PreparedStatement cancelPs = c.prepareStatement("CALL sp_huy_don_hang(?, ?)")) {
                        cancelPs.setString(1, maDh);
                        cancelPs.setString(2, "Reset Web Demo");
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

            // 5. Tru lai ton kho da nhap o cac phieu chuyen HOAN_TAT va phieu nhap PK_WEB_%
            List<Object[]> subtracts = new ArrayList<>();
            for (Object[] tr : transfers) {
                if ("HOAN_TAT".equals(tr[1])) {
                    subtracts.add(new Object[]{tr[3], tr[4], tr[5]});
                }
            }
            subtracts.addAll(receives);
            for (Object[] sub : subtracts) {
                String kho = (String) sub[0];
                String ctsp = (String) sub[1];
                int sl = (Integer) sub[2];
                try (PreparedStatement subDst = c.prepareStatement(
                        "UPDATE TONKHO SET SoLuongThucTe = GREATEST(0, SoLuongThucTe - ?), NgayCapNhat = CURRENT_TIMESTAMP "
                                + "WHERE MaKho = ? AND MaChiTietSP = ?")) {
                    subDst.setInt(1, sl);
                    subDst.setString(2, kho);
                    subDst.setString(3, ctsp);
                    subDst.executeUpdate();
                }
                try (PreparedStatement delEmpty = c.prepareStatement(
                        "DELETE FROM TONKHO WHERE MaKho = ? AND MaChiTietSP = ? "
                                + "AND SoLuongThucTe = 0 AND SoLuongDaGiu = 0 "
                                + "AND NOT (MaKho = 'KHO01' AND MaChiTietSP = 'CTSP20')")) {
                    delEmpty.setString(1, kho);
                    delEmpty.setString(2, ctsp);
                    delEmpty.executeUpdate();
                }
            }

            c.commit();
        } catch (SQLException ex) {
            quietRollback(c);
            throw ex;
        } finally {
            c.setAutoCommit(prevAuto);
        }
    }

    private void dropSupplementalIndexes(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String idx : SUPPLEMENTAL_INDEX_NAMES) {
                st.execute("DROP INDEX IF EXISTS " + idx);
            }
        }
    }

    private void restoreSupplementalIndexes(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String ddl : SUPPLEMENTAL_INDEX_DDL) {
                st.execute(ddl);
            }
        }
    }

    private void deleteBenchmarkRows(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM CHITIET_DONHANG WHERE MaCTDonHang LIKE 'CTDH_BM_%'");
            st.executeUpdate("DELETE FROM DONHANG WHERE MaDonHang LIKE 'DH_BM_%'");
            st.executeUpdate("DELETE FROM TONKHO WHERE MaChiTietSP LIKE 'CTSP_BM_%'");
            st.executeUpdate("DELETE FROM CHITIET_SP WHERE MaChiTietSP LIKE 'CTSP_BM_%'");
            st.executeUpdate("DELETE FROM KHACHHANG WHERE MaKH LIKE 'KH_BM_%'");
        }
    }

    private void seed50kBenchmarkData(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(
                    "INSERT INTO KHACHHANG (MaKH, HoTen, SDT, Email, DiaChi, NgayDangKy) "
                            + "SELECT 'KH_BM_' || LPAD(i::TEXT, 5, '0'), 'Khach Benchmark ' || i, "
                            + "'09' || LPAD((10000000 + i)::TEXT, 8, '0'), 'bm' || i || '@example.com', "
                            + "'Dia chi #' || i, TIMESTAMP '2024-01-01 00:00:00' + (i * 3 || ' minutes')::INTERVAL "
                            + "FROM generate_series(1, 30000) AS s(i)");
            st.executeUpdate(
                    "INSERT INTO DONHANG (MaDonHang, NgayDat, TrangThai, DiaChiGiaoHang, PhuongThucThanhToan, GhiChu, MaKH, MaKho) "
                            + "SELECT 'DH_BM_' || LPAD(i::TEXT, 5, '0'), TIMESTAMP '2025-01-01 08:00:00' + (i * 5 || ' minutes')::INTERVAL, "
                            + "CASE WHEN (i % 10) < 7 THEN 'HOAN_TAT' WHEN (i % 10) < 8 THEN 'DANG_GIAO' WHEN (i % 10) < 9 THEN 'DA_XAC_NHAN' ELSE 'DA_HUY' END, "
                            + "'Dia chi giao #' || i, CASE (i % 3) WHEN 0 THEN 'COD' WHEN 1 THEN 'CHUYEN_KHOAN' ELSE 'THE' END, NULL, "
                            + "'KH_BM_' || LPAD((((i - 1) % 1000) + 1)::TEXT, 5, '0'), 'KHO0' || (((i - 1) % 4) + 1) "
                            + "FROM generate_series(1, 50000) AS s(i)");
            st.executeUpdate(
                    "INSERT INTO CHITIET_DONHANG (MaCTDonHang, MaDonHang, MaChiTietSP, SoLuong, DonGiaBan, ChietKhau) "
                            + "SELECT 'CTDH_BM_' || LPAD((i * 2 - 1)::TEXT, 6, '0'), 'DH_BM_' || LPAD(i::TEXT, 5, '0'), "
                            + "'CTSP' || LPAD((((i - 1) % 18) + 1)::TEXT, 2, '0'), (i % 3) + 1, "
                            + "1500000.00 + ((i % 10) * 100000.00), CASE WHEN i % 5 = 0 THEN 5.00 ELSE 0.00 END "
                            + "FROM generate_series(1, 50000) AS s(i) "
                            + "UNION ALL "
                            + "SELECT 'CTDH_BM_' || LPAD((i * 2)::TEXT, 6, '0'), 'DH_BM_' || LPAD(i::TEXT, 5, '0'), "
                            + "'CTSP' || LPAD((((i + 5) % 18) + 1)::TEXT, 2, '0'), 1, 2200000.00, 0.00 "
                            + "FROM generate_series(1, 50000) AS s(i)");
            st.execute("ANALYZE");
        }
    }

    private List<Map<String, Object>> runFourBenchmarkExplains(Connection c) throws SQLException {
        String[][] cases = {
                {"BM1", "Xem lịch sử đơn hàng của 1 khách hàng, xếp đơn mới nhất lên đầu (lọc MaKH = 'KH_BM_00042', sắp xếp NgayDat DESC — chiếm 0,1% trên 50.010 đơn)",
                        "EXPLAIN (ANALYZE, BUFFERS) SELECT dh.MaDonHang, dh.NgayDat, dh.TrangThai, dh.MaKho, dh.DiaChiGiaoHang "
                                + "FROM DONHANG dh WHERE dh.MaKH = 'KH_BM_00042' ORDER BY dh.NgayDat DESC"},
                {"BM2", "Báo cáo doanh thu chốt ngày tại 1 kho: lọc đơn đã hoàn tất trong 1 ngày và cộng tiền chi tiết đơn (lọc MaKho = 'KHO01', TrangThai = 'HOAN_TAT', NgayDat trong 01/02/2025, JOIN CHITIET_DONHANG 100.018 dòng)",
                        "EXPLAIN (ANALYZE, BUFFERS) SELECT dh.MaKho, COUNT(DISTINCT dh.MaDonHang) AS SoDonHoanTat, "
                                + "SUM(ctdh.SoLuong) AS TongDoiGiayBan, "
                                + "ROUND(SUM(ctdh.SoLuong * ctdh.DonGiaBan * (1 - ctdh.ChietKhau / 100.0)), 2) AS DoanhThu "
                                + "FROM DONHANG dh JOIN CHITIET_DONHANG ctdh ON dh.MaDonHang = ctdh.MaDonHang "
                                + "WHERE dh.MaKho = 'KHO01' AND dh.TrangThai = 'HOAN_TAT' "
                                + "AND dh.NgayDat >= TIMESTAMP '2025-02-01 00:00:00' "
                                + "AND dh.NgayDat < TIMESTAMP '2025-02-02 00:00:00' GROUP BY dh.MaKho"},
                {"BM3", "Xem trang đầu 20 khách hàng mới đăng ký gần nhất trong 30.008 khách (sắp xếp NgayDangKy DESC, LIMIT 20 — triệt tiêu bước Sort)",
                        "EXPLAIN (ANALYZE, BUFFERS) SELECT kh.MaKH, kh.HoTen, kh.SDT, kh.Email, kh.NgayDangKy "
                                + "FROM KHACHHANG kh ORDER BY kh.NgayDangKy DESC LIMIT 20"},
                {"BM4", "Thống kê số đơn đã hoàn tất toàn hệ thống theo phương thức thanh toán (lọc TrangThai = 'HOAN_TAT', chiếm ~70% dữ liệu — giữ Seq Scan)",
                        "EXPLAIN (ANALYZE, BUFFERS) SELECT dh.PhuongThucThanhToan, COUNT(*) AS SoDon "
                                + "FROM DONHANG dh WHERE dh.TrangThai = 'HOAN_TAT' GROUP BY dh.PhuongThucThanhToan"}
        };

        List<Map<String, Object>> results = new ArrayList<>();
        for (String[] item : cases) {
            StringBuilder planSb = new StringBuilder();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(item[2])) {
                while (rs.next()) {
                    planSb.append(rs.getString(1)).append('\n');
                }
            }
            String rawPlan = planSb.toString().trim();
            results.add(parseExplainSummary(item[0], item[1], rawPlan));
        }
        return results;
    }

    private Map<String, Object> parseExplainSummary(String code, String label, String rawPlan) {
        double execMs = 0.0;
        Matcher execM = Pattern.compile("Execution Time:\\s*([0-9.]+)\\s*ms").matcher(rawPlan);
        if (execM.find()) {
            execMs = Double.parseDouble(execM.group(1));
        }

        int totalBuffers = 0;
        for (String line : rawPlan.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Planning:") || trimmed.startsWith("Execution Time:")) {
                break;
            }
            if (trimmed.startsWith("Buffers:")) {
                Matcher hitM = Pattern.compile("shared hit=(\\d+)").matcher(trimmed);
                Matcher readM = Pattern.compile("read=(\\d+)").matcher(trimmed);
                int hit = hitM.find() ? Integer.parseInt(hitM.group(1)) : 0;
                int read = readM.find() ? Integer.parseInt(readM.group(1)) : 0;
                totalBuffers = hit + read;
                break;
            }
        }

        String scanSummary = "Scan";
        if (rawPlan.contains("Bitmap Index Scan")) {
            scanSummary = rawPlan.contains("Seq Scan") ? "Seq Scan + Bitmap Index" : "Bitmap Index + Heap Scan";
        } else if (rawPlan.contains("Seq Scan")) {
            scanSummary = rawPlan.contains("Index Scan") ? "Seq Scan (DONHANG) + PK Join" : "Seq Scan";
        } else if (rawPlan.contains("Index Only Scan")) {
            scanSummary = "Index Only Scan";
        } else if (rawPlan.contains("Index Scan")) {
            scanSummary = "Index Scan";
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        m.put("execTimeMs", Math.round(execMs * 1000.0) / 1000.0);
        m.put("totalBuffers", totalBuffers);
        m.put("scanSummary", scanSummary);
        m.put("rawPlan", rawPlan);
        return m;
    }

    private String executeOqlWebBridge(Path oodbDir) throws Exception {
        ProcessResult res = runMavenOodb(oodbDir, true);
        String combined = res.stdout;
        int start = combined.indexOf("<<<OODB_JSON_START>>>");
        int end = combined.indexOf("<<<OODB_JSON_END>>>");
        if (res.exitCode != 0 || start < 0 || end <= start) {
            // Thu lai khong co -o neu local cache chua du
            res = runMavenOodb(oodbDir, false);
            combined = res.stdout;
            start = combined.indexOf("<<<OODB_JSON_START>>>");
            end = combined.indexOf("<<<OODB_JSON_END>>>");
        }
        if (res.exitCode != 0 || start < 0 || end <= start) {
            throw new IllegalStateException("Khong chay duoc OqlWebBridge trong " + oodbDir + ": " + combined);
        }
        return combined.substring(start + "<<<OODB_JSON_START>>>".length(), end).trim();
    }

    private ProcessResult runMavenOodb(Path oodbDir, boolean offline) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("cmd");
        cmd.add("/c");
        cmd.add("mvn");
        if (offline) {
            cmd.add("-o");
        }
        cmd.add("-q");
        cmd.add("compile");
        cmd.add("exec:java");
        cmd.add("-Dexec.mainClass=com.csdl.executor.OqlWebBridge");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(oodbDir.toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("MAVEN_OPTS", "-Dfile.encoding=UTF-8");
        Process p = pb.start();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream is = p.getInputStream()) {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) >= 0) {
                buf.write(chunk, 0, n);
            }
        }
        boolean finished = p.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IllegalStateException("Timeout chay OqlWebBridge (>60s)");
        }
        return new ProcessResult(p.exitValue(), buf.toString(StandardCharsets.UTF_8));
    }

    private record ProcessResult(int exitCode, String stdout) {}
    private record SqlQueryItem(String title, String sql) {}

    private List<SqlQueryItem> loadQueriesFrom03Sql() throws IOException {
        Path sqlPath = resolveSqlQueriesPath();
        String content = Files.readString(sqlPath, StandardCharsets.UTF_8);
        String[] parts = content.split(";");
        List<SqlQueryItem> items = new ArrayList<>();
        int idx = 1;
        Pattern titlePat = Pattern.compile("--\\s*(Câu\\s*\\d+[^\\r\\n]*)");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            Matcher m = titlePat.matcher(trimmed);
            String title = m.find() ? m.group(1).trim() : ("Truy vấn #" + idx);
            items.add(new SqlQueryItem(title, trimmed));
            idx++;
        }
        return items;
    }

    private Path resolveSqlQueriesPath() {
        List<Path> candidates = List.of(
                Path.of("../03_queries.sql"),
                Path.of("CSDL quan hệ/src/03_queries.sql"),
                Path.of("03_queries.sql")
        );
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("Khong tim thay file 03_queries.sql");
    }

    private Path resolveOodbProjectDir() {
        List<Path> candidates = List.of(
                Path.of("../../../CSDL Hướng đối tượng/src/objectdb_demo"),
                Path.of("CSDL Hướng đối tượng/src/objectdb_demo")
        );
        for (Path p : candidates) {
            if (Files.isRegularFile(p.resolve("pom.xml"))) {
                return p.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("Khong tim thay du an objectdb_demo");
    }

    private void callCreateOrder(
            Connection conn, String maDonHang, String maKh, String maKho,
            String diaChi, String pttt, String chiTietJson, String ghiChu) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("CALL sp_tao_don_hang(?, ?, ?, ?, ?, ?::jsonb, ?)")) {
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
        try (PreparedStatement ps = conn.prepareStatement("SELECT fn_tinh_ton_kho_kha_dung(?, ?)")) {
            ps.setString(1, maKho);
            ps.setString(2, maCtsp);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int queryInt(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private BigDecimal queryBigDecimal(Connection conn, String sql, String param) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private List<Map<String, Object>> queryRowsAsMaps(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(md.getColumnLabel(i), normalizeJdbcValue(rs.getObject(i)));
                }
                list.add(row);
            }
            return list;
        }
    }

    private Map<String, Object> readResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int colCount = md.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            columns.add(md.getColumnLabel(i));
        }
        List<List<Object>> rows = new ArrayList<>();
        while (rs.next()) {
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                row.add(normalizeJdbcValue(rs.getObject(i)));
            }
            rows.add(row);
        }
        return Map.of("columns", columns, "rows", rows, "rowCount", rows.size());
    }

    private Object normalizeJdbcValue(Object v) {
        if (v == null || v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            return v;
        }
        if (v instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return String.valueOf(v);
    }

    private void addTimeline(List<Map<String, Object>> timeline, long startNanos, String actor, String status, String detail) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        String ts = LocalTime.now().format(TIME_FMT);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ts", ts);
        item.put("elapsedMs", elapsedMs);
        item.put("actor", actor);
        item.put("status", status);
        item.put("detail", detail);
        synchronized (timeline) {
            timeline.add(item);
        }
    }

    private String nextOrderId() {
        return String.format("DH_WEB_%04d%02d", (System.currentTimeMillis() / 100) % 10000, ORDER_SEQ.getAndIncrement() % 100);
    }

    private String nextTransferId() {
        return String.format("PK_WEB_%04d%02d", (System.currentTimeMillis() / 100) % 10000, TRANSFER_SEQ.getAndIncrement() % 100);
    }

    private String nextReturnId() {
        return String.format("PTH_WEB_%04d%02d", (System.currentTimeMillis() / 100) % 10000, RETURN_SEQ.getAndIncrement() % 100);
    }

    private String nextDetailId(String prefix) {
        return prefix + String.format("%04d%02d", (System.currentTimeMillis() / 100) % 10000, DETAIL_SEQ.getAndIncrement() % 100);
    }

    private byte[] loadIndexHtml() throws IOException {
        Path devFile = Path.of("src/main/resources/web/index.html");
        if (Files.isRegularFile(devFile)) {
            return Files.readAllBytes(devFile);
        }
        try (InputStream is = WebDemoServer.class.getResourceAsStream("/web/index.html")) {
            if (is != null) {
                return is.readAllBytes();
            }
        }
        throw new IOException("Khong tim thay file /web/index.html");
    }

    private Map<String, Object> readJsonBody(HttpExchange ex) throws IOException {
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return JsonHelper.parseObject(raw);
    }

    private Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return map;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private void setNullableString(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, val);
        }
    }

    private void setNullableTimestamp(PreparedStatement ps, int idx, String dateStr, boolean endOfDay) throws SQLException {
        if (dateStr == null) {
            ps.setNull(idx, Types.TIMESTAMP);
            return;
        }
        String normalized = dateStr.length() == 10
                ? dateStr + (endOfDay ? " 23:59:59" : " 00:00:00")
                : dateStr.replace('T', ' ');
        ps.setTimestamp(idx, Timestamp.valueOf(normalized));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void sendJson(HttpExchange ex, int status, Object data) throws IOException {
        byte[] bytes = JsonHelper.toJson(data).getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("Cache-Control", "no-store");
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendText(HttpExchange ex, int status, String contentType, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void quietRollback(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static String firstLine(String msg) {
        if (msg == null) return "";
        int idx = msg.indexOf('\n');
        return idx >= 0 ? msg.substring(0, idx).trim() : msg.trim();
    }

    private static String resolveSetting(String propKey, String envKey, String defaultVal) {
        String prop = System.getProperty(propKey);
        if (prop != null && !prop.isBlank()) return prop.trim();
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) return env.trim();
        return defaultVal;
    }
}
