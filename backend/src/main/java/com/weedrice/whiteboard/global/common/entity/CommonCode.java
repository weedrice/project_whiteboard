package com.weedrice.whiteboard.global.common.entity;

import com.weedrice.whiteboard.domain.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "common_codes")
public class CommonCode extends BaseTimeEntity {

    @Id
    @Column(name = "type_code", length = 50)
    private String typeCode;

    @Column(name = "type_name", length = 100, nullable = false)
    private String typeName;

    @Column(name = "description", length = 255)
    private String description;
}
