package com.rentflow.integration.file;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.file.entity.FileMetadata;
import com.rentflow.file.entity.FileVisibility;
import com.rentflow.file.repository.FileMetadataRepository;
import com.rentflow.file.repository.ListingPhotoRepository;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleCategory;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class FileMetadataIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private FileMetadataRepository fileMetadataRepository;
    @Autowired private ListingPhotoRepository listingPhotoRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private AuthUser host;
    private AuthUser otherUser;
    private String hostToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        listingPhotoRepository.deleteAll();
        fileMetadataRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        authUserRepository.deleteAll();

        host = saveUser("host-" + UUID.randomUUID() + "@example.com", Role.HOST);
        otherUser = saveUser("other-" + UUID.randomUUID() + "@example.com", Role.CUSTOMER);
        hostToken = jwtTokenProvider.generateAccessToken(host.getId(), host.getEmail(), List.of(Role.HOST));
        otherToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail(), List.of(Role.CUSTOMER));
    }

    @Test
    void addListingPhotoForDraftListingCreatesPrivateMetadata() throws Exception {
        Listing draftListing = saveListing(host, ListingStatus.DRAFT);

        mockMvc.perform(post("/api/v1/host/listings/{id}/photos", draftListing.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucket":"rentflow-private",
                                  "objectKey":"listings/photo-a.jpg",
                                  "contentType":"image/jpeg",
                                  "sizeBytes":4096,
                                  "primary":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingId").value(draftListing.getId().toString()))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.signedUrl").exists());

        assertThat(fileMetadataRepository.findAll()).hasSize(1);
        FileMetadata metadata = fileMetadataRepository.findAll().get(0);
        assertThat(metadata.getOwnerUserId()).isEqualTo(host.getId());
        assertThat(metadata.getVisibility()).isEqualTo(FileVisibility.PRIVATE);
    }

    @Test
    void signedUrlRequiresPermissionForPrivateFileButPublicFileReadable() throws Exception {
        Listing draftListing = saveListing(host, ListingStatus.DRAFT);
        Listing activeListing = saveListing(host, ListingStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/host/listings/{id}/photos", draftListing.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucket":"rentflow-private",
                                  "objectKey":"listings/private.jpg",
                                  "contentType":"image/jpeg",
                                  "sizeBytes":4096
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));

        mockMvc.perform(post("/api/v1/host/listings/{id}/photos", activeListing.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucket":"rentflow-public",
                                  "objectKey":"listings/public.jpg",
                                  "contentType":"image/jpeg",
                                  "sizeBytes":4096
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));

        FileMetadata privateFile = fileMetadataRepository.findAll().stream()
                .filter(file -> file.getVisibility() == FileVisibility.PRIVATE)
                .findFirst()
                .orElseThrow();
        FileMetadata publicFile = fileMetadataRepository.findAll().stream()
                .filter(file -> file.getVisibility() == FileVisibility.PUBLIC)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/v1/files/{id}/signed-url", privateFile.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/files/{id}/signed-url", publicFile.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(publicFile.getId().toString()))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.signedUrl").exists());
    }

    private AuthUser saveUser(String email, Role role) {
        AuthUser user = new AuthUser(email, "hash", UserStatus.ACTIVE, true);
        user.addRole(role);
        return authUserRepository.save(user);
    }

    private Listing saveListing(AuthUser hostUser, ListingStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setHostId(hostUser.getId());
        vehicle.setCategory(VehicleCategory.SEDAN);
        vehicle.setMake("Toyota");
        vehicle.setModel("Vios");
        vehicle.setManufactureYear(2023);
        vehicle.setTransmission(TransmissionType.AUTO);
        vehicle.setFuelType(FuelType.PETROL);
        vehicle.setSeats(5);
        vehicle.setCity("Hanoi");
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle = vehicleRepository.save(vehicle);

        Listing listing = new Listing();
        listing.setHostId(hostUser.getId());
        listing.setVehicleId(vehicle.getId());
        listing.setTitle("Photo listing " + UUID.randomUUID());
        listing.setCity("Hanoi");
        listing.setBasePricePerDay(new BigDecimal("800000.00"));
        listing.setCurrency("VND");
        listing.setInstantBook(true);
        listing.setDailyKmLimit(200);
        listing.setCancellationPolicy(CancellationPolicy.FLEXIBLE);
        listing.setStatus(status);
        return listingRepository.save(listing);
    }
}
