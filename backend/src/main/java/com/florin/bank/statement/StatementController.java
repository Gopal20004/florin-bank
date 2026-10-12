package com.florin.bank.statement;

import com.florin.bank.common.PageResponse;
import com.florin.bank.transaction.LedgerEntry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class StatementController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final StatementService service;

    public StatementController(StatementService service) {
        this.service = service;
    }

    /** Activity across all of the customer's accounts, or one of them with {@code accountId}. */
    @GetMapping("/activity")
    public PageResponse<StatementLine> activity(
            @AuthenticationPrincipal Long customerId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) LedgerEntry.Direction direction,
            @RequestParam(required = false) @Size(max = 60, message = "Search for at most 60 characters") String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Pages start at 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Ask for at least 1 line")
            @Max(value = 100, message = "Ask for at most 100 lines per page") int size
    ) {
        return service.activity(customerId, new StatementQuery(accountId, from, to, direction, q), page, size);
    }

    @GetMapping("/accounts/{id}/transactions")
    public PageResponse<StatementLine> accountTransactions(
            @AuthenticationPrincipal Long customerId,
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) LedgerEntry.Direction direction,
            @RequestParam(required = false) @Size(max = 60, message = "Search for at most 60 characters") String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Pages start at 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Ask for at least 1 line")
            @Max(value = 100, message = "Ask for at most 100 lines per page") int size
    ) {
        return service.activity(customerId, new StatementQuery(id, from, to, direction, q), page, size);
    }

    /** Downloadable CSV statement for an inclusive date range. */
    @GetMapping("/accounts/{id}/statement")
    public ResponseEntity<String> statement(
            @AuthenticationPrincipal Long customerId,
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        StatementService.Csv csv = service.statement(customerId, id, from, to);
        return ResponseEntity.ok()
                .contentType(CSV)
                // The filename is plain ASCII, so the simple filename="..." form works in every browser.
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(csv.filename()).build().toString())
                .body(csv.content());
    }
}
