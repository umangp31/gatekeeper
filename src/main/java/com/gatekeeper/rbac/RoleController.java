package com.gatekeeper.rbac;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gatekeeper.rbac.dto.CreateRoleRequest;
import com.gatekeeper.rbac.dto.PermissionResponse;
import com.gatekeeper.rbac.dto.RoleResponse;
import com.gatekeeper.rbac.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Roles & Permissions", description = "Role CRUD, permission grants, role hierarchy (inheritance) and the permission catalogue.")
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "Create role", description = "Creates a role in the caller's tenant. Requires role:write. 409 if the name is taken.")
    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        Role role = roleService.createRole(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleResponse.from(role));
    }

    @Operation(summary = "List roles", description = "Lists all roles in the caller's tenant with their direct permissions and parents. Requires role:read.")
    @GetMapping("/roles")
    public List<RoleResponse> list() {
        return roleService.listRoles().stream().map(RoleResponse::from).toList();
    }

    @Operation(summary = "Get role", description = "Returns one role by id. Requires role:read. Other tenants' roles return 404.")
    @GetMapping("/roles/{id}")
    public RoleResponse get(@PathVariable UUID id) {
        return RoleResponse.from(roleService.getRole(id));
    }

    @Operation(summary = "Update role", description = "Updates the role's description. Requires role:write.")
    @PatchMapping("/roles/{id}")
    public RoleResponse update(@PathVariable UUID id, @RequestBody UpdateRoleRequest request) {
        return RoleResponse.from(roleService.updateRole(id, request.description()));
    }

    @Operation(summary = "Delete role", description = "Deletes the role, its permission grants, its hierarchy links and its user assignments; affected users' cached permissions are invalidated. Requires role:write.")
    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Grant permission to role", description = "Adds a permission code (e.g. flag:write — see GET /permissions) to the role. Audited. Requires role:write. 404 for unknown codes.")
    @PostMapping("/roles/{id}/permissions/{code}")
    public ResponseEntity<Void> grantPermission(@PathVariable UUID id, @PathVariable String code) {
        roleService.grantPermission(id, code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Revoke permission from role", description = "Removes the permission code from the role and invalidates the cache for every user holding this role or any descendant. Audited. Requires role:write.")
    @DeleteMapping("/roles/{id}/permissions/{code}")
    public ResponseEntity<Void> revokePermission(@PathVariable UUID id, @PathVariable String code) {
        roleService.revokePermission(id, code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add parent role", description = "Makes the role inherit every permission of parentId (and its ancestors). Rejects self-parenting and cycles with 409. Audited. Requires role:write.")
    @PostMapping("/roles/{id}/parents/{parentId}")
    public ResponseEntity<Void> addParent(@PathVariable UUID id, @PathVariable UUID parentId) {
        roleService.addParent(id, parentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove parent role", description = "Removes the inheritance link so the role no longer inherits from parentId. Audited. Requires role:write.")
    @DeleteMapping("/roles/{id}/parents/{parentId}")
    public ResponseEntity<Void> removeParent(@PathVariable UUID id, @PathVariable UUID parentId) {
        roleService.removeParent(id, parentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List permission catalogue", description = "Returns the fixed, global catalogue of permission codes (resource:action) that can be granted to roles. Requires role:read.")
    @GetMapping("/permissions")
    public List<PermissionResponse> permissionCatalog() {
        return roleService.listPermissionCatalog().stream().map(PermissionResponse::from).toList();
    }
}
