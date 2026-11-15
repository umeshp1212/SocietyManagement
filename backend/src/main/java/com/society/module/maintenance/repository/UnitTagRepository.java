package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.UnitTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitTagRepository extends JpaRepository<UnitTag, Long> {

    List<UnitTag> findByIsActiveTrueOrderByTagNameAsc();

    Optional<UnitTag> findByTagCode(String tagCode);

    boolean existsByTagCode(String tagCode);
}
