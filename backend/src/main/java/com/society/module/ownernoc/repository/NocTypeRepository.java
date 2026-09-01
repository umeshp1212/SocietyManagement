package com.society.module.ownernoc.repository;

import com.society.module.ownernoc.entity.NocType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NocTypeRepository extends JpaRepository<NocType, Long> {

    List<NocType> findAllByOrderByDisplayOrderAsc();

    List<NocType> findByIsActiveTrueOrderByDisplayOrderAsc();
}
