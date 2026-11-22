package com.csdl.model;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Lớp cha trừu tượng Phiếu kho (Generalization Base Class).
 * Minh họa tính kế thừa (Inheritance) trong CSDL Hướng đối tượng.
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class PhieuKho implements Serializable {

    @Id
    private String maPhieu;

    private String loaiPhieu; // NHAP, XUAT, CHUYEN

    @Temporal(TemporalType.TIMESTAMP)
    private Date ngayLap;

    private String trangThai; // BAN_NHAP, DANG_XU_LY, DANG_VAN_CHUYEN, HOAN_TAT, DA_HUY
    private String ghiChu;

    public PhieuKho() {}

    public PhieuKho(String maPhieu, String loaiPhieu, Date ngayLap, String trangThai, String ghiChu) {
        this.maPhieu = maPhieu;
        this.loaiPhieu = loaiPhieu;
        this.ngayLap = ngayLap;
        this.trangThai = trangThai;
        this.ghiChu = ghiChu;
    }

    // Phương thức nghiệp vụ chung
    public boolean laPhieuHoanTat() {
        return "HOAN_TAT".equalsIgnoreCase(trangThai);
    }

    public boolean duocPhepChinhSua() {
        return "BAN_NHAP".equalsIgnoreCase(trangThai);
    }

    // Getters và Setters
    public String getMaPhieu() { return maPhieu; }
    public void setMaPhieu(String maPhieu) { this.maPhieu = maPhieu; }

    public String getLoaiPhieu() { return loaiPhieu; }
    public void setLoaiPhieu(String loaiPhieu) { this.loaiPhieu = loaiPhieu; }

    public Date getNgayLap() { return ngayLap; }
    public void setNgayLap(Date ngayLap) { this.ngayLap = ngayLap; }

    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }

    public String getGhiChu() { return ghiChu; }
    public void setGhiChu(String ghiChu) { this.ghiChu = ghiChu; }
}

