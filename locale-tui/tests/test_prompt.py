"""Unit tests for prompt building (offline)."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

from config import Config
from services.translator import AITranslator


def test_glossary_injected_into_prompt():
    config = Config.load(Path(__file__).parent.parent / "config.yml")
    translator = AITranslator(config)
    entries = {"system_prompt": "System Prompt"}

    arabic = translator.build_prompt(entries, "Arabic")
    assert "Glossary for Arabic" in arabic
    assert "مطالبة" in arabic

    # Languages without a glossary don't get a glossary section
    assert "Glossary" not in translator.build_prompt(entries, "Japanese")
