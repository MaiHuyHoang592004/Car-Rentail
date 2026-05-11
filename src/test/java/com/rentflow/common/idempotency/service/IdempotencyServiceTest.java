package com.rentflow.common.idempotency.service;

import com.rentflow.common.exception.IdempotencyException;
import com.rentflow.common.idempotency.entity.IdempotencyKey;
import com.rentflow.common.idempotency.entity.IdempotencyStatus;
import com.rentflow.common.idempotency.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-11T00:00:00Z");
    private static final Instant LOCKED_UNTIL = Instant.parse("2026-05-11T00:00:30Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-05-16T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ROW_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String KEY = "8B71F8D2-9E1D-4F7A-BBE6-334C3816DF91";
    private static final String NORMALIZED_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";
    private static final String REQUEST_HASH = "same-hash";
    private static final String DIFFERENT_HASH = "different-hash";

    @Mock
    private IdempotencyKeyRepository repository;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new IdempotencyService(repository, clock);
    }

    @Test
    void resolveWithNoExistingRecordInsertsProcessingAndProceeds() {
        when(repository.insertProcessingIfAbsent(
                any(), eq(USER_ID), eq(IdempotencyScope.CREATE_BOOKING),
                eq(NORMALIZED_KEY), eq(REQUEST_HASH), eq(LOCKED_UNTIL), eq(EXPIRES_AT)))
                .thenReturn(1);

        IdempotencyResolution resolution = service.resolve(
                USER_ID, IdempotencyScope.CREATE_BOOKING, KEY, REQUEST_HASH);

        assertThat(resolution).isInstanceOf(IdempotencyResolution.Proceed.class);
        verify(repository).insertProcessingIfAbsent(
                any(), eq(USER_ID), eq(IdempotencyScope.CREATE_BOOKING),
                eq(NORMALIZED_KEY), eq(REQUEST_HASH), eq(LOCKED_UNTIL), eq(EXPIRES_AT));
        verify(repository, never()).findByUserIdAndScopeAndKeyForUpdate(any(), any(), any());
    }

    @Test
    void resolveProcessingWithActiveLockThrowsForSameHash() {
        IdempotencyKey existing = existing(IdempotencyStatus.PROCESSING, REQUEST_HASH, NOW.plusSeconds(5));
        mockDuplicate(existing);

        assertAlreadyProcessing(() -> service.resolve(
                USER_ID, IdempotencyScope.CREATE_BOOKING, KEY, REQUEST_HASH));
        verify(repository, never()).save(any());
    }

    @Test
    void resolveProcessingWithActiveLockThrowsForDifferentHash() {
        IdempotencyKey existing = existing(IdempotencyStatus.PROCESSING, REQUEST_HASH, NOW.plusSeconds(5));
        mockDuplicate(existing);

        assertAlreadyProcessing(() -> service.resolve(
                USER_ID, IdempotencyScope.CREATE_BOOKING, KEY, DIFFERENT_HASH));
        verify(repository, never()).save(any());
    }

    @Test
    void resolveProcessingWithExpiredLockRetriesSameHash() {
        IdempotencyKey existing = existing(IdempotencyStatus.PROCESSING, REQUEST_HASH, NOW.minusSeconds(1));
        mockDuplicate(existing);

        IdempotencyResolution resolution = service.resolve(
                USER_ID, IdempotencyScope.CREATE_BOOKING, KEY, REQUEST_HASH);

        assertRetryAllowed(resolution, REQUEST_HASH);
    }

    @Test
    void resolveProcessingWithExpiredLockRetriesDifferentHash() {
        IdempotencyKey existing = existing(IdempotencyStatus.PROCESSING, REQUEST_HASH, NOW.minusSeconds(1));
        mockDuplicate(existing);

        IdempotencyResolution resolution = service.resolve(
                USER_ID, IdempotencyScope.CREATE_BOOKING, KEY, DIFFERENT_HASH);

        assertRetryAllowed(resolution, DIFFERENT_HASH);
    }

    @Test
    void resolveCompletedWithSameHashReplaysStoredResponse() {
        IdempotencyKey existing = existing(IdempotencyStatus.COMPLETED, REQUEST_HASH, null);
        existing.setResponseStatus(201);
        existing.setResponseBody("{\"id\":\"booking-1\"}");
        mockDuplicate(existing);

        IdempotencyResolution resolution = service.resolve(
                USER_ID, IdempotencyScope.CREATE_BOOKING, KEY, REQUEST_HASH);

        assertThat(resolution).isEqualTo(
                IdempotencyResolution.replay(201, "{\"id\":\"booking-1\"}"));
        verify(repository, never()).save(any());
    }

    @Test
    void resolveCompletedWithDifferentHashThrowsConflict() {
        IdempotencyKey existing = existing(IdempotencyStatus.COMPLETED, REQUEST_HASH, null);
        mockDuplicate(existing);

        assertThatThrownBy(() -> service.resolve(
                USER_ID, IdempotencyScope.CREATE_BOOKING, KEY, DIFFERENT_HASH))
                .isInstanceOf(IdempotencyException.class)
                .hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_KEY_CONFLICT");
        verify(repository, never()).save(any());
    }

    @Test
    void resolveFailedWithActiveLockThrowsForSameHash() {
        IdempotencyKey existing = existing(IdempotencyStatus.FAILED, REQUEST_HASH, NOW.plusSeconds(5));
        mockDuplicate(existing);

        assertAlreadyProcessing(() -> service.resolve(
                USER_ID, IdempotencyScope.CREATE_BOOKING, KEY, REQUEST_HASH));
    }

    @Test
    void resolveFailedWithActiveLockThrowsForDifferentHash() {
        IdempotencyKey existing = existing(IdempotencyStatus.FAILED, REQUEST_HASH, NOW.plusSeconds(5));
        mockDuplicate(existing);

        assertAlreadyProcessing(() -> service.resolve(
                USER_ID, IdempotencyScope.CREATE_BOOKING, KEY, DIFFERENT_HASH));
    }

    @Test
    void resolveFailedWithExpiredLockRetriesSameHash() {
        IdempotencyKey existing = existing(IdempotencyStatus.FAILED, REQUEST_HASH, NOW.minusSeconds(1));
        mockDuplicate(existing);

        IdempotencyResolution resolution = service.resolve(
                USER_ID, IdempotencyScope.CREATE_BOOKING, KEY, REQUEST_HASH);

        assertRetryAllowed(resolution, REQUEST_HASH);
    }

    @Test
    void resolveFailedWithExpiredLockRetriesDifferentHash() {
        IdempotencyKey existing = existing(IdempotencyStatus.FAILED, REQUEST_HASH, NOW.minusSeconds(1));
        mockDuplicate(existing);

        IdempotencyResolution resolution = service.resolve(
                USER_ID, IdempotencyScope.CREATE_BOOKING, KEY, DIFFERENT_HASH);

        assertRetryAllowed(resolution, DIFFERENT_HASH);
    }

    @Test
    void completeStoresResponseAndClearsLock() {
        IdempotencyKey existing = existing(IdempotencyStatus.PROCESSING, REQUEST_HASH, LOCKED_UNTIL);
        when(repository.findByIdForUpdate(ROW_ID)).thenReturn(Optional.of(existing));

        service.complete(ROW_ID, 201, "{\"id\":\"booking-1\"}");

        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(repository).save(captor.capture());
        IdempotencyKey saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(saved.getResponseStatus()).isEqualTo(201);
        assertThat(saved.getResponseBody()).isEqualTo("{\"id\":\"booking-1\"}");
        assertThat(saved.getLockedUntil()).isNull();
    }

    @Test
    void failMarksProcessingRowFailedAndExtendsLock() {
        IdempotencyKey existing = existing(IdempotencyStatus.PROCESSING, REQUEST_HASH, LOCKED_UNTIL);
        when(repository.findByIdForUpdate(ROW_ID)).thenReturn(Optional.of(existing));

        service.fail(ROW_ID);

        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(repository).save(captor.capture());
        IdempotencyKey saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(IdempotencyStatus.FAILED);
        assertThat(saved.getLockedUntil()).isEqualTo(LOCKED_UNTIL);
    }

    @Test
    void failDoesNotOverwriteCompletedRow() {
        IdempotencyKey existing = existing(IdempotencyStatus.COMPLETED, REQUEST_HASH, null);
        when(repository.findByIdForUpdate(ROW_ID)).thenReturn(Optional.of(existing));

        service.fail(ROW_ID);

        verify(repository, never()).save(any());
        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
    }

    @Test
    void failDoesNotThrowWhenRepositoryFails() {
        when(repository.findByIdForUpdate(ROW_ID)).thenThrow(new RuntimeException("database unavailable"));

        assertThatCode(() -> service.fail(ROW_ID)).doesNotThrowAnyException();
    }

    private void mockDuplicate(IdempotencyKey existing) {
        when(repository.insertProcessingIfAbsent(
                any(), eq(USER_ID), eq(IdempotencyScope.CREATE_BOOKING),
                eq(NORMALIZED_KEY), any(), eq(LOCKED_UNTIL), eq(EXPIRES_AT)))
                .thenReturn(0);
        when(repository.findByUserIdAndScopeAndKeyForUpdate(
                USER_ID, IdempotencyScope.CREATE_BOOKING, NORMALIZED_KEY))
                .thenReturn(Optional.of(existing));
    }

    private IdempotencyKey existing(IdempotencyStatus status, String requestHash, Instant lockedUntil) {
        IdempotencyKey key = new IdempotencyKey();
        key.setId(ROW_ID);
        key.setUserId(USER_ID);
        key.setScope(IdempotencyScope.CREATE_BOOKING);
        key.setKey(NORMALIZED_KEY);
        key.setRequestHash(requestHash);
        key.setStatus(status);
        key.setLockedUntil(lockedUntil);
        key.setExpiresAt(EXPIRES_AT);
        return key;
    }

    private void assertAlreadyProcessing(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(IdempotencyException.class)
                .hasFieldOrPropertyWithValue("code", "REQUEST_ALREADY_PROCESSING");
    }

    private void assertRetryAllowed(IdempotencyResolution resolution, String expectedHash) {
        assertThat(resolution).isEqualTo(IdempotencyResolution.proceed(ROW_ID));
        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(repository).save(captor.capture());
        IdempotencyKey saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(saved.getRequestHash()).isEqualTo(expectedHash);
        assertThat(saved.getLockedUntil()).isEqualTo(LOCKED_UNTIL);
        assertThat(saved.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(saved.getResponseStatus()).isNull();
        assertThat(saved.getResponseBody()).isNull();
    }
}
