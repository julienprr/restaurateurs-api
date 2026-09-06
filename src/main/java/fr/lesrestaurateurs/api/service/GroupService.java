package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.repo.UserGroupRepository;
import fr.lesrestaurateurs.api.web.ApiException;
import fr.lesrestaurateurs.api.web.dto.GroupDetail;
import fr.lesrestaurateurs.api.web.dto.GroupSummary;
import fr.lesrestaurateurs.api.web.dto.InvitePreview;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les méthodes exposées aux contrôleurs renvoient des DTO plutôt que des
 * entités : la liste des membres est paresseuse et {@code open-in-view} est
 * désactivé, elle doit donc être lue à l'intérieur de la transaction.
 */
@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    /** Sécurité : évite une boucle infinie si l'aléa dégénère. */
    private static final int MAX_CODE_ATTEMPTS = 20;

    private final UserGroupRepository groups;

    public GroupService(UserGroupRepository groups) {
        this.groups = groups;
    }

    @Transactional
    public GroupDetail create(String name, User creator) {
        String cleanName = name.trim();
        UserGroup group = groups.save(new UserGroup(cleanName, freshInviteCode(), creator));
        log.info("Groupe « {} » créé par {}", cleanName, creator.getEmail());
        return GroupDetail.from(group);
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> findMine(User user) {
        return groups.findSummariesForUser(user.getId());
    }

    @Transactional(readOnly = true)
    public GroupDetail findOne(UUID groupId, User user) {
        return GroupDetail.from(requireMembership(groupId, user));
    }

    /** Aperçu d'une invitation, accessible même sans être membre. */
    @Transactional(readOnly = true)
    public InvitePreview preview(String rawCode, User user) {
        UserGroup group = requireInvite(rawCode);
        boolean alreadyMember = user != null && group.hasMember(user);
        return new InvitePreview(group.getName(), group.getMembers().size(), alreadyMember);
    }

    /**
     * Fait entrer l'utilisateur dans le groupe désigné par le code.
     * Rejoindre deux fois n'est pas une erreur : un lien peut être recliqué.
     */
    @Transactional
    public GroupDetail join(String rawCode, User user) {
        UserGroup group = requireInvite(rawCode);
        if (group.addMember(user)) {
            log.info("{} a rejoint le groupe « {} »", user.getEmail(), group.getName());
        }
        return GroupDetail.from(group);
    }

    /**
     * Vérifie l'appartenance depuis un appelant non transactionnel, typiquement
     * un contrôleur qui n'a rien à charger d'autre.
     */
    @Transactional(readOnly = true)
    public void assertMembership(UUID groupId, User user) {
        requireMembership(groupId, user);
    }

    /**
     * Charge un groupe en vérifiant l'appartenance. Réservé aux appels internes
     * depuis une transaction, les autres services s'appuient dessus.
     */
    public UserGroup requireMembership(UUID groupId, User user) {
        UserGroup group = groups.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Ce groupe n'existe pas."));

        if (!group.hasMember(user)) {
            // Même message qu'un groupe absent : ne pas confirmer son existence
            // à quelqu'un qui n'y a pas accès.
            throw ApiException.notFound("Ce groupe n'existe pas.");
        }
        return group;
    }

    private UserGroup requireInvite(String rawCode) {
        return groups.findByInviteCode(InviteCodes.normalize(rawCode))
                .orElseThrow(() -> ApiException.notFound(
                        "Cette invitation n'existe pas ou n'est plus valide."));
    }

    private String freshInviteCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = InviteCodes.generate();
            if (!groups.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Impossible de générer un code d'invitation unique");
    }
}
