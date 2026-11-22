package com.omnibid.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class AfterCommitExecutor {

    public void runAfterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeSafely(action);
                }
            });
        } else {
            executeSafely(action);
        }
    }

    private void executeSafely(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.warn("Tác vụ sau COMMIT gặp lỗi: {}", ex.getMessage(), ex);
        }
    }
}
