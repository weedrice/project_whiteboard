package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPostFile;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostFileRepository;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@Import(QuerydslConfig.class)
class ScheduledPostWithdrawalRepositoryTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);
    private static final LocalDateTime LEASE = NOW.minusMinutes(1);

    @Autowired TestEntityManager entityManager;
    @Autowired ScheduledPostRepository schedules;
    @Autowired ScheduledPostFileRepository reservations;
    @Autowired FileRepository files;
    @Autowired UserRepository users;

    @Test
    void withdrawalCancelsEveryUnpublishedStateAndReleasesOnlyItsReservations() {
        User owner = user("withdrawal-owner");
        User other = user("withdrawal-other");
        Board board = entityManager.persist(Board.builder().creator(owner)
                .boardName("Withdrawal schedules").boardUrl("withdrawal-schedules").build());
        Post publishedPost = entityManager.persist(Post.builder().board(board).user(owner)
                .title("Already published").contents("public content remains").build());
        List<Long> draftIds = new ArrayList<>();
        List<Long> fileIds = new ArrayList<>();
        List<Long> canceledIds = new ArrayList<>();
        for (String status : ScheduledPost.PROTECTED_DRAFT_STATUSES) {
            DraftPost draft = entityManager.persist(DraftPost.builder().userId(owner.getUserId()).board(board)
                    .title("Retained draft").contents("draft content").build());
            File file = entityManager.persist(File.builder().originalName("draft.png").filePath("test/" + status + ".png")
                    .fileSize(100L).mimeType("image/png").uploader(owner)
                    .relatedId(draft.getDraftId()).relatedType("DRAFT_POST").build());
            ScheduledPost schedule = schedule(owner, board, status, draft.getDraftId());
            entityManager.persist(new ScheduledPostFile(schedule.getScheduledPostId(), file.getFileId(), 1));
            draftIds.add(draft.getDraftId());
            fileIds.add(file.getFileId());
            canceledIds.add(schedule.getScheduledPostId());
        }
        ScheduledPost published = schedule(owner, board, ScheduledPost.STATUS_PUBLISHED, null);
        ReflectionTestUtils.setField(published, "publishedPostId", publishedPost.getPostId());
        ScheduledPost alreadyCanceled = schedule(owner, board, ScheduledPost.STATUS_CANCELED, null);
        ReflectionTestUtils.setField(alreadyCanceled, "canceledAt", NOW.minusDays(1));
        ScheduledPost otherSchedule = schedule(other, board, ScheduledPost.STATUS_SCHEDULED, null);
        File otherFile = entityManager.persist(File.builder().originalName("other.png").filePath("test/other.png")
                .fileSize(100L).mimeType("image/png").uploader(other).build());
        entityManager.persist(new ScheduledPostFile(otherSchedule.getScheduledPostId(), otherFile.getFileId(), 1));
        entityManager.flush();
        entityManager.clear();

        User lockedOwner = users.findByIdForUpdate(owner.getUserId()).orElseThrow();
        var service = new ScheduledPostWithdrawalService(schedules,
                new ScheduledPostFileService(files, reservations), Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        service.cancelUnpublishedForLockedUser(lockedOwner.getUserId());
        lockedOwner.delete(NOW);
        entityManager.flush();
        entityManager.clear();

        for (Long id : canceledIds) {
            ScheduledPost canceled = schedules.findById(id).orElseThrow();
            assertThat(canceled.getStatus()).isEqualTo(ScheduledPost.STATUS_CANCELED);
            assertThat(canceled.getCanceledAt()).isEqualTo(NOW);
            assertThat(canceled.getProcessingStartedAt()).isNull();
            assertThat(canceled.getDraftId()).isNull();
            assertThat(canceled.getFailureReason()).isNull();
            assertThat(schedules.claimForPublishing(id, NOW.plusDays(1), NOW.plusDays(1))).isZero();
            assertThat(schedules.markFailed(id, LEASE, "late worker failure")).isZero();
            assertThat(schedules.markPublished(id, LEASE, publishedPost.getPostId(), NOW)).isZero();
        }
        assertThat(schedules.findById(published.getScheduledPostId()).orElseThrow().getStatus()).isEqualTo("PUBLISHED");
        assertThat(entityManager.find(Post.class, publishedPost.getPostId()).getContents()).isEqualTo("public content remains");
        assertThat(schedules.findById(alreadyCanceled.getScheduledPostId()).orElseThrow().getCanceledAt()).isEqualTo(NOW.minusDays(1));
        assertThat(schedules.findById(otherSchedule.getScheduledPostId()).orElseThrow().getStatus()).isEqualTo("SCHEDULED");
        assertThat(reservations.findAll()).extracting(ref -> ref.getId().getScheduledPostId())
                .containsExactly(otherSchedule.getScheduledPostId());
        for (Long draftId : draftIds) {
            assertThat(entityManager.find(DraftPost.class, draftId)).isNotNull();
            assertThat(schedules.existsByDraftIdAndStatusIn(draftId, ScheduledPost.PROTECTED_DRAFT_STATUSES)).isFalse();
        }
        for (Long fileId : fileIds) {
            assertThat(files.findById(fileId).orElseThrow().getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
            assertThat(files.findById(fileId).orElseThrow().getRelatedType()).isEqualTo("DRAFT_POST");
        }

        // Restoring the same account does not restore canceled publication intent.
        User restored = users.findByIdForUpdate(owner.getUserId()).orElseThrow();
        restored.activate();
        entityManager.flush();
        service.cancelUnpublishedForLockedUser(owner.getUserId());
        assertThat(schedules.findUnpublishedIdsByUserId(owner.getUserId())).isEmpty();
        assertThat(schedules.findUnpublishedIdsByUserId(other.getUserId())).containsExactly(otherSchedule.getScheduledPostId());
    }

    private User user(String name) {
        return entityManager.persist(User.builder().loginId(name).email(name + "@example.test")
                .password("password").displayName(name).build());
    }

    private ScheduledPost schedule(User owner, Board board, String status, Long draftId) {
        ScheduledPost schedule = ScheduledPost.builder().userId(owner.getUserId()).board(board)
                .title("Scheduled content").contents("body").draftId(draftId).scheduledAt(NOW.minusHours(1)).build();
        ReflectionTestUtils.setField(schedule, "status", status);
        if (ScheduledPost.STATUS_PUBLISHING.equals(status)) {
            ReflectionTestUtils.setField(schedule, "processingStartedAt", LEASE);
        }
        if (ScheduledPost.STATUS_FAILED.equals(status)) {
            ReflectionTestUtils.setField(schedule, "failureReason", "old failure");
        }
        return entityManager.persist(schedule);
    }
}
