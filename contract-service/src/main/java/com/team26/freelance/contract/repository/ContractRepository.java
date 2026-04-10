package com.team26.freelance.contract.repository;

import com.team26.freelance.contract.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    @Query(value = "SELECT COUNT(id) FROM users WHERE id = :userId", nativeQuery = true)
    long countUsersById(@Param("userId") Long userId);

    @Query(value = "SELECT * FROM contracts WHERE (freelancer_id=:userId OR client_id=:userId) AND status='ACTIVE' ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<Contract> findMostRecentActiveContractByUserId(@Param("userId") Long userId);
}
