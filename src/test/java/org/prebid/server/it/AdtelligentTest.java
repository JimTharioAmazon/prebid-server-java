package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class AdtelligentTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdtelligent() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adtelligent-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adtelligent/test-adtelligent-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/adtelligent/test-adtelligent-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adtelligent/test-auction-adtelligent-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adtelligent/test-auction-adtelligent-response.json", response,
                singletonList("adtelligent"));
    }
}
