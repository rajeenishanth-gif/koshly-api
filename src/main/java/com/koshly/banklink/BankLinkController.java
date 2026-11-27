package com.koshly.banklink;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.koshly.account.AccountService;

/**
 * /api/bank-links: connecting banks. The account's country decides the
 * provider (Plaid in the US, Account Aggregator in India).
 *
 *   GET    /api/bank-links/provider  which provider, and is it ready
 *   GET    /api/bank-links           my connections
 *   POST   /api/bank-links           start linking -> 201, or 503 if not ready yet
 *   DELETE /api/bank-links/{id}      disconnect and remove its accounts -> 204
 */
@RestController
@RequestMapping("/api/bank-links")
public class BankLinkController {

    private final BankLinkService service;
    private final AccountService accounts;

    public BankLinkController(BankLinkService service, AccountService accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @GetMapping("/provider")
    public BankLinkService.ProviderInfo provider(@AuthenticationPrincipal Jwt jwt) {
        return service.providerFor(jwt);
    }

    @GetMapping
    public List<BankLinkResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(jwt);
    }

    @PostMapping
    public ResponseEntity<BankDataProvider.LinkStart> start(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.start(jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        BankLink link = service.disconnect(jwt, id);
        accounts.removeAllFromLink(jwt, link.getId());
        return ResponseEntity.noContent().build();
    }

}
