package com.rentflow.file.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.common.exception.AccessDeniedException;
import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.common.exception.ResourceNotFoundException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.file.dto.AddListingPhotoRequest;
import com.rentflow.file.dto.ListingPhotoResponse;
import com.rentflow.file.dto.SignedFileUrlResponse;
import com.rentflow.file.entity.FileMetadata;
import com.rentflow.file.entity.FilePurpose;
import com.rentflow.file.entity.FileStatus;
import com.rentflow.file.entity.FileVisibility;
import com.rentflow.file.entity.ListingPhoto;
import com.rentflow.file.repository.FileMetadataRepository;
import com.rentflow.file.repository.ListingPhotoRepository;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class FileService {

    private static final long LISTING_PHOTO_MAX_BYTES = 10L * 1024 * 1024;

    private final FileMetadataRepository fileMetadataRepository;
    private final ListingPhotoRepository listingPhotoRepository;
    private final ListingRepository listingRepository;
    private final SecurityContext securityContext;
    private final FileSignedUrlProperties signedUrlProperties;

    public FileService(
            FileMetadataRepository fileMetadataRepository,
            ListingPhotoRepository listingPhotoRepository,
            ListingRepository listingRepository,
            SecurityContext securityContext,
            FileSignedUrlProperties signedUrlProperties) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.listingPhotoRepository = listingPhotoRepository;
        this.listingRepository = listingRepository;
        this.securityContext = securityContext;
        this.signedUrlProperties = signedUrlProperties;
    }

    @Transactional
    public ListingPhotoResponse addListingPhoto(UUID listingId, AddListingPhotoRequest request) {
        validateListingPhotoInput(request);
        UUID hostId = securityContext.currentUserId();
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        FileMetadata metadata = new FileMetadata();
        metadata.setOwnerUserId(hostId);
        metadata.setPurpose(FilePurpose.LISTING_PHOTO);
        metadata.setBucket(request.bucket().trim());
        metadata.setObjectKey(request.objectKey().trim());
        metadata.setContentType(request.contentType().trim().toLowerCase());
        metadata.setSizeBytes(request.sizeBytes());
        metadata.setChecksum(request.checksum());
        metadata.setVisibility(listing.getStatus() == ListingStatus.ACTIVE ? FileVisibility.PUBLIC : FileVisibility.PRIVATE);
        metadata.setStatus(FileStatus.ACTIVE);
        metadata = fileMetadataRepository.save(metadata);

        ListingPhoto photo = new ListingPhoto();
        photo.setListingId(listingId);
        photo.setFileId(metadata.getId());
        photo.setDisplayOrder((int) listingPhotoRepository.countByListingId(listingId));
        photo.setPrimary(Boolean.TRUE.equals(request.primary()));
        photo = listingPhotoRepository.save(photo);

        Signed signed = buildSignedUrl(metadata);
        return new ListingPhotoResponse(
                photo.getId(),
                photo.getListingId(),
                photo.getFileId(),
                photo.isPrimary(),
                photo.getDisplayOrder(),
                metadata.getVisibility().name(),
                signed.url(),
                signed.expiresAt());
    }

    @Transactional(readOnly = true)
    public SignedFileUrlResponse getSignedUrl(UUID fileId) {
        FileMetadata file = fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", fileId.toString()));
        assertReadable(file);
        Signed signed = buildSignedUrl(file);
        return new SignedFileUrlResponse(file.getId(), file.getVisibility().name(), signed.url(), signed.expiresAt());
    }

    private void validateListingPhotoInput(AddListingPhotoRequest request) {
        if (!request.contentType().toLowerCase().startsWith("image/")) {
            throw new ValidationException("Listing photo contentType must be an image/* MIME type");
        }
        if (request.sizeBytes() > LISTING_PHOTO_MAX_BYTES) {
            throw new ValidationException("Listing photo must be <= 10MB");
        }
    }

    private void assertReadable(FileMetadata file) {
        if (file.getVisibility() == FileVisibility.PUBLIC) {
            return;
        }
        UUID currentUser = securityContext.currentUserId();
        if (securityContext.hasRole(Role.ADMIN) || file.getOwnerUserId().equals(currentUser)) {
            return;
        }
        throw new AccessDeniedException();
    }

    private Signed buildSignedUrl(FileMetadata metadata) {
        Instant expiresAt = Instant.now().plus(signedUrlProperties.getTtl());
        long expiresAtEpoch = expiresAt.getEpochSecond();
        String payload = metadata.getId() + ":" + metadata.getBucket() + ":" + metadata.getObjectKey() + ":" + expiresAtEpoch;
        String signature = hmacSha256(payload, signedUrlProperties.getSecret());
        String baseUrl = signedUrlProperties.getBaseUrl().replaceAll("/+$", "");
        String url = baseUrl + "/files/" + metadata.getId()
                + "?bucket=" + encode(metadata.getBucket())
                + "&key=" + encode(metadata.getObjectKey())
                + "&exp=" + expiresAtEpoch
                + "&sig=" + encode(signature);
        return new Signed(url, expiresAt);
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate signed url", e);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Signed(String url, Instant expiresAt) {
    }
}
