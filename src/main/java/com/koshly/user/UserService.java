package com.koshly.user;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository users;

    public UserService(AppUserRepository users) {
        this.users = users;
    }

    /** Makes sure the signed-in user has an app_user row, so their data can point to it. */
    @Transactional
    public void ensureUserExists(Jwt jwt) {
        users.insertIfMissing(jwt.getSubject(), jwt.getClaimAsString("email"));
    }
}
