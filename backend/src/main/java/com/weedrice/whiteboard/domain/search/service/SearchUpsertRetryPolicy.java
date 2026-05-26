package com.weedrice.whiteboard.domain.search.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.function.IntSupplier;

@Component
class SearchUpsertRetryPolicy {

    void updateOrCreate(IntSupplier update, Runnable create) {
        updateOrCreate(update, create, false);
    }

    void updateOrCreateOrThrow(IntSupplier update, Runnable create) {
        updateOrCreate(update, create, true);
    }

    private void updateOrCreate(IntSupplier update, Runnable create, boolean requireRecovery) {
        int updated = update.getAsInt();
        if (updated > 0) {
            return;
        }

        try {
            create.run();
        } catch (DataIntegrityViolationException exception) {
            int recovered = update.getAsInt();
            if (requireRecovery && recovered == 0) {
                throw exception;
            }
        }
    }
}
