package com.pedro.ledger.infrastructure.openfinance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

record OpenFinanceSyncPlan(List<OpenFinanceInitialImport.AccountPosting> newPostings, int pendingPairs) {

  static OpenFinanceSyncPlan prepare(
      List<OpenFinanceInitialImport.AccountImport> snapshots, Set<ExternalPosting> imported
  ) {
    var pending = new HashSet<ExternalPosting>();
    int pairs = 0;
    for (var candidate : OpenFinanceInitialImport.possibleInternalTransfers(snapshots)) {
      var debit = ExternalPosting.of(candidate.debit());
      var credit = ExternalPosting.of(candidate.credit());
      if (imported.contains(debit) && imported.contains(credit)) {
        continue;
      }
      pending.add(debit);
      pending.add(credit);
      pairs++;
    }

    var newPostings = snapshots.stream()
        .flatMap(snapshot -> snapshot.postings().stream().map(posting ->
            new OpenFinanceInitialImport.AccountPosting(snapshot.selection(), posting)))
        .filter(posting -> !imported.contains(ExternalPosting.of(posting)))
        .filter(posting -> !pending.contains(ExternalPosting.of(posting)))
        .toList();
    return new OpenFinanceSyncPlan(newPostings, pairs);
  }

  record ExternalPosting(UUID accountId, String transactionId) {

    static ExternalPosting of(OpenFinanceInitialImport.AccountPosting posting) {
      return new ExternalPosting(posting.account().accountId(), posting.posting().externalId());
    }
  }
}
