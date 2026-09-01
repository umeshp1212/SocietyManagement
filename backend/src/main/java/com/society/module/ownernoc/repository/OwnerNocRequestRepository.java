package com.society.module.ownernoc.repository;

import com.society.enums.OwnerNocStatus;
import com.society.module.ownernoc.entity.OwnerNocRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OwnerNocRequestRepository extends JpaRepository<OwnerNocRequest, Long> {

    @Query("SELECT r FROM OwnerNocRequest r JOIN FETCH r.owner JOIN FETCH r.nocType " +
           "WHERE r.status = :status ORDER BY r.createdOn DESC")
    List<OwnerNocRequest> findByStatusOrderByCreatedOnDesc(@Param("status") OwnerNocStatus status);

    @Query("SELECT r FROM OwnerNocRequest r JOIN FETCH r.nocType " +
           "WHERE r.owner.ownerId = :ownerId ORDER BY r.createdOn DESC")
    List<OwnerNocRequest> findByOwnerIdOrderByCreatedOnDesc(@Param("ownerId") Long ownerId);

    long countByStatus(OwnerNocStatus status);
}
