package com.serviq.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager manager;
    private final SecurityContextRepository contextRepository;
    public AuthController(AuthenticationManager manager, SecurityContextRepository contextRepository) {
        this.manager = manager; this.contextRepository = contextRepository;
    }
    public record Credentials(String email, String password) { }
    public record StaffSession(String email, List<String> roles) { }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    public StaffSession login(@RequestBody Credentials credentials,
            HttpServletRequest request, HttpServletResponse response) {
        if (credentials == null || credentials.email() == null || credentials.password() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
        try {
            Authentication auth = manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(credentials.email(), credentials.password()));
            // Rotate session identifier to reduce session-fixation risk.
            HttpSession session = request.getSession(false);
            if (session != null) request.changeSessionId();
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(auth);
            SecurityContextHolder.setContext(securityContext);
            contextRepository.saveContext(securityContext, request, response);
            return sessionFor(auth);
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    @GetMapping("/me")
    public StaffSession me(Authentication authentication) {
        return sessionFor(authentication);
    }
    private static StaffSession sessionFor(Authentication auth) {
        return new StaffSession(auth.getName(), auth.getAuthorities().stream()
            .map(a -> a.getAuthority()).toList());
    }
}
