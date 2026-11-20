package pl.databucket.e2e.model;

import java.util.List;

/** The answer to a sign-in: the token for the project, the user's roles, or the projects to choose from. */
public record AuthResponse(String token, String username, Boolean changePassword, String message,
                           List<AuthProject> projects, String code, AuthProject project, List<String> roles) {

    /** A project the user can sign in to. */
    public record AuthProject(int id, String name, String description, boolean enabled, boolean expired) {
    }
}
