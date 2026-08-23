package com.society.module.committee.entity;

import com.society.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "committee_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitteeMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "designation", nullable = false, length = 100)
    private String designation;

    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
