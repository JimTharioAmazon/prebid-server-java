package org.prebid.server.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.proto.openrtb.ext.request.adrino.ExtImpAdrino;
import org.prebid.server.proto.openrtb.ext.request.adtelligent.ExtImpAdtelligent;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.prebid.server.proto.openrtb.ext.request.audiencenetwork.ExtImpAudienceNetwork;
import org.prebid.server.proto.openrtb.ext.request.beachfront.ExtImpBeachfront;
import org.prebid.server.proto.openrtb.ext.request.eplanning.ExtImpEplanning;
import org.prebid.server.proto.openrtb.ext.request.openx.ExtImpOpenx;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.prebid.server.proto.openrtb.ext.request.sovrn.ExtImpSovrn;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.config.bidder.model.Ortb;
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class BidderParamValidatorTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";
    private static final String APPNEXUS_ALIAS = "appnexusAlias";
    private static final String SOVRN = "sovrn";
    private static final String ADTELLIGENT = "adtelligent";
    private static final String FACEBOOK = "audienceNetwork";
    private static final String OPENX = "openx";
    private static final String EPLANNING = "eplanning";
    private static final String BEACHFRONT = "beachfront";
    private static final String VISX = "visx";
    private static final String ADRINO = "adrino";

    @Mock(strictness = LENIENT)
    private BidderCatalog bidderCatalog;

    private BidderParamValidator bidderParamValidator;

    @BeforeEach
    public void setUp() {
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList(
                RUBICON,
                APPNEXUS,
                APPNEXUS_ALIAS,
                SOVRN,
                ADTELLIGENT,
                FACEBOOK,
                OPENX,
                EPLANNING,
                BEACHFRONT,
                VISX,
                ADRINO)));
        given(bidderCatalog.bidderInfoByName(anyString())).willReturn(givenBidderInfo());
        given(bidderCatalog.bidderInfoByName(eq(APPNEXUS_ALIAS))).willReturn(givenBidderInfo(APPNEXUS));

        bidderParamValidator = BidderParamValidator.create(bidderCatalog, "static/bidder-params", jacksonMapper);
    }

    @Test
    public void createShouldFailOnInvalidSchemaPath() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> BidderParamValidator.create(bidderCatalog, "noschema", jacksonMapper));
    }

    @Test
    public void createShouldFailOnEmptySchemaFile() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> BidderParamValidator.create(
                        bidderCatalog, "org/prebid/server/validation/schema/empty", jacksonMapper));
    }

    @Test
    public void createShouldFailOnInvalidSchemaFile() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> BidderParamValidator.create(
                        bidderCatalog, "org/prebid/server/validation/schema/invalid", jacksonMapper));
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenRubiconImpExtIsOkIgnoringCase() {
        // given
        final ExtImpRubicon ext = ExtImpRubicon.builder().accountId(1).siteId(2).zoneId(3).build();
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate("rUBIcon", node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenRubiconImpExtNotValid() {
        // given
        final ExtImpRubicon ext = ExtImpRubicon.builder().siteId(2).zoneId(3).build();

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        final Set<String> messages = bidderParamValidator.validate(RUBICON, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenAppnexusImpExtNotValid() {
        // given
        final ExtImpAppnexus ext = ExtImpAppnexus.builder().member("memberId").build();

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(APPNEXUS, node);

        // then
        assertThat(messages.size()).isEqualTo(5);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenAppnexusImpExtIsOk() {
        // given
        final ExtImpAppnexus ext = ExtImpAppnexus.builder().placementId(1).build();

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(APPNEXUS, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenAppnexusAliasImpExtNotValid() {
        // given
        final ExtImpAppnexus ext = ExtImpAppnexus.builder().member("memberId").build();

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(APPNEXUS_ALIAS, node);

        // then
        assertThat(messages.size()).isEqualTo(5);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenAppnexusAliasImpExtIsOk() {
        // given
        final ExtImpAppnexus ext = ExtImpAppnexus.builder().placementId(1).build();

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(APPNEXUS_ALIAS, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenSovrnImpExtIsOk() {
        // given
        final ExtImpSovrn ext = ExtImpSovrn.of("tag", null, null, null);

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(SOVRN, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenSovrnExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode();

        // when
        final Set<String> messages = bidderParamValidator.validate(SOVRN, node);

        // then
        assertThat(messages.size()).isEqualTo(3);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenAdtelligentImpExtIsOk() {
        // given
        final ExtImpAdtelligent ext = ExtImpAdtelligent.of("15", 1, 2, BigDecimal.valueOf(3));

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(ADTELLIGENT, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenAdtelligentImpExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode();

        // when
        final Set<String> messages = bidderParamValidator.validate(ADTELLIGENT, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenFacebookImpExtIsOk() {
        // given
        final ExtImpAudienceNetwork ext = ExtImpAudienceNetwork.of("placementId", "publisherId");

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(FACEBOOK, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenFacebookExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode();

        // when
        final Set<String> messages = bidderParamValidator.validate(FACEBOOK, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenOpenxImpExtIsOk() {
        // given
        final ExtImpOpenx ext = ExtImpOpenx.builder()
                .customParams(Collections.singletonMap("foo", mapper.convertValue("bar", JsonNode.class)))
                .customFloor(BigDecimal.valueOf(0.2))
                .delDomain("se-demo-d.openx.net")
                .unit("2222")
                .build();
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(OPENX, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenOpenxExtNotValid() {
        // given
        final ExtImpOpenx ext = ExtImpOpenx.builder()
                .customParams(Collections.singletonMap("foo", mapper.convertValue("bar", JsonNode.class)))
                .customFloor(BigDecimal.valueOf(0.2))
                .delDomain("se-demo-d.openx.net")
                .unit("not-numeric")
                .build();
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(OPENX, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenEplanningImpExtIsOk() {
        // given
        final ExtImpEplanning ext = ExtImpEplanning.of("clientId", "");
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(EPLANNING, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenEplanningExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode().put("exchange_id", 5);

        // when
        final Set<String> messages = bidderParamValidator.validate(EPLANNING, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenBeachfrontImpExtIsOk() {
        // given
        final ExtImpBeachfront ext = ExtImpBeachfront.of("appId", null, BigDecimal.ONE, "adm");
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(BEACHFRONT, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenBeachfrontExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode();

        // when
        final Set<String> messages = bidderParamValidator.validate(BEACHFRONT, node);

        // then
        assertThat(messages.size()).isEqualTo(3);
    }

    @Test
    public void schemaShouldReturnSchemasString() throws IOException {
        // given
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("test-rubicon", "test-appnexus")));

        bidderParamValidator = BidderParamValidator.create(
                bidderCatalog, "org/prebid/server/validation/schema/valid", jacksonMapper);

        // when
        final String result = bidderParamValidator.schemas();

        // then
        assertThat(result).isEqualTo(ResourceUtil.readFromClasspath(
                "org/prebid/server/validation/schema//valid/test-schemas.json"));
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenVisxUidNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode().put("uid", "1a2b3c");

        // when
        final Set<String> messages = bidderParamValidator.validate(VISX, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldReturnNoValidationMessagesWhenVisxUidValid() {
        // given
        final JsonNode stringUidNode = mapper.createObjectNode().put("uid", "123");
        final JsonNode integerUidNode = mapper.createObjectNode().put("uid", 567);

        // when
        final Set<String> messagesStringUid = bidderParamValidator.validate(VISX, stringUidNode);
        final Set<String> messagesIntegerUid = bidderParamValidator.validate(VISX, integerUidNode);

        // then
        assertThat(messagesStringUid).isEmpty();
        assertThat(messagesIntegerUid).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenAdrinoImpExtNotValid() {
        // given
        final ExtImpAdrino ext = ExtImpAdrino.of(null);

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(ADRINO, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    private static BidderInfo givenBidderInfo(String aliasOf) {
        return BidderInfo.create(
                true,
                null,
                true,
                "https://endpoint.com",
                aliasOf,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                true,
                false,
                CompressionType.NONE,
                Ortb.of(false),
                0L);
    }

    private static BidderInfo givenBidderInfo() {
        return givenBidderInfo(null);
    }
}
