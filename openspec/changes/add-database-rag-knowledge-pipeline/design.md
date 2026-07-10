## Context

The current RAG path indexes classpath Markdown files with recursive fixed-size splitting and generic metadata. It already uses SiliconFlow BGE embeddings, Qdrant via LangChain4j, an optional SiliconFlow reranker, administrator search-test APIs, and in-memory traces. The business database already stores the facts users care about: schools, colleges, programs, score lines, plans, admission results, data sources, and data quality.

The new pipeline should be small enough for this project but shaped like production RAG: deterministic source generation, semantic chunking, rich metadata, filtered retrieval, evaluation, and citation evidence.

## Goals / Non-Goals

**Goals:**
- Generate first-wave RAG knowledge from MySQL facts.
- Chunk by business entity and section, not arbitrary character boundaries.
- Preserve metadata needed for filtering, evaluation, observability, and citations.
- Keep the existing embedding, Qdrant, and rerank stack.
- Add tests before implementation for chunking, metadata filters, evaluation, and citation mapping.

**Non-Goals:**
- Build a full document CMS.
- Add a new vector database or a new LLM provider.
- Implement hybrid sparse+dense retrieval in this first pass.
- Replace all recommendation logic with RAG; RAG should support explanation and factual grounding first.

## Decisions

1. Use database facts as the primary generated source.
   - Rationale: DB rows are already normalized, source-linked, and closer to recommendation needs than hand-written Markdown.
   - Alternative considered: generate only Markdown and feed it through the current splitter. This is easier to inspect but loses section-level metadata precision.

2. Represent chunks as an internal `StructuredRagChunk` before turning them into LangChain4j `TextSegment`.
   - Rationale: tests can validate chunk ids, text, metadata, and citations without requiring embedding or Qdrant.
   - Alternative considered: create `TextSegment` directly in the mapper/service. That couples business generation to vector indexing too early.

3. Add a dedicated export mapper and builder.
   - Rationale: existing recommendation mappers are optimized for recommendation lists, while RAG export needs source/quality/citation payloads.
   - Alternative considered: reuse `AiDatabaseToolMapper` queries. This risks broadening a tool-specific query into indexing infrastructure.

4. Use metadata filters at the search-test boundary first.
   - Rationale: administrator validation is the fastest way to verify filtered retrieval before wiring every recommendation path.
   - Alternative considered: put filters only inside recommendation generation. That would make debugging much harder.

5. Make citations plain DTOs built from selected chunks.
   - Rationale: report generation can consume the citation DTO without knowing Qdrant internals.
   - Alternative considered: embed citation text directly in prompts only. That helps generation but does not provide auditable report data.

## Risks / Trade-offs

- Sparse or inconsistent database facts -> generated chunks may expose missing data. Mitigation: explicitly mark missing facts and include data quality metadata.
- LangChain4j filter API version differences -> metadata filtering may need a small adapter. Mitigation: isolate filter construction in one helper and test it.
- Evaluation quality depends on representative cases. Mitigation: ship a small seed set and make the runner accept more cases later.
- Reindexing generated and manual docs together can clear existing Qdrant content. Mitigation: keep deterministic ids and expose clear rebuild endpoints/status.

## Migration Plan

1. Add structured chunk DTOs, builders, mapper query, and tests.
2. Add index path for generated chunks while retaining classpath Markdown indexing.
3. Add search-test metadata filter DTO fields and response metadata.
4. Add lightweight evaluation service and seed evaluation cases.
5. Add citation DTO/mapper for recommendation report integration.
6. Run focused unit tests and OpenSpec validation before broader app testing.
