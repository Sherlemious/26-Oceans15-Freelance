package com.team26.freelance.contract.repository;

import com.team26.freelance.contract.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    @Query("SELECT COUNT(c) FROM Contract c WHERE c.createdAt < :cutoff AND c.status IN ('COMPLETED', 'TERMINATED')")
    long countPurgeable(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Transactional
    @Query("DELETE FROM Contract c WHERE c.createdAt < :cutoff AND c.status IN ('COMPLETED', 'TERMINATED')")
    int deleteOldContracts(@Param("cutoff") LocalDateTime cutoff);
}