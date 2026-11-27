package com.koshly.accountlink;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/account-links: "Add a country" from the account menu.
 *
 *   POST /api/account-links/codes  {"country":"IN","channel":"email"|"sms","destination":"..."}
 *        -> 202 {verificationId, channel, sentTo, expiresAt}   (a 6-digit code is sent)
 *   POST /api/account-links/codes/{id}/verify  {"code":"123456"}
 *        -> 200 {country, signInToken}   (the app signs in to that country with it)
 */
@RestController
@RequestMapping("/api/account-links")
public class AccountLinkController {

    private final AccountLinkService service;

    public AccountLinkController(AccountLinkService service) {
        this.service = service;
    }

    @PostMapping("/codes")
    public ResponseEntity<AccountLinkService.CodeSent> sendCode(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendCodeRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.sendCode(jwt, request.country(), Channel.from(request.channel()), request.destination()));
    }

    @PostMapping("/codes/{id}/verify")
    public AccountLinkService.LinkedAccount verify(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody VerifyRequest request) {
        return service.verify(jwt, id, request.code());
    }

    public record SendCodeRequest(
            @NotNull @Pattern(regexp = "^[A-Z]{2}$", message = "Choose a country.") String country,
            @NotNull @Pattern(regexp = "^(email|sms)$", message = "Choose email or mobile number.") String channel,
            @NotBlank(message = "Enter where to send the code.") @Size(max = 254) String destination) {
    }

    public record VerifyRequest(
            @NotBlank(message = "Enter the 6-digit code.")
            @Pattern(regexp = "^\\s*[0-9]{6}\\s*$", message = "The code is 6 digits.") String code) {
    }
}
