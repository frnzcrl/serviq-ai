package com.serviq.platform.customer.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRegistrationRequest(

        @NotBlank
        @Size(max = 150)
        String fullName,

        @Email
        @Size(max = 255)
        String emailAddress,

        @Size(max = 30)
        String phoneNumber

) {
}
