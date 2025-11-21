package io.crmplatform.web;

import io.crmplatform.web.dto.AuthDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end slice over the real stack: MockMvc through the security filter chain, the service
 * layer, Hibernate and a throwaway MySQL 8 container migrated by the actual Flyway scripts.
 *
 * <p>An H2 substitute would have been faster to start and useless here - it would not validate the
 * production DDL, the {@code ddl-auto: validate} contract, or MySQL's collation behaviour in the
 * case-insensitive search. The seeded demo users double as the RBAC fixtures.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CustomerControllerIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
            .withDatabaseName("crm")
            .withUsername("crm_app")
            .withPassword("crm_app_password")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------------------------------------------------------------------------------
    // Authentication
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("the contact list is not reachable without a token")
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a garbage bearer token is treated as anonymous, not as a server error")
    void malformedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login returns an access token, a refresh token and the user's roles")
    void loginIssuesTokenPair() throws Exception {
        AuthDtos.TokenResponse tokens = login("admin@slatecrm.io", "Admin@12345");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokens.expiresIn()).isEqualTo(900L);
        assertThat(tokens.user().roles()).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("a wrong password yields a 401 problem document that does not leak which half failed")
    void badCredentialsReturnProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "admin@slatecrm.io", "password", "WrongPassword1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication failed"))
                .andExpect(jsonPath("$.detail").value("Invalid credentials."));
    }

    @Test
    @DisplayName("replaying a rotated refresh token revokes the rest of the chain")
    void refreshTokenReplayRevokesChain() throws Exception {
        String original = login("rep@slatecrm.io", "Rep@12345").refreshToken();

        AuthDtos.TokenResponse rotated = refresh(original);
        assertThat(rotated.refreshToken()).isNotEqualTo(original);

        // The rotation revoked the original, so presenting it again is a replay ...
        mockMvc.perform(refreshRequest(original)).andExpect(status().isUnauthorized());

        // ... and the replay must also kill the token the rotation handed out, otherwise
        // whoever holds the newer token keeps a working session.
        mockMvc.perform(refreshRequest(rotated.refreshToken())).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // Pagination, sorting and filtering
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("the list endpoint returns the PageResponse envelope with seeded contacts")
    void listReturnsPagedEnvelope() throws Exception {
        String token = login("admin@slatecrm.io", "Admin@12345").accessToken();

        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "lastName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    @DisplayName("the search filter matches an email fragment case-insensitively")
    void searchFiltersByEmailFragment() throws Exception {
        String token = login("manager@slatecrm.io", "Manager@12345").accessToken();

        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .param("search", "FISCHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("elena.fischer@northwind-logistics.example.com"))
                .andExpect(jsonPath("$.content[0].lifecycleStage").value("CHAMPION"));
    }

    @Test
    @DisplayName("the lifecycle stage filter narrows the result set")
    void stageFilterNarrowsResults() throws Exception {
        String token = login("manager@slatecrm.io", "Manager@12345").accessToken();

        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .param("stage", "LEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("a page size above the configured cap is clamped rather than honoured")
    void pageSizeIsCapped() throws Exception {
        String token = login("admin@slatecrm.io", "Admin@12345").accessToken();

        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    // ---------------------------------------------------------------------------------
    // Role based access control
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("READ_ONLY may read contacts")
    void readOnlyCanRead() throws Exception {
        String token = login("viewer@slatecrm.io", "ReadOnly@12345").accessToken();

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("READ_ONLY is refused a write with 403")
    void readOnlyCannotWrite() throws Exception {
        String token = login("viewer@slatecrm.io", "ReadOnly@12345").accessToken();

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", 1,
                                "firstName", "Nina",
                                "lastName", "Okafor",
                                "email", "nina.okafor@example.com",
                                "lifecycleStage", "LEAD"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    @DisplayName("READ_ONLY is refused the admin-only user directory")
    void readOnlyCannotListUsers() throws Exception {
        String token = login("viewer@slatecrm.io", "ReadOnly@12345").accessToken();

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a validation failure is reported as a problem document with per-field errors")
    void validationErrorsAreItemised() throws Exception {
        String token = login("admin@slatecrm.io", "Admin@12345").accessToken();

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", 1,
                                "firstName", "",
                                "lastName", "Okafor",
                                "email", "not-an-email",
                                "lifecycleStage", "LEAD"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.firstName").exists());
    }

    @Test
    @DisplayName("an unknown contact id returns a 404 problem document")
    void unknownIdReturnsNotFound() throws Exception {
        String token = login("admin@slatecrm.io", "Admin@12345").accessToken();

        mockMvc.perform(get("/api/v1/customers/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    private AuthDtos.TokenResponse login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthDtos.TokenResponse.class);
    }

    private AuthDtos.TokenResponse refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(refreshRequest(refreshToken))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthDtos.TokenResponse.class);
    }

    private MockHttpServletRequestBuilder refreshRequest(String refreshToken) throws Exception {
        return post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken)));
    }
}
