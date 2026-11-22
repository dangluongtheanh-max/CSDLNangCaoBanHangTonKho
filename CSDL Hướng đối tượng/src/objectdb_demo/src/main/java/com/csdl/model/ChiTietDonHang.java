package com.csdl.model;

import javax.persistence.*;
import java.io.Serializable;

/**
 * Lớp Chi tiết đơn hàng trong phân hệ CSDL Hướng đối tượng.
 */
@Entity
public class ChiTietDonHang implements Serializable {

    @Id
    private String maCTDonHang;

    private int soLuong;
    private double donGiaBan;
    private double chietKhau; // Tỷ lệ % (0..100)

    // Quan hệ N:1 hai chiều với Đơn hàng cha
    @ManyToOne(fetch = FetchType.EAGER)
    private DonHang donHang;

    // Quan hệ N:1 hai chiều với Biến thể sản phẩm (ChiTietSP)
    @ManyToOne(fetch = FetchType.EAGER)
    private ChiTietSP bienTheSP;

    public ChiTietDonHang() {}

    public ChiTietDonHang(String maCTDonHang, int soLuong, double donGiaBan, double chietKhau,
                          DonHang donHang, ChiTietSP bienTheSP) {
        this.maCTDonHang = maCTDonHang;
        this.soLuong = soLuong;
        this.donGiaBan = donGiaBan;
        this.chietKhau = chietKhau;
        this.donHang = donHang;
        this.bienTheSP = bienTheSP;
    }

    // Phương thức nghiệp vụ
    public double tinhThanhTien() {
        return soLuong * donGiaBan * (1.0 - chietKhau / 100.0);
    }

    // Getters và Setters
    public String getMaCTDonHang() { return maCTDonHang; }
    public void setMaCTDonHang(String maCTDonHang) { this.maCTDonHang = maCTDonHang; }

    public int getSoLuong() { return soLuong; }
    public void setSoLuong(int soLuong) { this.soLuong = soLuong; }

    public double getDonGiaBan() { return donGiaBan; }
    public void setDonGiaBan(double donGiaBan) { this.donGiaBan = donGiaBan; }

    public double getChietKhau() { return chietKhau; }
    public void setChietKhau(double chietKhau) { this.chietKhau = chietKhau; }

    public DonHang getDonHang() { return donHang; }
    public void setDonHang(DonHang donHang) { this.donHang = donHang; }

    public ChiTietSP getBienTheSP() { return bienTheSP; }
    public void setBienTheSP(ChiTietSP bienTheSP) { this.bienTheSP = bienTheSP; }
}

