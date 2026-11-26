package com.koshly.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.koshly.testsupport.KoshlyTest;

@KoshlyTest
class CurrentUserControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void rejectsRequestsWithoutAToken() throws Exception {
        mvc.perform(get("/api/users/current")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheSignedInUser() throws Exception {
        mvc.perform(get("/api/users/current").with(jwt().jwt(token -> token
                        .subject("user-123")
                        .claim("email", "nish@example.com")
                        .claim("email_verified", true)
                        .claim("firebase", Map.of("sign_in_provider", "password")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("user-123"))
                .andExpect(jsonPath("$.email").value("nish@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.signInProvider").value("password"));
    }

    @Test
    void otherApiEndpointsNeedAVerifiedEmail() throws Exception {
        mvc.perform(get("/api/accounts").with(jwt().jwt(t -> t.claim("email_verified", false))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts").with(jwt().jwt(t -> t.claim("email_verified", true))))
                .andExpect(status().isOk());
    }

    @Test
    void currentUserWorksBeforeEmailIsVerified() throws Exception {
        mvc.perform(get("/api/users/current").with(jwt().jwt(t -> t.claim("email_verified", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void anythingNotListedIsDeniedEvenWhenSignedIn() throws Exception {
        mvc.perform(get("/actuator/env").with(jwt())).andExpect(status().isForbidden());
    }
}
