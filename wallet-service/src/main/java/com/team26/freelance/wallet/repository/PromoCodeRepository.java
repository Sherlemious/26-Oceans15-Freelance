package com.team26.freelance.wallet.repository;

import com.team26.freelance.wallet.model.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    @Query(value = """
        SELECT pc.id,
               pc.code,
               pc.discount_type,
               pc.discount_value,
               pc.current_uses,
               COALESCE(SUM(pp.discount_applied), 0),
               pc.active,
               pc.expiry_date
        FROM promo_codes pc
        LEFT JOIN payout_promos pp ON pp.promo_code_id = pc.id
        GROUP BY pc.id, pc.code, pc.discount_type, pc.discount_value,
                 pc.current_uses, pc.active, pc.expiry_date
        ORDER BY pc.current_uses DESC, pc.id ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopUsedPromoCodes(@Param("limit") int limit);
}
