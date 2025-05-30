package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtRequestPrebidBidderConfig {

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.bidders
     */
    List<String> bidders;

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config
     */
    ExtBidderConfig config;
}
