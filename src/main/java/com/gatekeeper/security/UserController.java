package com.gatekeeper.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gatekeeper.security.dto.CreateUserRequest;
import com.gatekeeper.security.dto.UpdateAttributesRequest;
import com.gatekeeper.security.dto.AssignRoleRequest;
import com.gatekeeper.security.dto.UserResponse;
import com.gatekeeper.rbac.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Users", description = "User provisioning, ABAC attributes, role assignment and effective-permission lookup — all scoped to the caller's tenant.")
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final RoleService roleService;

    public UserController(UserService userService, RoleService roleService) {
        this.userService = userService;
        this.roleService = roleService;
    }

    @Operation(summary = "Create user", description = "Creates a user in the caller's tenant with a BCrypt-hashed password. Requires user:write. 409 if the email already exists in this tenant.")
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @Operation(summary = "List users", description = "Lists all users in the caller's tenant. Requires user:read.")
    @GetMapping
    public List<UserResponse> list() {
        return userService.listUsers().stream().map(UserResponse::from).toList();
    }

    @Operation(summary = "Get user", description = "Returns one user by id. Requires user:read. Users of other tenants are invisible and return 404 (never 403).")
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return UserResponse.from(userService.getUser(id));
    }

    @Operation(summary = "Update user attributes", description = "Replaces the user's free-form JSON attributes (e.g. department, region). These are embedded in the JWT attrs claim and evaluated by ABAC rules via attr('name'). Requires user:write.")
    @PatchMapping("/{id}/attributes")
    public UserResponse updateAttributes(@PathVariable UUID id, @Valid @RequestBody UpdateAttributesRequest request) {
        return UserResponse.from(userService.updateAttributes(id, request.attributes()));
    }

    @Operation(summary = "Get effective permissions", description = "Returns the user's effective permission codes, resolved through the full role-inheritance graph (recursive CTE) and served through the Redis cache. Requires user:read.")
    @GetMapping("/{id}/permissions")
    public Set<String> permissions(@PathVariable UUID id) {
        return userService.getEffectivePermissions(id);
    }

    @Operation(summary = "Assign role to user", description = "Grants the user the given role (and, transitively, every permission of that role and its ancestors). Requires role:assign. Invalidates the user's cached permissions.")
    @PostMapping("/{id}/roles")
    public ResponseEntity<Void> assignRole(@PathVariable UUID id, @Valid @RequestBody AssignRoleRequest request) {
        roleService.assignRoleToUser(id, request.roleId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Revoke role from user", description = "Removes the role from the user and invalidates their cached permissions. Requires role:assign.")
    @DeleteMapping("/{id}/roles/{roleId}")
    public ResponseEntity<Void> revokeRole(@PathVariable UUID id, @PathVariable UUID roleId) {
        roleService.revokeRoleFromUser(id, roleId);
        return ResponseEntity.noContent().build();
    }
}
