package fr.lesrestaurateurs.api.web;

import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.security.CurrentUser;
import fr.lesrestaurateurs.api.service.GroupService;
import fr.lesrestaurateurs.api.web.dto.CreateGroupRequest;
import fr.lesrestaurateurs.api.web.dto.GroupDetail;
import fr.lesrestaurateurs.api.web.dto.GroupSummary;
import fr.lesrestaurateurs.api.web.dto.InvitePreview;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/groups")
    public List<GroupSummary> myGroups(@CurrentUser User user) {
        return groupService.findMine(user);
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDetail create(@Valid @RequestBody CreateGroupRequest body, @CurrentUser User user) {
        return groupService.create(body.name(), user);
    }

    @GetMapping("/groups/{id}")
    public GroupDetail one(@PathVariable UUID id, @CurrentUser User user) {
        return groupService.findOne(id, user);
    }

    /**
     * Aperçu d'une invitation. Volontairement ouvert aux visiteurs non
     * connectés : c'est l'écran qui s'affiche quand on clique sur le lien reçu.
     */
    @GetMapping("/invites/{code}")
    public InvitePreview preview(@PathVariable String code,
                                 @CurrentUser(required = false) User user) {
        return groupService.preview(code, user);
    }

    @PostMapping("/invites/{code}/join")
    public GroupDetail join(@PathVariable String code, @CurrentUser User user) {
        return groupService.join(code, user);
    }
}
