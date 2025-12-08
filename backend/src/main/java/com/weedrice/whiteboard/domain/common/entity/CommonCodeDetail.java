package com.weedrice.whiteboard.domain.common.entity;

import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "common_code_details",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_code_details_value", columnNames = {"type_code", "code_value"})
        },
        indexes = {
                @Index(name = "idx_common_code_details_type", columnList = "type_code, is_active, sort_order")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CommonCodeDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_code", nullable = false)
    private CommonCode commonCode;

    @Column(name = "code_value", nullable = false, length = 100)
    private String codeValue;

    @Column(name = "code_name", nullable = false, length = 100)
    private String codeName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_active", nullable = false, length = 1)
    private Boolean isActive;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private LocalDateTime modifiedAt;

    @Builder
    public CommonCodeDetail(CommonCode commonCode, String codeValue, String codeName, Integer sortOrder, Boolean isActive) {
        this.commonCode = commonCode;
        this.codeValue = codeValue;
        this.codeName = codeName;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.isActive = isActive != null ? isActive : true;
    }

    public void update(String codeName, Integer sortOrder, Boolean isActive) {
        this.codeName = codeName;
        this.sortOrder = sortOrder;
        this.isActive = isActive;
    }
}
