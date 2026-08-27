package com.society.module.member.repository;

import com.society.module.member.entity.ProfileUpdateRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileUpdateRequestRepository extends JpaRepository<ProfileUpdateRequest, Long> {

    List<ProfileUpdateRequest> findByStatusOrderByCreatedOnDesc(String status);

    List<ProfileUpdateRequest> findByOwner_OwnerIdOrderByCreatedOnDesc(Long ownerId);

    boolean existsByOwner_OwnerIdAndStatus(Long ownerId, String status);
}
