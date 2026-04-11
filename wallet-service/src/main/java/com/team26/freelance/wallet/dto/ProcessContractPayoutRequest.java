package com.team26.freelance.wallet.dto;

import com.team26.freelance.wallet.model.PayoutMethod;

public class ProcessContractPayoutRequest {

    private PayoutMethod method;
    private String accountLastFour;

    public PayoutMethod getMethod() {
        return method;
    }

    public void setMethod(PayoutMethod method) {
        this.method = method;
    }

    public String getAccountLastFour() {
        return accountLastFour;
    }

    public void setAccountLastFour(String accountLastFour) {
        this.accountLastFour = accountLastFour;
    }
}
