package com.team26.freelance.wallet.adapter;

import com.team26.freelance.wallet.dto.PayoutMethodBreakdownDTO;
import java.math.BigDecimal;
import java.math.MathContext;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class MongoDocumentAdapter {

    public PayoutMethodBreakdownDTO adapt(Document document) {
        String payoutMethod = document.getString("payoutMethod");
        BigDecimal totalAmount = toBigDecimal(document.get("totalAmount"));
        long count = toLong(document.get("count"));
        long completedCount = toLong(document.get("completedCount"));
        BigDecimal averageAmount = count == 0
            ? BigDecimal.ZERO
            : totalAmount.divide(BigDecimal.valueOf(count), MathContext.DECIMAL64);
        double successRate = count == 0 ? 0.0 : (completedCount * 100.0) / count;

        return new PayoutMethodBreakdownDTO(
            payoutMethod,
            totalAmount,
            count,
            averageAmount,
            successRate
        );
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }
}
