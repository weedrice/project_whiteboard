package com.weedrice.whiteboard.domain.board.entity;

import java.io.Serializable;
import java.util.Objects;

public class BoardVisitId implements Serializable {
    private Long user;
    private Long board;

    public BoardVisitId() {
    }

    public BoardVisitId(Long user, Long board) {
        this.user = user;
        this.board = board;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BoardVisitId that)) {
            return false;
        }
        return Objects.equals(user, that.user) && Objects.equals(board, that.board);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, board);
    }
}
