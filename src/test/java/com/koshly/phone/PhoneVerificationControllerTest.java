package com.koshly.phone;

import static com.koshly.testsupport.TestUsers.indian;
import static com.koshly.testsupport.TestUsers.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.jayway.jsonpath.JsonPath;
import com.koshly.accountlink.Channel;
import com.koshly.accountlink.CodeSender;
import com.koshly.testsupport.KoshlyTest;
import com.koshly.testsupport.TestUsers;

/**
 * Verifying the mobile number on your own account. The text message and the
 * Firebase update are Mockito stand-ins, so the test reads the code that was
 * "sent" and nothing real is sent or changed.
 */
@KoshlyTest
@Transactional
class PhoneVerificationControllerTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CodeSender sender;

    @MockitoBean
    private PhoneNumbers phones;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Asks for a code and returns {verificationId, code}. */
    private String[] sendCode(RequestPostProcessor who, String phone) throws Exception {
        String body = mvc.perform(post("/api/users/current/phone/codes").with(who)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.channel").value("sms"))
                .andReturn().getResponse().getContentAsString();
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).send(any(), anyString(), code.capture());
        return new String[] {JsonPath.read(body, "$.verificationId"), code.getValue()};
    }

    private ResultActions verifyCode(RequestPostProcessor who, String id, String code) throws Exception {
        return mvc.perform(post("/api/users/current/phone/codes/" + id + "/verify").with(who)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    @Test
    void aRightCodeVerifiesTheNumber() throws Exception {
        String[] sent = sendCode(user("alice"), "+1 (469) 555-0142");
        verify(sender).send(eq(Channel.SMS), eq("+14695550142"), anyString()); // normalized

        verifyCode(user("alice"), sent[0], sent[1])
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+14695550142"));
        // A US account has no tenant.
        verify(phones).markVerified(eq("alice"), isNull(), eq("+14695550142"));

        // A code works once.
        verifyCode(user("alice"), sent[0], sent[1]).andExpect(status().isGone());
    }

    @Test
    void anIndianAccountIsUpdatedInItsTenant() throws Exception {
        String[] sent = sendCode(indian("priya"), "+919876543210");
        verifyCode(indian("priya"), sent[0], sent[1]).andExpect(status().isOk());
        verify(phones).markVerified("priya", TestUsers.INDIA_TENANT, "+919876543210");
    }

    @Test
    void aWrongCodeDoesntVerify() throws Exception {
        String[] sent = sendCode(user("alice"), "+14695550142");
        String wrong = sent[1].equals("000000") ? "111111" : "000000";
        verifyCode(user("alice"), sent[0], wrong).andExpect(status().isBadRequest());
        verify(phones, never()).markVerified(anyString(), any(), anyString());
    }

    @Test
    void aPhoneCodeCantBeUsedToAddACountry() throws Exception {
        String[] sent = sendCode(user("alice"), "+14695550142");
        mvc.perform(post("/api/account-links/codes/" + sent[0] + "/verify").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + sent[1] + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void someoneElsesCodeIsNotFound() throws Exception {
        String[] sent = sendCode(user("alice"), "+14695550142");
        verifyCode(user("bob"), sent[0], sent[1]).andExpect(status().isNotFound());
    }

    @Test
    void aNumberVerifiedOnAnotherAccountIsRefused() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "This number is already verified on another Koshly account."))
                .when(phones).markVerified(anyString(), any(), anyString());
        String[] sent = sendCode(user("alice"), "+14695550142");
        verifyCode(user("alice"), sent[0], sent[1]).andExpect(status().isConflict());
    }

    @Test
    void rejectsANumberWithoutACountryCode() throws Exception {
        mvc.perform(post("/api/users/current/phone/codes").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"469 555 0142\"}"))
                .andExpect(status().isBadRequest());
        verify(sender, never()).send(any(), anyString(), anyString());
    }
}
