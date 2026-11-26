package com.koshly.user;

public record CurrentUserResponse(
        String uid,
        String email,
        boolean emailVerified,
        String signInProvider) {
}