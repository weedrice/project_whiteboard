package com.weedrice.whiteboard.global.common.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "commonCode", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommonCodeDetail> details = new ArrayList<>();
}
