package com.example.trainingsystems.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "rehab_plans",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_rehab_plans_patient_date",
            columnNames = {"patient_id", "plan_date"}
        ),
        @UniqueConstraint(
            name = "uq_rehab_plans_plan_id",
            columnNames = "plan_id"
        )
    },
    indexes = @Index(
        name = "idx_rehab_plans_patient_date",
        columnList = "patient_id,plan_date"
    )
)
@Getter
@Setter
public class RehabPlanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false, length = 100)
    private String planId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "condition_type", nullable = false, length = 20)
    private String condition;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(
        mappedBy = "rehabPlan",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderBy("itemOrder ASC, id ASC")
    private List<RehabPlanItemEntity> items = new ArrayList<>();

    public void addItem(RehabPlanItemEntity item) {
        items.add(item);
        item.setRehabPlan(this);
    }

    public void removeItem(RehabPlanItemEntity item) {
        items.remove(item);
        item.setRehabPlan(null);
    }

    @PrePersist
    public void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void beforeUpdate() {
        updatedAt = Instant.now();
    }
}
