package com.pedro.ledger.infrastructure.openfinance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class OpenFinanceTransferReview {

  private OpenFinanceTransferReview() {}

  static List<OpenFinanceInitialImport.TransferCandidate> resolve(
      List<OpenFinanceInitialImport.TransferCandidate> candidates, List<Decision> decisions
  ) {
    var reviewed = new HashSet<OpenFinanceInitialImport.TransferCandidate>();
    var usedPostings = new HashSet<OpenFinanceInitialImport.AccountPosting>();
    var transfers = new ArrayList<OpenFinanceInitialImport.TransferCandidate>();
    for (var decision : decisions) {
      var matching = candidates.stream().filter(decision::matches).toList();
      if (matching.size() != 1 || !reviewed.add(matching.getFirst())) {
        throw new IllegalStateException("A revisão não identifica um único par novo. Rode a prévia novamente.");
      }
      var candidate = matching.getFirst();
      if (decision.transfer()) {
        if (!usedPostings.add(candidate.debit()) || !usedPostings.add(candidate.credit())) {
          throw new IllegalStateException("O mesmo lançamento não pode integrar duas transferências.");
        }
        transfers.add(candidate);
      }
    }
    if (reviewed.size() != candidates.size()) {
      throw new IllegalStateException("Revise todos os pares antes de confirmar a importação.");
    }
    return List.copyOf(transfers);
  }

  record Decision(boolean transfer, String debitAccount, String creditAccount, LocalDate date, BigDecimal amount) {

    static Decision parse(String option) {
      boolean transfer = option.startsWith("--transfer=");
      String prefix = transfer ? "--transfer=" : "--separate=";
      if (!option.startsWith(prefix)) {
        throw new IllegalArgumentException("Use --transfer= ou --separate= para revisar um par.");
      }
      String[] fields = option.substring(prefix.length()).split(",", -1);
      if (fields.length != 4) {
        throw new IllegalArgumentException("Informe origem,destino,dataDaSaída,valor para cada revisão.");
      }
      return new Decision(transfer, fields[0], fields[1], LocalDate.parse(fields[2]),
          new BigDecimal(fields[3]));
    }

    boolean matches(OpenFinanceInitialImport.TransferCandidate candidate) {
      var debit = candidate.debit();
      return debitAccount.equals(debit.account().name())
          && creditAccount.equals(candidate.credit().account().name())
          && date.equals(debit.posting().date().atOffset(ZoneOffset.UTC).toLocalDate())
          && amount.compareTo(debit.posting().amount().amount()) == 0;
    }
  }
}
