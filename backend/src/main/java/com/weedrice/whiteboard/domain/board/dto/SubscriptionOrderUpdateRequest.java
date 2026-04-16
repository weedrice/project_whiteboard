package com.weedrice.whiteboard.domain.board.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class SubscriptionOrderUpdateRequest {
    private List<String> boardUrls;
}
