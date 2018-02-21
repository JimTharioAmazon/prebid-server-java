package org.rtb.vexing.model.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class CookieSyncRequest {

    String uuid;

    List<String> bidders;
}
