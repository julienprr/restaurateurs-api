package fr.lesrestaurateurs.api.web.dto;

import fr.lesrestaurateurs.api.domain.UserGroup;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record GroupDetail(UUID id, String name, String inviteCode, List<UserResponse> members) {

    /** À construire dans une transaction : la liste des membres est paresseuse. */
    public static GroupDetail from(UserGroup group) {
        List<UserResponse> members = group.getMembers().stream()
                .sorted(Comparator.comparing(user -> user.getDisplayName().toLowerCase()))
                .map(UserResponse::from)
                .toList();
        return new GroupDetail(group.getId(), group.getName(), group.getInviteCode(), members);
    }
}
