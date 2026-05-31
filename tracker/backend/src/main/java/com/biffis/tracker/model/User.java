package com.biffis.tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps the {@code users} table (see V1__schema.sql). Hibernate runs in
 * validate mode, so column names/types here must match the migration exactly.
 *
 * {@code passwordHash} is never serialized to JSON — controllers return
 * {@code dto.UserView}, never this entity.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column
    private String gender;

    // --- Biometrics (V8). Stable facts only; weight/height/age are derived. ---

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "biological_sex")
    private String biologicalSex;

    @Column(name = "blood_type")
    private String bloodType;

    @Column(name = "activity_level")
    private String activityLevel;

    @Column(name = "weight_goal")
    private String weightGoal;

    @Column(name = "drug_allergies")
    private String drugAllergies;

    @Column(name = "chronic_conditions")
    private String chronicConditions;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected User() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public String getGender() {
        return gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getBiologicalSex() {
        return biologicalSex;
    }

    public void setBiologicalSex(String biologicalSex) {
        this.biologicalSex = biologicalSex;
    }

    public String getBloodType() {
        return bloodType;
    }

    public void setBloodType(String bloodType) {
        this.bloodType = bloodType;
    }

    public String getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(String activityLevel) {
        this.activityLevel = activityLevel;
    }

    public String getWeightGoal() {
        return weightGoal;
    }

    public void setWeightGoal(String weightGoal) {
        this.weightGoal = weightGoal;
    }

    public String getDrugAllergies() {
        return drugAllergies;
    }

    public void setDrugAllergies(String drugAllergies) {
        this.drugAllergies = drugAllergies;
    }

    public String getChronicConditions() {
        return chronicConditions;
    }

    public void setChronicConditions(String chronicConditions) {
        this.chronicConditions = chronicConditions;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
