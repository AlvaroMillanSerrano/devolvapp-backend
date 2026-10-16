package es.devolvapp.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;


@Entity(name="d_user")
@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class User {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NotBlank
    @Size(max=100)
    @Column(name="u_name")
    private String name;

    @NotBlank
    @Size(max=100)
    private String surname;

    @NotBlank(message = "DNI obligatorio")
    @Size(max=18)
    @Pattern(
            regexp = "(?i)(\\d{8}[A-Z]|[XYZ]\\d{7}[A-Z])",
            message = "El formato del DNI/NIE no es válido"
    )
    @Column(unique = true, name = "id_card")
    private String idCard;

    @NotBlank
    @Size(max=20)
    @Column(name="phone_number")
    private String phoneNumber;

    @Size(max=100)
    @NotBlank(message="El email es obligatorio")
    @Email
    @Column(unique = true)
    private String email;

    @NotBlank(message ="La contraseña es obligatoria")
    @Size(max=100)
    private String password;


    @NotNull
    @Column(name = "u_role")
    @Enumerated(EnumType.STRING)
    private Role rol;

    @DecimalMin("0.0")
    private float wallet;

    @NotBlank
    @Size(max=100)
    private String city;

    @NotBlank
    @Size(max=150)
    private String street;

    @NotBlank
    @Size(max=20)
    private String floor;

    @NotBlank
    @Size(max=10)
    private String letter;

    @NotBlank
    @Size(max=10)
    @Column(name="zip_code")
    private String zipCode;


    @Column(name="created_At")
    private LocalDateTime createdAt;

    @NotNull
    @Column(name="isactive")
    private boolean isActive;

}
