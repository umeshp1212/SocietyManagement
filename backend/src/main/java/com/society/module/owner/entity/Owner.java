package com.society.module.owner.entity;

import com.society.common.BaseEntity;
import com.society.enums.OwnerStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "owners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Owner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "contact_number", length = 15)
    private String contactNumber;

    @Column(name = "alternate_number", length = 15)
    private String alternateNumber;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "aadhar_number", length = 255)
    private String aadharNumber;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "permanent_address", columnDefinition = "TEXT")
    private String permanentAddress;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 15)
    private String emergencyContactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OwnerStatus status = OwnerStatus.ACTIVE;
}
