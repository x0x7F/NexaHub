"""Integration tests for AI translation quality.

These tests call the real API and verify translation results meet expectations.
Run with: uv run pytest tests/test_translator.py -v
"""

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

from config import Config
from services.translator import AITranslator

CONFIG_PATH = Path(__file__).parent.parent / "config.yml"


@pytest.fixture(scope="module")
def translator():
    config = Config.load(CONFIG_PATH)
    return AITranslator(config)


@pytest.mark.asyncio
async def test_prompt_uses_glossary_term(translator):
    """'prompt' should use the Arabic glossary term, not a generic word."""
    result = await translator.translate_batch(
        {"system_prompt": "System Prompt", "prompt_template": "Prompt Template"},
        "Arabic",
    )
    assert "system_prompt" in result
    assert "prompt_template" in result
    assert "مطالبة" in result["system_prompt"], (
        f"Expected 'مطالبة' in system_prompt translation, got: {result['system_prompt']!r}"
    )


@pytest.mark.asyncio
async def test_placeholder_preserved(translator):
    """Android placeholders %1$s / %s must not be translated or modified."""
    result = await translator.translate_batch(
        {"welcome_user": "Welcome, %1$s!", "items_count": "You have %d items"},
        "Japanese",
    )
    assert "%1$s" in result["welcome_user"], (
        f"Placeholder %1$s missing in: {result['welcome_user']!r}"
    )
    assert "%d" in result["items_count"], (
        f"Placeholder %d missing in: {result['items_count']!r}"
    )


@pytest.mark.asyncio
async def test_brand_name_not_translated(translator):
    """Brand names 'OpenAI', 'Anthropic' and 'Google' must not be translated."""
    result = await translator.translate_batch(
        {"provider_openai": "OpenAI Provider", "provider_anthropic": "Anthropic Provider"},
        "Japanese",
    )
    assert "OpenAI" in result["provider_openai"], (
        f"Brand name 'OpenAI' should be preserved, got: {result['provider_openai']!r}"
    )
    assert "Anthropic" in result["provider_anthropic"], (
        f"Brand name 'Anthropic' should be preserved, got: {result['provider_anthropic']!r}"
    )


@pytest.mark.asyncio
async def test_token_not_over_translated(translator):
    """'Token' should stay in English in the LLM context, not be translated into an unrelated word."""
    result = await translator.translate_batch(
        {"token_usage": "Token Usage", "max_tokens": "Max Tokens"},
        "Japanese",
    )
    for key in ("token_usage", "max_tokens"):
        val = result[key].lower()
        assert "token" in val, (
            f"Expected 'token' to remain in {key!r} translation, got: {result[key]!r}"
        )


@pytest.mark.asyncio
async def test_keys_preserved_in_response(translator):
    """The keys of the returned JSON must match the input one to one."""
    entries = {
        "setting_title": "Settings",
        "cancel_button": "Cancel",
        "confirm_button": "Confirm",
    }
    result = await translator.translate_batch(entries, "Japanese")
    assert set(result.keys()) == set(entries.keys()), (
        f"Response keys mismatch. Expected {set(entries.keys())}, got {set(result.keys())}"
    )


@pytest.mark.asyncio
async def test_non_empty_translations(translator):
    """No key may be translated to an empty string."""
    entries = {"app_name": "NexaHub", "loading": "Loading..."}
    result = await translator.translate_batch(entries, "Japanese")
    for key, val in result.items():
        assert val and val.strip(), f"Translation for {key!r} is empty"
