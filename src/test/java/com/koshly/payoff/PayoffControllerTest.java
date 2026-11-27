package com.koshly.payoff;

import static com.koshly.testsupport.TestUsers.user;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.koshly.testsupport.KoshlyTest;

/** GET /api/payoff-plan against the test database. Each test is rolled back. */
@KoshlyTest
@Transactional
class PayoffControllerTest {

    private static final String HIGH_RATE_CARD = """
            {"type":"credit","subtype":"credit_card","name":"Sapphire","institution":"Chase","last4":"4821",
             "balance":3000,
             "creditCard":{"creditLimit":10000,"statementBalance":3000,"minimumDue":90,"dueDay":15,
                           "apr":24.99,"color":"#1E3A8A"}}
            """;
    // A store card with a deferred-interest promo ending far in the future, so it only needs its minimum now.
    private static final String STORE_CARD = """
            {"type":"credit","subtype":"credit_card","name":"Store Card","institution":"Synchrony","last4":"1180",
             "balance":1200,
             "creditCard":{"creditLimit":3000,"statementBalance":1200,"minimumDue":35,"dueDay":3,
                           "apr":29.99,"color":"#BE123C",
                           "promo":{"type":"purchase","apr":0,"balance":1200,"endDate":"2030-06-01","fee":0,
                                    "deferredInterest":true}}}
            """;
    private static final String AUTO_LOAN = """
            {"type":"loan","subtype":"auto","name":"Auto Loan","institution":"Toyota Financial","balance":12000,
             "loan":{"originalAmount":20000,"monthlyPayment":350,"paymentDay":1,"apr":6.9}}
            """;
    private static final String MORTGAGE = """
            {"type":"loan","subtype":"mortgage","name":"Home","institution":"Rocket","balance":250000,
             "loan":{"originalAmount":300000,"monthlyPayment":1600,"paymentDay":1,"apr":6.5}}
            """;
    private static final String SAVINGS = """
            {"type":"depository","subtype":"savings","name":"Savings","institution":"Ally","last4":"7731",
             "balance":5000}
            """;
    private static final String CHECKING = """
            {"type":"depository","subtype":"checking","name":"Checking","institution":"Chase","last4":"2290",
             "balance":2000}
            """;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void add(String uid, String json) throws Exception {
        mvc.perform(post("/api/accounts").with(user(uid)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void plansFromTheCardsAndCashOnTheAccount() throws Exception {
        add("alice", HIGH_RATE_CARD);
        add("alice", STORE_CARD);
        add("alice", CHECKING);

        mvc.perform(get("/api/payoff-plan").param("monthly", "800").param("cushion", "500").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.strategy").value("avalanche"))
                .andExpect(jsonPath("$.cash").value(2000))
                .andExpect(jsonPath("$.availableNow").value(1500))
                .andExpect(jsonPath("$.monthlyBudget").value(800))
                .andExpect(jsonPath("$.monthlyBudgetDefaulted").value(false))
                .andExpect(jsonPath("$.minimumsThisMonth").value(125))
                .andExpect(jsonPath("$.debts.length()").value(2))
                // The 24.99% card gets everything above the minimums: 1500 - 35.
                .andExpect(jsonPath("$.debts[?(@.name == 'Sapphire')].payThisMonth").value(1465.0))
                .andExpect(jsonPath("$.debts[?(@.name == 'Sapphire')].reason").value("highest_rate"))
                // The 0% store card waits, with only its minimum.
                .andExpect(jsonPath("$.debts[?(@.name == 'Store Card')].payThisMonth").value(35.0))
                .andExpect(jsonPath("$.debts[?(@.name == 'Store Card')].reason").value("promo_wait"))
                .andExpect(jsonPath("$.debts[?(@.name == 'Store Card')].promo.deferredInterest").value(true))
                .andExpect(jsonPath("$.paidOff").value(true))
                .andExpect(jsonPath("$.debtFreeMonth").exists())
                .andExpect(jsonPath("$.interestSaved").exists())
                .andExpect(jsonPath("$.otherStrategy").value("snowball"))
                .andExpect(jsonPath("$.schedule[0].payments.length()").value(2))
                .andExpect(jsonPath("$.warnings.length()").value(0));
    }

    @Test
    void loansAreIncludedAndMortgagesStayOnScheduleByDefault() throws Exception {
        add("alice", HIGH_RATE_CARD);
        add("alice", AUTO_LOAN);
        add("alice", MORTGAGE);
        add("alice", CHECKING);
        add("alice", SAVINGS); // $7,000 cash covers this month's $2,040 of minimums and loan payments

        mvc.perform(get("/api/payoff-plan").param("monthly", "2600").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debts.length()").value(3))
                // 90 card minimum + 350 + 1600 loan payments
                .andExpect(jsonPath("$.minimumsThisMonth").value(2040))
                .andExpect(jsonPath("$.debts[?(@.name == 'Auto Loan')].kind").value("loan"))
                .andExpect(jsonPath("$.debts[?(@.name == 'Auto Loan')].acceptsExtra").value(true))
                .andExpect(jsonPath("$.debts[?(@.name == 'Home')].acceptsExtra").value(false))
                .andExpect(jsonPath("$.debts[?(@.name == 'Home')].reason").value("regular_only"))
                // The mortgage isn't in the debt-free total.
                .andExpect(jsonPath("$.totalDebt").value(15000))
                .andExpect(jsonPath("$.warnings.length()").value(0));
    }

    @Test
    void theUserChoosesWhichLoansGetExtraPayments() throws Exception {
        add("alice", AUTO_LOAN);
        add("alice", CHECKING);
        // An empty list: no loan gets extra money, so the auto loan stays on its schedule.
        mvc.perform(get("/api/payoff-plan").param("extraLoans", "").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debts[0].acceptsExtra").value(false));
        mvc.perform(get("/api/payoff-plan").param("extraLoans", "not-an-id").with(user("alice")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savesTheDeferredInterestFlagOnTheCard() throws Exception {
        add("alice", STORE_CARD);
        mvc.perform(get("/api/accounts").with(user("alice")))
                .andExpect(jsonPath("$[0].creditCard.promo.deferredInterest").value(true));
    }

    @Test
    void withoutABudgetItPlansOnTheMinimums() throws Exception {
        add("alice", HIGH_RATE_CARD);
        mvc.perform(get("/api/payoff-plan").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyBudgetDefaulted").value(true))
                .andExpect(jsonPath("$.monthlyBudget").value(90));
    }

    @Test
    void noCardsMeansNothingToPay() throws Exception {
        add("alice", CHECKING);
        mvc.perform(get("/api/payoff-plan").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debts.length()").value(0))
                .andExpect(jsonPath("$.paidOff").value(true))
                .andExpect(jsonPath("$.totalDebt").value(0));
    }

    @Test
    void rejectsBadInput() throws Exception {
        mvc.perform(get("/api/payoff-plan").param("strategy", "fastest").with(user("alice")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/payoff-plan").param("monthly", "-5").with(user("alice")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/payoff-plan").param("cushion", "abc").with(user("alice")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void needsASignIn() throws Exception {
        mvc.perform(get("/api/payoff-plan")).andExpect(status().isUnauthorized());
    }
}
