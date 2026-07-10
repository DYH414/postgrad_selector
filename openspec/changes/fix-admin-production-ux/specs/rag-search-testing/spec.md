## ADDED Requirements

### Requirement: RAG search testing page is reachable from production admin navigation
The RAG management page that provides search testing SHALL be reachable through the production admin menu route and through internal admin links.

#### Scenario: Administrator opens RAG search testing from the menu
- **WHEN** an authenticated administrator opens the RAG observability menu item
- **THEN** the page renders the RAG status and search-test panel without navigating to the 404 page

#### Scenario: Internal link targets RAG search testing
- **WHEN** another admin page links to the RAG observability page
- **THEN** the link targets the working production admin route for the RAG search-test page
