package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.constant.ScrapConstraints;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostScrapRequest {
    @Size(max = ScrapConstraints.MAX_REMARK_LENGTH)
    private String remark;
}
