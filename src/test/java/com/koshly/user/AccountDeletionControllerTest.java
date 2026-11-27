package com.koshly.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.koshly.config.SessionCheck;
import com.koshly.testsupport.KoshlyTest;

/**
 * DELETE /api/users/current. Firebase (profile, username, sign-in) and the
 * "session still active?" check are Mockito stand-ins.
 */
@KoshlyTest
@Transactional
class AccountDeletionControllerTest {

    private static final String CARD = """
            {"type":"credit","subtype":"credit_card","name":"Sapphire","institution":"Chase","last4":"4821",
             "balance":500,
             "creditCard":{"creditLimit":5000,"statementBalance":500,"minimumDue":25,"dueDay":15,
                           "apr":24.99,"color":"#1E3A8A"}}
            """;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private UserIdentities identities;

    @MockitoBean
    private SessionCheck sessions;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** A verified user who signed in {@code secondsAgo} seconds ago. */
    private static RequestPostProcessor signedIn(String uid, long secondsAgo) {
        return jwt().jwt(token -> token.subject(uid)
                .claim("email", uid + "@example.com")
                .claim("email_verified", true)
                .claim("auth_time", Instant.now().getEpochSecond() - secondsAgo));
    }

    private void addCard(String uid) throws Exception {
        mvc.perform(post("/api/accounts").with(signedIn(uid, 10)).contentType(MediaType.APPLICATION_JSON).content(CARD))
                .andExpect(status().isCreated());
    }

    @Test
    void deletesEverythingForTheUser() throws Exception {
        addCard("alice");
        addCard("bob");

        mvc.perform(delete("/api/users/current").with(signedIn("alice", 60)))
                .andExpect(status().isNoContent());
        verify(identities).delete(org.mockito.ArgumentMatchers.eq("alice"), isNull());

        // Alice's data is gone; Bob's is untouched.
        mvc.perform(get("/api/accounts").with(signedIn("alice", 60)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/accounts").with(signedIn("bob", 60)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void needsARecentSignIn() throws Exception {
        addCard("alice");
        // Signed in two hours ago: must sign in again first.
        mvc.perform(delete("/api/users/current").with(signedIn("alice", 2 * 60 * 60)))
                .andExpect(status().isForbidden());
        verify(identities, never()).delete(anyString(), any());
        mvc.perform(get("/api/accounts").with(signedIn("alice", 60)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aRevokedSessionCantDelete() throws Exception {
        addCard("alice");
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your session has ended. Please sign in again."))
                .when(sessions).requireActive(any());
        mvc.perform(delete("/api/users/current").with(signedIn("alice", 60)))
                .andExpect(status().isUnauthorized());
        verify(identities, never()).delete(anyString(), any());
    }

    @Test
    void ifFirebaseFailsTheUserIsTold() throws Exception {
        // (In the real app the database delete is rolled back too, because Firebase runs inside the
        // same transaction. This test runs inside its own test transaction, so it checks the reply only.)
        addCard("alice");
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Couldn't delete your account right now."))
                .when(identities).delete(anyString(), any());
        mvc.perform(delete("/api/users/current").with(signedIn("alice", 60)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void needsASignIn() throws Exception {
        mvc.perform(delete("/api/users/current")).andExpect(status().isUnauthorized());
    }
}
