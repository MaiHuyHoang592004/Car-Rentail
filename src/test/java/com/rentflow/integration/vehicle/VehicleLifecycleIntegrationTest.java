package com.rentflow.integration.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.vehicle.entity.*;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@Tag("integration")
class VehicleLifecycleIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rentflow")
            .withUsername("rentflow")
            .withPassword("rentflow");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    private String hostToken;
    private UUID hostUserId;

    @BeforeEach
    void setUp() throws Exception {
        userRoleRepository.deleteAll();
        authUserRepository.deleteAll();
        vehicleRepository.deleteAll();

        JsonNode regResult = registerUser("host@example.com", "Password@123", "HOST");
        hostUserId = UUID.fromString(regResult.get("id").asText());
        hostToken = login("host@example.com", "Password@123");
    }

    @Test
    void createVehicle_defaultsToActive() throws Exception {
        mockMvc.perform(post("/api/v1/host/vehicles")
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "category": "SEDAN",
                      "make": "Toyota",
                      "model": "Camry",
                      "year": 2020,
                      "plateNumber": "ABC-123",
                      "vin": "1HGBH41JXMN109186",
                      "transmission": "AUTO",
                      "fuelType": "PETROL",
                      "seats": 5,
                      "city": "Hanoi"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.make").value("Toyota"))
            .andExpect(jsonPath("$.model").value("Camry"));
    }

    @Test
    void createVehicle_asDraft() throws Exception {
        mockMvc.perform(post("/api/v1/host/vehicles")
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "category": "SUV",
                      "make": "Honda",
                      "model": "CR-V",
                      "year": 2021,
                      "plateNumber": "XYZ-999",
                      "transmission": "AUTO",
                      "fuelType": "HYBRID",
                      "seats": 5,
                      "status": "DRAFT",
                      "city": "Hanoi"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void listVehicles_returnsAllVehicles() throws Exception {
        String vehicleId = createVehicle("SEDAN", "BMW", "3 Series", 2022, "AA-1", "AUTO", "PETROL", 5);

        mockMvc.perform(get("/api/v1/host/vehicles")
                .header("Authorization", "Bearer " + hostToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].make").value("BMW"));
    }

    @Test
    void listVehicles_filtersbyStatus() throws Exception {
        createVehicle("SEDAN", "BMW", "3 Series", 2022, "AA-1", "AUTO", "PETROL", 5);

        mockMvc.perform(get("/api/v1/host/vehicles")
                .header("Authorization", "Bearer " + hostToken)
                .param("status", "MAINTENANCE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void archiveVehicle_requiresOwnership() throws Exception {
        JsonNode otherReg = registerUser("other@example.com", "Password@123", "HOST");
        String otherToken = login("other@example.com", "Password@123");
        String vehicleId = createVehicle("SEDAN", "Audi", "A4", 2023, "BB-2", "AUTO", "PETROL", 5);

        mockMvc.perform(delete("/api/v1/host/vehicles/" + vehicleId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void archiveVehicle_returns204NoContent() throws Exception {
        String vehicleId = createVehicle("SEDAN", "Mercedes", "C-Class", 2022, "CC-3", "AUTO", "PETROL", 5);

        mockMvc.perform(delete("/api/v1/host/vehicles/" + vehicleId)
                .header("Authorization", "Bearer " + hostToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void hostCannotAccessVehicleWithoutAuth() throws Exception {
        String vehicleId = createVehicle("SEDAN", "Mazda", "3", 2021, "DD-4", "AUTO", "PETROL", 5);

        mockMvc.perform(get("/api/v1/host/vehicles/" + vehicleId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotAccessVehicleEndpoints() throws Exception {
        registerUser("customer@example.com", "Password@123", "CUSTOMER");
        String customerToken = login("customer@example.com", "Password@123");

        mockMvc.perform(get("/api/v1/host/vehicles")
                .header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateVehicle_patchMakes() throws Exception {
        String vehicleId = createVehicle("SEDAN", "Toyota", "Camry", 2020, "ABC-1", "AUTO", "PETROL", 5);

        mockMvc.perform(patch("/api/v1/host/vehicles/" + vehicleId)
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "make": "Lexus" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.make").value("Lexus"));
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

    private String createVehicle(String category, String make, String model, int year,
                                  String plate, String transmission, String fuel, int seats) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/host/vehicles")
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "category": "%s",
                      "make": "%s",
                      "model": "%s",
                      "year": %d,
                      "plateNumber": "%s",
                      "transmission": "%s",
                      "fuelType": "%s",
                      "seats": %d,
                      "city": "Hanoi"
                    }
                    """.formatted(category, make, model, year, plate, transmission, fuel, seats)))
            .andExpect(status().isCreated())
            .andReturn();
        return parseJson(result).get("id").asText();
    }

    private JsonNode parseJson(MvcResult result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString());
    }
}
