package com.koshly.dashboard;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import static com.koshly.testsupport.TestUsers.indian;

/** Runs against the throwaway test database (see KoshlyTest). Each test is rolled back. */
@KoshlyTest
@Transactional
class DashboardControllerTest {

    // A card with a 0% balance transfer ending in 2030, so the promo is always in the future.
    private static final String CARD = """
            {"type":"credit","subtype":"credit_card","name":"Sapphire","institution":"Chase","last4":"4821",
             "balance":1842.36,
             "creditCard":{"creditLimit":12000,"statementBalance":1620.11,"minimumDue":40,"dueDay":15,
                           "apr":21.49,"color":"#1E3A8A",
                           "promo":{"type":"balance_transfer","apr":0,"balance":400,"endDate":"2030-03-15","fee":12}}}
            """;
    private static final String CHECKING = """
            {"type":"depository","subtype":"checking","name":"Checking","institution":"Chase","last4":"2290",
             "balance":4825.17}
            """;
    private static final String LOAN = """
            {"type":"loan","subtype":"auto","name":"Auto Loan","institution":"Toyota Financial","balance":18450.22,
             "loan":{"originalAmount":32000,"monthlyPayment":545,"paymentDay":1,"apr":5.9}}
            """;

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

    private void add(String uid, String json) throws Exception {
        mvc.perform(post("/api/accounts").with(user(uid)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void addsUpOneCountrysMoney() throws Exception {
        add("alice", CARD);
        add("alice", CHECKING);
        add("alice", LOAN);

        mvc.perform(get("/api/dashboard").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("US"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.cash").value(4825.17))
                .andExpect(jsonPath("$.cardDebt").value(1842.36))
                .andExpect(jsonPath("$.loanDebt").value(18450.22))
                .andExpect(jsonPath("$.totalDebt").value(20292.58))
                // 4825.17 - 1842.36 - 18450.22
                .andExpect(jsonPath("$.netBalance").value(-15467.41))
                .andExpect(jsonPath("$.totalLimit").value(12000))
                .andExpect(jsonPath("$.availableCredit").value(10157.64))
                // 1842.36 / 12000 = 0.15353 -> 0.1535
                .andExpect(jsonPath("$.utilization").value(0.1535))
                // the card's minimum and the loan payment are both due within 30 days
                .andExpect(jsonPath("$.upcoming.length()").value(2))
                .andExpect(jsonPath("$.dueSoonTotal").value(585))
                .andExpect(jsonPath("$.promos.length()").value(1))
                .andExpect(jsonPath("$.promos[0].promo.type").value("balance_transfer"))
                .andExpect(jsonPath("$.promos[0].monthlyToClear").exists());
    }

    @Test
    void anIndianAccountHasItsOwnDashboardInRupees() throws Exception {
        add("alice", CHECKING); // a US account's money
        mvc.perform(get("/api/dashboard").with(indian("priya")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("IN"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.cash").value(0))
                .andExpect(jsonPath("$.upcoming.length()").value(0));
    }

    @Test
    void anotherUsersAccountsAreNotCounted() throws Exception {
        add("alice", CHECKING);
        mvc.perform(get("/api/dashboard").with(user("bob")))
                .andExpect(jsonPath("$.cash").value(0));
    }
}
