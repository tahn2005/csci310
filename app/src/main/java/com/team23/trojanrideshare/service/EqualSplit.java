package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.RideMember;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * FR-4.2.1: every rider pays the same whole number of cents; the organizer chooses which
 * riders pay one extra cent when the cost doesn't divide evenly.
 */
public class EqualSplit implements SplitStrategy {

    public static final String METHOD = "EQUAL";

    private final List<String> extraCentUserIds;

    /** @param extraCentUserIds who gets the leftover cents; null picks the first riders. */
    public EqualSplit(List<String> extraCentUserIds) {
        this.extraCentUserIds = extraCentUserIds == null ? null : new ArrayList<>(extraCentUserIds);
    }

    /** How many riders must receive an extra cent. */
    public static int leftoverCents(long cents, int riders) {
        return riders <= 0 ? 0 : (int) (cents % riders);
    }

    @Override
    public List<CostShare> split(long cents, List<RideMember> riders) throws AppException {
        int n = riders.size();
        if (n == 0) throw AppException.validation("This ride has no riders.");
        long base = cents / n;
        int leftover = leftoverCents(cents, n);

        Set<String> riderIds = new LinkedHashSet<>();
        for (RideMember m : riders) riderIds.add(m.getUserId());

        Set<String> extra = new LinkedHashSet<>();
        if (extraCentUserIds == null) {
            for (RideMember m : riders) {
                if (extra.size() == leftover) break;
                extra.add(m.getUserId());
            }
        } else {
            for (String id : extraCentUserIds) {
                if (!riderIds.contains(id)) {
                    throw AppException.conflict("The riders in this ride changed. Please review the split again.");
                }
                extra.add(id);
            }
        }
        if (extra.size() != leftover) {
            throw AppException.validation(leftover == 0
                    ? "The cost divides evenly, so nobody pays an extra cent."
                    : "Choose exactly " + leftover + (leftover == 1 ? " rider" : " riders")
                    + " to pay one extra cent.");
        }

        List<CostShare> out = new ArrayList<>();
        for (RideMember m : riders) {
            long amount = base + (extra.contains(m.getUserId()) ? 1 : 0);
            out.add(new CostShare(null, m.getUserId(), m.getDisplayName(), amount));
        }
        return out;
    }

    @Override
    public String method() {
        return METHOD;
    }
}
