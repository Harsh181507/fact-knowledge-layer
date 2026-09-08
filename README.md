# Fact Knowledge Layer

A prototype that ingests PDFs, extracts grounded facts, and reasons about how
facts relate across documents (corroborate / contradict / reconcile). Built
for the Superjoin VIT 2026 Engineering Intern assignment.

## Setup and Run Instructions

**Requirements:** Java 17+, Maven (or use the included `./mvnw`), a free
Google Gemini API key.

1. Get a **free** API key (no credit card required) from
   [Google AI Studio](https://aistudio.google.com/apikey), then set it as an
   environment variable:
   ```bash
   export GEMINI_API_KEY=...
   ```
2. Run the app:
   ```bash
   ./mvnw spring-boot:run
   ```
3. Open **http://localhost:8080** — a single-page UI to upload PDFs and
   browse extracted facts and relationships.

No database setup is required: the project defaults to a file-based H2
database (`./data/fkl`) created automatically on first run. To use Postgres
instead, edit `src/main/resources/application.properties` — swap the
commented Postgres block in for the H2 block — and point it at a running
Postgres instance.

### API (if you'd rather not use the UI)

| Method | Path                              | Purpose                                   |
|--------|------------------------------------|--------------------------------------------|
| POST   | `/api/documents` (multipart `file`)| Upload a PDF; extracts + compares facts    |
| GET    | `/api/documents`                   | List documents with their facts            |
| GET    | `/api/documents/{id}`              | One document + its facts                   |
| GET    | `/api/facts?needsReview=true`      | All facts, optionally only flagged ones    |
| GET    | `/api/facts/{id}`                  | One fact with its evidence                 |
| GET    | `/api/relationships?type=CONTRADICTS` | All relationships, optionally filtered  |

```bash
curl -F "file=@report.pdf" http://localhost:8080/api/documents
```

## Video Demo

*[Add your demo link here before submitting — a PDF being processed, plus
the four required cases below, shown via the UI or curl.]*

## Approach

**Pipeline:** `PdfTextExtractionService` (Apache PDFBox) pulls text per page
with `[page N]` markers → `FactExtractionService` chunks long documents and
prompts the LLM to return facts as JSON (`subject`, `metric`, `value`,
`unit`, `factType`, `confidence`, a verbatim `evidenceQuote`, and an open
`attributes` map for whatever context the document actually supports, e.g.
period/scope/currency) → each fact is verified by checking its evidence
quote is an actual substring of the extracted text, so hallucinated evidence
gets dropped before it's ever stored → `FactComparisonService` then looks for
other facts sharing the same normalized `metric` key across *other*
documents and asks the LLM to classify the pair as `CORROBORATES`,
`CONTRADICTS`, `RECONCILED_BY_CONTEXT`, or `UNRELATED`, with a short
explanation, which is persisted as a `FactRelationship`.

**Why an open attribute map instead of a fixed schema:** the assignment
explicitly says the documents should dictate what counts as a fact, and that
the solution shouldn't hard-code document-specific schemas. Storing
`subject/metric/value/unit` as first-class columns (for querying and
blocking) plus a JSON `attributes` bag for everything else (period, region,
role, conditions, as-of date, ...) lets new kinds of facts show up without a
migration or code change — this is also the "schema evolves dynamically"
brownie point.

**Why blocking by metric key before comparing:** comparing every fact
against every other fact is O(n²) LLM calls, which stops scaling once you
have more than a couple of PDFs. Grouping candidates by a normalized metric
label first (a real indexed query, not filtering in memory) keeps the number
of LLM comparison calls roughly linear in the number of facts, which is what
makes "many PDFs in the same knowledge layer" workable.

**AI tools used:** Google's Gemini API (free tier) is the extraction/reasoning
engine itself — not just a coding aid — for both fact extraction and pairwise
comparison, since the assignment explicitly allows using an LLM as part of
the system. The specific model is a single config property
(`llm.api.model` in `application.properties`) rather than hard-coded,
because Google's free-tier model lineup and per-model quotas changed
multiple times during development — this makes swapping models a one-line
config change instead of a code change. It's used with
`responseMimeType: application/json` so it returns structured JSON directly,
without needing prompt tricks to avoid markdown fences. Claude (Anthropic)
was used as a coding assistant to help write and review this codebase.

**Trade-offs made for a prototype:**
- Processing is synchronous (upload blocks until extraction + comparison
  finish) rather than a background job with polling — simpler to demo, but
  won't scale to very large PDFs without a timeout/async rework.
- Comparison blocking uses exact-match on a normalized metric string, not
  embeddings — simpler and fully generalizable (no hard-coded document
  rules), but it will miss cross-document facts about the same real-world
  metric if the LLM labels them very differently between documents.
- No OCR: scanned/image-only PDFs are rejected with a clear error rather
  than silently producing nothing.

## Showing the Four Required Cases

With the starter PDFs loaded (upload them one at a time through the UI or
curl), the four cases can be found by filtering `GET /api/relationships`:

1. **Corroborated fact** — `?type=CORROBORATES`: the same fact stated
   differently in two documents.
2. **Genuine contradiction** — `?type=CONTRADICTS`.
3. **Contradiction explained by context** — `?type=RECONCILED_BY_CONTEXT`
   (e.g. different fiscal periods, different scopes, or a status that
   changed between the two documents' dates).
4. **Extraction/reasoning failure** — `GET /api/facts?needsReview=true`
   returns facts the model itself flagged as low-confidence (ambiguous
   wording, unclear referent, cut-off text) instead of silently trusting
   them; each carries a `reviewNote` explaining why. Failed comparisons and
   unreadable/scanned PDFs are also surfaced (`SourceDocument.status =
   FAILED` with a `processingNote`) rather than swallowed.

*(Fill in the actual fact/relationship IDs you find with the starter PDFs
here before recording the demo video.)*

## Limitations and Next Steps

- **No incremental re-comparison trigger for edited facts** — if a fact were
  ever corrected, existing relationships involving it wouldn't be
  recomputed.
- **Blocking is exact-match on metric label** — a lightweight embedding
  similarity step (or a second LLM pass to cluster metric labels) would
  catch more true cross-document matches than string equality does.
- **Synchronous processing** — should move to an async job + status polling
  for large PDFs or many concurrent uploads.
- **No OCR** — scanned PDFs currently fail extraction outright; adding
  Tesseract (or a vision-capable LLM pass on page images) would handle them.
- **Comparison is currently metric-scoped only** — it doesn't yet attempt
  entity resolution (e.g. recognizing "123 Main St" and "123 Main Street,
  Suite 400" as the same address) beyond what the LLM does implicitly when
  given both quotes; a dedicated entity-resolution step would make address/
  name matching more reliable.

## Additional Notes

The `attributes` JSON bag on `Fact` and the metric-key blocking in
`FactComparisonService` are the two design choices this system leans on most
to generalize beyond the three starter PDFs to arbitrary future ones.
