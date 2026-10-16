package es.devolvapp.backend.dto;

import lombok.Data;

@Data
public class UserDTO {

    private String name;
    private String surname;
    private String idCard;
    private String phoneNumber;
    private String email;
    private String rol;
    private float wallet;
    private String city;
    private String street;
    private String floor;
    private String letter;
    private String zipCode;
}
