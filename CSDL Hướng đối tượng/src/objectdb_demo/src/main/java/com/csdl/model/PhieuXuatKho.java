package com.csdl.model;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Lớp con Phiếu xuất kho chuyên biệt hóa từ PhieuKho (Specialization Subclass).
 * Kế thừa toàn bộ thuộc tính và phương thức của PhieuKho.
 */
@Entity
public class PhieuXuatKho extends PhieuKho implements Serializable {

    private String khoNguon;
    private String maPhieuTra;

    // Quan hệ 1:1 với Đơn hàng xuất bán
    @OneToOne(fetch = FetchType.EAGER)
    private DonHang donHang;

    public PhieuXuatKho() {
        super();
    }

    public PhieuXuatKho(String maPhieu, Date ngayLap, String trangThai, String ghiChu,
                        String khoNguon, String maPhieuTra, DonHang donHang) {
        super(maPhieu, "XUAT", ngayLap, trangThai, ghiChu);
        this.khoNguon = khoNguon;
        this.maPhieuTra = maPhieuTra;
        this.donHang = donHang;
    }

    // Phương thức nghiệp vụ riêng của lớp con
    public boolean laXuatGiaoDonHang() {
        return donHang != null;
    }

    // Getters và Setters
    public String getKhoNguon() { return khoNguon; }
    public void setKhoNguon(String khoNguon) { this.khoNguon = khoNguon; }

    public String getMaPhieuTra() { return maPhieuTra; }
    public void setMaPhieuTra(String maPhieuTra) { this.maPhieuTra = maPhieuTra; }

    public DonHang getDonHang() { return donHang; }
    public void setDonHang(DonHang donHang) { this.donHang = donHang; }
}

