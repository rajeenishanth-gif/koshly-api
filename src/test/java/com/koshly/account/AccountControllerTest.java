package com.koshly.account;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Runs against the local Postgres (start it with: docker compose up -d postgres).
 * @Transactional rolls every test back, so tests leave no data behind.
 */
@SpringBootTest
@Transactional
class AccountControllerTest {

    private static final String CREDIT_CARD = """
            {
              "type": "credit", "subtype": "credit_card",
              "name": "Sapphire Preferred", "institution": "Chase", "last4": "4821",
              "balance": 1842.36,
              "creditCard": {
                "creditLimit": 12000, "statementBalance": 1620.11, "minimumDue": 40,
                "dueDay": 15, "apr": 21.49, "color": "#1E3A8A",
                "promo": { "type": "balance_transfer", "apr": 0, "balance": 400,
                           "endDate": "2027-03-15", "fee": 12 }
              }
            }
            """;

    private static final String CHECKING = """
            {
              "type": "depository", "subtype": "checking",
              "name": "Everyday Checking", "institution": "Chase", "last4": "2290",
              "balance": 4825.17
            }
            """;

    private static final String AUTO_LOAN = """
            {
              "type": "loan", "subtype": "auto",
              "name": "Auto Loan", "institution": "Toyota Financial",
              "balance": 18450.22,
              "loan": { "originalAmount": 32000, "monthlyPayment": 545, "paymentDay": 1, "apr": 5.9 }
            }
            """;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** A signed-in user with a verified email. */
    private static RequestPostProcessor user(String uid) {
        return jwt().jwt(token -> token
                .subject(uid)
                .claim("email", uid + "@example.com")
                .claim("email_verified", true));
    }

    /** Adds an account as {@code uid} and returns its id. */
    private String add(String uid, String json) throws Exception {
        String location = mvc.perform(post("/api/accounts").with(user(uid))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void addsAllThreeKindsAndListsThemTogether() throws Exception {
        mvc.perform(post("/api/accounts").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(CREDIT_CARD))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.type").value("credit"))
                .andExpect(jsonPath("$.balance").value(1842.36))
                .andExpect(jsonPath("$.creditCard.nextDueDate").exists())
                .andExpect(jsonPath("$.creditCard.promo.type").value("balance_transfer"))
                .andExpect(jsonPath("$.loan").doesNotExist());
        add("alice", CHECKING);
        add("alice", AUTO_LOAN);

        mvc.perform(get("/api/accounts").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // sorted by type: credit, depository, loan
                .andExpect(jsonPath("$[0].type").value("credit"))
                .andExpect(jsonPath("$[1].type").value("depository"))
                .andExpect(jsonPath("$[1].creditCard").doesNotExist())
                .andExpect(jsonPath("$[2].type").value("loan"))
                .andExpect(jsonPath("$[2].loan.nextPaymentDate").exists());
    }

    @Test
    void anotherUserCannotSeeChangeOrDeleteMyAccount() throws Exception {
        String id = add("alice", CREDIT_CARD);

        mvc.perform(get("/api/accounts").with(user("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/accounts/" + id).with(user("bob")))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/accounts/" + id).with(user("bob"))
                        .contentType(MediaType.APPLICATION_JSON).content(CREDIT_CARD))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/accounts/" + id).with(user("bob")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/accounts/" + id).with(user("alice")))
                .andExpect(status().isOk());
    }

    @Test
    void updatesAndDeletesMyAccount() throws Exception {
        String id = add("alice", AUTO_LOAN);

        mvc.perform(put("/api/accounts/" + id).with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AUTO_LOAN.replace("18450.22", "17905.22")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(17905.22));

        mvc.perform(delete("/api/accounts/" + id).with(user("alice")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/accounts/" + id).with(user("alice")))
                .andExpect(status().isNotFound());
    }

    @Test
    void typeCannotChange() throws Exception {
        String id = add("alice", CHECKING);

        mvc.perform(put("/api/accounts/" + id).with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(AUTO_LOAN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explainsEachInvalidField() throws Exception {
        String bad = CREDIT_CARD
                .replace("\"4821\"", "\"4111111111111111\"")   // a full card number: never allowed
                .replace("\"Chase\"", "\"<script>\"")
                .replace("\"minimumDue\": 40", "\"minimumDue\": 5000");

        mvc.perform(post("/api/accounts").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.last4").exists())
                .andExpect(jsonPath("$.errors.institution").exists())
                .andExpect(jsonPath("$.errors.minimumDueWithinStatement").exists());
    }

    @Test
    void detailsMustMatchTheType() throws Exception {
        // A bank account that wrongly includes loan details and a loan subtype
        String bad = CHECKING
                .replace("\"checking\"", "\"auto\"")
                .replace("\"balance\": 4825.17",
                        "\"balance\": 4825.17, \"loan\": { \"originalAmount\": 1, \"monthlyPayment\": 1, \"paymentDay\": 1, \"apr\": 0 }");

        mvc.perform(post("/api/accounts").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.subtypeMatchingType").exists())
                .andExpect(jsonPath("$.errors.loanDetailsMatchingType").exists());
    }

    @Test
    void needsAVerifiedEmail() throws Exception {
        mvc.perform(post("/api/accounts")
                        .with(jwt().jwt(t -> t.subject("carol").claim("email_verified", false)))
                        .contentType(MediaType.APPLICATION_JSON).content(CHECKING))
                .andExpect(status().isForbidden());
    }

    @Test
    void needsToBeSignedIn() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }
}
