package com.koshly.account;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/accounts: the signed-in user's credit cards, bank accounts and loans.
 *
 *   GET    /api/accounts        my accounts (all in my Koshly account's country)
 *   POST   /api/accounts        add one                 -> 201 Created
 *   GET    /api/accounts/{id}   one of mine             -> 404 if it isn't mine
 *   PUT    /api/accounts/{id}   replace its details (type can't change)
 *   DELETE /api/accounts/{id}   remove it: hidden now, deleted for good after 30 days
 *   POST   /api/accounts/{id}/restore   undo a removal (manual accounts only)
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(jwt);
    }

    @GetMapping("/{id}")
    public AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(jwt, id);
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountRequest request) {
        AccountResponse created = service.create(jwt, request);
        return ResponseEntity.created(URI.create("/api/accounts/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody AccountRequest request) {
        return service.update(jwt, id, request);
    }

    @DeleteMapping("/{id}")
    public RemovedAccountResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.remove(jwt, id);
    }

    @PostMapping("/{id}/restore")
    public AccountResponse restore(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.restore(jwt, id);
    }
}
