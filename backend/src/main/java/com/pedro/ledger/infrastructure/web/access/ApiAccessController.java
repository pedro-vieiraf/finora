package com.pedro.ledger.infrastructure.web.access;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApiAccessController {

  @GetMapping("/auth/session")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void session() {}
}
