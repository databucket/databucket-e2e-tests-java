package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A sign-in: user, password and the project (left out: the answer lists the user's projects). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignIn(String username, String password, Long projectId) {
}
