package com.csdl.model;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Lớp Đơn hàng trong phân hệ CSDL Hướng đối tượng.
 */
@Entity
public class DonHang implements Serializable {

    @Id
    private String maDonHang;

    @Temporal(TemporalType.TIMESTAMP)
    private Date ngayDat;

    private String trangThai;
    private String diaChiGiaoHang;
    private String phuongThucThanhToan;
    private String ghiChu;
    private String maKho;

    // Quan hệ N:1 hai chiều với Khách hàng
    @ManyToOne(fetch = FetchType.EAGER)
    private KhachHang khachHang;

    // Quan hệ 1:N hai chiều với Chi tiết đơn (Dùng LIST để duy trì thứ tự)
    @OneToMany(mappedBy = "donHang", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChiTietDonHang> dsChiTiet = new ArrayList<>();

    // Quan hệ 1:1 hai chiều tùy chọn với Phiếu xuất kho
    @OneToOne(mappedBy = "donHang", fetch = FetchType.LAZY)
    private PhieuXuatKho phieuXuat;

    public DonHang() {}

    public DonHang(String maDonHang, Date ngayDat, String trangThai, String diaChiGiaoHang,
                   String phuongThucThanhToan, String ghiChu, String maKho, KhachHang khachHang) {
        this.maDonHang = maDonHang;
        this.ngayDat = ngayDat;
        this.trangThai = trangThai;
        this.diaChiGiaoHang = diaChiGiaoHang;
        this.phuongThucThanhToan = phuongThucThanhToan;
        this.ghiChu = ghiChu;
        this.maKho = maKho;
        this.khachHang = khachHang;
    }

    // Phương thức nghiệp vụ
    public double tinhTongTien() {
        double tong = 0.0;
        for (ChiTietDonHang ct : dsChiTiet) {
            tong += ct.tinhThanhTien();
        }
        return tong;
    }

    public int tinhTongSoLuong() {
        int tong = 0;
        for (ChiTietDonHang ct : dsChiTiet) {
            tong += ct.getSoLuong();
        }
        return tong;
    }

    public boolean duocPhepXuatKho() {
        return "DA_XAC_NHAN".equalsIgnoreCase(trangThai) || "DANG_XU_LY".equalsIgnoreCase(trangThai);
    }

    public boolean duocPhepXoaHuy() {
        return "CHO_XAC_NHAN".equalsIgnoreCase(trangThai)
                || "DA_XAC_NHAN".equalsIgnoreCase(trangThai)
                || "DANG_XU_LY".equalsIgnoreCase(trangThai);
    }

    // Getters và Setters
    public String getMaDonHang() { return maDonHang; }
    public void setMaDonHang(String maDonHang) { this.maDonHang = maDonHang; }

    public Date getNgayDat() { return ngayDat; }
    public void setNgayDat(Date ngayDat) { this.ngayDat = ngayDat; }

    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }

    public String getDiaChiGiaoHang() { return diaChiGiaoHang; }
    public void setDiaChiGiaoHang(String diaChiGiaoHang) { this.diaChiGiaoHang = diaChiGiaoHang; }

    public String getPhuongThucThanhToan() { return phuongThucThanhToan; }
    public void setPhuongThucThanhToan(String phuongThucThanhToan) { this.phuongThucThanhToan = phuongThucThanhToan; }

    public String getGhiChu() { return ghiChu; }
    public void setGhiChu(String ghiChu) { this.ghiChu = ghiChu; }

    public String getMaKho() { return maKho; }
    public void setMaKho(String maKho) { this.maKho = maKho; }

    public KhachHang getKhachHang() { return khachHang; }
    public void setKhachHang(KhachHang khachHang) { this.khachHang = khachHang; }

    public List<ChiTietDonHang> getDsChiTiet() { return dsChiTiet; }
    public void setDsChiTiet(List<ChiTietDonHang> dsChiTiet) { this.dsChiTiet = dsChiTiet; }

    public PhieuXuatKho getPhieuXuat() { return phieuXuat; }
    public void setPhieuXuat(PhieuXuatKho phieuXuat) { this.phieuXuat = phieuXuat; }
}

