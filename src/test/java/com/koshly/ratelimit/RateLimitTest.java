package com.koshly.ratelimit;

import static com.koshly.testsupport.TestUsers.indian;
import static com.koshly.testsupport.TestUsers.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.koshly.testsupport.TestUsers;
import com.koshly.testsupport.TestcontainersConfiguration;

/**
 * The limit in front of the real endpoints, with tiny limits (3 reads, 2 writes a minute).
 * Not @KoshlyTest, which switches the limit off for the rest of the suite; this runs in
 * its own app so its counts start at zero. Each test uses its own user IDs for the same reason.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "koshly.accounts.tenants.IN=" + TestUsers.INDIA_TENANT,
        "koshly.accounts.enabled-countries=US,IN",
        // Tests use the deploy setting above, never a real Firestore document.
        "koshly.app-config.firestore=false",
        "koshly.rate-limit.reads-per-minute=3",
        "koshly.rate-limit.writes-per-minute=2"})
@Transactional
class RateLimitTest {

    private static final String URL = "/api/users/current/preferences";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void answers429WithRetryAfterOverTheReadLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get(URL).with(user("reader"))).andExpect(status().isOk());
        }
        mvc.perform(get(URL).with(user("reader")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Too many requests. Please wait a moment and try again."));
    }

    @Test
    void writesHaveTheirOwnLowerLimit() throws Exception {
        String body = "{\"timeZone\": \"America/Chicago\"}";
        mvc.perform(get(URL).with(user("writer"))).andExpect(status().isOk()); // creates the user's settings
        for (int i = 0; i < 2; i++) {
            mvc.perform(put(URL).with(user("writer")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
        mvc.perform(put(URL).with(user("writer")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
        // Reading still works: the two limits are counted separately.
        mvc.perform(get(URL).with(user("writer"))).andExpect(status().isOk());
    }

    @Test
    void oneBusyPersonDoesNotSlowDownAnother() throws Exception {
        for (int i = 0; i < 3; i++) mvc.perform(get(URL).with(user("busy")));
        mvc.perform(get(URL).with(user("busy"))).andExpect(status().isTooManyRequests());
        mvc.perform(get(URL).with(user("calm"))).andExpect(status().isOk());
    }

    @Test
    void theSameIdInAnotherCountryIsCountedSeparately() throws Exception {
        for (int i = 0; i < 3; i++) mvc.perform(get(URL).with(user("twin")));
        mvc.perform(get(URL).with(user("twin"))).andExpect(status().isTooManyRequests());
        mvc.perform(get(URL).with(indian("twin"))).andExpect(status().isOk());
    }

    @Test
    void healthIsNeverLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }
}
