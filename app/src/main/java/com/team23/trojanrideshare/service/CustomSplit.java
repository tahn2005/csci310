package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.util.Money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** FR-4.2.2: the organizer sets each rider's amount; the amounts must add up exactly. */
public class CustomSplit implements SplitStrategy {

    public static final String METHOD = "CUSTOM";

    private final Map<String, Long> amounts;

    /** @param amounts cents per rider user id. */
    public CustomSplit(Map<String, Long> amounts) {
        this.amounts = amounts == null ? new HashMap<>() : new HashMap<>(amounts);
    }

    @Override
    public List<CostShare> split(long cents, List<RideMember> riders) throws AppException {
        if (riders.isEmpty()) throw AppException.validation("This ride has no riders.");
        List<CostShare> out = new ArrayList<>();
        long sum = 0;
        for (RideMember m : riders) {
            Long amount = amounts.get(m.getUserId());
            if (amount == null) {
                throw AppException.validation("Enter an amount for every rider, including " + m.getDisplayName() + ".");
            }
            if (amount < 0) throw AppException.validation("Amounts can't be negative.");
            sum += amount;
            out.add(new CostShare(null, m.getUserId(), m.getDisplayName(), amount));
        }
        if (sum != cents) {
            long diff = Math.abs(cents - sum);
            throw AppException.validation("The shares add up to " + Money.format(sum) + ", which is "
                    + Money.format(diff) + (sum < cents ? " less" : " more") + " than the actual cost of "
                    + Money.format(cents) + ".");
        }
        return out;
    }

    @Override
    public String method() {
        return METHOD;
    }
}
