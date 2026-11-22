package com.csdl.model;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Lớp Biến thể sản phẩm (theo Size / Màu sắc) trong phân hệ CSDL Hướng đối tượng.
 */
@Entity
public class ChiTietSP implements Serializable {

    @Id
    private String maChiTietSP;

    private String sku;
    private String size;
    private String mauSac;
    private String trangThai;

    // Quan hệ N:1 hai chiều với Sản phẩm cha
    @ManyToOne(fetch = FetchType.EAGER)
    private SanPham sanPham;

    // Quan hệ 1:N hai chiều với các dòng chi tiết đơn hàng đã đặt mua
    @OneToMany(mappedBy = "bienTheSP", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ChiTietDonHang> dsChiTietDonHang = new HashSet<>();

    public ChiTietSP() {}

    public ChiTietSP(String maChiTietSP, String sku, String size, String mauSac, String trangThai, SanPham sanPham) {
        this.maChiTietSP = maChiTietSP;
        this.sku = sku;
        this.size = size;
        this.mauSac = mauSac;
        this.trangThai = trangThai;
        this.sanPham = sanPham;
    }

    // Phương thức nghiệp vụ
    public int tinhTongSoLuongDaBan() {
        int tong = 0;
        for (ChiTietDonHang ct : dsChiTietDonHang) {
            if (ct.getDonHang() != null && !"DA_HUY".equalsIgnoreCase(ct.getDonHang().getTrangThai())) {
                tong += ct.getSoLuong();
            }
        }
        return tong;
    }

    // Getters và Setters
    public String getMaChiTietSP() { return maChiTietSP; }
    public void setMaChiTietSP(String maChiTietSP) { this.maChiTietSP = maChiTietSP; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getMauSac() { return mauSac; }
    public void setMauSac(String mauSac) { this.mauSac = mauSac; }

    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }

    public SanPham getSanPham() { return sanPham; }
    public void setSanPham(SanPham sanPham) { this.sanPham = sanPham; }

    public Set<ChiTietDonHang> getDsChiTietDonHang() { return dsChiTietDonHang; }
    public void setDsChiTietDonHang(Set<ChiTietDonHang> dsChiTietDonHang) { this.dsChiTietDonHang = dsChiTietDonHang; }
}

