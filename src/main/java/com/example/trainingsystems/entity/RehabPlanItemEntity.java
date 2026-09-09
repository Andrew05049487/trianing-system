package com.example.trainingsystems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "rehab_plan_items",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_rehab_plan_items_plan_exercise",
        columnNames = {"rehab_plan_id", "exercise_id"}
    ),
    indexes = @Index(
        name = "idx_rehab_plan_items_plan_order",
        columnList = "rehab_plan_id,item_order"
    )
)
@Getter
@Setter
public class RehabPlanItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rehab_plan_id", nullable = false)
    private RehabPlanEntity rehabPlan;

    @Column(name = "exercise_id", nullable = false, length = 100)
    private String exerciseId;

    @Column(name = "item_order", nullable = false)
    private Integer itemOrder;

    @Column(nullable = false)
    private Integer sets;

    @Column(name = "reps_per_set", nullable = false)
    private Integer repsPerSet;

    @Column(nullable = false)
    private boolean done;
}
