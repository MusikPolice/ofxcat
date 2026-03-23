package ca.jonathanfritz.ofxcat.service;

import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

/**
 * Produces vendor spending reports from a date range. Two output modes are supported:
 *
 * <ul>
 *   <li>{@link #getVendorSpend} returns the raw {@link VendorGroup} list for terminal rendering by
 *       the caller.
 *   <li>{@link #writeToFile} writes a two-column XLSX table (vendor name + total).
 * </ul>
 *
 * <p>Note: fastexcel does not support chart generation. To add a pie chart, open the XLSX in Excel
 * or LibreOffice, select the VENDOR and TOTAL columns, and insert a pie chart manually.
 */
public class VendorSpendingService {

    private static final String XLSX_CURRENCY_FORMAT = "$#,##0.00";

    private final VendorGroupingService vendorGroupingService;

    @Inject
    public VendorSpendingService(VendorGroupingService vendorGroupingService) {
        this.vendorGroupingService = vendorGroupingService;
    }

    /**
     * Returns vendor groups for the given date range, sorted by total amount ascending (largest
     * absolute spends first, since spend amounts are negative).
     *
     * @param startDate start of the date range, inclusive
     * @param endDate end of the date range, inclusive
     * @return vendor groups sorted by total amount ascending
     */
    public List<VendorGroup> getVendorSpend(LocalDate startDate, LocalDate endDate) {
        return vendorGroupingService.groupByVendor(startDate, endDate);
    }

    /**
     * Writes a vendor spending report to an XLSX file. Creates the parent directory if it does not
     * exist.
     *
     * @param groups the vendor groups to write (typically from {@link #getVendorSpend})
     * @param outputFile the path at which to write the XLSX file
     * @return the path of the written file
     * @throws IOException if the file cannot be written
     */
    public Path writeToFile(List<VendorGroup> groups, Path outputFile) throws IOException {
        final Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (Workbook wb = new Workbook(Files.newOutputStream(outputFile), "ofxcat", "1.0");
                Worksheet ws = wb.newWorksheet("Vendors")) {
            ws.freezePane(0, 1);

            // header row
            ws.value(0, 0, "VENDOR");
            ws.style(0, 0).bold().set();
            ws.value(0, 1, "TRANSACTIONS");
            ws.style(0, 1).bold().set();
            ws.value(0, 2, "TOTAL");
            ws.style(0, 2).bold().set();

            // data rows: one per vendor group
            int row = 1;
            for (VendorGroup group : groups) {
                ws.value(row, 0, group.displayName());
                ws.value(row, 1, group.transactionCount());
                ws.value(row, 2, group.totalAmount());
                ws.style(row, 2).format(XLSX_CURRENCY_FORMAT).set();
                row++;
            }

            ws.width(0, 30);
            ws.width(1, 14);
            ws.width(2, 14);
        }

        return outputFile;
    }
}
