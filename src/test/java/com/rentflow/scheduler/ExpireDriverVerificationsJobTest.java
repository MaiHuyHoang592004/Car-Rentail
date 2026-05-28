package com.rentflow.scheduler;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ExpireDriverVerificationsJobTest {

    @Test
    void disabledJobDoesNotCallProcessor() {
        ExpireDriverVerificationsProcessor processor = mock(ExpireDriverVerificationsProcessor.class);
        ExpireDriverVerificationsJob job = new ExpireDriverVerificationsJob(processor, false, 100);

        job.run();

        verify(processor, never()).processBatch(100);
    }

    @Test
    void enabledJobCallsProcessorWithConfiguredBatchSize() {
        ExpireDriverVerificationsProcessor processor = mock(ExpireDriverVerificationsProcessor.class);
        ExpireDriverVerificationsJob job = new ExpireDriverVerificationsJob(processor, true, 20);

        job.run();

        verify(processor).processBatch(20);
    }
}
