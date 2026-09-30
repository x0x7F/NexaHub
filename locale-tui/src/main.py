#!/usr/bin/env python3
"""Android Locale Manager TUI Application."""

import re
import sys
import asyncio
from typing import Callable
from pathlib import Path

# Add src to path for imports
sys.path.insert(0, str(Path(__file__).parent))

import click
from config import Config
from app import LocaleTuiApp
from services.xml_parser import StringsXmlParser
from services.translator import AITranslator
from models.entry import TranslationEntry


def load_config() -> Config:
    """Load configuration from file."""
    config_path = Path(__file__).parent.parent / "config.yml"

    if not config_path.exists():
        click.echo(f"Error: config file not found {config_path}", err=True)
        click.echo("Please create config.yml from the template.", err=True)
        sys.exit(1)

    try:
        config = Config.load(config_path)
    except Exception as e:
        click.echo(f"Error: failed to load config - {e}", err=True)
        sys.exit(1)

    # Validate configuration
    if not config.openai_api_key:
        click.echo("Warning: OPENAI_API_KEY is not set. AI translation will be unavailable.", err=True)

    return config


@click.group(invoke_without_command=True)
@click.pass_context
def cli(ctx):
    """Android Locale Manager - manage and translate Android string resources

    Run without arguments to launch the TUI; use subcommands for command-line operations.
    """
    if ctx.invoked_subcommand is None:
        # No command provided, launch TUI
        config = load_config()
        app = LocaleTuiApp(config)
        app.run()


@cli.command("test-connection")
def test_connection():
    """Test the AI service connection

    \b
    Example:
        locale-tui test-connection
    """
    config = load_config()

    if not config.openai_api_key:
        click.echo("Error: OPENAI_API_KEY is not set; can't test the connection.", err=True)
        sys.exit(1)

    click.echo("AI service configuration:")
    click.echo(f"  Base URL: {config.openai_base_url}")
    click.echo(f"  Model: {config.translation_model}")
    click.echo("Testing connection...")

    async def test_async():
        translator = AITranslator(config)
        return await translator.test_connection()

    try:
        content = asyncio.run(test_async())
        click.echo("✓ Connected")
        if content:
            click.echo(f"Response: {content}")
        else:
            click.echo("The response is empty, but the API returned a valid result.")
    except Exception as e:
        click.echo(f"✗ Connection failed: {e}", err=True)
        sys.exit(1)


@cli.command()
@click.argument("key")
@click.argument("value")
@click.option(
    "--module",
    "-m",
    default=None,
    help="Module name (defaults to the first module in the config file)",
)
@click.option("--skip-translate", is_flag=True, help="Skip automatic translation and only add the source language entry")
def add(key: str, value: str, module: str, skip_translate: bool):
    """Add a new language entry and translate it automatically

    \b
    Example:
        locale-tui add hello_world "Hello, World!"
        locale-tui add greeting "Welcome" -m app
        locale-tui add test_key "Test" --skip-translate
    """
    config = load_config()

    # Select module
    if module:
        selected_module = next((m for m in config.modules if m.name == module), None)
        if not selected_module:
            click.echo(f"Error: module not found '{module}'", err=True)
            click.echo(f"Available modules: {', '.join(m.name for m in config.modules)}", err=True)
            sys.exit(1)
    else:
        if not config.modules:
            click.echo("Error: no modules defined in the config file", err=True)
            sys.exit(1)
        selected_module = config.modules[0]

    click.echo(f"Using module: {selected_module.name}")

    # Get source language
    source_lang = config.get_source_language()
    if not source_lang:
        click.echo("Error: no source language configured", err=True)
        sys.exit(1)

    # Resolve res directory
    res_dir = config.project_root / selected_module.res_path
    if not res_dir.exists():
        click.echo(f"Error: resource directory does not exist {res_dir}", err=True)
        sys.exit(1)

    # Add entry to source language file
    source_file = res_dir / "values" / "strings.xml"
    click.echo(f"Adding entry to {source_file.relative_to(config.project_root)}...")

    try:
        StringsXmlParser.update_entry(source_file, key, value)
        click.echo(f"✓ Added entry: {key} = {value}")
    except Exception as e:
        click.echo(f"Error: failed to add entry - {e}", err=True)
        sys.exit(1)

    # Translate to other languages
    if not skip_translate:
        target_languages = [lang.code for lang in config.languages if not lang.is_source]

        if not target_languages:
            click.echo("No target languages configured; skipping translation.")
            return

        click.echo(f"Translating into {len(target_languages)} languages...")

        # Create entry for translation
        entry = TranslationEntry(key=key, translations={"values": value})

        async def translate_async():
            translator = AITranslator(config)

            async def translate_one(lang_code: str):
                lang_name = config.get_language_name(lang_code)

                try:
                    translations = await translator.translate_batch(
                        {key: value}, lang_name
                    )

                    if key in translations:
                        return lang_code, lang_name, translations[key], None
                    return lang_code, lang_name, None, "Translation failed (no result returned)"
                except Exception as e:
                    return lang_code, lang_name, None, str(e)

            tasks = [translate_one(lang_code) for lang_code in target_languages]
            results = await asyncio.gather(*tasks)

            for lang_code, lang_name, translated_value, error in results:
                click.echo(f"Translating to {lang_name}...", nl=False)

                if error:
                    click.echo(f" ✗ Error: {error}", err=True)
                    continue

                entry.set_translation(lang_code, translated_value)

                # Save to file
                target_file = res_dir / lang_code / "strings.xml"
                StringsXmlParser.update_entry(target_file, key, translated_value)

                click.echo(f" ✓ {translated_value}")

        asyncio.run(translate_async())
        click.echo("Done!")


@cli.command()
@click.argument("key")
@click.argument("value")
@click.option(
    "--lang",
    "-l",
    default=None,
    help="Language code (e.g. values, values-ja, values-ko-rKR); defaults to the source language",
)
@click.option(
    "--module",
    "-m",
    default=None,
    help="Module name (defaults to the first module in the config file)",
)
def set(key: str, value: str, lang: str, module: str):
    """Manually set the value of an entry for a given language

    \b
    Example:
        locale-tui set hello_world "안녕하세요, 세계!" -l values-ko-rKR
        locale-tui set greeting "Welcome" -l values
        locale-tui set test_key "テスト" -l values-ja -m app
    """
    config = load_config()

    # Select module
    if module:
        selected_module = next((m for m in config.modules if m.name == module), None)
        if not selected_module:
            click.echo(f"Error: module not found '{module}'", err=True)
            click.echo(f"Available modules: {', '.join(m.name for m in config.modules)}", err=True)
            sys.exit(1)
    else:
        if not config.modules:
            click.echo("Error: no modules defined in the config file", err=True)
            sys.exit(1)
        selected_module = config.modules[0]

    # Resolve language directory
    if lang is None:
        lang = "values"  # Default to source language

    # Resolve res directory
    res_dir = config.project_root / selected_module.res_path
    if not res_dir.exists():
        click.echo(f"Error: resource directory does not exist {res_dir}", err=True)
        sys.exit(1)

    # Target file
    target_file = res_dir / lang / "strings.xml"
    lang_name = config.get_language_name(lang) if lang != "values" else "source language"

    click.echo(f"Setting {lang_name} entry: {key} = {value}")
    click.echo(f"Target file: {target_file.relative_to(config.project_root)}")

    try:
        StringsXmlParser.update_entry(target_file, key, value)
        click.echo(f"✓ Set successfully")
    except Exception as e:
        click.echo(f"Error: failed to set - {e}", err=True)
        sys.exit(1)


@cli.command()
@click.option(
    "--module",
    "-m",
    default=None,
    help="Module name (defaults to the first module in the config file)",
)
def list_keys(module: str):
    """List the keys of all language entries

    \b
    Example:
        locale-tui list-keys
        locale-tui list-keys -m app
    """
    config = load_config()

    # Select module
    if module:
        selected_module = next((m for m in config.modules if m.name == module), None)
        if not selected_module:
            click.echo(f"Error: module not found '{module}'", err=True)
            sys.exit(1)
    else:
        if not config.modules:
            click.echo("Error: no modules defined in the config file", err=True)
            sys.exit(1)
        selected_module = config.modules[0]

    # Resolve res directory
    res_dir = config.project_root / selected_module.res_path
    source_file = res_dir / "values" / "strings.xml"

    if not source_file.exists():
        click.echo(f"Error: source file does not exist {source_file}", err=True)
        sys.exit(1)

    # Parse and display
    entries = StringsXmlParser.parse(source_file)

    click.echo(f"Module '{selected_module.name}' has {len(entries)} entries:")
    click.echo()

    for key in sorted(entries.keys()):
        value = entries[key]
        # Truncate long values
        if len(value) > 60:
            value = value[:57] + "..."
        click.echo(f"  {key:40} {value}")


_batch_options = [
    click.option(
        "--lang",
        "-l",
        "langs",
        multiple=True,
        help="Target language code, repeatable (e.g. -l values-ar -l values-ja); defaults to all non-source languages",
    ),
    click.option(
        "--module",
        "-m",
        "module_names",
        multiple=True,
        help="Module name, repeatable; defaults to all modules",
    ),
    click.option("--concurrency", "-c", default=8, show_default=True, help="Number of concurrent requests"),
    click.option("--retries", default=3, show_default=True, help="Maximum attempts per batch"),
    click.option("--dry-run", is_flag=True, help="Only count entries to translate, without calling the translator"),
]


def batch_options(func):
    for option in reversed(_batch_options):
        func = option(func)
    return func


def run_batch_translation(
    langs: tuple[str, ...],
    module_names: tuple[str, ...],
    concurrency: int,
    retries: int,
    dry_run: bool,
    select: Callable[[dict[str, str], dict[str, str]], dict[str, str]],
    action: str,
):
    """Translate entries picked by select(source, existing) for each module/language.

    Results are validated by AITranslator.translate_entries and written in place.
    """
    config = load_config()

    if not dry_run and not config.openai_api_key:
        click.echo("Error: OPENAI_API_KEY is not set; can't translate.", err=True)
        sys.exit(1)

    known_langs = [lang.code for lang in config.languages if not lang.is_source]
    for code in langs:
        if code not in known_langs:
            click.echo(f"Error: target language '{code}' not found in the config", err=True)
            click.echo(f"Available languages: {', '.join(known_langs)}", err=True)
            sys.exit(1)
    target_langs = list(langs) or known_langs

    modules = config.modules
    if module_names:
        modules = [m for m in config.modules if m.name in module_names]
        unknown = {*module_names} - {m.name for m in modules}
        if unknown:
            click.echo(f"Error: modules not found {', '.join(sorted(unknown))}", err=True)
            click.echo(f"Available modules: {', '.join(m.name for m in config.modules)}", err=True)
            sys.exit(1)

    all_failures: list[tuple[str, str, str, str]] = []

    async def run_all():
        translator = None if dry_run else AITranslator(config)
        for module in modules:
            res_dir = config.project_root / module.res_path
            source_file = res_dir / "values" / "strings.xml"
            if not source_file.exists():
                continue
            source = StringsXmlParser.parse(source_file)

            for lang_code in target_langs:
                lang_name = config.get_language_name(lang_code)
                target_file = res_dir / lang_code / "strings.xml"
                existing = StringsXmlParser.parse(target_file)
                selected = select(source, existing)
                if not selected:
                    continue

                label = f"[{module.name}/{lang_code}]"
                click.echo(f"{label} {action} {len(selected)} entries")
                if dry_run:
                    continue

                def on_progress(done: int, total: int, label=label):
                    click.echo(f"\r{label} {done}/{total}", nl=False)

                translations, failures = await translator.translate_entries(
                    selected,
                    lang_name,
                    concurrency=concurrency,
                    retries=retries,
                    progress_callback=on_progress,
                )
                click.echo()

                if translations:
                    StringsXmlParser.update_entries(target_file, translations)
                click.echo(
                    f"{label} ✓ wrote {len(translations)}/{len(selected)} entries -> "
                    f"{target_file.relative_to(config.project_root)}"
                )
                for key, error in failures.items():
                    all_failures.append((module.name, lang_code, key, error))

    asyncio.run(run_all())

    if all_failures:
        click.echo(f"\n{len(all_failures)} translations failed (not written; you can run again):", err=True)
        for module_name, lang_code, key, error in all_failures:
            click.echo(f"  {module_name}/{lang_code} {key}: {error}", err=True)
        sys.exit(1)

    click.echo("Done!")


@cli.command("translate-missing")
@batch_options
def translate_missing(**options):
    """Batch-translate missing entries (useful after adding a new language)

    \b
    Translations are checked to have the same placeholders and newlines as the source, and quotes are escaped automatically;
    entries that fail validation are retried, and if they still fail they aren't written and are listed at the end.

    \b
    Example:
        locale-tui translate-missing -l values-ar
        locale-tui translate-missing -m app --dry-run
    """
    run_batch_translation(
        **options,
        select=lambda source, existing: {
            k: v for k, v in source.items() if k not in existing
        },
        action="missing",
    )


@cli.command()
@click.option(
    "--match",
    "pattern",
    default=None,
    help="Regular expression (case-insensitive); retranslate entries whose English source matches",
)
@click.option("--key", "keys", multiple=True, help="Select entries by key, repeatable")
@batch_options
def retranslate(pattern: str | None, keys: tuple[str, ...], **options):
    """Retranslate existing entries (e.g. to unify terminology after updating the glossary)

    \b
    Only overwrites entries that already exist in the target language; use translate-missing for missing entries.
    Specify at least one of --match and --key; when both are given, their union is used.

    \b
    Example:
        locale-tui retranslate -l values-ar --match '\\bprompts?\\b' --dry-run
        locale-tui retranslate -l values-ja --key setting_page_title
    """
    if not pattern and not keys:
        click.echo("Error: specify at least --match or --key", err=True)
        sys.exit(1)
    try:
        regex = re.compile(pattern, re.IGNORECASE) if pattern else None
    except re.error as e:
        click.echo(f"Error: invalid regular expression - {e}", err=True)
        sys.exit(1)

    def select(source: dict[str, str], existing: dict[str, str]) -> dict[str, str]:
        return {
            k: v
            for k, v in source.items()
            if k in existing and (k in keys or (regex and regex.search(v)))
        }

    run_batch_translation(**options, select=select, action="retranslate")


def main():
    """Main entry point."""
    cli()


if __name__ == "__main__":
    main()
