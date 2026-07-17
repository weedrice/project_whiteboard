package com.weedrice.whiteboard.global.lock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "domain_locks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainLock {

    @Id
    @Column(name = "lock_name", length = 100, nullable = false)
    private String lockName;

    public DomainLock(String lockName) {
        this.lockName = lockName;
    }
}
