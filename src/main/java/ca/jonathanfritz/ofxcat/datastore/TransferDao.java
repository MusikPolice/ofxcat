package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.datastore.utils.ResultSetDeserializer;
import ca.jonathanfritz.ofxcat.datastore.utils.SqlFunction;
import ca.jonathanfritz.ofxcat.datastore.utils.TransactionState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class TransferDao {

    private final Connection connection;
    private final SqlFunction<TransactionState, List<Transfer>> transferDeserializer;

    private static final Logger logger = LogManager.getLogger(TransferDao.class);

    @Inject
    public TransferDao(Connection connection, CategorizedTransactionDao categorizedTransactionDao) {
        this.connection = connection;
        this.transferDeserializer = new ResultSetDeserializer<>(((transactionState, transfers) -> {
            final ResultSet resultSet = transactionState.getResultSet();

            final long id = resultSet.getLong("id");

            final long sourceId = resultSet.getLong("source_id");
            final CategorizedTransaction source = categorizedTransactionDao.select(sourceId)
                    .orElseThrow(() -> new SQLException(String.format("CategorizedTransaction with id %d does not exist", sourceId)));

            final long sinkId = resultSet.getLong("sink_id");
            final CategorizedTransaction sink = categorizedTransactionDao.select(sinkId)
                    .orElseThrow(() -> new SQLException(String.format("CategorizedTransaction with id %d does not exist", sinkId)));

            transfers.add(new Transfer(id, source, sink));
        }));
    }

    public Optional<Transfer> select(long id) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return select(t, id);
        } catch (SQLException e) {
            logger.error("Failed to query Transfer with id {}", id, e);
            return Optional.empty();
        }
    }

    public Optional<Transfer> select(DatabaseTransaction t, long id) throws SQLException {
        logger.debug("Attempting to query Transfer with id {}", id);
        final String selectStatement = "SELECT * FROM Transfer WHERE id = ?";
        final List<Transfer> results = t.query(selectStatement, ps -> ps.setLong(1, id), transferDeserializer);
        return DatabaseTransaction.getFirstResult(results);
    }

    // for testing
    public Optional<Transfer> selectByFitId(String fitId) {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            logger.debug("Attempting to query Transfer with sink or source fitId {}", fitId);
            final String selectStatement = "SELECT t.* FROM Transfer AS t " +
                    "INNER JOIN CategorizedTransaction AS si ON t.sink_id = si.id " +
                    "INNER JOIN CategorizedTransaction AS so ON t.source_id = so.id " +
                    "WHERE si.fitId = ? OR so.fitId = ?";
            final List<Transfer> results = t.query(selectStatement, ps -> {
                ps.setString(1, fitId);
                ps.setString(2, fitId);
            }, transferDeserializer);
            return DatabaseTransaction.getFirstResult(results);
        } catch (SQLException e) {
            logger.error("Failed to query Transfer with sink or source fitId {}", fitId, e);
            return Optional.empty();
        }
    }

    public Optional<Transfer> insert(Transfer transferToInsert) {
        try(DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return insert(t, transferToInsert);
        } catch (SQLException e) {
            logger.error("Failed to insert Transfer {}", transferToInsert, e);
            return Optional.empty();
        }
    }

    public Optional<Transfer> insert(DatabaseTransaction t, Transfer transferToInsert) throws SQLException {
        logger.debug("Attempting to insert Transfer {}", transferToInsert);
        final String insertStatement = "INSERT INTO Transfer (source_id, sink_id) VALUES (?, ?);";
        return t.insert(insertStatement, ps -> {
            ps.setLong(1, transferToInsert.getSource().getId());
            ps.setLong(2, transferToInsert.getSink().getId());
        }, transferDeserializer);
    }

    public boolean isDuplicate(DatabaseTransaction t, Transfer transfer) throws SQLException {
        final String selectStatement = "SELECT * FROM Transfer WHERE source_id = ? AND sink_id = ?;";
        final List<Transfer> results = t.query(selectStatement, ps -> {
            ps.setLong(1, transfer.getSource().getId());
            ps.setLong(2, transfer.getSink().getId());
        }, transferDeserializer);
        return results.size() > 0;
    }
}
