package es.devolvapp.backend.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "d_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "id_card", unique = true, length = 9)
    private String idCard;

    @Column(name = "phone_number", unique = true, length = 15)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(nullable = false, length = 12)
    private String status = "ACTIVA";

    @Column(name = "preferred_zone_id")
    private Long preferredZoneId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 12)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    /*
     * Legacy DTO compatibility only. These properties are not persisted by
     * the new schema; registration needs a separate address persistence flow.
     */
    @Transient
    private float wallet;
    @Transient
    private String city;
    @Transient
    private String street;
    @Transient
    private String floor;
    @Transient
    private String letter;
    @Transient
    private String zipCode;

    public String getName() {
        return firstName;
    }

    public void setName(String name) {
        this.firstName = name;
    }

    public String getSurname() {
        return lastName;
    }

    public void setSurname(String surname) {
        this.lastName = surname;
    }

    public String getPassword() {
        return passwordHash;
    }

    public void setPassword(String password) {
        this.passwordHash = password;
    }

    public Role getRol() {
        return roles.stream().findFirst().orElse(null);
    }

    public void setRol(Role role) {
        roles.clear();
        if (role != null) {
            roles.add(role);
        }
    }

    public boolean isActive() {
        return "ACTIVA".equals(status);
    }

    public void setActive(boolean active) {
        status = active ? "ACTIVA" : "SUSPENDIDA";
    }
}
