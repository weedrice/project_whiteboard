package com.weedrice.whiteboard.global.common.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "common_code_details",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_code_details_value", columnNames = {"type_code", "code_value"})
        },
        indexes = {
                @Index(name = "idx_common_code_details_type", columnList = "type_code, is_active, sort_order")
        })
public class CommonCodeDetail extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_code", nullable = false)
    private CommonCode commonCode;

    @Column(name = "code_value", length = 100, nullable = false)
    private String codeValue;

    @Column(name = "code_name", length = 100, nullable = false)
    private String codeName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", length = 1, nullable = false)
    private String isActive;

    @Builder
    public CommonCodeDetail(CommonCode commonCode, String codeValue, String codeName, Integer sortOrder, Boolean isActive) {
        this.commonCode = commonCode;
        this.codeValue = codeValue;
        this.codeName = codeName;
        this.sortOrder = sortOrder;
        this.isActive = isActive ? "Y" : "N";
    }

    public void update(String codeValue, String codeName, Integer sortOrder, Boolean isActive) {
        this.codeValue = codeValue;
        this.codeName = codeName;
        this.sortOrder = sortOrder;
        this.isActive = isActive ? "Y" : "N";
    }
}
