package fr.lesrestaurateurs.api.web.dto;

import fr.lesrestaurateurs.api.domain.User;
import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
