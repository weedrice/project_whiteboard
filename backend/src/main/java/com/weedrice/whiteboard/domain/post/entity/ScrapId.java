package com.weedrice.whiteboard.domain.post.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ScrapId implements Serializable {
    private Long user;
    private Long post;
}
