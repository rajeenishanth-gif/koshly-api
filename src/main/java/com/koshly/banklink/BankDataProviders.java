package com.koshly.banklink;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.koshly.region.Market;

/** Finds the bank-linking provider for a country, or for an existing link. */
@Component
public class BankDataProviders {

    private final Map<String, BankDataProvider> byId;

    BankDataProviders(List<BankDataProvider> providers) {
        this.byId = providers.stream().collect(Collectors.toMap(BankDataProvider::id, Function.identity()));
        // Every country must name a provider that exists, or the app refuses to start.
        for (Market market : Market.values()) {
            if (!byId.containsKey(market.bankLinkProvider())) {
                throw new IllegalStateException("No BankDataProvider \"" + market.bankLinkProvider()
                        + "\" for " + market.country());
            }
        }
    }

    public BankDataProvider forCountry(Market market) {
        return byId.get(market.bankLinkProvider());
    }

    public BankDataProvider forLink(BankLink link) {
        return byId.get(link.getProvider());
    }
}
