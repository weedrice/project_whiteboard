package com.weedrice.whiteboard.domain.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.LocalDateTime;

@Entity
@Table(name = "common_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CommonCode {

    @Id
    @Column(name = "type_code", length = 50)
    private String typeCode;

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Column(name = "description")
    private String description;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private LocalDateTime modifiedAt;

    @Builder
    public CommonCode(String typeCode, String typeName, String description) {
        this.typeCode = typeCode;
        this.typeName = typeName;
        this.description = description;
    }

    public void update(String typeName, String description) {
        this.typeName = typeName;
        this.description = description;
    }
}
