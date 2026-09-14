package com.nilm.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StaffRegionAccessId implements Serializable {
    @Column(name = "staff_id")
    private Long staffId;
    @Column(name = "region_code", length = 20)
    private String regionCode;
    protected StaffRegionAccessId() {}
    public StaffRegionAccessId(Long staffId, String regionCode) {
        this.staffId = staffId;
        this.regionCode = regionCode;
    }
    public Long getStaffId() { return staffId; }
    public String getRegionCode() { return regionCode; }
    @Override public boolean equals(Object o) {
        return o instanceof StaffRegionAccessId that
                && Objects.equals(staffId, that.staffId) && Objects.equals(regionCode, that.regionCode);
    }
    @Override public int hashCode() { return Objects.hash(staffId, regionCode); }
}
