package com.omnibid.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class AfterCommitExecutor {

    public void execute(Runnable task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.warn("Error running post-commit task: {}", e.getMessage(), e);
                    }
                }
            });
        } else {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("Error running task: {}", e.getMessage(), e);
            }
        }
    }
}
