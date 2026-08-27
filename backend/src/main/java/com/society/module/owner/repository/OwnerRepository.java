package com.society.module.owner.repository;

import com.society.enums.OwnerStatus;
import com.society.module.owner.entity.Owner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Page<Owner> findByStatus(OwnerStatus status, Pageable pageable);

    @Query("SELECT o FROM Owner o WHERE o.status = :status AND " +
           "(LOWER(o.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "o.contactNumber LIKE CONCAT('%', :search, '%') OR " +
           "LOWER(o.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Owner> searchOwners(@Param("status") OwnerStatus status,
                             @Param("search") String search,
                             Pageable pageable);

    @Query("SELECT o FROM Owner o WHERE " +
           "LOWER(o.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "o.contactNumber LIKE CONCAT('%', :search, '%') OR " +
           "LOWER(o.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Owner> searchAllOwners(@Param("search") String search, Pageable pageable);

    List<Owner> findByStatusOrderByFullNameAsc(OwnerStatus status);

    boolean existsByContactNumber(String contactNumber);

    @Query("SELECT o FROM Owner o WHERE o.contactNumber = :phone OR o.alternateNumber = :phone")
    List<Owner> findByContactNumberOrAlternateNumber(@Param("phone") String phone);
}
