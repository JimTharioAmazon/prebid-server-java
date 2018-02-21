package org.rtb.vexing.handler;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Format;
import io.netty.util.AsciiString;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.adapter.HttpConnector;
import org.rtb.vexing.auction.PreBidRequestContextFactory;
import org.rtb.vexing.cache.CacheService;
import org.rtb.vexing.cache.model.BidCacheResult;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.execution.GlobalTimeout;
import org.rtb.vexing.metric.AccountMetrics;
import org.rtb.vexing.metric.AdapterMetrics;
import org.rtb.vexing.metric.MetricName;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.PreBidRequestContext.PreBidRequestContextBuilder;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.request.PreBidRequest.PreBidRequestBuilder;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.BidderStatus.BidderStatusBuilder;
import org.rtb.vexing.model.response.PreBidResponse;
import org.rtb.vexing.settings.ApplicationSettings;
import org.rtb.vexing.settings.model.Account;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class AuctionHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private AdapterCatalog adapterCatalog;
    @Mock
    private Adapter rubiconAdapter;
    @Mock
    private Adapter appnexusAdapter;
    @Mock
    private PreBidRequestContextFactory preBidRequestContextFactory;
    @Mock
    private CacheService cacheService;
    @Mock
    private Vertx vertx;
    @Mock
    private Metrics metrics;
    @Mock
    private AdapterMetrics adapterMetrics;
    @Mock
    private AccountMetrics accountMetrics;
    @Mock
    private AdapterMetrics accountAdapterMetrics;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private HttpConnector httpConnector;

    private AuctionHandler auctionHandler;

    @Before
    public void setUp() {
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.of(null, null)));

        given(adapterCatalog.getByCode(eq(RUBICON))).willReturn(rubiconAdapter);
        given(adapterCatalog.isValidCode(eq(RUBICON))).willReturn(true);
        given(adapterCatalog.getByCode(eq(APPNEXUS))).willReturn(appnexusAdapter);
        given(adapterCatalog.isValidCode(eq(APPNEXUS))).willReturn(true);

        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(AdditionalAnswers.<Long, Handler<Long>>answerVoid((p, h) -> h.handle(0L)));

        given(metrics.forAdapter(any())).willReturn(adapterMetrics);
        given(metrics.forAccount(anyString())).willReturn(accountMetrics);
        given(accountMetrics.forAdapter(any())).willReturn(accountAdapterMetrics);

        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);

        auctionHandler = new AuctionHandler(applicationSettings, adapterCatalog, preBidRequestContextFactory,
                cacheService, vertx, metrics, httpConnector);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(null, null, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, null, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, preBidRequestContextFactory, null,
                        null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, preBidRequestContextFactory,
                        cacheService, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, preBidRequestContextFactory,
                        cacheService, vertx, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, preBidRequestContextFactory,
                        cacheService, vertx, metrics, null));
    }

    @Test
    public void shouldRespondWithErrorIfRequestIsNotValid() throws IOException {
        // given
        given(preBidRequestContextFactory.fromRequest(any()))
                .willReturn(Future.failedFuture(new PreBidException("Could not create")));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("Error parsing request: Could not create");
    }

    @Test
    public void shouldRespondWithErrorIfRequestBodyHasUnknownAccountId() throws IOException {
        // given
        givenPreBidRequestContext(identity(), identity());

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("Unknown account id: Unknown account");
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        givenPreBidRequestContext(identity(), identity());

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).putHeader(eq(new AsciiString("Date")), ArgumentMatchers.<CharSequence>isNotNull());
        verify(httpResponse)
                .putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldRespondWithNoCookieStatusIfNoLiveUidsInCookie() throws IOException {
        // given
        givenPreBidRequestContext(identity(), builder -> builder.noLiveUids(true));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("no_cookie");
    }

    @Test
    public void shouldRespondWithErrorIfUnexpectedExceptionOccurs() throws IOException {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        given(httpConnector.call(any(), any(), any())).willReturn(Future.failedFuture(new RuntimeException()));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("Unexpected server error");
    }

    @Test
    public void shouldInteractWithCacheServiceIfRequestHasBidsAndCacheMarkupFlag() throws IOException {
        // given
        final GlobalTimeout timeout = GlobalTimeout.create(1000L);
        givenPreBidRequestContext(
                builder -> builder.cacheMarkup(1),
                builder -> builder
                        .timeout(timeout)
                        .bidders(singletonList(Bidder.of(RUBICON, singletonList(null)))));

        givenBidderRespondingWithBids(RUBICON, identity(), "bidId1");

        given(cacheService.cacheBids(anyList(), any())).willReturn(Future.succeededFuture(singletonList(
                BidCacheResult.of("0b4f60d1-fb99-4d95-ba6f-30ac90f9a315", "cached_asset_url"))));
        given(cacheService.getCachedAssetURL(anyString())).willReturn("cached_asset_url");

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(cacheService).cacheBids(anyList(), same(timeout));

        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBids()).extracting(Bid::getAdm).containsNull();
        assertThat(preBidResponse.getBids()).extracting(Bid::getNurl).containsNull();
        assertThat(preBidResponse.getBids()).extracting(Bid::getCacheId)
                .containsOnly("0b4f60d1-fb99-4d95-ba6f-30ac90f9a315");
        assertThat(preBidResponse.getBids()).extracting(Bid::getCacheUrl).containsOnly("cached_asset_url");
    }

    @Test
    public void shouldNotInteractWithCacheServiceIfRequestHasBidsAndNoCacheMarkupFlag() throws IOException {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        givenBidderRespondingWithBids(RUBICON, identity(), "bidId1");

        // when
        auctionHandler.handle(routingContext);

        // then
        verifyZeroInteractions(cacheService);

        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBids()).extracting(Bid::getCacheId).containsNull();
        assertThat(preBidResponse.getBids()).extracting(Bid::getCacheUrl).containsNull();
    }

    @Test
    public void shouldNotInteractWithCacheServiceIfRequestHasNoBidsButCacheMarkupFlag() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(builder -> builder.cacheMarkup(1));

        givenBidderRespondingWithBids(RUBICON, identity());

        // when
        auctionHandler.handle(routingContext);

        // then
        verifyZeroInteractions(cacheService);
    }

    @Test
    public void shouldRespondWithErrorIfCacheServiceFails() throws IOException {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(builder -> builder.cacheMarkup(1));

        givenBidderRespondingWithBids(RUBICON, identity(), "bidId1");

        given(cacheService.cacheBids(anyList(), any())).willReturn(Future.failedFuture("http exception"));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("Prebid cache failed: http exception");
    }

    @Test
    public void shouldRespondWithMultipleBidderStatusesAndBidsWhenMultipleAdUnitsAndBidsInPreBidRequest()
            throws IOException {
        // given
        givenPreBidRequestContextWith2AdUnitsAnd2BidsEach(builder -> builder.noLiveUids(false));

        given(httpConnector.call(any(), any(), any()))
                .willReturn(Future.succeededFuture(BidderResult.of(
                        BidderStatus.builder().bidder(RUBICON).responseTimeMs(100).build(),
                        Arrays.stream(new String[]{"bidId1", "bidId2"})
                                .map(id -> org.rtb.vexing.model.response.Bid.builder()
                                        .bidId(id)
                                        .price(new BigDecimal("5.67"))
                                        .build())
                                .collect(Collectors.toList()),
                        false)))
                .willReturn(Future.succeededFuture(BidderResult.of(
                        BidderStatus.builder().bidder(APPNEXUS).responseTimeMs(100).build(),
                        Arrays.stream(new String[]{"bidId3", "bidId4"})
                                .map(id -> org.rtb.vexing.model.response.Bid.builder()
                                        .bidId(id)
                                        .price(new BigDecimal("5.67"))
                                        .build())
                                .collect(Collectors.toList()),
                        false)));
        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("OK");
        assertThat(preBidResponse.getTid()).isEqualTo("tid");
        assertThat(preBidResponse.getBidderStatus()).extracting(BidderStatus::getBidder)
                .containsOnly(RUBICON, APPNEXUS);
        assertThat(preBidResponse.getBids()).extracting(Bid::getBidId)
                .containsOnly("bidId1", "bidId2", "bidId3", "bidId4");
    }

    @Test
    public void shouldRespondWithBidsWithTargetingKeywordsWhenSortBidsFlagIsSetInPreBidRequest() throws IOException {
        // given
        final List<AdUnitBid> adUnitBids = asList(null, null);
        final List<Bidder> bidders = asList(Bidder.of(RUBICON, adUnitBids), Bidder.of(APPNEXUS, adUnitBids));
        givenPreBidRequestContext(builder -> builder.sortBids(1), builder -> builder.bidders(bidders));

        given(httpConnector.call(any(), any(), any()))
                .willReturn(Future.succeededFuture(BidderResult.of(
                        BidderStatus.builder().bidder(RUBICON).responseTimeMs(100).build(),
                        asList(
                                org.rtb.vexing.model.response.Bid.builder()
                                        .bidder(RUBICON).code("adUnitCode1").bidId("bidId1")
                                        .price(new BigDecimal("5.67"))
                                        .responseTimeMs(60).adServerTargeting(
                                        singletonMap("rpfl_1001", "2_tier0100")).build(),
                                org.rtb.vexing.model.response.Bid.builder()
                                        .bidder(RUBICON).code("adUnitCode2").bidId("bidId2")
                                        .price(new BigDecimal("6.35"))
                                        .responseTimeMs(80).build()),
                        false)))
                .willReturn(Future.succeededFuture(BidderResult.of(
                        BidderStatus.builder().bidder(APPNEXUS).responseTimeMs(100).build(),
                        asList(
                                org.rtb.vexing.model.response.Bid.builder()
                                        .bidder(APPNEXUS).code("adUnitCode1").bidId("bidId3")
                                        .price(new BigDecimal("5.67"))
                                        .responseTimeMs(50).build(),
                                org.rtb.vexing.model.response.Bid.builder()
                                        .bidder(APPNEXUS).code("adUnitCode2").bidId("bidId4")
                                        .price(new BigDecimal("7.15"))
                                        .responseTimeMs(100).build()),
                        false)));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBids()).extracting(Bid::getAdServerTargeting).doesNotContainNull();
        // verify that ad server targeting has been preserved
        assertThat(preBidResponse.getBids()).extracting(Bid::getBidId, b -> b.getAdServerTargeting().get("rpfl_1001"))
                .contains(tuple("bidId1", "2_tier0100"));
        // weird way to verify that sorting has happened before bids grouped by ad unit code are enriched with targeting
        // keywords
        assertThat(preBidResponse.getBids()).extracting(Bid::getBidId, b -> b.getAdServerTargeting().get("hb_bidder"))
                .containsOnly(
                        tuple("bidId1", null),
                        tuple("bidId2", null),
                        tuple("bidId3", APPNEXUS),
                        tuple("bidId4", APPNEXUS));
    }

    @Test
    public void shouldRespondWithValidBannerBidIfSizeIsMissedButRecoveredFromAdUnit() throws IOException {
        // given
        final List<AdUnitBid> adUnitBids = singletonList(AdUnitBid.builder()
                .adUnitCode("adUnitCode1")
                .bidId("bidId1")
                .sizes(Collections.singletonList(Format.builder().w(100).h(200).build()))
                .build());
        final List<Bidder> bidders = singletonList(Bidder.of(RUBICON, adUnitBids));

        givenPreBidRequestContext(identity(), builder -> builder.bidders(bidders));

        given(httpConnector.call(any(), any(), any())).willReturn(Future.succeededFuture(BidderResult.of(
                BidderStatus.builder().bidder(RUBICON).responseTimeMs(100).numBids(1).build(),
                singletonList(
                        org.rtb.vexing.model.response.Bid.builder().mediaType(MediaType.banner)
                                .bidder(RUBICON).code("adUnitCode1").bidId("bidId1").price(new BigDecimal("5.67"))
                                .build()),
                false)));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBids()).hasSize(1);
        assertThat(preBidResponse.getBidderStatus().get(0).getNumBids()).isEqualTo(1);
    }

    @Test
    public void shouldRespondWithValidVideoBidEvenIfSizeIsMissed() throws IOException {
        // given
        final List<AdUnitBid> adUnitBids = singletonList(null);
        final List<Bidder> bidders = singletonList(Bidder.of(RUBICON, adUnitBids));

        givenPreBidRequestContext(identity(), builder -> builder.bidders(bidders));

        given(httpConnector.call(any(), any(), any())).willReturn(Future.succeededFuture(BidderResult.of(
                BidderStatus.builder().bidder(RUBICON).responseTimeMs(100).numBids(1).build(),
                singletonList(
                        org.rtb.vexing.model.response.Bid.builder().mediaType(MediaType.video).bidId("bidId1")
                                .price(new BigDecimal("5.67")).build()),
                false)));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBids()).hasSize(1);
        assertThat(preBidResponse.getBidderStatus().get(0).getNumBids()).isEqualTo(1);
    }

    @Test
    public void shouldTolerateUnsupportedBidderInPreBidRequest() throws IOException {
        // given
        final List<Bidder> bidders = asList(
                Bidder.of("unsupported", singletonList(null)),
                Bidder.of(RUBICON, singletonList(null)));
        givenPreBidRequestContext(identity(), builder -> builder.bidders(bidders));

        givenBidderRespondingWithBids(RUBICON, identity(), "bidId1");

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBidderStatus())
                .extracting(BidderStatus::getBidder, BidderStatus::getError).containsOnly(
                tuple("unsupported", "Unsupported bidder"),
                tuple(RUBICON, null));
        assertThat(preBidResponse.getBids()).hasSize(1);
    }

    @Test
    public void shouldTolerateErrorResultFromAdapter() throws IOException {
        // given
        givenPreBidRequestContextWith2AdUnitsAnd2BidsEach(identity());

        given(httpConnector.call(any(), any(), any()))
                .willReturn(Future.succeededFuture(BidderResult.of(
                        BidderStatus.builder().bidder(RUBICON).responseTimeMs(500).error("rubicon error").build(),
                        emptyList(), false)))
                .willReturn(Future.succeededFuture(BidderResult.of(
                        BidderStatus.builder().bidder(APPNEXUS).responseTimeMs(100).build(),
                        singletonList(org.rtb.vexing.model.response.Bid.builder()
                                .bidId("bidId1")
                                .price(new BigDecimal("5.67"))
                                .build()),
                        false)));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBidderStatus()).extracting(BidderStatus::getBidder, BidderStatus::getError)
                .containsOnly(
                        tuple(RUBICON, "rubicon error"),
                        tuple(APPNEXUS, null));
        assertThat(preBidResponse.getBids()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldIncrementCommonMetrics() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(builder -> builder.app(App.builder().build()));

        // simulate calling end handler that is supposed to update request_time timer value
        given(httpResponse.endHandler(any())).willAnswer(inv -> {
            ((Handler<Void>) inv.getArgument(0)).handle(null);
            return null;
        });

        givenBidderRespondingWithBids(RUBICON, builder -> builder.noCookie(true).numBids(1), "bidId1");

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.requests));
        verify(metrics).incCounter(eq(MetricName.app_requests));
        verify(accountMetrics).incCounter(eq(MetricName.requests));
        verify(metrics).updateTimer(eq(MetricName.request_time), anyLong());
        verify(adapterMetrics).incCounter(eq(MetricName.requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.requests));
        verify(adapterMetrics).updateTimer(eq(MetricName.request_time), eq(100L));
        verify(accountAdapterMetrics).updateTimer(eq(MetricName.request_time), eq(100L));
        verify(adapterMetrics).incCounter(eq(MetricName.no_cookie_requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.no_cookie_requests));
        verify(accountMetrics).incCounter(eq(MetricName.bids_received), eq(1L));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.bids_received), eq(1L));
        verify(adapterMetrics).updateHistogram(eq(MetricName.prices), eq(5670L));
        verify(accountMetrics).updateHistogram(eq(MetricName.prices), eq(5670L));
        verify(accountAdapterMetrics).updateHistogram(eq(MetricName.prices), eq(5670L));
        verify(accountMetrics, never()).incCounter(eq(MetricName.no_bid_requests));
        verify(accountAdapterMetrics, never()).incCounter(eq(MetricName.no_bid_requests));
        verify(metrics, never()).incCounter(eq(MetricName.safari_requests));
        verify(metrics, never()).incCounter(eq(MetricName.no_cookie_requests));
        verify(metrics, never()).incCounter(eq(MetricName.safari_no_cookie_requests));
        verify(metrics, never()).incCounter(eq(MetricName.error_requests));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldIncrementNoBidMetrics() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        givenBidderRespondingWithBids(RUBICON, builder -> builder.noBid(true));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(adapterMetrics).incCounter(eq(MetricName.no_bid_requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.no_bid_requests));
    }

    @Test
    public void shouldIncrementSafariAndNoCookieMetrics() {
        // given
        givenPreBidRequestContext(identity(), builder -> builder.noLiveUids(true));

        httpRequest.headers().add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) " +
                "AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7");

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.safari_requests));
        verify(metrics).incCounter(eq(MetricName.no_cookie_requests));
        verify(metrics).incCounter(eq(MetricName.safari_no_cookie_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfRequestBodyHasUnknownAccountId() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfRequestIsNotValid() {
        // given
        given(preBidRequestContextFactory.fromRequest(any()))
                .willReturn(Future.failedFuture(new PreBidException("Could not create")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfAdapterReturnsError() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        givenBidderRespondingWithError(RUBICON, "rubicon error", false);

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(adapterMetrics).incCounter(eq(MetricName.error_requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.error_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfAdapterReturnsTimeoutError() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        givenBidderRespondingWithError(RUBICON, "time out", true);

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(adapterMetrics).incCounter(eq(MetricName.timeout_requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.timeout_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfCacheServiceFails() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(builder -> builder.cacheMarkup(1));

        givenBidderRespondingWithBids(RUBICON, identity(), "bidId1");

        given(cacheService.cacheBids(anyList(), any())).willReturn(Future.failedFuture("http exception"));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    private void givenPreBidRequestContextWith1AdUnitAnd1Bid(
            Function<PreBidRequestBuilder, PreBidRequestBuilder> preBidRequestBuilderCustomizer) {

        final List<Bidder> bidders = singletonList(Bidder.of(RUBICON, singletonList(null)));
        givenPreBidRequestContext(preBidRequestBuilderCustomizer, builder -> builder.bidders(bidders));
    }

    private void givenPreBidRequestContextWith2AdUnitsAnd2BidsEach(
            Function<PreBidRequestContextBuilder, PreBidRequestContextBuilder> preBidRequestContextBuilderCustomizer) {
        final List<AdUnitBid> adUnitBids = asList(null, null);
        final List<Bidder> bidders = asList(
                Bidder.of(RUBICON, adUnitBids),
                Bidder.of(APPNEXUS, adUnitBids));
        givenPreBidRequestContext(identity(),
                preBidRequestContextBuilderCustomizer.compose(builder -> builder.bidders(bidders)));
    }

    private void givenPreBidRequestContext(
            Function<PreBidRequestBuilder, PreBidRequestBuilder> preBidRequestBuilderCustomizer,
            Function<PreBidRequestContextBuilder, PreBidRequestContextBuilder> preBidRequestContextBuilderCustomizer) {

        final PreBidRequest preBidRequest = preBidRequestBuilderCustomizer.apply(
                PreBidRequest.builder()
                        .tid("tid")
                        .accountId("accountId")
                        .adUnits(emptyList()))
                .build();
        final PreBidRequestContext preBidRequestContext = preBidRequestContextBuilderCustomizer.apply(
                PreBidRequestContext.builder()
                        .bidders(emptyList())
                        .preBidRequest(preBidRequest))
                .build();
        given(preBidRequestContextFactory.fromRequest(any())).willReturn(Future.succeededFuture(preBidRequestContext));
    }

    private void givenBidderRespondingWithBids(String bidder, Function<BidderStatusBuilder, BidderStatusBuilder>
            bidderStatusBuilderCustomizer, String... bidIds) {
        given(httpConnector.call(any(), any(), any()))
                .willReturn(Future.succeededFuture(BidderResult.of(
                        bidderStatusBuilderCustomizer.apply(BidderStatus.builder()
                                .bidder(bidder)
                                .responseTimeMs(100))
                                .build(),
                        Arrays.stream(bidIds)
                                .map(id -> org.rtb.vexing.model.response.Bid.builder()
                                        .bidId(id)
                                        .price(new BigDecimal("5.67"))
                                        .build())
                                .collect(Collectors.toList()),
                        false)));
    }

    private void givenBidderRespondingWithError(String bidder, String error, boolean timedOut) {
        given(httpConnector.call(any(), any(), any()))
                .willReturn(Future.succeededFuture(BidderResult.of(
                        BidderStatus.builder().bidder(bidder).responseTimeMs(500).error(error).build(),
                        emptyList(), timedOut)));
    }

    private PreBidResponse capturePreBidResponse() throws IOException {
        final ArgumentCaptor<String> preBidResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse).end(preBidResponseCaptor.capture());
        return mapper.readValue(preBidResponseCaptor.getValue(), PreBidResponse.class);
    }
}
