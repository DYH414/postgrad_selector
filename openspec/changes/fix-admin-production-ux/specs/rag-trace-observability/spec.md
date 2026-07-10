## ADDED Requirements

### Requirement: RAG trace observation route is production reachable
The recent RAG trace UI SHALL be reachable from the production admin route used by the visible menu and by cross-page observability links.

#### Scenario: Administrator opens recent RAG traces from production route
- **WHEN** an authenticated administrator opens the RAG observability page from the production route
- **THEN** the page displays the recent RAG Trace section or an actionable empty state

#### Scenario: No RAG traces exist on production route
- **WHEN** the RAG observability page has no recent traces
- **THEN** the page explains that running a search test or AI chat will generate trace entries
