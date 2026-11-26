package com.koshly.banklink;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.koshly.testsupport.KoshlyTest;

/** Runs against the throwaway test database (see KoshlyTest). Each test is rolled back. */
@KoshlyTest
@Transactional
class BankLinkControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor user(String uid) {
        return jwt().jwt(token -> token.subject(uid)
                .claim("email", uid + "@example.com")
                .claim("email_verified", true));
    }

    @Test
    void eachCountryUsesItsOwnProvider() throws Exception {
        mvc.perform(get("/api/bank-links/provider?country=US").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("plaid"))
                .andExpect(jsonPath("$.name").value("Plaid"))
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/bank-links/provider?country=IN").with(user("alice")))
                .andExpect(jsonPath("$.provider").value("account_aggregator"))
                .andExpect(jsonPath("$.name").value("Account Aggregator"));
        mvc.perform(get("/api/bank-links/provider?country=FR").with(user("alice")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startingALinkSaysItIsNotAvailableYet() throws Exception {
        mvc.perform(post("/api/bank-links").with(user("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\": \"IN\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(
                        "Bank linking with Account Aggregator isn't available yet. You can add accounts yourself for now."));
        // Nothing was saved.
        mvc.perform(get("/api/bank-links?country=IN").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cannotDisconnectSomeoneElsesOrUnknownLinks() throws Exception {
        mvc.perform(delete("/api/bank-links/" + UUID.randomUUID()).with(user("alice")))
                .andExpect(status().isNotFound());
    }
}
