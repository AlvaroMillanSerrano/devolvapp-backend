package es.devolvapp.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Pattern(regexp="\\d{8}[A-Za-z]]",message="DNI inválido")
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


    @NotBlank
    @Column(name = "u_role")
    @Enumerated(EnumType.STRING)
    private Role rol;

    @NotBlank
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

    @NotBlank
    @Column(name="created_At")
    private LocalDateTime createdAt;

    @NotBlank
    @Column(name="isactive")
    private boolean isActive;

}
