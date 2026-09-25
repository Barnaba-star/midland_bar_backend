package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Projection.CommissionTotalProjection;
import com.midland.bar.Bar.Projection.BarProjection;
import com.midland.bar.Utils.Responses.ResponsePage;

public class ServiceAndStoreReportResponse {

    private ResponsePage<BarProjection> data;
    private CommissionTotalProjection commissionTotals;

    public ServiceAndStoreReportResponse(
            ResponsePage<BarProjection> data,
            CommissionTotalProjection commissionTotals
    ) {
        this.data = data;
        this.commissionTotals = commissionTotals;
    }

    public ResponsePage<BarProjection> getData() {
        return data;
    }

    public CommissionTotalProjection getCommissionTotals() {
        return commissionTotals;
    }
}
