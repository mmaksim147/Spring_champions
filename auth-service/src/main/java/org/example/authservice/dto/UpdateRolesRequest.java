package org.example.authservice.dto;

import java.util.Set;

public class UpdateRolesRequest {
    private Set<String> roles;

    public UpdateRolesRequest() {}

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
}