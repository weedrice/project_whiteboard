package com.weedrice.whiteboard.domain.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DraftRecoveryResponse {
    private DraftRecoveryStatus status;
    private boolean staleCandidate;
    private Long draftId;
    private DraftResponse draft;

    public static DraftRecoveryResponse available(DraftResponse draft, boolean staleCandidate) {
        return DraftRecoveryResponse.builder()
                .status(DraftRecoveryStatus.AVAILABLE)
                .staleCandidate(staleCandidate)
                .draftId(draft.getDraftId())
                .draft(draft)
                .build();
    }

    public static DraftRecoveryResponse protectedDraft(Long draftId, boolean staleCandidate) {
        return DraftRecoveryResponse.builder()
                .status(DraftRecoveryStatus.PROTECTED)
                .staleCandidate(staleCandidate)
                .draftId(draftId)
                .build();
    }

    public static DraftRecoveryResponse missing(boolean staleCandidate) {
        return DraftRecoveryResponse.builder()
                .status(DraftRecoveryStatus.MISSING)
                .staleCandidate(staleCandidate)
                .build();
    }

    public static DraftRecoveryResponse ambiguous(boolean staleCandidate) {
        return DraftRecoveryResponse.builder()
                .status(DraftRecoveryStatus.AMBIGUOUS)
                .staleCandidate(staleCandidate)
                .build();
    }
}
