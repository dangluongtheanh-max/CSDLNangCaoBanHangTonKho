package com.csdl.model;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Lớp Khách hàng trong phân hệ CSDL Hướng đối tượng (ODMG 3.0 / JPA ObjectDB).
 */
@Entity
public class KhachHang implements Serializable {

    @Id
    private String maKH;

    private String hoTen;
    private String sdt;
    private String email;
    private String diaChi;

    @Temporal(TemporalType.TIMESTAMP)
    private Date ngayDangKy;

    // Quan hệ 1:N hai chiều với Đơn hàng (Collection: Set<DonHang>)
    @OneToMany(mappedBy = "khachHang", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<DonHang> dsDonHang = new HashSet<>();

    public KhachHang() {}

    public KhachHang(String maKH, String hoTen, String sdt, String email, String diaChi, Date ngayDangKy) {
        this.maKH = maKH;
        this.hoTen = hoTen;
        this.sdt = sdt;
        this.email = email;
        this.diaChi = diaChi;
        this.ngayDangKy = ngayDangKy;
    }

    // Phương thức nghiệp vụ hướng đối tượng (Business Method)
    public double tinhTongChiTieu() {
        double tong = 0.0;
        for (DonHang dh : dsDonHang) {
            if ("HOAN_TAT".equalsIgnoreCase(dh.getTrangThai())) {
                tong += dh.tinhTongTien();
            }
        }
        return tong;
    }

    public int demDonHoanTat() {
        int count = 0;
        for (DonHang dh : dsDonHang) {
            if ("HOAN_TAT".equalsIgnoreCase(dh.getTrangThai())) {
                count++;
            }
        }
        return count;
    }

    public boolean coDonChuaHoanTat() {
        for (DonHang dh : dsDonHang) {
            if (!"HOAN_TAT".equalsIgnoreCase(dh.getTrangThai()) && !"DA_HUY".equalsIgnoreCase(dh.getTrangThai())) {
                return true;
            }
        }
        return false;
    }

    // Getters và Setters
    public String getMaKH() { return maKH; }
    public void setMaKH(String maKH) { this.maKH = maKH; }

    public String getHoTen() { return hoTen; }
    public void setHoTen(String hoTen) { this.hoTen = hoTen; }

    public String getSdt() { return sdt; }
    public void setSdt(String sdt) { this.sdt = sdt; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDiaChi() { return diaChi; }
    public void setDiaChi(String diaChi) { this.diaChi = diaChi; }

    public Date getNgayDangKy() { return ngayDangKy; }
    public void setNgayDangKy(Date ngayDangKy) { this.ngayDangKy = ngayDangKy; }

    public Set<DonHang> getDsDonHang() { return dsDonHang; }
    public void setDsDonHang(Set<DonHang> dsDonHang) { this.dsDonHang = dsDonHang; }
}

