package com.rentflow.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.Role;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingExtra;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.mapper.BookingMapper;
import com.rentflow.booking.repository.BookingExtraRepository;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.PaymentException;
import com.rentflow.common.exception.PaymentProviderUnavailableException;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.ratelimit.RateLimitService;
import com.rentflow.common.security.EmailVerificationPolicy;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.listing.entity.Extra;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.notification.service.AdminNotificationService;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.provider.CaptureCommand;
import com.rentflow.payment.provider.CaptureResult;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.VoidCommand;
import com.rentflow.payment.provider.VoidResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.rentflow.common.web.PageResponse;

@Service
public class BookingService {

    private static final List<BookingStatus> PATCHABLE_STATUSES = List.of(
            BookingStatus.HELD,
            BookingStatus.PENDING_HOST_APPROVAL,
            BookingStatus.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final BookingExtraRepository bookingExtraRepository;
    private final ListingRepository listingRepository;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final RateLimitService rateLimitService;
    private final BookingPriceCalculator bookingPriceCalculator;
    private final BookingValidator bookingValidator;
    private final AvailabilityReserver availabilityReserver;
    private final SecurityContext securityContext;
    private final EmailVerificationPolicy emailVerificationPolicy;
    private final ObjectMapper objectMapper;
    private final BookingMapper bookingMapper;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentProviderRouter paymentProviderRouter;
    private final CorrelationIdHelper correlationIdHelper;
    private final CancellationPolicyCalculator cancellationPolicyCalculator;
    private final BookingTimelineService bookingTimelineService;
    private final AuditLogService auditLogService;
    private final OutboxService outboxService;
    private final AdminNotificationService adminNotificationService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final Clock clock;
    private final long holdDurationMinutes;
    private final boolean requireEmailVerification;

    public BookingService(
            BookingRepository bookingRepository,
            BookingExtraRepository bookingExtraRepository,
            ListingRepository listingRepository,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            RateLimitService rateLimitService,
            BookingPriceCalculator bookingPriceCalculator,
            BookingValidator bookingValidator,
            AvailabilityReserver availabilityReserver,
            SecurityContext securityContext,
            EmailVerificationPolicy emailVerificationPolicy,
            ObjectMapper objectMapper,
            BookingMapper bookingMapper,
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentProviderRouter paymentProviderRouter,
            CorrelationIdHelper correlationIdHelper,
            CancellationPolicyCalculator cancellationPolicyCalculator,
            BookingTimelineService bookingTimelineService,
            AuditLogService auditLogService,
            OutboxService outboxService,
            AdminNotificationService adminNotificationService,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${rentflow.booking.hold-duration-minutes:15}") long holdDurationMinutes,
            @Value("${rentflow.booking.require-email-verification:true}") boolean requireEmailVerification) {
        this.bookingRepository = bookingRepository;
        this.bookingExtraRepository = bookingExtraRepository;
        this.listingRepository = listingRepository;
        this.idempotencyService = idempotencyService;
        this.idempotencyFailureMarker = idempotencyFailureMarker;
        this.rateLimitService = rateLimitService;
        this.bookingPriceCalculator = bookingPriceCalculator;
        this.bookingValidator = bookingValidator;
        this.availabilityReserver = availabilityReserver;
        this.securityContext = securityContext;
        this.emailVerificationPolicy = emailVerificationPolicy;
        this.objectMapper = objectMapper;
        this.bookingMapper = bookingMapper;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentProviderRouter = paymentProviderRouter;
        this.correlationIdHelper = correlationIdHelper;
        this.cancellationPolicyCalculator = cancellationPolicyCalculator;
        this.bookingTimelineService = bookingTimelineService;
        this.auditLogService = auditLogService;
        this.outboxService = outboxService;
        this.adminNotificationService = adminNotificationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.holdDurationMinutes = holdDurationMinutes;
        this.requireEmailVerification = requireEmailVerification;
    }

    @Transactional
    public BookingResponse createBooking(String idempotencyKey, CreateBookingRequest request) {
        UUID customerId = securityContext.currentUserId();
        String requestHash = idempotencyService.computeHash(request);
        IdempotencyResolution resolution = idempotencyService.resolve(
                customerId,
                IdempotencyScope.CREATE_BOOKING,
                idempotencyKey,
                requestHash);

        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeResponse(replay.responseBodyJson());
        }

        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            requireVerifiedEmailForBooking(customerId);
            rateLimitService.consumeBookingCreate(customerId);
            BookingResponse response = createBookingAfterIdempotency(customerId, request);
            idempotencyService.complete(idempotencyKeyId, 201, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    private void requireVerifiedEmailForBooking(UUID customerId) {
        if (!requireEmailVerification) {
            return;
        }
        emailVerificationPolicy.requireVerifiedEmail(customerId);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> listMyBookings(BookingStatus status, Pageable pageable) {
        UUID customerId = securityContext.currentUserId();
        securityContext.requireRole(Role.CUSTOMER);

        Page<Booking> bookings = status == null
                ? bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                : bookingRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, status, pageable);

        return bookingMapper.toSummaryPage(bookings);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(UUID id) {
        UUID currentUserId = securityContext.currentUserId();
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(id)));

        if (!canViewBooking(booking, currentUserId)) {
            throw new BookingNotFoundException(String.valueOf(id));
        }

        return bookingMapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse patchBookingLocations(UUID id, PatchBookingLocationRequest request) {
        UUID currentUserId = securityContext.currentUserId();
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(id)));

        if (!booking.getCustomerId().equals(currentUserId)) {
            throw new BookingNotFoundException(String.valueOf(id));
        }
        if (!PATCHABLE_STATUSES.contains(booking.getStatus())) {
            throw new BusinessRuleException(
                    "BOOKING_INVALID_STATUS",
                    "Booking cannot be patched in its current status");
        }

        if (request.pickupLocation() != null) {
            booking.setPickupLocation(request.pickupLocation());
        }
        if (request.returnLocation() != null) {
            booking.setReturnLocation(request.returnLocation());
        }

        return bookingMapper.toResponse(booking);
    }

    public CancelBookingResponse cancelBooking(UUID id, String idempotencyKey, CancelBookingRequest request) {
        CancelBookingRequest cancelRequest = request == null ? new CancelBookingRequest(null) : request;
        UUID customerId = securityContext.currentUserId();
        String requestHash = idempotencyService.computeHash(new CancelHashInput(id, cancelRequest));
        IdempotencyResolution resolution = idempotencyService.resolve(
                customerId,
                IdempotencyScope.CANCEL_BOOKING,
                idempotencyKey,
                requestHash);

        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeCancelResponse(replay.responseBodyJson());
        }

        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            PreparedCancelContext prepared = required(transactionTemplate.execute(status ->
                    prepareCancellation(id, customerId, cancelRequest, idempotencyKeyId)));
            if (prepared.completedResponse() != null) {
                return prepared.completedResponse();
            }

            if (prepared.captureCommand() != null) {
                CaptureResult captureResult = callPreparedCapture(prepared);
                try {
                    PreparedCancelContext capturePrepared = prepared;
                    prepared = required(transactionTemplate.execute(status ->
                            finalizeCaptureAndPrepareVoid(capturePrepared, captureResult)));
                } catch (BusinessRuleException e) {
                    if (isUnsafeFinalization(e)) {
                        markTransactionUnsafeRequiresNew(
                                prepared.captureTransactionId(),
                                prepared.paymentId(),
                                "CoreBank capture succeeded but cancellation finalization was no longer safe");
                    }
                    throw e;
                }
            }

            VoidResult voidResult;
            try {
                voidResult = callPreparedVoid(prepared);
            } catch (RuntimeException e) {
                PreparedCancelContext voidFailurePrepared = prepared;
                return required(transactionTemplate.execute(status ->
                        finalizeVoidFailure(voidFailurePrepared, e)));
            }

            try {
                PreparedCancelContext voidPrepared = prepared;
                return required(transactionTemplate.execute(status ->
                        finalizeVoidSuccess(voidPrepared, voidResult)));
            } catch (BusinessRuleException e) {
                if (isUnsafeFinalization(e)) {
                    markTransactionUnsafeRequiresNew(
                            prepared.voidTransactionId(),
                            prepared.paymentId(),
                            "CoreBank void succeeded but cancellation finalization was no longer safe");
                }
                throw e;
            }
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    private CancelBookingResponse buildCancelResponse(Booking booking, UUID customerId, CancelOutcome outcome) {
        return new CancelBookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getCancellationReason(),
                true,
                outcome.voidRetryRequired(),
                outcome.code(),
                outcome.paymentRetryState());
    }

    private BookingResponse createBookingAfterIdempotency(UUID customerId, CreateBookingRequest request) {
        securityContext.requireRole(Role.CUSTOMER);
        bookingValidator.validateDriverVerification(customerId);

        Listing listing = bookingValidator.resolveListingForBooking(request.listingId(), customerId);
        long rentalDays = bookingValidator.validateDates(request.pickupDate(), request.returnDate());
        bookingValidator.validateCustomerOverlap(customerId, request.pickupDate(), request.returnDate());
        List<AvailabilityCalendar> lockedAvailability = availabilityReserver.lockAndValidate(
                request.listingId(), request.pickupDate(), request.returnDate(), rentalDays);

        PriceCalculationResult price = bookingPriceCalculator.calculate(
                listing,
                request.pickupDate(),
                request.returnDate(),
                request.extras(),
                listing.getExtras());
        PolicySnapshot policySnapshot = new PolicySnapshot(
                listing.getCancellationPolicy(),
                listing.getInstantBook(),
                listing.getDailyKmLimit());
        String priceSnapshotJson = serialize(price);
        String policySnapshotJson = serialize(policySnapshot);

        Instant now = Instant.now(clock);
        UUID holdToken = UUID.randomUUID();
        Instant holdExpiresAt = now.plus(holdDurationMinutes, ChronoUnit.MINUTES);
        Booking booking = saveBooking(customerId, listing, request, holdToken, holdExpiresAt,
                priceSnapshotJson, policySnapshotJson);

        saveBookingExtras(booking.getId(), price.extras());
        availabilityReserver.hold(lockedAvailability, booking.getId(), holdToken, holdExpiresAt);

        JsonNode priceSnapshotNode = readTree(priceSnapshotJson);
        JsonNode policySnapshotNode = readTree(policySnapshotJson);
        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getListingId(),
                listing.getTitle(),
                booking.getCustomerId(),
                booking.getHostId(),
                booking.getPickupDate(),
                booking.getReturnDate(),
                booking.getPickupLocation(),
                booking.getReturnLocation(),
                booking.getHoldExpiresAt(),
                price.totalAmount(),
                price.currency(),
                priceSnapshotNode,
                policySnapshotNode,
                booking.getCreatedAt() == null ? now : booking.getCreatedAt());
    }

    private boolean canViewBooking(Booking booking, UUID currentUserId) {
        return booking.getCustomerId().equals(currentUserId)
                || booking.getHostId().equals(currentUserId)
                || securityContext.hasRole(Role.ADMIN);
    }

    private boolean canCancelBooking(Booking booking, UUID currentUserId) {
        return booking.getCustomerId().equals(currentUserId)
                || booking.getHostId().equals(currentUserId)
                || securityContext.hasRole(Role.ADMIN);
    }

    private PreparedCancelContext prepareCancellation(
            UUID bookingId,
            UUID actorId,
            CancelBookingRequest cancelRequest,
            UUID idempotencyKeyId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(bookingId)));
        if (!canCancelBooking(booking, actorId)) {
            throw new BookingNotFoundException(String.valueOf(bookingId));
        }
        String cancellationReason = sanitizeCancellationReason(cancelRequest.reason());

        if (booking.getStatus() == BookingStatus.HELD) {
            List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
            CancelOutcome outcome = cancelHeld(booking, availabilityRows, cancellationReason);
            CancelBookingResponse response = buildCancelResponse(booking, actorId, outcome);
            idempotencyService.complete(idempotencyKeyId, 200, serialize(response));
            return PreparedCancelContext.completed(response);
        }

        if (booking.getStatus() != BookingStatus.PENDING_HOST_APPROVAL
                && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking cannot be cancelled in its current status");
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            validateBeforePickup(booking);
        }

        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(booking.getId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
        validateCancelablePayment(payment);
        List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
        validateAvailabilityRowsForCancellation(booking, availabilityRows);

        BigDecimal penaltyAmount = BigDecimal.ZERO;
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            BigDecimal totalAmount = readTotalAmount(booking);
            CancellationPolicy policy = readCancellationPolicy(booking);
            CancellationPolicyCalculator.CancellationPolicyResult calc =
                    cancellationPolicyCalculator.calculate(policy, booking.getPickupDate(), totalAmount);
            penaltyAmount = calc.penaltyAmount();
        }

        if (penaltyAmount.compareTo(BigDecimal.ZERO) > 0) {
            PreparedCapture preparedCapture = prepareCaptureTransaction(payment, penaltyAmount);
            return new PreparedCancelContext(
                    null,
                    actorId,
                    booking.getId(),
                    payment.getId(),
                    booking.getStatus(),
                    cancellationReason,
                    idempotencyKeyId,
                    penaltyAmount,
                    preparedCapture.transactionId(),
                    preparedCapture.command(),
                    null,
                    null);
        }

        PreparedVoid preparedVoid = prepareVoidTransaction(payment);
        return new PreparedCancelContext(
                null,
                actorId,
                booking.getId(),
                payment.getId(),
                booking.getStatus(),
                cancellationReason,
                idempotencyKeyId,
                penaltyAmount,
                null,
                null,
                preparedVoid.transactionId(),
                preparedVoid.command());
    }

    private CancelOutcome cancelHeld(Booking booking, List<AvailabilityCalendar> availabilityRows, String cancellationReason) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(cancellationReason);
        bookingRepository.save(booking);
        availabilityReserver.releaseHeld(availabilityRows, booking);
        emitCancellationSignals(booking, cancellationReason);
        return CancelOutcome.completed();
    }

    private PreparedCancelContext finalizeCaptureAndPrepareVoid(
            PreparedCancelContext prepared,
            CaptureResult captureResult) {
        Booking booking = bookingRepository.findByIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(prepared.bookingId())));
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
        List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
        PaymentTransaction captureTx = paymentTransactionRepository.findByIdForUpdate(prepared.captureTransactionId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_TRANSACTION_NOT_FOUND", "Payment transaction not found"));

        validateCancellationStillSafe(prepared, booking, payment, availabilityRows, false);
        applyCaptureSuccess(payment, captureResult, prepared.penaltyAmount());
        captureTx.setStatus(PaymentTransactionStatus.SUCCEEDED);
        captureTx.setProviderJournalId(captureResult.providerJournalId());
        captureTx.setProviderResponse(captureResult.providerMetadataJson());
        captureTx.setProviderErrorCode(null);
        captureTx.setProviderErrorMessage(null);
        paymentTransactionRepository.save(captureTx);

        bookingTimelineService.append(
                booking.getId(),
                "CANCELLATION_PENALTY_CAPTURED",
                securityContext.currentUserId(),
                resolveActorType(securityContext.currentUserId(), booking),
                serialize(Map.of("amount", prepared.penaltyAmount(), "currency", payment.getCurrency())));

        PreparedVoid preparedVoid = prepareVoidTransaction(payment);
        return prepared.withVoid(preparedVoid.transactionId(), preparedVoid.command());
    }

    private PreparedCapture prepareCaptureTransaction(BookingPayment payment, BigDecimal amount) {
        String correlationId = correlationIdHelper.getOrGenerate();
        String requestId = UUID.randomUUID().toString();
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBookingPaymentId(payment.getId());
        tx.setBookingId(payment.getBookingId());
        tx.setType(PaymentTransactionType.CAPTURE);
        tx.setStatus(PaymentTransactionStatus.PENDING);
        tx.setAmount(amount);
        tx.setCurrency(payment.getCurrency());
        tx.setProvider(payment.getProvider());
        tx.setProviderRequestId(requestId);
        tx.setProviderRef(payment.getProviderPaymentOrderId());
        tx = paymentTransactionRepository.save(tx);

        CaptureCommand command = new CaptureCommand(
                    "rentflow:cancel:capture:" + payment.getId() + ":" + requestId,
                    payment.getProviderPaymentOrderId(),
                    amount,
                    payment.getCurrency(),
                    correlationId,
                    requestId,
                    payment.getId().toString(),
                    correlationId);
        return new PreparedCapture(tx.getId(), command);
    }

    private PreparedVoid prepareVoidTransaction(BookingPayment payment) {
        String correlationId = correlationIdHelper.getOrGenerate();
        String requestId = UUID.randomUUID().toString();
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBookingPaymentId(payment.getId());
        tx.setBookingId(payment.getBookingId());
        tx.setType(PaymentTransactionType.VOID);
        tx.setStatus(PaymentTransactionStatus.PENDING);
        tx.setAmount(BigDecimal.ZERO);
        tx.setCurrency(payment.getCurrency());
        tx.setProvider(payment.getProvider());
        tx.setProviderRequestId(requestId);
        tx.setProviderRef(payment.getProviderHoldId());
        tx = paymentTransactionRepository.save(tx);

        VoidCommand command = new VoidCommand(
                "rentflow:cancel:void:" + payment.getId() + ":" + requestId,
                payment.getProviderHoldId(),
                correlationId,
                requestId,
                payment.getId().toString(),
                correlationId);
        return new PreparedVoid(tx.getId(), command);
    }

    private CaptureResult callPreparedCapture(PreparedCancelContext prepared) {
        try {
            return paymentProviderRouter.route(PaymentProviderType.COREBANK).capture(prepared.captureCommand());
        } catch (PaymentProviderUnavailableException e) {
            markTransactionFailedRequiresNew(prepared.captureTransactionId(), prepared.paymentId(),
                    "PAYMENT_PROVIDER_UNAVAILABLE", e.getMessage());
            throw new PaymentException("PAYMENT_FAILED", "Payment capture failed during cancellation", e);
        } catch (RuntimeException e) {
            markTransactionFailedRequiresNew(prepared.captureTransactionId(), prepared.paymentId(),
                    "PAYMENT_PROVIDER_ERROR", e.getMessage());
            throw e;
        }
    }

    private VoidResult callPreparedVoid(PreparedCancelContext prepared) {
        try {
            return paymentProviderRouter.route(PaymentProviderType.COREBANK).voidAuthorization(prepared.voidCommand());
        } catch (RuntimeException e) {
            throw e;
        }
    }

    private CancelBookingResponse finalizeVoidSuccess(PreparedCancelContext prepared, VoidResult voidResult) {
        Booking booking = bookingRepository.findByIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(prepared.bookingId())));
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
        List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
        PaymentTransaction voidTx = paymentTransactionRepository.findByIdForUpdate(prepared.voidTransactionId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_TRANSACTION_NOT_FOUND", "Payment transaction not found"));

        validateCancellationStillSafe(prepared, booking, payment, availabilityRows, prepared.captureCommand() != null);
        applyVoidSuccess(payment, voidResult);
        voidTx.setStatus(PaymentTransactionStatus.SUCCEEDED);
        voidTx.setProviderResponse(voidResult.providerMetadataJson());
        voidTx.setProviderErrorCode(null);
        voidTx.setProviderErrorMessage(null);
        paymentTransactionRepository.save(voidTx);
        finalizeBookingCancellation(booking, availabilityRows, prepared.cancellationReason());

        CancelBookingResponse response = buildCancelResponse(booking, prepared.actorId(), CancelOutcome.completed());
        idempotencyService.complete(prepared.idempotencyKeyId(), 200, serialize(response));
        return response;
    }

    private CancelBookingResponse finalizeVoidFailure(PreparedCancelContext prepared, RuntimeException e) {
        Booking booking = bookingRepository.findByIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(prepared.bookingId())));
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
        List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
        PaymentTransaction voidTx = paymentTransactionRepository.findByIdForUpdate(prepared.voidTransactionId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_TRANSACTION_NOT_FOUND", "Payment transaction not found"));

        validateCancellationStillSafe(prepared, booking, payment, availabilityRows, prepared.captureCommand() != null);
        voidTx.setStatus(PaymentTransactionStatus.FAILED);
        voidTx.setProviderErrorCode(e instanceof PaymentProviderUnavailableException
                ? "PAYMENT_PROVIDER_UNAVAILABLE"
                : "PAYMENT_PROVIDER_ERROR");
        voidTx.setProviderErrorMessage(e.getMessage());
        paymentTransactionRepository.save(voidTx);

        boolean firstTransitionToRetryRequired = markVoidFailed(payment, e);
        emitVoidRetryRequiredSignals(booking, payment, firstTransitionToRetryRequired);
        finalizeBookingCancellation(booking, availabilityRows, prepared.cancellationReason());

        CancelBookingResponse response = buildCancelResponse(booking, prepared.actorId(), CancelOutcome.withRetryRequired());
        idempotencyService.complete(prepared.idempotencyKeyId(), 200, serialize(response));
        return response;
    }

    private void validateCancelablePayment(BookingPayment payment) {
        if (payment == null) {
            throw new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking");
        }
        if (payment.getProvider() != PaymentProviderType.COREBANK) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_UNSUPPORTED", "Cancellation supports CoreBank payment only");
        }
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new BusinessRuleException("PAYMENT_INVALID_STATUS", "Payment must be AUTHORIZED for cancellation");
        }
        if (payment.getProviderPaymentOrderId() == null || payment.getProviderPaymentOrderId().isBlank()) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_REFERENCE_MISSING", "Missing provider payment order id");
        }
        if (payment.getProviderHoldId() == null || payment.getProviderHoldId().isBlank()) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_REFERENCE_MISSING", "Missing provider hold id");
        }
    }

    private void validateBeforePickup(Booking booking) {
        LocalDate today = LocalDate.now(clock);
        if (!today.isBefore(booking.getPickupDate())) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking cannot be cancelled after check-in date");
        }
    }

    private void validateCancellationStillSafe(
            PreparedCancelContext prepared,
            Booking booking,
            BookingPayment payment,
            List<AvailabilityCalendar> availabilityRows,
            boolean captureAlreadyApplied) {
        try {
            if (!canCancelBooking(booking, prepared.actorId())) {
                throw new BookingNotFoundException(String.valueOf(prepared.bookingId()));
            }
            if (booking.getStatus() != prepared.bookingStatus()) {
                throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking state changed during cancellation");
            }
            if (booking.getStatus() == BookingStatus.CONFIRMED) {
                validateBeforePickup(booking);
            }
            if (captureAlreadyApplied) {
                validateCapturedPaymentForPreparedVoid(prepared, payment);
            } else {
                validateCancelablePayment(payment);
            }
            validateAvailabilityRowsForCancellation(booking, availabilityRows);
        } catch (BusinessRuleException | BookingNotFoundException e) {
            throw new BusinessRuleException(
                    "PAYMENT_FINALIZATION_UNSAFE",
                    "Payment provider mutation succeeded but booking cancellation could not be finalized safely");
        }
    }

    private void validateCapturedPaymentForPreparedVoid(PreparedCancelContext prepared, BookingPayment payment) {
        if (payment.getProvider() != PaymentProviderType.COREBANK) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_UNSUPPORTED", "Cancellation supports CoreBank payment only");
        }
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new BusinessRuleException("PAYMENT_INVALID_STATUS", "Payment capture state changed during cancellation");
        }
        if (payment.getCapturedAmount().compareTo(prepared.penaltyAmount()) < 0) {
            throw new BusinessRuleException("PAYMENT_INVALID_STATUS", "Payment captured amount changed during cancellation");
        }
        if (payment.getProviderPaymentOrderId() == null || payment.getProviderPaymentOrderId().isBlank()) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_REFERENCE_MISSING", "Missing provider payment order id");
        }
        if (payment.getProviderHoldId() == null || payment.getProviderHoldId().isBlank()) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_REFERENCE_MISSING", "Missing provider hold id");
        }
    }

    private void validateAvailabilityRowsForCancellation(Booking booking, List<AvailabilityCalendar> availabilityRows) {
        long expectedDays = ChronoUnit.DAYS.between(booking.getPickupDate(), booking.getReturnDate());
        boolean invalid = availabilityRows.size() != expectedDays
                || availabilityRows.stream().anyMatch(row -> !booking.getId().equals(row.getBookingId()))
                || availabilityRows.stream().anyMatch(row -> row.getStatus() != AvailabilityStatus.HOLD
                && row.getStatus() != AvailabilityStatus.BOOKED);
        if (invalid) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Availability is no longer reserved for this booking");
        }
    }

    private void markTransactionFailedRequiresNew(
            UUID transactionId,
            UUID paymentId,
            String providerErrorCode,
            String providerErrorMessage) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> {
            PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(transactionId)
                    .orElseThrow(() -> new BusinessRuleException("PAYMENT_TRANSACTION_NOT_FOUND", "Payment transaction not found"));
            tx.setStatus(PaymentTransactionStatus.FAILED);
            tx.setProviderErrorCode(providerErrorCode);
            tx.setProviderErrorMessage(providerErrorMessage);
            paymentTransactionRepository.save(tx);
        });
    }

    private void markTransactionUnsafeRequiresNew(UUID transactionId, UUID paymentId, String providerErrorMessage) {
        markTransactionFailedRequiresNew(
                transactionId,
                paymentId,
                "PAYMENT_FINALIZATION_UNSAFE",
                providerErrorMessage);
    }

    private boolean isUnsafeFinalization(BusinessRuleException e) {
        return "PAYMENT_FINALIZATION_UNSAFE".equals(e.getCode());
    }

    private boolean markVoidFailed(BookingPayment payment, RuntimeException e) {
        boolean firstTransitionToRetryRequired = !payment.isVoidRetryRequired();
        payment.setProviderStatus("VOID_RETRY_REQUIRED");
        payment.setProviderMetadata(serialize(java.util.Map.of(
                "code", "PAYMENT_VOID_RETRY_REQUIRED",
                "message", e.getMessage())));
        payment.setVoidRetryRequired(true);
        int nextCount = payment.getVoidRetryCount() == null ? 1 : payment.getVoidRetryCount() + 1;
        payment.setVoidRetryCount(nextCount);
        payment.setVoidRetryLastError(e.getMessage());
        payment.setVoidRetryNextAt(clock.instant().plusSeconds(300));
        bookingPaymentRepository.save(payment);
        return firstTransitionToRetryRequired;
    }

    private void applyCaptureSuccess(BookingPayment payment, CaptureResult result, BigDecimal amount) {
        payment.setCapturedAmount(payment.getCapturedAmount().add(amount));
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setProviderStatus(result.providerStatus());
        payment.setProviderMetadata(result.providerMetadataJson());
        payment.setVoidRetryRequired(false);
        payment.setVoidRetryNextAt(null);
        payment.setVoidRetryLastError(null);
        bookingPaymentRepository.save(payment);
    }

    private void applyVoidSuccess(BookingPayment payment, VoidResult result) {
        if (payment.getCapturedAmount().compareTo(BigDecimal.ZERO) > 0) {
            payment.setStatus(PaymentStatus.CAPTURED);
        } else {
            payment.setStatus(PaymentStatus.VOIDED);
        }
        payment.setProviderStatus(result.providerStatus());
        payment.setProviderMetadata(result.providerMetadataJson());
        payment.setVoidRetryRequired(false);
        payment.setVoidRetryNextAt(null);
        payment.setVoidRetryLastError(null);
        bookingPaymentRepository.save(payment);
    }

    private void finalizeBookingCancellation(
            Booking booking,
            List<AvailabilityCalendar> availabilityRows,
            String cancellationReason) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(cancellationReason);
        bookingRepository.save(booking);

        List<AvailabilityCalendar> releasable = availabilityRows.stream()
                .filter(row -> booking.getId().equals(row.getBookingId()))
                .filter(row -> row.getStatus() == AvailabilityStatus.HOLD
                        || row.getStatus() == AvailabilityStatus.BOOKED)
                .peek(row -> {
                    row.setStatus(AvailabilityStatus.FREE);
                    row.setBookingId(null);
                    row.setHoldToken(null);
                    row.setHoldExpiresAt(null);
                })
                .toList();
        if (!releasable.isEmpty()) {
            availabilityReserver.saveRows(releasable);
        }
        emitCancellationSignals(booking, cancellationReason);
    }

    private void emitCancellationSignals(Booking booking, String cancellationReason) {
        UUID actorId = securityContext.currentUserId();
        String actorType = resolveActorType(actorId, booking);
        String details = serialize(Map.of(
                "bookingId", booking.getId(),
                "status", booking.getStatus().name(),
                "cancellationReason", cancellationReason == null ? "" : cancellationReason));
        bookingTimelineService.append(booking.getId(), "BOOKING_CANCELLED", actorId, actorType, details);
        auditLogService.record(actorId, actorType, "BOOKING_CANCEL", "BOOKING", booking.getId(), "SUCCEEDED", details);
        outboxService.append("BOOKING", booking.getId(), "BOOKING_CANCELLED", details);
    }

    private void emitVoidRetryRequiredSignals(
            Booking booking,
            BookingPayment payment,
            boolean firstTransitionToRetryRequired) {
        UUID actorId = securityContext.currentUserId();
        String actorType = resolveActorType(actorId, booking);
        String details = serialize(Map.of(
                "bookingId", booking.getId(),
                "paymentId", payment.getId(),
                "retryCount", payment.getVoidRetryCount()));
        bookingTimelineService.append(booking.getId(), "PAYMENT_VOID_RETRY_REQUIRED", actorId, actorType, details);
        auditLogService.record(actorId, actorType, "PAYMENT_VOID_RETRY_REQUIRED",
                "BOOKING_PAYMENT", payment.getId(), "FAILED", details);
        outboxService.append("BOOKING_PAYMENT", payment.getId(), "PAYMENT_VOID_RETRY_REQUIRED", details);
        if (firstTransitionToRetryRequired) {
            adminNotificationService.notifyPaymentVoidRetryRequired(
                    booking.getId(),
                    payment.getId(),
                    payment.getVoidRetryCount() == null ? 0 : payment.getVoidRetryCount());
        }
    }

    private String resolveActorType(UUID actorId, Booking booking) {
        if (securityContext.hasRole(Role.ADMIN)) {
            return "ADMIN";
        }
        if (booking.getHostId().equals(actorId)) {
            return "HOST";
        }
        return "CUSTOMER";
    }

    private BigDecimal readTotalAmount(Booking booking) {
        try {
            JsonNode root = objectMapper.readTree(booking.getPriceSnapshot());
            JsonNode node = root.get("totalAmount");
            if (node == null || node.isNull()) {
                throw new IllegalStateException("Booking price snapshot missing totalAmount");
            }
            return node.decimalValue();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse booking price snapshot", e);
        }
    }

    private CancellationPolicy readCancellationPolicy(Booking booking) {
        try {
            JsonNode root = objectMapper.readTree(booking.getPolicySnapshot());
            String value = root.path("cancellationPolicy").asText(null);
            if (value == null) {
                throw new IllegalStateException("Booking policy snapshot missing cancellationPolicy");
            }
            return CancellationPolicy.valueOf(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse booking policy snapshot", e);
        }
    }

    private Booking saveBooking(
            UUID customerId,
            Listing listing,
            CreateBookingRequest request,
            UUID holdToken,
            Instant holdExpiresAt,
            String priceSnapshotJson,
            String policySnapshotJson) {
        Booking booking = new Booking();
        booking.setCustomerId(customerId);
        booking.setHostId(listing.getHostId());
        booking.setListingId(listing.getId());
        booking.setPickupDate(request.pickupDate());
        booking.setReturnDate(request.returnDate());
        booking.setStatus(BookingStatus.HELD);
        booking.setHoldToken(holdToken);
        booking.setHoldExpiresAt(holdExpiresAt);
        booking.setHostApprovalExpiresAt(null);
        booking.setPickupLocation(request.pickupLocation());
        booking.setReturnLocation(request.returnLocation());
        booking.setPriceSnapshot(priceSnapshotJson);
        booking.setPolicySnapshot(policySnapshotJson);
        return bookingRepository.save(booking);
    }

    private void saveBookingExtras(UUID bookingId, List<ExtraLineItem> lineItems) {
        List<BookingExtra> bookingExtras = lineItems.stream()
                .map(line -> {
                    BookingExtra bookingExtra = new BookingExtra();
                    bookingExtra.setBookingId(bookingId);
                    bookingExtra.setExtraId(line.extraId());
                    bookingExtra.setQuantity(line.quantity());
                    bookingExtra.setPriceSnapshot(line.unitPrice());
                    return bookingExtra;
                })
                .toList();
        if (!bookingExtras.isEmpty()) {
            bookingExtraRepository.saveAll(bookingExtras);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize booking JSON", e);
        }
    }

    private BookingResponse deserializeResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, BookingResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize idempotency response", e);
        }
    }

    private CancelBookingResponse deserializeCancelResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, CancelBookingResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize idempotency response", e);
        }
    }

    private String sanitizeCancellationReason(String reason) {
        if (reason == null) {
            return null;
        }
        String sanitized = reason.replaceAll("<[^>]*>", "").trim();
        if (sanitized.isBlank()) {
            return null;
        }
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to read booking JSON", e);
        }
    }

    private <T> T required(T value) {
        if (value == null) {
            throw new IllegalStateException("Transactional callback returned null unexpectedly");
        }
        return value;
    }

    private record CancelHashInput(UUID bookingId, CancelBookingRequest request) {
    }

    private record PreparedCapture(UUID transactionId, CaptureCommand command) {
    }

    private record PreparedVoid(UUID transactionId, VoidCommand command) {
    }

    private record PreparedCancelContext(
            CancelBookingResponse completedResponse,
            UUID actorId,
            UUID bookingId,
            UUID paymentId,
            BookingStatus bookingStatus,
            String cancellationReason,
            UUID idempotencyKeyId,
            BigDecimal penaltyAmount,
            UUID captureTransactionId,
            CaptureCommand captureCommand,
            UUID voidTransactionId,
            VoidCommand voidCommand
    ) {
        static PreparedCancelContext completed(CancelBookingResponse response) {
            return new PreparedCancelContext(
                    response,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    BigDecimal.ZERO,
                    null,
                    null,
                    null,
                    null);
        }

        PreparedCancelContext withVoid(UUID transactionId, VoidCommand command) {
            return new PreparedCancelContext(
                    completedResponse,
                    actorId,
                    bookingId,
                    paymentId,
                    bookingStatus,
                    cancellationReason,
                    idempotencyKeyId,
                    penaltyAmount,
                    captureTransactionId,
                    captureCommand,
                    transactionId,
                    command);
        }
    }

    private record CancelOutcome(boolean voidRetryRequired, String code, String paymentRetryState) {
        static CancelOutcome completed() {
            return new CancelOutcome(false, null, null);
        }

        static CancelOutcome withRetryRequired() {
            return new CancelOutcome(true, "PAYMENT_VOID_RETRY_REQUIRED", "VOID_RETRY_REQUIRED");
        }
    }
}
