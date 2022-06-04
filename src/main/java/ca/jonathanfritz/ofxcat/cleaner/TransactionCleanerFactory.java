package ca.jonathanfritz.ofxcat.cleaner;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Uses classpath scanning to find and initialize one instance of every implementation of {@link TransactionCleaner} in this package.
 *
 * When a caller needs an implementation for a particular bankId, that implementation is returned from an internal cache. If there
 * is no implementation for the bankId that the caller specifies, an instance of {@link DefaultTransactionCleaner} is returned.
 *
 * To add support for a particular bankId, create a new implementation of {@link TransactionCleaner} in this package.
 *
 * @see TransactionCleaner
 * @see DefaultTransactionCleaner
 */
public class TransactionCleanerFactory {

    private final Map<String, TransactionCleaner> cache;

    private static final Logger logger = LogManager.getLogger(TransactionCleanerFactory.class);

    public TransactionCleanerFactory() {
        // scan this package to find classes that implement the TransactionCleaner interface
        try (ScanResult result = new ClassGraph().enableClassInfo().acceptPackages(TransactionCleanerFactory.class.getPackageName()).scan()) {
            final Class<?>[] implementations = result.getClassesImplementing(TransactionCleaner.class.getName())
                    .loadClasses()
                    .toArray(new Class<?>[]{});

            // try to create an instance of each and populate the cache with the bankIds that they service
            cache = Arrays.stream(implementations)
                    .map(aClass -> {
                        try {
                            // all implementations MUST have a zero args constructor
                            TransactionCleaner tc = (TransactionCleaner) aClass.getConstructor().newInstance();
                            logger.info("Created new instance of TransactionCleaner {} for bankId {}", tc.getClass().getName(), tc.getBankId());
                            return tc;
                        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                            logger.error("Failed to create an instance of {}", aClass.getPackage().getName(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(TransactionCleaner::getBankId, tc -> tc));
        }
    }

    // TODO: bankId is null for credit card "accounts"
    //  if there are transactions on the CC that require massaging, may need to make a special cleaner for them
    public TransactionCleaner findByBankId(String bankId) {
        final TransactionCleaner cached = cache.get(bankId);
        if (cached != null) {
            logger.info("Found TransactionCleaner {} for bankId {}", cached.getClass().getName(), bankId);
            return cached;
        }
        logger.warn("No TransactionCleaner implementation available for bankId {}. Returning {}", bankId, DefaultTransactionCleaner.class.getName());
        return cache.get(DefaultTransactionCleaner.DEFAULT_BANK_ID);
    }
}
