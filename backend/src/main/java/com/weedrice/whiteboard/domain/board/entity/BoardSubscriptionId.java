package com.weedrice.whiteboard.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BoardSubscriptionId implements Serializable {
    private Long user;
    private Long board;
}
