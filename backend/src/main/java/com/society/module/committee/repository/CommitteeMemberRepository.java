package com.society.module.committee.repository;

import com.society.module.committee.entity.CommitteeMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, Long> {

    List<CommitteeMember> findAllByOrderByDisplayOrderAsc();

    List<CommitteeMember> findByIsActiveTrueOrderByDisplayOrderAsc();
}
