package com.team23.trojanrideshare.gateway;

import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.Place;

import java.util.List;

/** Wraps a location API: place suggestions for a keyword and place coordinates. */
public interface LocationGateway {

    /** Up to a handful of places matching {@code query}; empty if nothing matches. */
    void suggest(String query, Callback<List<Place>> cb);

    /** The place with its name and coordinates. */
    void getPlace(String placeId, Callback<Place> cb);
}
