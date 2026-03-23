package ca.jonathanfritz.ofxcat.model;

import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import java.util.List;

/**
 * Represents a group of transactions that all belong to the same vendor, as determined by
 * token overlap clustering in {@link VendorGroupingService}.
 *
 * @param displayName a human-readable vendor name reconstructed from the original transaction
 *     descriptions
 * @param transactions the transactions assigned to this vendor group
 * @param totalAmount the sum of all transaction amounts in this group (negative = net spend)
 * @param transactionCount the number of transactions in this group
 */
public record VendorGroup(
        String displayName, List<CategorizedTransaction> transactions, float totalAmount, int transactionCount) {}
