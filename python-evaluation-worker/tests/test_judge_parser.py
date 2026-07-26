"""
§6.2 Judge strict parser 测试。
"""

import pytest
from app.judge.parser import JudgeOutput, JudgeParseError, parse_judge_output


# ── 正常解析 ──────────────────────────────────────────────────────────────────

def test_parse_valid_passed():
    raw = '{"faithfulness":0.9,"answerRelevance":0.85,"citationCorrectness":null,"passed":true,"reasons":[],"unsupportedClaims":[]}'
    out = parse_judge_output(raw)
    assert out.passed is True
    assert out.faithfulness == pytest.approx(0.9)
    assert out.answer_relevance == pytest.approx(0.85)
    assert out.citation_correctness is None


def test_parse_valid_failed():
    raw = '{"faithfulness":0.5,"answerRelevance":0.6,"citationCorrectness":0.4,"passed":false,"reasons":["low faithfulness"],"unsupportedClaims":["claim1"]}'
    out = parse_judge_output(raw)
    assert out.passed is False
    assert out.unsupported_claims == ["claim1"]


def test_parse_extracts_from_markdown_fence():
    raw = '```json\n{"faithfulness":0.9,"answerRelevance":0.9,"citationCorrectness":null,"passed":true,"reasons":[],"unsupportedClaims":[]}\n```'
    out = parse_judge_output(raw)
    assert out.passed is True


def test_parse_extracts_from_surrounding_text():
    raw = 'Here is my evaluation:\n{"faithfulness":0.9,"answerRelevance":0.9,"citationCorrectness":null,"passed":true,"reasons":[],"unsupportedClaims":[]}\nDone.'
    out = parse_judge_output(raw)
    assert out.passed is True


# ── 边界值 ────────────────────────────────────────────────────────────────────

def test_boundary_exactly_0_80_passes():
    raw = '{"faithfulness":0.80,"answerRelevance":0.80,"citationCorrectness":null,"passed":true,"reasons":[],"unsupportedClaims":[]}'
    out = parse_judge_output(raw)
    assert out.passed is True


def test_boundary_citation_0_80_ok():
    raw = '{"faithfulness":0.9,"answerRelevance":0.9,"citationCorrectness":0.80,"passed":true,"reasons":[],"unsupportedClaims":[]}'
    out = parse_judge_output(raw)
    assert out.passed is True


# ── 一致性校验：passed 与分数不一致应拒绝 ──────────────────────────────────────

def test_passed_true_but_low_faithfulness_rejected():
    raw = '{"faithfulness":0.7,"answerRelevance":0.9,"citationCorrectness":null,"passed":true,"reasons":[],"unsupportedClaims":[]}'
    with pytest.raises(JudgeParseError):
        parse_judge_output(raw)


def test_passed_true_with_unsupported_claims_rejected():
    raw = '{"faithfulness":0.9,"answerRelevance":0.9,"citationCorrectness":null,"passed":true,"reasons":[],"unsupportedClaims":["bad claim"]}'
    with pytest.raises(JudgeParseError):
        parse_judge_output(raw)


def test_passed_true_low_citation_rejected():
    raw = '{"faithfulness":0.9,"answerRelevance":0.9,"citationCorrectness":0.5,"passed":true,"reasons":[],"unsupportedClaims":[]}'
    with pytest.raises(JudgeParseError):
        parse_judge_output(raw)


# ── 格式错误 ──────────────────────────────────────────────────────────────────

def test_no_json_raises():
    with pytest.raises(JudgeParseError):
        parse_judge_output("This is not JSON at all.")


def test_invalid_json_raises():
    with pytest.raises(JudgeParseError):
        parse_judge_output('{"faithfulness": bad}')


def test_missing_field_raises():
    # 缺 unsupportedClaims
    raw = '{"faithfulness":0.9,"answerRelevance":0.9,"citationCorrectness":null,"passed":false,"reasons":[]}'
    with pytest.raises(JudgeParseError):
        parse_judge_output(raw)


def test_extra_field_raises():
    raw = '{"faithfulness":0.9,"answerRelevance":0.9,"citationCorrectness":null,"passed":false,"reasons":[],"unsupportedClaims":[],"extra":"field"}'
    with pytest.raises(JudgeParseError):
        parse_judge_output(raw)


def test_score_out_of_range_raises():
    raw = '{"faithfulness":1.5,"answerRelevance":0.9,"citationCorrectness":null,"passed":false,"reasons":[],"unsupportedClaims":[]}'
    with pytest.raises(JudgeParseError):
        parse_judge_output(raw)
