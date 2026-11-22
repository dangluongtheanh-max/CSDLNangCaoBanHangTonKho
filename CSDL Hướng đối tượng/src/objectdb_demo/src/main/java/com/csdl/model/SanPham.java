package com.csdl.model;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Lớp Sản phẩm (Mẫu giày) trong phân hệ CSDL Hướng đối tượng.
 */
@Entity
public class SanPham implements Serializable {

    @Id
    private String maSanPham;

    private String tenSanPham;
    private String moTa;
    private double giaNiemYet;
    private String trangThai;
    private String maDanhMuc;
    private String maThuongHieu;

    // Quan hệ 1:N hai chiều với Biến thể sản phẩm (Collection: Set<ChiTietSP>)
    @OneToMany(mappedBy = "sanPham", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ChiTietSP> dsBienThe = new HashSet<>();

    public SanPham() {}

    public SanPham(String maSanPham, String tenSanPham, String moTa, double giaNiemYet,
                   String trangThai, String maDanhMuc, String maThuongHieu) {
        this.maSanPham = maSanPham;
        this.tenSanPham = tenSanPham;
        this.moTa = moTa;
        this.giaNiemYet = giaNiemYet;
        this.trangThai = trangThai;
        this.maDanhMuc = maDanhMuc;
        this.maThuongHieu = maThuongHieu;
    }

    // Phương thức nghiệp vụ
    public int demSoBienTheDangBan() {
        int count = 0;
        for (ChiTietSP bt : dsBienThe) {
            if ("DANG_BAN".equalsIgnoreCase(bt.getTrangThai())) {
                count++;
            }
        }
        return count;
    }

    public boolean coTheDatHang() {
        return "DANG_KINH_DOANH".equalsIgnoreCase(trangThai) && demSoBienTheDangBan() > 0;
    }

    // Getters và Setters
    public String getMaSanPham() { return maSanPham; }
    public void setMaSanPham(String maSanPham) { this.maSanPham = maSanPham; }

    public String getTenSanPham() { return tenSanPham; }
    public void setTenSanPham(String tenSanPham) { this.tenSanPham = tenSanPham; }

    public String getMoTa() { return moTa; }
    public void setMoTa(String moTa) { this.moTa = moTa; }

    public double getGiaNiemYet() { return giaNiemYet; }
    public void setGiaNiemYet(double giaNiemYet) { this.giaNiemYet = giaNiemYet; }

    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }

    public String getMaDanhMuc() { return maDanhMuc; }
    public void setMaDanhMuc(String maDanhMuc) { this.maDanhMuc = maDanhMuc; }

    public String getMaThuongHieu() { return maThuongHieu; }
    public void setMaThuongHieu(String maThuongHieu) { this.maThuongHieu = maThuongHieu; }

    public Set<ChiTietSP> getDsBienThe() { return dsBienThe; }
    public void setDsBienThe(Set<ChiTietSP> dsBienThe) { this.dsBienThe = dsBienThe; }
}

