package com.serviq.platform.advisor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/advisor")
public class ServiceIntakeAdvisorController {
    private final OllamaIntakeAdvisor advisor;
    public ServiceIntakeAdvisorController(OllamaIntakeAdvisor advisor) { this.advisor = advisor; }
    public record AnalyzeRequest(@NotBlank @Size(min=12,max=4000) String description) {}

    @PostMapping("/analyze")
    public ServiceIntakeAdvisor.Suggestion analyze(@Valid @RequestBody AnalyzeRequest request) {
        try { return advisor.analyze(request.description()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
    }
}
