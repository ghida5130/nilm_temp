package com.nilm.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

@Entity
@Table(name = "care_subject")
public class CareSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "risk_policy_id")
    private Long riskPolicyId;

    @Column(name = "household_id", nullable = false, length = 50)
    private String householdId;

    @Column(name = "subject_number", nullable = false, unique = true, length = 50)
    private String subjectNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] name;

    @Column(name = "name_blind_index", length = 64)
    private String nameBlindIndex;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] phone;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] address;

    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubjectStatus status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected CareSubject() {
    }

    private CareSubject(String householdId, String subjectNumber, LocalDate birthDate,
                        byte[] name, byte[] phone, byte[] address, String regionCode,
                        Long riskPolicyId, Instant now) {
        this.householdId = DomainChecks.text(householdId, "householdId", 50);
        this.subjectNumber = DomainChecks.text(subjectNumber, "subjectNumber", 50);
        this.birthDate = DomainChecks.required(birthDate, "birthDate");
        this.name = copyRequired(name, "name");
        this.phone = copyRequired(phone, "phone");
        this.address = copyRequired(address, "address");
        this.regionCode = DomainChecks.text(regionCode, "regionCode", 20);
        this.riskPolicyId = positiveIdOrNull(riskPolicyId, "riskPolicyId");
        this.status = SubjectStatus.PENDING;
        this.registeredAt = DomainChecks.required(now, "now");
        this.updatedAt = now;
    }

    public static CareSubject register(String householdId, String subjectNumber,
                                       LocalDate birthDate, byte[] name, byte[] phone,
                                       byte[] address, String regionCode, Long riskPolicyId,
                                       Instant now) {
        return new CareSubject(householdId, subjectNumber, birthDate, name, phone,
                address, regionCode, riskPolicyId, now);
    }

    public static CareSubject register(String householdId, String subjectNumber,
                                       LocalDate birthDate, byte[] name, String nameBlindIndex,
                                       byte[] phone, byte[] address, String regionCode,
                                       Long riskPolicyId, Instant now) {
        CareSubject subject = register(householdId, subjectNumber, birthDate, name, phone,
                address, regionCode, riskPolicyId, now);
        subject.setNameBlindIndex(nameBlindIndex);
        return subject;
    }

    public void setNameBlindIndex(String nameBlindIndex) {
        if (nameBlindIndex == null || !nameBlindIndex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("nameBlindIndex must be a lowercase SHA-256 hex value");
        }
        this.nameBlindIndex = nameBlindIndex;
    }

    public void activate(Instant now) {
        requireStatus(SubjectStatus.PENDING, "Only a pending subject can be activated");
        touch(now);
        status = SubjectStatus.ACTIVE;
        if (activatedAt == null) {
            activatedAt = now;
        }
    }

    public void pause(Instant now) {
        requireStatus(SubjectStatus.ACTIVE, "Only an active subject can be paused");
        touch(now);
        status = SubjectStatus.PAUSED;
    }

    public void resume(Instant now) {
        requireStatus(SubjectStatus.PAUSED, "Only a paused subject can be resumed");
        touch(now);
        status = SubjectStatus.ACTIVE;
    }

    public void end(Instant now) {
        requireOngoing("Only an ongoing subject can be ended");
        touch(now);
        status = SubjectStatus.ENDED;
        endedAt = now;
    }

    public void markDeceased(Instant now) {
        requireOngoing("Only an ongoing subject can be marked deceased");
        touch(now);
        status = SubjectStatus.DECEASED;
        endedAt = now;
    }

    public void changeRiskPolicy(Long riskPolicyId, Instant now) {
        requireNotEnded();
        Long validatedRiskPolicyId = positiveIdOrNull(riskPolicyId, "riskPolicyId");
        touch(now);
        this.riskPolicyId = validatedRiskPolicyId;
    }

    public void updateContact(byte[] name, byte[] phone, byte[] address,
                              String regionCode, Instant now) {
        requireNotEnded();
        byte[] validatedName = copyRequired(name, "name");
        byte[] validatedPhone = copyRequired(phone, "phone");
        byte[] validatedAddress = copyRequired(address, "address");
        String validatedRegionCode = DomainChecks.text(regionCode, "regionCode", 20);
        touch(now);
        this.name = validatedName;
        this.nameBlindIndex = null;
        this.phone = validatedPhone;
        this.address = validatedAddress;
        this.regionCode = validatedRegionCode;
    }

    public void updateContact(byte[] name, String nameBlindIndex, byte[] phone, byte[] address,
                              String regionCode, Instant now) {
        updateContact(name, phone, address, regionCode, now);
        setNameBlindIndex(nameBlindIndex);
    }

    private void touch(Instant now) {
        DomainChecks.chronological(updatedAt, now, "now");
        updatedAt = now;
    }

    private void requireStatus(SubjectStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private void requireOngoing(String message) {
        if (status == SubjectStatus.ENDED || status == SubjectStatus.DECEASED) {
            throw new IllegalStateException(message);
        }
    }

    private void requireNotEnded() {
        requireOngoing("An ended subject cannot be changed");
    }

    private static byte[] copyRequired(byte[] value, String name) {
        DomainChecks.required(value, name);
        if (value.length == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return Arrays.copyOf(value, value.length);
    }

    private static Long positiveIdOrNull(Long value, String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public Long getId() { return id; }
    public Long getRiskPolicyId() { return riskPolicyId; }
    public String getHouseholdId() { return householdId; }
    public String getSubjectNumber() { return subjectNumber; }
    public LocalDate getBirthDate() { return birthDate; }
    public byte[] getName() { return Arrays.copyOf(name, name.length); }
    public String getNameBlindIndex() { return nameBlindIndex; }
    public byte[] getPhone() { return Arrays.copyOf(phone, phone.length); }
    public byte[] getAddress() { return Arrays.copyOf(address, address.length); }
    public String getRegionCode() { return regionCode; }
    public SubjectStatus getStatus() { return status; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getEndedAt() { return endedAt; }
}
