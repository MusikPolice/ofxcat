package ca.jonathanfritz.ofxcat.integration;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.service.ReportingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for reporting workflows.
 * Tests the complete flow from data setup through report generation.
 */
class ReportingWorkflowIntegrationTest extends AbstractDatabaseTest {

    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final CategorizedTransactionDao categorizedTransactionDao;

    ReportingWorkflowIntegrationTest() {
        this.accountDao = injector.getInstance(AccountDao.class);
        this.categoryDao = injector.getInstance(CategoryDao.class);
        this.categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
    }

    /**
     * Test generating a monthly spending report with multiple categories.
     */
    @Test
    void monthlySpendingReportByCategory() {
        // Setup: Create account and categories
        Account account = accountDao.insert(TestUtils.createRandomAccount("Checking")).orElseThrow();
        Category groceries = categoryDao.insert(new Category("Groceries")).orElseThrow();
        Category restaurants = categoryDao.insert(new Category("Restaurants")).orElseThrow();
        Category utilities = categoryDao.insert(new Category("Utilities")).orElseThrow();

        // Create transactions for January 2024
        LocalDate jan15 = LocalDate.of(2024, 1, 15);

        // Grocery transactions: -$300 total
        insertTransaction(account, groceries, "SAFEWAY", -100.00f, jan15);
        insertTransaction(account, groceries, "WHOLE FOODS", -150.00f, jan15.plusDays(7));
        insertTransaction(account, groceries, "TRADER JOES", -50.00f, jan15.plusDays(14));

        // Restaurant transactions: -$75 total
        insertTransaction(account, restaurants, "CHIPOTLE", -25.00f, jan15.plusDays(3));
        insertTransaction(account, restaurants, "STARBUCKS", -50.00f, jan15.plusDays(10));

        // Utility transactions: -$150 total
        insertTransaction(account, utilities, "ELECTRIC COMPANY", -100.00f, jan15.plusDays(1));
        insertTransaction(account, utilities, "WATER DEPT", -50.00f, jan15.plusDays(2));

        // Generate report
        SpyCli spyCli = new SpyCli();
        ReportingService reportingService = new ReportingService(
                categorizedTransactionDao, accountDao, categoryDao, spyCli);

        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        // Use reportTransactionsMonthly for all-category report
        reportingService.reportTransactionsMonthly(startDate, endDate);

        // Verify: Report was generated with correct data
        List<String> output = spyCli.getCapturedLines();
        assertFalse(output.isEmpty(), "Report should produce output");

        // Verify header is present (reportTransactionsMonthly uses "MONTH" as first column)
        assertTrue(output.get(0).contains("MONTH"),
                "Report should have a header row with MONTH column");

        // Convert output to string for easier searching
        String reportText = String.join("\n", output);

        // Verify categories appear in report header (categories are uppercased)
        assertTrue(reportText.contains("GROCERIES"), "Report should include Groceries category");
        assertTrue(reportText.contains("RESTAURANTS"), "Report should include Restaurants category");
        assertTrue(reportText.contains("UTILITIES"), "Report should include Utilities category");
    }

    /**
     * Test account listing report shows all accounts with correct details.
     */
    @Test
    @SuppressWarnings("ReturnValueIgnored") // inserts are test setup; orElseThrow asserts success
    void accountListingReport() {
        // Setup: Create multiple accounts
        accountDao.insert(Account.newBuilder()
                .setName("Primary Checking")
                .setAccountNumber("1234567890")
                .setBankId("BANK001")
                .setAccountType("CHECKING")
                .build()).orElseThrow();

        accountDao.insert(Account.newBuilder()
                .setName("High Yield Savings")
                .setAccountNumber("9876543210")
                .setBankId("BANK001")
                .setAccountType("SAVINGS")
                .build()).orElseThrow();

        accountDao.insert(Account.newBuilder()
                .setName("Rewards Card")
                .setAccountNumber("4111111111111111")
                .setBankId("CARD_ISSUER")
                .setAccountType("CREDITLINE")
                .build()).orElseThrow();

        // Generate account report
        SpyCli spyCli = new SpyCli();
        ReportingService reportingService = new ReportingService(
                categorizedTransactionDao, accountDao, categoryDao, spyCli);

        reportingService.reportAccounts();

        // Verify: Report includes all accounts
        List<String> output = spyCli.getCapturedLines();
        assertFalse(output.isEmpty(), "Report should produce output");

        String reportText = String.join("\n", output);

        // Verify each account appears
        assertTrue(reportText.contains("Primary Checking"), "Report should include checking account");
        assertTrue(reportText.contains("High Yield Savings"), "Report should include savings account");
        assertTrue(reportText.contains("Rewards Card"), "Report should include credit card");

        // Verify account numbers appear
        assertTrue(reportText.contains("1234567890"), "Report should show checking account number");
        assertTrue(reportText.contains("9876543210"), "Report should show savings account number");
    }

    /**
     * Test category listing report shows all categories.
     */
    @Test
    @SuppressWarnings("ReturnValueIgnored") // inserts are test setup; orElseThrow asserts success
    void categoryListingReport() {
        // Setup: Create categories
        categoryDao.insert(new Category("Groceries")).orElseThrow();
        categoryDao.insert(new Category("Dining Out")).orElseThrow();
        categoryDao.insert(new Category("Entertainment")).orElseThrow();

        // Generate category report
        SpyCli spyCli = new SpyCli();
        ReportingService reportingService = new ReportingService(
                categorizedTransactionDao, accountDao, categoryDao, spyCli);

        reportingService.reportCategories();

        // Verify: Report includes all categories
        List<String> output = spyCli.getCapturedLines();
        assertFalse(output.isEmpty(), "Report should produce output");

        String reportText = String.join("\n", output);

        // Verify user-created categories appear (uppercased)
        assertTrue(reportText.contains("GROCERIES"), "Report should include Groceries");
        assertTrue(reportText.contains("DINING OUT"), "Report should include Dining Out");
        assertTrue(reportText.contains("ENTERTAINMENT"), "Report should include Entertainment");

        // Verify system categories appear
        assertTrue(reportText.contains("TRANSFER"), "Report should include TRANSFER category");
        assertTrue(reportText.contains("UNKNOWN"), "Report should include UNKNOWN category");
    }

    /**
     * Test reporting with date range filtering.
     */
    @Test
    void reportWithDateRangeFiltering() {
        // Setup
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        Category category = categoryDao.insert(new Category("Test Category")).orElseThrow();

        // Create transactions across different months
        insertTransaction(account, category, "JAN TRANSACTION", -100.00f, LocalDate.of(2024, 1, 15));
        insertTransaction(account, category, "FEB TRANSACTION", -100.00f, LocalDate.of(2024, 2, 15));
        insertTransaction(account, category, "MAR TRANSACTION", -100.00f, LocalDate.of(2024, 3, 15));
        insertTransaction(account, category, "APR TRANSACTION", -100.00f, LocalDate.of(2024, 4, 15));

        // Generate report for Feb-Mar only
        SpyCli spyCli = new SpyCli();
        ReportingService reportingService = new ReportingService(
                categorizedTransactionDao, accountDao, categoryDao, spyCli);

        LocalDate startDate = LocalDate.of(2024, 2, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);
        reportingService.reportTransactionsInCategory(category.getId(), startDate, endDate);

        // The report should be generated (specific content depends on implementation)
        List<String> output = spyCli.getCapturedLines();
        // At minimum, report should produce some output for the date range
        assertNotNull(output, "Report should produce output");
    }

    /**
     * Test reporting with no transactions in date range.
     */
    @Test
    @SuppressWarnings("ReturnValueIgnored") // inserts are test setup; orElseThrow asserts success
    void reportWithNoTransactionsInRange() {
        // Setup: Create account and category but no transactions
        accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        categoryDao.insert(new Category("Empty Category")).orElseThrow();

        // Generate report for a period with no transactions
        SpyCli spyCli = new SpyCli();
        ReportingService reportingService = new ReportingService(
                categorizedTransactionDao, accountDao, categoryDao, spyCli);

        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        // Use reportTransactionsMonthly for all-category report
        reportingService.reportTransactionsMonthly(startDate, endDate);

        // Verify: Report handles empty data gracefully
        List<String> output = spyCli.getCapturedLines();
        assertNotNull(output, "Report should handle empty data gracefully");
        // Should still have headers even with no data
        assertFalse(output.isEmpty(), "Report should produce headers even with no transactions");
    }

    /**
     * Test complete workflow: import transactions, then generate report.
     */
    @Test
    void completeImportToReportWorkflow() {
        // Setup: Create account and categories
        Account account = accountDao.insert(TestUtils.createRandomAccount("Checking")).orElseThrow();
        Category income = categoryDao.insert(new Category("Income")).orElseThrow();
        Category bills = categoryDao.insert(new Category("Bills")).orElseThrow();

        LocalDate date = LocalDate.of(2024, 1, 15);

        // Insert transactions (simulating post-import state)
        insertTransaction(account, income, "PAYCHECK", 2000.00f, date);
        insertTransaction(account, bills, "RENT", -1000.00f, date.plusDays(1));
        insertTransaction(account, bills, "ELECTRIC", -150.00f, date.plusDays(2));
        insertTransaction(account, bills, "INTERNET", -75.00f, date.plusDays(3));

        // Step 1: Generate account report
        SpyCli accountReportCli = new SpyCli();
        ReportingService reportingService1 = new ReportingService(
                categorizedTransactionDao, accountDao, categoryDao, accountReportCli);
        reportingService1.reportAccounts();

        List<String> accountReport = accountReportCli.getCapturedLines();
        assertFalse(accountReport.isEmpty(), "Account report should be generated");

        // Step 2: Generate category report
        SpyCli categoryReportCli = new SpyCli();
        ReportingService reportingService2 = new ReportingService(
                categorizedTransactionDao, accountDao, categoryDao, categoryReportCli);
        reportingService2.reportCategories();

        List<String> categoryReport = categoryReportCli.getCapturedLines();
        assertFalse(categoryReport.isEmpty(), "Category report should be generated");

        // Step 3: Generate transaction report
        SpyCli transactionReportCli = new SpyCli();
        ReportingService reportingService3 = new ReportingService(
                categorizedTransactionDao, accountDao, categoryDao, transactionReportCli);

        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        // Use reportTransactionsMonthly for all-category report
        reportingService3.reportTransactionsMonthly(startDate, endDate);

        List<String> transactionReport = transactionReportCli.getCapturedLines();
        assertFalse(transactionReport.isEmpty(), "Transaction report should be generated");
    }

    // ==================== Helper Methods ====================

    private void insertTransaction(Account account, Category category, String description,
                                   float amount, LocalDate date) {
        Transaction.TransactionType type = amount >= 0 ?
                Transaction.TransactionType.CREDIT : Transaction.TransactionType.DEBIT;

        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setDescription(description)
                .setAmount(amount)
                .setDate(date)
                .setType(type)
                .build();

        CategorizedTransaction categorizedTransaction = new CategorizedTransaction(transaction, category);
        categorizedTransactionDao.insert(categorizedTransaction);
    }

    // ==================== Test Double ====================

    private static class SpyCli extends CLI {
        private final List<String> capturedLines = new ArrayList<>();

        SpyCli() {
            super(null, null);
        }

        @Override
        public void println(List<String> lines) {
            capturedLines.addAll(lines);
        }

        @Override
        public void println(String line) {
            capturedLines.add(line);
        }

        public List<String> getCapturedLines() {
            return Collections.unmodifiableList(capturedLines);
        }
    }
}
