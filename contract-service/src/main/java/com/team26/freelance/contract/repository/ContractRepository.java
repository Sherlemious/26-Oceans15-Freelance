package com.team26.freelance.contract.repository;

import com.team26.freelance.contract.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

	@Query(value = "SELECT * FROM contracts WHERE metadata->>:key = :value", nativeQuery = true)
	List<Contract> findByMetadataEquals(@Param("key") String key, @Param("value") String value);

	@Query(value = "SELECT * FROM contracts WHERE CAST(metadata->>:key AS numeric) > CAST(:value AS numeric)", nativeQuery = true)
	List<Contract> findByMetadataGreaterThan(@Param("key") String key, @Param("value") String value);

	@Query(value = "SELECT * FROM contracts WHERE CAST(metadata->>:key AS numeric) < CAST(:value AS numeric)", nativeQuery = true)
	List<Contract> findByMetadataLessThan(@Param("key") String key, @Param("value") String value);
}
