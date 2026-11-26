package com.koshly.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Runs against the throwaway test database (see KoshlyTest). Each test is rolled back. */
@KoshlyTest
@Transactional
class PreferencesControllerTest {

    private static final String URL = "/api/users/current/preferences";

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
    void startsWithTheDefaultOrder() throws Exception {
        mvc.perform(get(URL).with(user("new-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dashboard.length()").value(6))
                .andExpect(jsonPath("$.dashboard[0].section").value("upcoming"))
                .andExpect(jsonPath("$.dashboard[0].visible").value(true))
                .andExpect(jsonPath("$.country").value("US"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.dataRegion").value("us"));
    }

    @Test
    void choosingIndiaSwitchesTheCurrencyToRupees() throws Exception {
        mvc.perform(put(URL).with(user("priya")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\": \"IN\", \"timeZone\": \"Asia/Kolkata\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("IN"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"))
                // the dashboard order wasn't sent, so it's unchanged (the default)
                .andExpect(jsonPath("$.dashboard[0].section").value("upcoming"));
    }

    @Test
    void rejectsUnsupportedCountriesAndTimeZones() throws Exception {
        for (String body : new String[] {"{\"country\": \"FR\"}", "{\"timeZone\": \"Mars/Olympus\"}"}) {
            mvc.perform(put(URL).with(user("alice")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void savesOrderAndHiddenSectionsAndAddsAnyLeftOut() throws Exception {
        mvc.perform(put(URL).with(user("alice")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"dashboard": [
                          {"section": "cards", "visible": true},
                          {"section": "promotions", "visible": false},
                          {"section": "upcoming", "visible": true}
                        ]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dashboard.length()").value(6));

        mvc.perform(get(URL).with(user("alice")))
                .andExpect(jsonPath("$.dashboard[0].section").value("cards"))
                .andExpect(jsonPath("$.dashboard[1].section").value("promotions"))
                .andExpect(jsonPath("$.dashboard[1].visible").value(false))
                .andExpect(jsonPath("$.dashboard[2].section").value("upcoming"))
                // the three not sent come after, in the default order, shown
                .andExpect(jsonPath("$.dashboard[3].section").value("subscriptions"))
                .andExpect(jsonPath("$.dashboard[3].visible").value(true));

        // Someone else still has the default order.
        mvc.perform(get(URL).with(user("bob")))
                .andExpect(jsonPath("$.dashboard[0].section").value("upcoming"));
    }

    @Test
    void rejectsUnknownOrRepeatedSections() throws Exception {
        mvc.perform(put(URL).with(user("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dashboard\": [{\"section\": \"crypto\", \"visible\": true}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(URL).with(user("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dashboard\": [{\"section\": \"cards\", \"visible\": true},"
                                + " {\"section\": \"cards\", \"visible\": false}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void needsAVerifiedEmail() throws Exception {
        mvc.perform(get(URL).with(jwt().jwt(token -> token.subject("u").claim("email_verified", false))))
                .andExpect(status().isForbidden());
    }
}
