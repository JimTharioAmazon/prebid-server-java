package org.rtb.vexing.bidder.model;

import com.iab.openrtb.response.Bid;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.model.openrtb.ext.response.BidType;

/**
 * Bid returned by a {@link Bidder}.
 */
@AllArgsConstructor(staticName = "of")
@Value
public final class BidderBid {

    /**
     * bid.ext will become "response.seatbid[i].bid.ext.bidder" in the final OpenRTB response
     */
    Bid bid;

    /**
     * This will become response.seatbid[i].bid.ext.prebid.type" in the final OpenRTB response
     */
    BidType type;
}
