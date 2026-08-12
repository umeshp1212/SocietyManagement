package com.society.module.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tenant_family_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantFamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "member_name", nullable = false, length = 150)
    private String memberName;

    @Column(name = "age")
    private Integer age;

    @Column(name = "relation", nullable = false, length = 50)
    private String relation;

    @Column(name = "aadhar_number", length = 255)
    private String aadharNumber;

    @Column(name = "contact_number", length = 15)
    private String contactNumber;
}
