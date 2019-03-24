package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.io.OfxParser;
import ca.jonathanfritz.ofxcat.transactions.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.transactions.Category;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import ca.jonathanfritz.ofxcat.transactions.TransactionCategoryStore;
import com.webcohesion.ofx4j.OFXException;
import com.webcohesion.ofx4j.io.OFXParseException;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class OfxCat {

    private final TransactionCategoryStore transactionCategoryStore;

    public OfxCat(TransactionCategoryStore transactionCategoryStore) {
        this.transactionCategoryStore = transactionCategoryStore;
    }

    private Set<Transaction> parseOfxFile(final File inputFile) throws OFXException {
        try (final FileInputStream inputStream = new FileInputStream(inputFile)) {

            final OfxParser ofxParser = new OfxParser();
            return ofxParser.parse(inputStream);

        } catch (FileNotFoundException e) {
            throw new OFXException("File not found", e);
        } catch (OFXParseException e) {
            throw new OFXException("Failed to parse OFX file", e);
        } catch (IOException e) {
            throw new OFXException("An unexpected exception occurred", e);
        }
    }

    private Set<CategorizedTransaction> categorizeTransactions(Set<Transaction> transactions) {
        final Set<CategorizedTransaction> categorizedTransactions = new HashSet<>();

        for (Transaction transaction : transactions) {
            // try to automatically categorize the transaction
            CategorizedTransaction categorizedTransaction = transactionCategoryStore.getCategoryExact(transaction);
            if (categorizedTransaction == null) {
                // didn't work, fall back to prompting the user for a category
                categorizedTransaction = categorizeTransaction(transaction);
            }

            if (categorizedTransaction != null) {
                categorizedTransactions.add(categorizedTransaction);
                System.out.println(String.format("Categorized transaction %s as %s", transaction, categorizedTransaction.getCategory().getName()));
            } else {
                System.err.println("Failed to categorize transaction " + transaction.toString());
            }
        }

        return categorizedTransactions;
    }

    private static void exportTransactions(Set<CategorizedTransaction> categorizedTransactions) {
        // bucket transactions by category name
        final Map<Category, List<CategorizedTransaction>> buckets = new HashMap<>();
        for (CategorizedTransaction transaction : categorizedTransactions) {
            if (buckets.containsKey(transaction.getCategory())) {
                buckets.get(transaction.getCategory()).add(transaction);
            } else {
                buckets.put(transaction.getCategory(), new ArrayList<>(Collections.singletonList(transaction)));
            }
        }

        // sort transactions by date within their categories, and sum transactions for each category
        final Map<Category, Double> categorySums = new HashMap<>();
        for (Map.Entry<Category, List<CategorizedTransaction>> entry : buckets.entrySet()) {
            entry.getValue().sort(Comparator.comparing(Transaction::getDate));
            categorySums.put(entry.getKey(), entry.getValue()
                    .parallelStream()
                    .mapToDouble(Transaction::getAmount).sum());
        }

        for (Map.Entry<Category, Double> entry : categorySums.entrySet()) {
            System.out.println(String.format("%S: %S", entry.getKey().getName(), "$" + entry.getValue().toString()));
        }
    }

    private CategorizedTransaction categorizeTransaction(final Transaction transaction) {
        final Scanner sc = new Scanner(System.in);

        while (true) {
            // no exact match, let's try a fuzzy match
            final List<Category> potentialCategories = transactionCategoryStore.getCategoryFuzzy(transaction, 5);
            if (potentialCategories.isEmpty()) {
                final CategorizedTransaction categorizedTransaction = addNewCategory(transaction, sc);
                if (categorizedTransaction != null) {
                    return categorizedTransaction;
                }
                continue;
            }

            // a bunch of potential matches, select one
            System.out.println(String.format("Select an existing category for Transaction %s:", transaction));
            for (int i = 1; i < potentialCategories.size() + 1; i++) {
                System.out.println(String.format("%d) %s", i, potentialCategories.get(i - 1).getName()));
            }
            System.out.println("Or enter 'A' to create a new category");
            System.out.print(">");

            final String input = sc.nextLine();
            if ("A".equalsIgnoreCase(input)) {
                final CategorizedTransaction categorizedTransaction = addNewCategory(transaction, sc);
                if (categorizedTransaction != null) {
                    return categorizedTransaction;
                }
                continue;
            } else if (StringUtils.isNumeric(input)) {
                // existing category
                final int categoryNum = Integer.parseInt(input);
                if (categoryNum >= 1 && categoryNum <= potentialCategories.size()) {
                    final Category category = potentialCategories.get(categoryNum - 1);
                    return transactionCategoryStore.put(transaction, category);
                }
            }

            // bad input - try again
            System.err.println(String.format("%s is not a valid selection", input));
        }
    }

    private CategorizedTransaction addNewCategory(Transaction transaction, Scanner sc) {
        // no existing categories that pass the threshold test - prompt for a new one
        System.out.println(String.format("Please enter a new category for transaction %s", transaction));
        System.out.println(">");
        final String input = sc.nextLine();
        if (StringUtils.isNotBlank(input)) {
            return transactionCategoryStore.put(transaction, new Category(input));
        }
        return null;
    }

    public static void main(String[] args) {
        final Options options = new Options();
        options.addOption("f", "file", true, "the ofx file to parse");

        try {
            final CommandLineParser commandLineParser = new DefaultParser();
            final CommandLine commandLine = commandLineParser.parse(options, args);

            if (commandLine.hasOption("f")) {
                final File file  = new File(commandLine.getOptionValue("f"));
                final TransactionCategoryStore transactionCategoryStore = new TransactionCategoryStore(); // TODO: load categorizations from previous runs here
                final OfxCat ofxCat = new OfxCat(transactionCategoryStore);
                final Set<Transaction> transactions = ofxCat.parseOfxFile(file);
                final Set<CategorizedTransaction> categorizedTransactions = ofxCat.categorizeTransactions(transactions);

                // TODO: present the results in a pleasing manner
                exportTransactions(categorizedTransactions);

                // TODO: save state of transaction store to disk

            } else {
                System.err.println("Use the -f or --file parameter to specify a valid *.ofx file to parse");
            }
        } catch (ParseException e) {
            System.err.println("Failed to parse command line parameters");
        } catch (OFXException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }


}
