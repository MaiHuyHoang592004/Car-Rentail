package com.rentflow.scheduler;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ExpireHeldBookingsJobTest {

    @Test
    void disabledJobDoesNotCallProcessor() {
        ExpireHeldBookingsProcessor processor = mock(ExpireHeldBookingsProcessor.class);
        ExpireHeldBookingsJob job = new ExpireHeldBookingsJob(processor, false, 100);

        job.run();

        verify(processor, never()).processBatch(100);
    }

    @Test
    void enabledJobCallsProcessorWithConfiguredBatchSize() {
        ExpireHeldBookingsProcessor processor = mock(ExpireHeldBookingsProcessor.class);
        ExpireHeldBookingsJob job = new ExpireHeldBookingsJob(processor, true, 25);

        job.run();

        verify(processor).processBatch(25);
    }
}
