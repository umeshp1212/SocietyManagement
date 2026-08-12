package com.society.module.settings.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "society_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocietySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "society_name", nullable = false, length = 300)
    private String societyName;

    @Column(name = "address_line1", length = 300)
    private String addressLine1;

    @Column(name = "address_line2", length = 300)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "registration_date", length = 20)
    private String registrationDate;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "chairman_name", length = 150)
    private String chairmanName;

    @Column(name = "secretary_name", length = 150)
    private String secretaryName;

    @Column(name = "treasurer_name", length = 150)
    private String treasurerName;

    @Column(name = "logo_path", length = 500)
    private String logoPath;
}
