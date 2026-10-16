package es.devolvapp.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterUserDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String name;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres")
    private String surname;

    @NotBlank(message = "El DNI/NIE es obligatorio")
    @Size(max = 18, message = "El DNI/NIE no puede superar los 18 caracteres")
    @Pattern(
            regexp = "(?i)(\\d{8}[A-Z]|[XYZ]\\d{7}[A-Z])",
            message = "El formato del DNI/NIE no es válido"
    )
    private String idCard;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
    private String phoneNumber;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico no tiene un formato válido")
    @Size(max = 100, message = "El correo electrónico no puede superar los 100 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 100, message = "La contraseña no puede superar los 100 caracteres")
    private String password;

    private String rol;
    private float wallet;

    @NotBlank(message = "La ciudad es obligatoria")
    @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres")
    private String city;

    @NotBlank(message = "La calle es obligatoria")
    @Size(max = 150, message = "La calle no puede superar los 150 caracteres")
    private String street;

    @NotBlank(message = "El piso es obligatorio")
    @Size(max = 20, message = "El piso no puede superar los 20 caracteres")
    private String floor;

    @NotBlank(message = "La letra es obligatoria")
    @Size(max = 10, message = "La letra no puede superar los 10 caracteres")
    private String letter;

    @NotBlank(message = "El código postal es obligatorio")
    @Size(max = 10, message = "El código postal no puede superar los 10 caracteres")
    private String zipCode;

}
