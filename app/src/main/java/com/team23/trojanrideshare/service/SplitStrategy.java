package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.RideMember;

import java.util.List;

/** Strategy pattern: how a ride's actual cost is divided among its riders (FR-4.2). */
public interface SplitStrategy {

    /** One share per rider, in rider order. Shares always add up to {@code cents}. */
    List<CostShare> split(long cents, List<RideMember> riders) throws AppException;

    /** "EQUAL" or "CUSTOM", stored with the ride. */
    String method();
}
