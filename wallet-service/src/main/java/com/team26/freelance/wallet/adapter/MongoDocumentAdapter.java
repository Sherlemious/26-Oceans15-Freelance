package com.team26.freelance.wallet.adapter;

import com.team26.freelance.wallet.dto.PayoutMethodBreakdownDTO;
import java.math.BigDecimal;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class MongoDocumentAdapter {

    public PayoutMethodBreakdownDTO adapt(Document document) {
        String method = document.getString("method");
        long successCount = toLong(document.get("successCount"));
        long failureCount = toLong(document.get("failureCount"));
        BigDecimal totalAmount = toBigDecimal(document.get("totalAmount"));
        long total = successCount + failureCount;
        double successRate = total > 0 ? (double) successCount / total : 0.0;

        return new PayoutMethodBreakdownDTO(method, successCount, failureCount, totalAmount, successRate);
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
