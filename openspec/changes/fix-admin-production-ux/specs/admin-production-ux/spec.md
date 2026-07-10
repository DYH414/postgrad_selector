## ADDED Requirements

### Requirement: Admin landing page is product-oriented
The system SHALL show a postgrad-oriented admin landing page after login instead of the default framework introduction page.

#### Scenario: Administrator opens the default index
- **WHEN** an authenticated administrator opens `/index`
- **THEN** the page displays postgrad product operations content, including shortcuts or status cards for school data, data health, AI observability, RAG observability, and the user client

#### Scenario: Landing page avoids framework marketing
- **WHEN** the administrator views the first screen after login
- **THEN** the primary content does not present the default RuoYi framework introduction as the main product experience

### Requirement: Admin observability navigation uses production routes
The system SHALL use the same AI/RAG observability routes in menus, buttons, breadcrumbs, and documentation-visible navigation.

#### Scenario: AI observability links to RAG observability
- **WHEN** an administrator clicks "查看 RAG 观测台" from the AI observability page
- **THEN** the browser navigates to the working RAG observability page and does not show the 404 page

#### Scenario: RAG observability is directly reachable
- **WHEN** an authenticated administrator opens the documented RAG observability route
- **THEN** the RAG observability page renders successfully with status, search-test, and trace sections

### Requirement: Admin user-client entry is useful
The system SHALL make the admin "用户端" entry lead to a usable user-client experience or bridge page instead of an empty admin content area.

#### Scenario: Administrator opens user-client entry
- **WHEN** an administrator opens the "用户端" menu item
- **THEN** the page displays a clear way to open or preview the user recommendation experience

#### Scenario: User-client target is unavailable
- **WHEN** the user-client target URL is not configured or unavailable
- **THEN** the admin page displays a clear explanation and local-development guidance rather than a blank page

### Requirement: Observability pages have medium-width layouts
The system SHALL keep AI and RAG observability KPI cards readable on medium-width desktop viewports.

#### Scenario: AI observability is opened in a medium-width browser
- **WHEN** the AI observability page is viewed at a medium desktop width
- **THEN** KPI cards use a multi-column layout where possible instead of collapsing directly to one card per row

#### Scenario: RAG observability is opened in a medium-width browser
- **WHEN** the RAG observability page is viewed at a medium desktop width
- **THEN** the status cards and primary diagnostic panels remain easy to scan without excessive vertical scrolling

### Requirement: Empty states guide operators
The system SHALL display actionable empty states for admin observability pages.

#### Scenario: AI observability has no calls
- **WHEN** no AI calls have been recorded
- **THEN** the page explains that no AI calls exist yet and points the operator toward generating an AI recommendation or chat event

#### Scenario: RAG observability has no traces
- **WHEN** no RAG traces have been recorded
- **THEN** the page explains that traces will appear after a search test or AI chat uses RAG

### Requirement: Admin UX smoke paths are covered
The system SHALL include smoke verification for key admin production UX paths.

#### Scenario: Smoke test covers product landing and observability
- **WHEN** the smoke suite runs against the admin frontend
- **THEN** it verifies `/index`, AI observability, RAG observability, and the user-client entry do not render a 404 or blank primary content area
