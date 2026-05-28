package com.rentflow.file.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.common.exception.AccessDeniedException;
import com.rentflow.file.dto.AddListingPhotoRequest;
import com.rentflow.file.dto.ListingPhotoResponse;
import com.rentflow.file.dto.SignedFileUrlResponse;
import com.rentflow.file.entity.FileMetadata;
import com.rentflow.file.entity.FileStatus;
import com.rentflow.file.entity.FileVisibility;
import com.rentflow.file.entity.ListingPhoto;
import com.rentflow.file.repository.FileMetadataRepository;
import com.rentflow.file.repository.ListingPhotoRepository;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.common.security.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock private FileMetadataRepository fileMetadataRepository;
    @Mock private ListingPhotoRepository listingPhotoRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private SecurityContext securityContext;

    private FileService service;
    private UUID hostId;
    private UUID listingId;

    @BeforeEach
    void setUp() {
        FileSignedUrlProperties properties = new FileSignedUrlProperties();
        properties.setTtl(Duration.ofMinutes(10));
        properties.setBaseUrl("https://files.test.local");
        properties.setSecret("test-secret");
        service = new FileService(fileMetadataRepository, listingPhotoRepository, listingRepository, securityContext, properties);
        hostId = UUID.randomUUID();
        listingId = UUID.randomUUID();
    }

    @Test
    void addListingPhotoForDraftListingDefaultsToPrivate() {
        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setHostId(hostId);
        listing.setStatus(ListingStatus.DRAFT);
        when(securityContext.currentUserId()).thenReturn(hostId);
        when(listingRepository.findByIdAndHostId(listingId, hostId)).thenReturn(Optional.of(listing));
        when(listingPhotoRepository.countByListingId(listingId)).thenReturn(0L);
        doReturn(savedFile(FileVisibility.PRIVATE)).when(fileMetadataRepository).save(any(FileMetadata.class));
        doReturn(savedPhoto()).when(listingPhotoRepository).save(any(ListingPhoto.class));

        ListingPhotoResponse response = service.addListingPhoto(listingId, new AddListingPhotoRequest(
                "bucket-a", "path/a.jpg", "image/jpeg", 1024L, null, true));

        ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
        org.mockito.Mockito.verify(fileMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getVisibility()).isEqualTo(FileVisibility.PRIVATE);
        assertThat(response.visibility()).isEqualTo("PRIVATE");
        assertThat(response.signedUrl()).contains("https://files.test.local/files/");
    }

    @Test
    void addListingPhotoForActiveListingBecomesPublic() {
        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setHostId(hostId);
        listing.setStatus(ListingStatus.ACTIVE);
        when(securityContext.currentUserId()).thenReturn(hostId);
        when(listingRepository.findByIdAndHostId(listingId, hostId)).thenReturn(Optional.of(listing));
        when(listingPhotoRepository.countByListingId(listingId)).thenReturn(0L);
        doReturn(savedFile(FileVisibility.PUBLIC)).when(fileMetadataRepository).save(any(FileMetadata.class));
        doReturn(savedPhoto()).when(listingPhotoRepository).save(any(ListingPhoto.class));

        ListingPhotoResponse response = service.addListingPhoto(listingId, new AddListingPhotoRequest(
                "bucket-a", "path/b.jpg", "image/jpeg", 1024L, null, false));

        assertThat(response.visibility()).isEqualTo("PUBLIC");
    }

    @Test
    void signedUrlForPrivateFileDeniedForNonOwner() {
        UUID fileId = UUID.randomUUID();
        FileMetadata file = savedFile(FileVisibility.PRIVATE);
        file.setId(fileId);
        file.setOwnerUserId(UUID.randomUUID());
        when(fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(securityContext.currentUserId()).thenReturn(UUID.randomUUID());
        when(securityContext.hasRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> service.getSignedUrl(fileId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void signedUrlForPublicFileAllowed() {
        UUID fileId = UUID.randomUUID();
        FileMetadata file = savedFile(FileVisibility.PUBLIC);
        file.setId(fileId);
        file.setOwnerUserId(UUID.randomUUID());
        when(fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)).thenReturn(Optional.of(file));

        SignedFileUrlResponse response = service.getSignedUrl(fileId);

        assertThat(response.fileId()).isEqualTo(fileId);
        assertThat(response.visibility()).isEqualTo("PUBLIC");
        assertThat(response.signedUrl()).contains("https://files.test.local/files/" + fileId);
    }

    private FileMetadata savedFile(FileVisibility visibility) {
        FileMetadata file = new FileMetadata();
        file.setId(UUID.randomUUID());
        file.setOwnerUserId(hostId);
        file.setBucket("bucket-a");
        file.setObjectKey("path/key.jpg");
        file.setContentType("image/jpeg");
        file.setSizeBytes(1000L);
        file.setVisibility(visibility);
        file.setStatus(FileStatus.ACTIVE);
        return file;
    }

    private ListingPhoto savedPhoto() {
        ListingPhoto photo = new ListingPhoto();
        photo.setId(UUID.randomUUID());
        photo.setListingId(listingId);
        photo.setFileId(UUID.randomUUID());
        photo.setDisplayOrder(0);
        photo.setPrimary(true);
        return photo;
    }
}
