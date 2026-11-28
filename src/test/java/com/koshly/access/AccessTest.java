package com.koshly.access;

import static com.koshly.testsupport.TestUsers.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.koshly.accountlink.CountryLink;
import com.koshly.accountlink.CountryLinkRepository;
import com.koshly.testsupport.TestUsers;
import com.koshly.testsupport.TestcontainersConfiguration;
import com.koshly.user.AppUserRepository;

/**
 * The phone and subscription checks with both switches on (in real use they're set in
 * Firestore config/app; here through the deploy-setting defaults). Its own app, like
 * RateLimitTest, because @KoshlyTest runs with both switches off.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "koshly.accounts.tenants.IN=" + TestUsers.INDIA_TENANT,
        "koshly.accounts.tenants.CA=" + TestUsers.CANADA_TENANT,
        "koshly.accounts.enabled-countries=US,IN,CA",
        "koshly.app-config.firestore=false",
        "koshly.app-config.require-subscription=true",
        "koshly.app-config.require-phone-verification=true",
        "koshly.rate-limit.reads-per-minute=1000000",
        "koshly.rate-limit.writes-per-minute=1000000"})
@Transactional
class AccessTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private CountryLinkRepository links;

    private static final String CARD = """
            {"type":"credit","subtype":"credit_card","name":"Card","institution":"Bank","last4":"1234",
             "balance":100,"creditCard":{"creditLimit":1000,"statementBalance":50,"minimumDue":25,"dueDay":15,"apr":20,"color":"#1E3A8A"}}
            """;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** A US user whose sign-in carries a confirmed mobile number. */
    private static RequestPostProcessor withPhone(String uid) {
        return jwt().jwt(token -> token.subject(uid)
                .claim("email", uid + "@example.com")
                .claim("email_verified", true)
                .claim("phone_number", "+14695550142"));
    }

    private void subscribe(String uid, String status, Instant expiresAt) {
        users.insertIfMissing(uid, uid + "@example.com", "us", "US", "USD", "America/Chicago");
        subscriptions.saveAndFlush(new Subscription(uid, "koshly", 1, "manual", status, expiresAt));
    }

    @Test
    void withoutAConfirmedPhoneOnlyTheBasicsWork() throws Exception {
        mvc.perform(get("/api/dashboard").with(user("alice")))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PHONE_REQUIRED"));
        // Still allowed: why, and the profile.
        mvc.perform(get("/api/access").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value("PHONE_REQUIRED"))
                .andExpect(jsonPath("$.phoneVerified").value(false));
        mvc.perform(get("/api/users/current").with(user("alice"))).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions addCard(RequestPostProcessor who) throws Exception {
        return mvc.perform(post("/api/accounts").with(who).contentType(MediaType.APPLICATION_JSON).content(CARD));
    }

    @Test
    void withoutAPlanYouGetTheFreeTier() throws Exception {
        // The dashboard works...
        mvc.perform(get("/api/dashboard").with(withPhone("bob"))).andExpect(status().isOk());
        mvc.perform(get("/api/access").with(withPhone("bob")))
                .andExpect(jsonPath("$.blocked").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.freeTier").value(true))
                .andExpect(jsonPath("$.freeLimits.creditCards").value(2))
                .andExpect(jsonPath("$.countriesAllowed").value(1));
        // ...with two cards, not three.
        addCard(withPhone("bob")).andExpect(status().isCreated());
        addCard(withPhone("bob")).andExpect(status().isCreated());
        addCard(withPhone("bob"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("FREE_LIMIT"));
        // Bank linking is for paid plans (503 while the provider isn't ready, which is checked first).
        mvc.perform(post("/api/bank-links").with(withPhone("bob")))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .isIn(402, 503));
    }

    @Test
    void aPlanLiftsTheLimits() throws Exception {
        subscribe("frank", "active", Instant.now().plus(30, ChronoUnit.DAYS));
        for (int i = 0; i < 3; i++) {
            addCard(withPhone("frank")).andExpect(status().isCreated());
        }
    }

    @Test
    void anAddedCountryNeedsAPlanBehindIt() throws Exception {
        users.insertIfMissing("gina", "gina@example.com", "us", "US", "USD", "America/Chicago");
        links.saveAndFlush(new CountryLink("gina-ca", "CA", "gina"));
        RequestPostProcessor ginaInCanada = TestUsers.tenant("gina-ca", TestUsers.CANADA_TENANT);
        mvc.perform(get("/api/dashboard").with(ginaInCanada))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_REQUIRED"));
        mvc.perform(get("/api/access").with(ginaInCanada))
                .andExpect(jsonPath("$.home").value(false))
                .andExpect(jsonPath("$.homeCountry").value("US"));
    }

    @Test
    void anActivePlanOpensEverything() throws Exception {
        subscribe("carol", "active", Instant.now().plus(30, ChronoUnit.DAYS));
        mvc.perform(get("/api/dashboard").with(withPhone("carol"))).andExpect(status().isOk());
        mvc.perform(get("/api/access").with(withPhone("carol")))
                .andExpect(jsonPath("$.blocked").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.plan").value("koshly"))
                .andExpect(jsonPath("$.countriesAllowed").value(1));
    }

    @Test
    void anExpiredPlanFallsBackToTheFreeTier() throws Exception {
        subscribe("dave", "active", Instant.now().minus(1, ChronoUnit.DAYS));
        mvc.perform(get("/api/access").with(withPhone("dave")))
                .andExpect(jsonPath("$.freeTier").value(true))
                .andExpect(jsonPath("$.subscriptionEnded").value(true));
        subscribe("erin", "expired", null);
        mvc.perform(get("/api/access").with(withPhone("erin"))).andExpect(jsonPath("$.freeTier").value(true));
    }
}
