package com.team26.freelance.wallet.adapter;

import com.team26.freelance.wallet.dto.PromoCodeUsageDTO;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PromoCodeUsageObjectArrayAdapter {

    public PromoCodeUsageDTO adapt(Object[] row) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryDate;

        if (row[7] instanceof LocalDateTime localDateTime) {
            expiryDate = localDateTime;
        } else if (row[7] instanceof Timestamp timestamp) {
            expiryDate = timestamp.toLocalDateTime();
        } else {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected expiry date type returned from database"
            );
        }

        return PromoCodeUsageDTO.builder()
                .promoCodeId(((Number) row[0]).longValue())
                .code((String) row[1])
                .discountType((String) row[2])
                .discountValue(((Number) row[3]).doubleValue())
                .timesUsed(((Number) row[4]).intValue())
                .totalDiscountGiven(row[5] == null ? 0.0 : ((Number) row[5]).doubleValue())
                .active((Boolean) row[6])
                .expired(expiryDate.isBefore(now))
                .build();
    }
}