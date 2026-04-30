package com.team26.freelance.wallet.adapter;

import com.team26.freelance.wallet.dto.PayoutMethodBreakdownDTO;
import java.math.BigDecimal;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoDocumentAdapterTest {

    private final MongoDocumentAdapter adapter = new MongoDocumentAdapter();

    @Test
    void adaptShouldComputeAverageAndSuccessRate() {
        Document document = new Document()
                .append("payoutMethod", "PAYPAL")
                .append("totalAmount", 300.0)
                .append("count", 3)
                .append("completedCount", 2);

        PayoutMethodBreakdownDTO dto = adapter.adapt(document);

        assertEquals("PAYPAL", dto.getPayoutMethod());
        assertEquals(0, BigDecimal.valueOf(300.0).compareTo(dto.getTotalAmount()));
        assertEquals(3, dto.getCount());
        assertEquals(0, BigDecimal.valueOf(100.0).compareTo(dto.getAverageAmount()));
        assertEquals(66.66666666666667, dto.getSuccessRate());
    }
}
