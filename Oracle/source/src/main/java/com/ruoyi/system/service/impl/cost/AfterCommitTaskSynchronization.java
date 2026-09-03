package com.ruoyi.system.service.impl.cost;

import org.springframework.transaction.support.TransactionSynchronization;

class AfterCommitTaskSynchronization implements TransactionSynchronization {
    private final Runnable runnable;

    AfterCommitTaskSynchronization(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void afterCommit() {
        if (runnable != null) {
            runnable.run();
        }
    }
}
