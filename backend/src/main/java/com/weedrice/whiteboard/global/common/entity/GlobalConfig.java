package com.weedrice.whiteboard.global.common.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "global_configs")
public class GlobalConfig extends BaseTimeEntity {

    @Id
    @Column(name = "config_key", length = 100)
    private String configKey;

    @Column(name = "config_value", length = 255)
    private String configValue;

    @Column(name = "description", length = 255)
    private String description;

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }
}
