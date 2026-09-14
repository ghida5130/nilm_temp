package com.nilm.monitoring.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "staff_region_access")
public class StaffRegionAccess {
    @EmbeddedId
    private StaffRegionAccessId id;
    protected StaffRegionAccess() {}
    public StaffRegionAccess(Long staffId, String regionCode) {
        this.id = new StaffRegionAccessId(staffId, regionCode);
    }
    public StaffRegionAccessId getId() { return id; }
}
