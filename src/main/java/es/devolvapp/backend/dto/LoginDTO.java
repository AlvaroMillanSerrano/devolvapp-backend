package es.devolvapp.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;


    @Data
    public class LoginDTO {
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El correo electrónico no tiene un formato válido")
        private String email;

        @NotBlank(message = "La contraseña es obligatoria")
        private String password;
    }

