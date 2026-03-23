package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.config.AppConfig;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.matching.TokenNormalizer;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Clusters transactions into vendor groups using token overlap. Two transactions belong to the same
 * vendor group if their normalized token sets have pairwise overlap ≥ the configured threshold.
 * Transitive connections are resolved via union-find so that A↔B and B↔C produces one group even
 * if A and C share no tokens directly.
 *
 * <p>The display name for each group is reconstructed from the original transaction descriptions by
 * finding the tokens shared by a majority of transactions in the group (core tokens), then
 * recovering their natural left-to-right order from the descriptions.
 */
public class VendorGroupingService {

    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;
    private final Connection connection;
    private final TokenNormalizer tokenNormalizer;
    private final double overlapThreshold;

    private static final Logger logger = LogManager.getLogger(VendorGroupingService.class);

    // A token must appear in at least this fraction of a group's transactions to be a core token
    private static final double CORE_TOKEN_MAJORITY = 0.6;

    @Inject
    public VendorGroupingService(
            CategorizedTransactionDao categorizedTransactionDao,
            TransactionTokenDao transactionTokenDao,
            Connection connection,
            TokenNormalizer tokenNormalizer,
            AppConfig appConfig) {
        this.categorizedTransactionDao = categorizedTransactionDao;
        this.transactionTokenDao = transactionTokenDao;
        this.connection = connection;
        this.tokenNormalizer = tokenNormalizer;
        this.overlapThreshold = appConfig.getVendorGrouping().getOverlapThreshold();
    }

    /**
     * Groups all transactions in [startDate, endDate] by vendor. TRANSFER and UNKNOWN transactions
     * are excluded. The returned list is sorted by total amount ascending (largest absolute spends
     * first, since spend amounts are negative).
     *
     * @param startDate start of the date range, inclusive
     * @param endDate end of the date range, inclusive
     * @return vendor groups sorted by total amount ascending
     */
    public List<VendorGroup> groupByVendor(LocalDate startDate, LocalDate endDate) {
        List<CategorizedTransaction> transactions =
                categorizedTransactionDao.selectByDateRangeForVendorGrouping(startDate, endDate);

        if (transactions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Set<String>> tokensByTxId = loadTokens(transactions);

        Map<Long, Long> parent = initializeUnionFind(transactions);
        clusterByTokenOverlap(transactions, tokensByTxId, parent);

        return buildGroups(transactions, tokensByTxId, parent);
    }

    private Map<Long, Set<String>> loadTokens(List<CategorizedTransaction> transactions) {
        Set<Long> ids = transactions.stream().map(CategorizedTransaction::getId).collect(Collectors.toSet());

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return transactionTokenDao.getTokensForTransactions(t, ids);
        } catch (SQLException e) {
            logger.error("Failed to load tokens for vendor grouping", e);
            return Collections.emptyMap();
        }
    }

    private Map<Long, Long> initializeUnionFind(List<CategorizedTransaction> transactions) {
        Map<Long, Long> parent = new HashMap<>();
        for (CategorizedTransaction tx : transactions) {
            parent.put(tx.getId(), tx.getId());
        }
        return parent;
    }

    private void clusterByTokenOverlap(
            List<CategorizedTransaction> transactions, Map<Long, Set<String>> tokensByTxId, Map<Long, Long> parent) {

        // Build an inverted index: token -> set of transaction IDs that contain it.
        // Only transactions sharing at least one token are candidates for the same group.
        Map<String, Set<Long>> tokenIndex = new HashMap<>();
        for (CategorizedTransaction tx : transactions) {
            Set<String> tokens = tokensByTxId.getOrDefault(tx.getId(), Collections.emptySet());
            for (String token : tokens) {
                tokenIndex.computeIfAbsent(token, k -> new HashSet<>()).add(tx.getId());
            }
        }

        // For each token, count shared tokens between every pair of transactions that contain it.
        // This gives us the numerator for the overlap ratio without O(n^2) full pairwise comparison.
        Map<TxPair, Integer> sharedTokenCounts = new HashMap<>();
        for (Set<Long> txIds : tokenIndex.values()) {
            List<Long> idList = new ArrayList<>(txIds);
            for (int i = 0; i < idList.size(); i++) {
                for (int j = i + 1; j < idList.size(); j++) {
                    TxPair pair = TxPair.of(idList.get(i), idList.get(j));
                    sharedTokenCounts.merge(pair, 1, Integer::sum);
                }
            }
        }

        // Evaluate each candidate pair and union if overlap meets the threshold.
        for (Map.Entry<TxPair, Integer> entry : sharedTokenCounts.entrySet()) {
            TxPair pair = entry.getKey();
            int shared = entry.getValue();
            Set<String> tokensA = tokensByTxId.getOrDefault(pair.a(), Collections.emptySet());
            Set<String> tokensB = tokensByTxId.getOrDefault(pair.b(), Collections.emptySet());
            int minSize = Math.min(tokensA.size(), tokensB.size());
            if (minSize == 0) {
                continue;
            }
            double overlap = (double) shared / minSize;
            if (overlap >= overlapThreshold) {
                union(parent, pair.a(), pair.b());
            }
        }
    }

    private List<VendorGroup> buildGroups(
            List<CategorizedTransaction> transactions, Map<Long, Set<String>> tokensByTxId, Map<Long, Long> parent) {

        // Group transactions by their union-find root.
        Map<Long, List<CategorizedTransaction>> clusters = new HashMap<>();
        for (CategorizedTransaction tx : transactions) {
            long root = find(parent, tx.getId());
            clusters.computeIfAbsent(root, k -> new ArrayList<>()).add(tx);
        }

        List<VendorGroup> groups = new ArrayList<>();
        for (List<CategorizedTransaction> cluster : clusters.values()) {
            String displayName = computeDisplayName(cluster, tokensByTxId);
            float total = 0f;
            for (CategorizedTransaction tx : cluster) {
                total += tx.getAmount();
            }
            groups.add(new VendorGroup(displayName, Collections.unmodifiableList(cluster), total, cluster.size()));
        }

        // Sort by total amount ascending: largest absolute spends (most negative) come first.
        groups.sort(Comparator.comparingDouble(g -> g.totalAmount()));
        return Collections.unmodifiableList(groups);
    }

    private String computeDisplayName(List<CategorizedTransaction> cluster, Map<Long, Set<String>> tokensByTxId) {

        // Step 1: find core tokens — those appearing in at least 60% of transactions in the group.
        int majorityThreshold = (int) Math.ceil(cluster.size() * CORE_TOKEN_MAJORITY);
        Map<String, Integer> tokenFrequency = new HashMap<>();
        for (CategorizedTransaction tx : cluster) {
            for (String token : tokensByTxId.getOrDefault(tx.getId(), Collections.emptySet())) {
                tokenFrequency.merge(token, 1, Integer::sum);
            }
        }
        Set<String> coreTokens = tokenFrequency.entrySet().stream()
                .filter(e -> e.getValue() >= majorityThreshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (coreTokens.isEmpty()) {
            // Fallback: no stable core tokens; use the first transaction's raw description.
            return cluster.get(0).getDescription();
        }

        // Step 2: for each transaction, extract the ordered subsequence of core tokens from its
        // description, then find the most common such subsequence across the group.
        Map<String, Integer> subsequenceFrequency = new HashMap<>();
        for (CategorizedTransaction tx : cluster) {
            List<String> ordered = tokenNormalizer.normalizeOrdered(tx.getDescription());
            String subsequence = ordered.stream().filter(coreTokens::contains).collect(Collectors.joining(" "));
            if (!subsequence.isBlank()) {
                subsequenceFrequency.merge(subsequence, 1, Integer::sum);
            }
        }

        if (subsequenceFrequency.isEmpty()) {
            return cluster.get(0).getDescription();
        }

        // Step 3: return the most common subsequence, title-cased.
        // Ties are broken alphabetically (alphabetically first wins).
        String best = subsequenceFrequency.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry<String, Integer>::getValue)
                        .thenComparing(Comparator.comparing(Map.Entry<String, Integer>::getKey)
                                .reversed()))
                .map(Map.Entry::getKey)
                .orElse(cluster.get(0).getDescription());

        return toTitleCase(best);
    }

    private String toTitleCase(String s) {
        String[] words = s.split(" ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return result.toString();
    }

    // Union-find: find root with path compression.
    private long find(Map<Long, Long> parent, long id) {
        if (!parent.get(id).equals(id)) {
            parent.put(id, find(parent, parent.get(id)));
        }
        return parent.get(id);
    }

    // Union-find: merge the sets containing a and b.
    private void union(Map<Long, Long> parent, long a, long b) {
        long rootA = find(parent, a);
        long rootB = find(parent, b);
        if (rootA != rootB) {
            parent.put(rootA, rootB);
        }
    }

    // Normalized pair (a <= b) used as a map key for candidate pairs.
    private record TxPair(long a, long b) {
        static TxPair of(long x, long y) {
            return new TxPair(Math.min(x, y), Math.max(x, y));
        }
    }
}
