package com.rentflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
class SecurityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ListingRepository listingRepository;

    private String hostAToken;
    private String hostBToken;
    private UUID hostAId;
    private UUID hostBId;

    @BeforeEach
    void setUp() throws Exception {
        userRoleRepository.deleteAll();
        authUserRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();

        JsonNode regA = registerUser("hostA@example.com", "Password@123", "HOST");
        hostAId = UUID.fromString(regA.get("id").asText());
        hostAToken = login("hostA@example.com", "Password@123");

        JsonNode regB = registerUser("hostB@example.com", "Password@123", "HOST");
        hostBId = UUID.fromString(regB.get("id").asText());
        hostBToken = login("hostB@example.com", "Password@123");
    }

    @Test
    void hostCannotAccessAnotherHostsVehicle() throws Exception {
        String vehicleId = createVehicle(hostAToken, "Toyota", "Camry", "ABC-1");

        mockMvc.perform(get("/api/v1/host/vehicles/" + vehicleId)
                .header("Authorization", "Bearer " + hostBToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void hostCannotUpdateAnotherHostsVehicle() throws Exception {
        String vehicleId = createVehicle(hostAToken, "Honda", "Civic", "XYZ-2");

        mockMvc.perform(patch("/api/v1/host/vehicles/" + vehicleId)
                .header("Authorization", "Bearer " + hostBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "make": "Updated" }
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void hostCannotArchiveAnotherHostsVehicle() throws Exception {
        String vehicleId = createVehicle(hostAToken, "Ford", "Focus", "FFF-3");

        mockMvc.perform(delete("/api/v1/host/vehicles/" + vehicleId)
                .header("Authorization", "Bearer " + hostBToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void hostCannotAccessAnotherHostsListing() throws Exception {
        String vehicleId = createVehicle(hostAToken, "BMW", "3 Series", "BMW-1");
        String listingId = createListing(hostAToken, vehicleId, "My BMW");

        mockMvc.perform(get("/api/v1/host/listings/" + listingId)
                .header("Authorization", "Bearer " + hostBToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void hostCanAccessOwnVehicle() throws Exception {
        String vehicleId = createVehicle(hostAToken, "Audi", "A4", "AUDI-1");

        mockMvc.perform(get("/api/v1/host/vehicles/" + vehicleId)
                .header("Authorization", "Bearer " + hostAToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.make").value("Audi"));
    }

    @Test
    void unauthenticatedUser_cannotAccessVehicleEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/host/vehicles"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/host/vehicles")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotAccessHostVehicleEndpoints() throws Exception {
        String customerToken = getCustomerToken();

        mockMvc.perform(get("/api/v1/host/vehicles")
                .header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/host/vehicles")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotAccessAdminEndpoints() throws Exception {
        String vehicleId = createVehicle(hostAToken, "Tesla", "Model 3", "TES-1");
        String listingId = createListing(hostAToken, vehicleId, "Tesla for rent");

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                .header("Authorization", "Bearer " + hostAToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/approve")
                .header("Authorization", "Bearer " + hostAToken))
            .andExpect(status().isForbidden());
    }

    // Helpers

    private JsonNode registerUser(String email, String password, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s",
                      "fullName": "Test User"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isCreated())
            .andReturn();

        String userId = parseJson(result).get("id").asText();
        var user = authUserRepository.findById(UUID.fromString(userId)).orElseThrow();
        Role requestedRole = Role.valueOf(role);
        if (requestedRole != Role.CUSTOMER) {
            userRoleRepository.save(new UserRole(user, requestedRole));
        }

        return parseJson(result);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();
        return parseJson(result).get("accessToken").asText();
    }

    private String getCustomerToken() throws Exception {
        registerUser("customer@example.com", "Password@123", "CUSTOMER");
        return login("customer@example.com", "Password@123");
    }

    private String createVehicle(String token, String make, String model, String plate) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/host/vehicles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "category": "SEDAN",
                      "make": "%s",
                      "model": "%s",
                      "year": 2020,
                      "plateNumber": "%s",
                      "transmission": "AUTO",
                      "fuelType": "PETROL",
                      "seats": 5,
                      "city": "Hanoi"
                    }
                    """.formatted(make, model, plate)))
            .andExpect(status().isCreated())
            .andReturn();
        return parseJson(result).get("id").asText();
    }

    private String createListing(String token, String vehicleId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/host/listings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "vehicleId": "%s",
                      "title": "%s",
                      "city": "Hanoi",
                      "basePricePerDay": 500000
                    }
                    """.formatted(vehicleId, title)))
            .andExpect(status().isCreated())
            .andReturn();
        return parseJson(result).get("id").asText();
    }

    private JsonNode parseJson(MvcResult result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString());
    }
}
