package com.society.module.member.repository;

import com.society.module.member.entity.MemberRegistrationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberRegistrationRequestRepository extends JpaRepository<MemberRegistrationRequest, Long> {

    List<MemberRegistrationRequest> findByStatusOrderByCreatedOnDesc(String status);

    boolean existsByEmailAndStatus(String email, String status);

    boolean existsByMobileAndStatus(String mobile, String status);
}
