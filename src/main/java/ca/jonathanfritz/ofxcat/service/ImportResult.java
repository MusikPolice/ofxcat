package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import java.util.List;

public record ImportResult(List<CategorizedTransaction> transactions, int duplicateCount) {}
