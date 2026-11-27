package com.koshly.phone;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.koshly.accountlink.OneTimeCodes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * /api/users/current/phone: verify the mobile number on your account.
 *
 *   POST /api/users/current/phone/codes  {"phone":"+14695550142"}
 *        -> 202 {verificationId, channel, sentTo, expiresAt}   (a 6-digit code is texted)
 *   POST /api/users/current/phone/codes/{id}/verify  {"code":"123456"}
 *        -> 200 {phone}   (the number is now verified; refresh the sign-in token)
 */
@RestController
@RequestMapping("/api/users/current/phone")
public class PhoneVerificationController {

    private final PhoneVerificationService service;

    public PhoneVerificationController(PhoneVerificationService service) {
        this.service = service;
    }

    @PostMapping("/codes")
    public ResponseEntity<OneTimeCodes.CodeSent> sendCode(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.sendCode(jwt, request.phone()));
    }

    @PostMapping("/codes/{id}/verify")
    public PhoneVerificationService.Verified verify(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody VerifyRequest request) {
        return service.verify(jwt, id, request.code());
    }

    public record SendRequest(@NotBlank(message = "Enter your mobile number.") @Size(max = 20) String phone) {
    }

    public record VerifyRequest(
            @NotBlank(message = "Enter the 6-digit code.")
            @Pattern(regexp = "^\\s*[0-9]{6}\\s*$", message = "The code is 6 digits.") String code) {
    }
}
