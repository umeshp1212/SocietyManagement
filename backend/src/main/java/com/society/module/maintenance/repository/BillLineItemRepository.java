package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.BillLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillLineItemRepository extends JpaRepository<BillLineItem, Long> {

    List<BillLineItem> findByBill_BillIdOrderByDisplayOrderAsc(Long billId);

    void deleteByBill_BillId(Long billId);
}
