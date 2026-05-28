package com.rentflow.scheduler;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ExpireHostApprovalsJobTest {

    @Test
    void disabledJobDoesNotCallProcessor() {
        ExpireHostApprovalsProcessor processor = mock(ExpireHostApprovalsProcessor.class);
        ExpireHostApprovalsJob job = new ExpireHostApprovalsJob(processor, false, 100);

        job.run();

        verify(processor, never()).processBatch(100);
    }

    @Test
    void enabledJobCallsProcessor() {
        ExpireHostApprovalsProcessor processor = mock(ExpireHostApprovalsProcessor.class);
        ExpireHostApprovalsJob job = new ExpireHostApprovalsJob(processor, true, 25);

        job.run();

        verify(processor).processBatch(25);
    }
}
