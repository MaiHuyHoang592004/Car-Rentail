package com.rentflow.scheduler;

import com.rentflow.outbox.service.OutboxPublisherService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OutboxPublisherJobTest {

    @Test
    void runInvokesPublisherWhenEnabled() {
        OutboxPublisherService publisherService = mock(OutboxPublisherService.class);
        OutboxPublisherJob job = new OutboxPublisherJob(publisherService, true, 50, 5, 60, 300);

        job.run();

        verify(publisherService).processBatch(50, 5, 60, 300);
    }

    @Test
    void runSkipsWhenDisabled() {
        OutboxPublisherService publisherService = mock(OutboxPublisherService.class);
        OutboxPublisherJob job = new OutboxPublisherJob(publisherService, false, 50, 5, 60, 300);

        job.run();

        verifyNoInteractions(publisherService);
    }
}
