package com.koshly.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PreferencesService {

    private final AppUserRepository users;
    private final UserService userService;

    public PreferencesService(AppUserRepository users, UserService userService) {
        this.users = users;
        this.userService = userService;
    }

    /** The user's settings, or the defaults if they haven't saved any. */
    @Transactional(readOnly = true)
    public PreferencesResponse get(Jwt jwt) {
        String stored = users.findByFirebaseUid(jwt.getSubject()).map(AppUser::getDashboardSections).orElse(null);
        return new PreferencesResponse(DashboardLayout.parse(stored));
    }

    @Transactional
    public PreferencesResponse save(Jwt jwt, PreferencesRequest request) {
        List<DashboardLayout.Section> sections = request.dashboard().stream()
                .map(s -> new DashboardLayout.Section(s.section(), s.visible()))
                .toList();
        String problem = DashboardLayout.problemWith(sections);
        if (problem != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
        }
        userService.ensureUserExists(jwt);
        AppUser user = users.findByFirebaseUid(jwt.getSubject()).orElseThrow();
        user.setDashboardSections(DashboardLayout.format(sections));
        users.flush();
        return new PreferencesResponse(DashboardLayout.parse(user.getDashboardSections()));
    }
}
