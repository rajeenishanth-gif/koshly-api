package com.koshly.accountlink;

import static com.koshly.testsupport.TestUsers.indian;
import static com.koshly.testsupport.TestUsers.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.koshly.config.SessionCheck;
import com.koshly.testsupport.KoshlyTest;
import com.koshly.testsupport.TestUsers;

/**
 * "Add a country". Sending codes and creating Firebase users are replaced by
 * Mockito stand-ins (@MockitoBean), so the test can read the code that was
 * "sent" and no real email, SMS or Firebase call happens.
 */
@KoshlyTest
@Transactional
class AccountLinkControllerTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CodeSender sender;

    @MockitoBean
    private TenantAccounts tenants;

    /** Firebase's "is this session still active?" check: a stand-in that says yes. */
    @MockitoBean
    private SessionCheck sessions;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(tenants.signInTokenFor(anyString(), any(), anyString())).thenReturn("custom-token-for-india");
    }

    /** Asks for a code and returns {verificationId, code}. */
    private String[] sendCode(RequestPostProcessor who, String channel, String destination) throws Exception {
        String body = mvc.perform(post("/api/account-links/codes").with(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\":\"IN\",\"channel\":\"" + channel + "\",\"destination\":\"" + destination + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.verificationId");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).send(any(), anyString(), code.capture());
        return new String[] {id, code.getValue()};
    }

    private org.springframework.test.web.servlet.ResultActions verifyCode(RequestPostProcessor who, String id,
            String code) throws Exception {
        return mvc.perform(post("/api/account-links/codes/" + id + "/verify").with(who)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    @Test
    void aRightCodeSignsYouInToTheOtherCountry() throws Exception {
        String[] sent = sendCode(user("alice"), "email", "Alice@Example.com");
        verify(sender).send(eq(Channel.EMAIL), eq("alice@example.com"), anyString()); // normalized

        verifyCode(user("alice"), sent[0], sent[1])
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("IN"))
                .andExpect(jsonPath("$.signInToken").value("custom-token-for-india"));
        verify(tenants).signInTokenFor(TestUsers.INDIA_TENANT, Channel.EMAIL, "alice@example.com");

        // A code works once.
        verifyCode(user("alice"), sent[0], sent[1]).andExpect(status().isGone());
    }

    @Test
    void aMobileNumberWorksToo() throws Exception {
        String[] sent = sendCode(user("alice"), "sms", "+1 (555) 201-4477");
        verifyCode(user("alice"), sent[0], sent[1]).andExpect(status().isOk());
        verify(tenants).signInTokenFor(TestUsers.INDIA_TENANT, Channel.SMS, "+15552014477");
    }

    @Test
    void wrongCodesRunOutAfterFiveTries() throws Exception {
        String[] sent = sendCode(user("alice"), "email", "alice@example.com");
        String wrong = sent[1].equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            verifyCode(user("alice"), sent[0], wrong).andExpect(status().isBadRequest());
        }
        // Even the right code no longer works.
        verifyCode(user("alice"), sent[0], sent[1]).andExpect(status().isTooManyRequests());
        verify(tenants, never()).signInTokenFor(anyString(), any(), anyString());
    }

    @Test
    void someoneElseCantUseYourCode() throws Exception {
        String[] sent = sendCode(user("alice"), "email", "alice@example.com");
        verifyCode(user("mallory"), sent[0], sent[1]).andExpect(status().isNotFound());
    }

    @Test
    void refusesYourOwnCountryAndBadAddresses() throws Exception {
        // An Indian account adding India.
        mvc.perform(post("/api/account-links/codes").with(indian("priya")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\":\"IN\",\"channel\":\"email\",\"destination\":\"priya@example.com\"}"))
                .andExpect(status().isBadRequest());
        // Not an email; a phone without a country code.
        mvc.perform(post("/api/account-links/codes").with(user("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\":\"IN\",\"channel\":\"email\",\"destination\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/account-links/codes").with(user("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\":\"IN\",\"channel\":\"sms\",\"destination\":\"98765 43210\"}"))
                .andExpect(status().isBadRequest());
        verify(sender, never()).send(any(), anyString(), anyString());
    }

    @Test
    void limitsHowManyCodesYouCanAskFor() throws Exception {
        for (int i = 0; i < AccountLinkService.MAX_CODES_PER_HOUR; i++) {
            sendCode(user("alice"), "email", "alice@example.com");
        }
        mvc.perform(post("/api/account-links/codes").with(user("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\":\"IN\",\"channel\":\"email\",\"destination\":\"alice@example.com\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void masksWhereTheCodeWent() {
        org.assertj.core.api.Assertions.assertThat(AccountLinkService.mask(Channel.EMAIL, "nishanth@gmail.com"))
                .isEqualTo("n•••@gmail.com");
        org.assertj.core.api.Assertions.assertThat(AccountLinkService.mask(Channel.SMS, "+919876543210"))
                .isEqualTo("+91 •••• ••3210");
    }
}
