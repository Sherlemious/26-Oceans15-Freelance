package com.team26.freelance.contract.repository;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
	List<Contract> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime startDateTime, LocalDateTime endDateTime);

	List<Contract> findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(
			LocalDateTime startDateTime,
			LocalDateTime endDateTime,
			ContractStatus status
	);
}
