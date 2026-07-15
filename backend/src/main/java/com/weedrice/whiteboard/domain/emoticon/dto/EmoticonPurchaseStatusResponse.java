package com.weedrice.whiteboard.domain.emoticon.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmoticonPurchaseStatusResponse {
    private boolean purchased;
    private boolean available;
    private int price;
}
