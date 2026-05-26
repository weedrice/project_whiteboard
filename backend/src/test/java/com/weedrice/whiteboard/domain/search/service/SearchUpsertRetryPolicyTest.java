package com.weedrice.whiteboard.domain.search.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchUpsertRetryPolicyTest {

    private SearchUpsertRetryPolicy searchUpsertRetryPolicy;

    @BeforeEach
    void setUp() {
        searchUpsertRetryPolicy = new SearchUpsertRetryPolicy();
    }

    @Test
    void updateOrCreate_returnsWhenUpdateSucceeds() {
        AtomicInteger createCount = new AtomicInteger();

        searchUpsertRetryPolicy.updateOrCreate(() -> 1, createCount::incrementAndGet);

        assertThat(createCount).hasValue(0);
    }

    @Test
    void updateOrCreate_retriesUpdateAfterDuplicateCreate() {
        AtomicInteger updateCount = new AtomicInteger();

        searchUpsertRetryPolicy.updateOrCreate(
                () -> updateCount.getAndIncrement(),
                () -> {
                    throw new DataIntegrityViolationException("duplicate");
                });

        assertThat(updateCount).hasValue(2);
    }

    @Test
    void updateOrCreate_doesNotRequireRecoveryUpdate() {
        AtomicInteger updateCount = new AtomicInteger();

        searchUpsertRetryPolicy.updateOrCreate(
                () -> {
                    updateCount.incrementAndGet();
                    return 0;
                },
                () -> {
                    throw new DataIntegrityViolationException("duplicate");
                });

        assertThat(updateCount).hasValue(2);
    }

    @Test
    void updateOrCreateOrThrow_rethrowsWhenRecoveryUpdateFails() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("duplicate");

        assertThatThrownBy(() -> searchUpsertRetryPolicy.updateOrCreateOrThrow(
                () -> 0,
                () -> {
                    throw exception;
                }))
                .isSameAs(exception);
    }
}
