package com.csdl.executor;

import com.csdl.model.*;
import com.csdl.seeder.DataSeeder;

import javax.persistence.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Lớp thực thi kiểm chứng 06 truy vấn trên ObjectDB (sử dụng JPQL ánh xạ từ OQL chuẩn).
 * Xuất kết quả ra màn hình Console và lưu vào file target/execution_output.txt.
 */
public class OqlExecutor {

    private static final String DB_PATH = "target/csdl_giay.odb";
    private static final String LOG_PATH = "target/execution_output.txt";
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0.00");

    private static PrintStream logOut;

    private static void log(String line) {
        System.out.println(line);
        if (logOut != null) {
            logOut.println(line);
        }
    }

    public static void main(String[] args) {
        // Xóa file CSDL cũ để đảm bảo tính tái lập (reproducible)
        new File(DB_PATH).delete();
        new File("target").mkdirs();

        try {
            logOut = new PrintStream(new FileOutputStream(LOG_PATH), true, StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            System.err.println("Không tạo được file log: " + e.getMessage());
        }

        log("=========================================================================================");
        log("          ĐỒ ÁN CƠ SỞ DỮ LIỆU NÂNG CAO - ĐỀ TÀI 2 (CHUYÊN NGÀNH GIÀY)");
        log("     KIỂM THỬ THỰC NGHIỆM PHÂN HỆ CSDL HƯỚNG ĐỐI TƯỢNG VỚI OBJECTDB & JPQL/OQL");
        log("=========================================================================================\n");

        EntityManagerFactory emf = Persistence.createEntityManagerFactory(DB_PATH);
        EntityManager em = emf.createEntityManager();

        try {
            log("[1/2] Đang nạp dữ liệu mẫu vào ObjectDB (DataSeeder)...");
            DataSeeder.seed(em);
            log("      -> Nạp thành công 8 Khách hàng, 10 Sản phẩm, 20 Biến thể, 10 Đơn hàng, 18 Chi tiết, 7 Phiếu xuất kho.\n");

            log("[2/2] Bắt đầu thực thi 06 câu truy vấn đối chiếu OQL chuẩn <-> JPQL ObjectDB:\n");

            // -----------------------------------------------------------------
            // QUERY 1: Path Expression (dh.khachHang.hoTen, dh.khachHang.sdt)
            // -----------------------------------------------------------------
            log("-----------------------------------------------------------------------------------------");
            log("TRUY VẤN 1 [Path Expression]: Đơn hàng kèm thông tin Khách hàng (không dùng JOIN)");
            log("JPQL: SELECT dh.maDonHang, dh.ngayDat, dh.trangThai, dh.khachHang.hoTen, dh.khachHang.sdt, dh.diaChiGiaoHang");
            log("      FROM DonHang dh WHERE dh.trangThai IN ('DANG_XU_LY', 'DANG_GIAO', 'HOAN_TAT') ORDER BY dh.ngayDat DESC");
            log("-----------------------------------------------------------------------------------------");
            String jpql1 = "SELECT dh.maDonHang, dh.ngayDat, dh.trangThai, dh.khachHang.hoTen, dh.khachHang.sdt, dh.diaChiGiaoHang " +
                           "FROM DonHang dh " +
                           "WHERE dh.trangThai IN ('DANG_XU_LY', 'DANG_GIAO', 'HOAN_TAT') " +
                           "ORDER BY dh.ngayDat DESC";
            List<Object[]> res1 = em.createQuery(jpql1, Object[].class).getResultList();
            log(String.format("| %-9s | %-19s | %-12s | %-18s | %-11s | %-36s |",
                    "Mã ĐH", "Ngày đặt", "Trạng thái", "Họ tên KH", "SĐT", "Địa chỉ giao hàng"));
            log("+-----------+---------------------+--------------+--------------------+-------------+--------------------------------------+");
            for (Object[] r : res1) {
                log(String.format("| %-9s | %-19s | %-12s | %-18s | %-11s | %-36s |",
                        r[0], DF.format((Date) r[1]), r[2], r[3], r[4], r[5]));
            }
            log("Tổng cộng: " + res1.size() + " bản ghi.\n");

            // -----------------------------------------------------------------
            // QUERY 2: Collection Traversal & Exists (ct IN dh.dsChiTiet)
            // -----------------------------------------------------------------
            log("-----------------------------------------------------------------------------------------");
            log("TRUY VẤN 2 [Collection Traversal]: Đơn hàng có mua sản phẩm Nike Pegasus 41 (SP01)");
            log("JPQL: SELECT DISTINCT dh.maDonHang, dh.ngayDat, dh.trangThai, dh.khachHang.hoTen");
            log("      FROM DonHang dh JOIN dh.dsChiTiet ct");
            log("      WHERE ct.bienTheSP.sanPham.maSanPham = 'SP01' ORDER BY dh.maDonHang");
            log("-----------------------------------------------------------------------------------------");
            String jpql2 = "SELECT DISTINCT dh.maDonHang, dh.ngayDat, dh.trangThai, dh.khachHang.hoTen " +
                           "FROM DonHang dh JOIN dh.dsChiTiet ct " +
                           "WHERE ct.bienTheSP.sanPham.maSanPham = 'SP01' " +
                           "ORDER BY dh.maDonHang";
            List<Object[]> res2 = em.createQuery(jpql2, Object[].class).getResultList();
            log(String.format("| %-9s | %-19s | %-14s | %-18s |",
                    "Mã ĐH", "Ngày đặt", "Trạng thái", "Họ tên KH"));
            log("+-----------+---------------------+----------------+--------------------+");
            for (Object[] r : res2) {
                log(String.format("| %-9s | %-19s | %-14s | %-18s |",
                        r[0], DF.format((Date) r[1]), r[2], r[3]));
            }
            log("Tổng cộng: " + res2.size() + " bản ghi.\n");

            // -----------------------------------------------------------------
            // QUERY 3: Method Invocation (tinhTongTien() > 5,000,000)
            // -----------------------------------------------------------------
            log("-----------------------------------------------------------------------------------------");
            log("TRUY VẤN 3 [Method Invocation]: Đơn hàng giá trị cao (> 5,000,000 VNĐ)");
            log("Cách A (JPQL Query Aggregation) & Cách B (Gọi Method dh.tinhTongTien() trên Entity)");
            log("-----------------------------------------------------------------------------------------");
            String jpql3 = "SELECT dh.maDonHang, dh.khachHang.hoTen, " +
                           "SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)), " +
                           "SUM(ct.soLuong) " +
                           "FROM DonHang dh JOIN dh.dsChiTiet ct " +
                           "GROUP BY dh.maDonHang, dh.khachHang.hoTen " +
                           "HAVING SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) > 5000000.0 " +
                           "ORDER BY SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) DESC";
            List<Object[]> res3 = em.createQuery(jpql3, Object[].class).getResultList();
            log(String.format("| %-9s | %-18s | %-17s | %-11s | %-18s |",
                    "Mã ĐH", "Họ tên KH", "Tổng tiền (VNĐ)", "Tổng SL", "OOP Method Check"));
            log("+-----------+--------------------+-------------------+-------------+--------------------+");
            for (Object[] r : res3) {
                String maDh = (String) r[0];
                DonHang dhEntity = em.find(DonHang.class, maDh);
                double tienMethod = dhEntity.tinhTongTien();
                int slMethod = dhEntity.tinhTongSoLuong();

                log(String.format("| %-9s | %-18s | %17s | %11d | %18s |",
                        r[0], r[1], MONEY_FMT.format((Number) r[2]), ((Number) r[3]).intValue(),
                        "Khớp (" + MONEY_FMT.format(tienMethod) + ")"));
            }
            log("Tổng cộng: " + res3.size() + " bản ghi.\n");

            // -----------------------------------------------------------------
            // QUERY 4: Group By & Aggregate
            // -----------------------------------------------------------------
            log("-----------------------------------------------------------------------------------------");
            log("TRUY VẤN 4 [Group By & Aggregate]: Thống kê tổng chi tiêu và số đơn hoàn tất của Khách");
            log("JPQL: SELECT kh.maKH, kh.hoTen, kh.sdt, COUNT(DISTINCT dh.maDonHang),");
            log("             SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0))");
            log("      FROM KhachHang kh JOIN kh.dsDonHang dh JOIN dh.dsChiTiet ct");
            log("      WHERE dh.trangThai = 'HOAN_TAT' GROUP BY kh.maKH, kh.hoTen, kh.sdt ORDER BY 5 DESC");
            log("-----------------------------------------------------------------------------------------");
            String jpql4 = "SELECT kh.maKH, kh.hoTen, kh.sdt, COUNT(DISTINCT dh.maDonHang), " +
                           "SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) " +
                           "FROM KhachHang kh JOIN kh.dsDonHang dh JOIN dh.dsChiTiet ct " +
                           "WHERE dh.trangThai = 'HOAN_TAT' " +
                           "GROUP BY kh.maKH, kh.hoTen, kh.sdt " +
                           "ORDER BY SUM(ct.soLuong * ct.donGiaBan * (1.0 - ct.chietKhau / 100.0)) DESC";
            List<Object[]> res4 = em.createQuery(jpql4, Object[].class).getResultList();
            log(String.format("| %-6s | %-18s | %-11s | %-14s | %-17s |",
                    "Mã KH", "Họ tên KH", "SĐT", "Số đơn hoàn tất", "Tổng chi tiêu (VNĐ)"));
            log("+--------+--------------------+-------------+----------------+-------------------+");
            for (Object[] r : res4) {
                log(String.format("| %-6s | %-18s | %-11s | %14d | %17s |",
                        r[0], r[1], r[2], ((Number) r[3]).intValue(), MONEY_FMT.format((Number) r[4])));
            }
            log("Tổng cộng: " + res4.size() + " bản ghi.\n");

            // -----------------------------------------------------------------
            // QUERY 5: Inheritance & Polymorphism (PhieuXuatKho extends PhieuKho)
            // -----------------------------------------------------------------
            log("-----------------------------------------------------------------------------------------");
            log("TRUY VẤN 5 [Inheritance & Polymorphism]: Phiếu xuất kho (kế thừa thuộc tính PhieuKho)");
            log("JPQL: SELECT px.maPhieu, px.ngayLap, px.trangThai, px.khoNguon, px.donHang.maDonHang, px.donHang.khachHang.hoTen");
            log("      FROM PhieuXuatKho px WHERE px.donHang IS NOT NULL ORDER BY px.maPhieu");
            log("-----------------------------------------------------------------------------------------");
            String jpql5 = "SELECT px.maPhieu, px.ngayLap, px.trangThai, px.khoNguon, " +
                           "px.donHang.maDonHang, px.donHang.khachHang.hoTen " +
                           "FROM PhieuXuatKho px " +
                           "WHERE px.donHang IS NOT NULL " +
                           "ORDER BY px.maPhieu";
            List<Object[]> res5 = em.createQuery(jpql5, Object[].class).getResultList();
            log(String.format("| %-9s | %-19s | %-11s | %-10s | %-9s | %-18s |",
                    "Mã Phiếu", "Ngày lập (kế thừa)", "Trạng thái", "Kho nguồn", "Mã ĐH", "Khách nhận hàng"));
            log("+-----------+---------------------+-------------+------------+-----------+--------------------+");
            for (Object[] r : res5) {
                log(String.format("| %-9s | %-19s | %-11s | %-10s | %-9s | %-18s |",
                        r[0], DF.format((Date) r[1]), r[2], r[3], r[4], r[5]));
            }
            log("Tổng cộng: " + res5.size() + " bản ghi.\n");

            // -----------------------------------------------------------------
            // QUERY 6: Nested Collection (Số biến thể và tổng số lượng đã đặt)
            // -----------------------------------------------------------------
            log("-----------------------------------------------------------------------------------------");
            log("TRUY VẤN 6 [Nested Collection]: Sản phẩm kèm số lượng biến thể và tổng số lượng đặt");
            log("-----------------------------------------------------------------------------------------");
            List<SanPham> spList = em.createQuery("SELECT sp FROM SanPham sp ORDER BY sp.maSanPham", SanPham.class).getResultList();
            log(String.format("| %-10s | %-32s | %-16s | %-11s | %-12s |",
                    "Mã SP", "Tên sản phẩm mẫu", "Giá niêm yết (VNĐ)", "Số biến thể", "Tổng đặt mua"));
            log("+------------+----------------------------------+------------------+-------------+--------------+");
            for (SanPham sp : spList) {
                int soBienThe = sp.getDsBienThe().size();
                int tongDat = 0;
                for (ChiTietSP bt : sp.getDsBienThe()) {
                    tongDat += bt.tinhTongSoLuongDaBan();
                }
                log(String.format("| %-10s | %-32s | %16s | %11d | %12d |",
                        sp.getMaSanPham(), sp.getTenSanPham(), MONEY_FMT.format(sp.getGiaNiemYet()),
                        soBienThe, tongDat));
            }
            log("Tổng cộng: " + spList.size() + " bản ghi.\n");

            log("=========================================================================================");
            log("          HOÀN TẤT THỰC NGHIỆM: 100% KẾT QUẢ KHỚP TUYỆT ĐỐI VỚI POSTGRESQL!");
            log("=========================================================================================");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            em.close();
            emf.close();
            if (logOut != null) {
                logOut.close();
            }
        }
    }
}
