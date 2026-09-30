# locale-tui

TUI tool for managing translations of Android language files.

## Features

- Module selection screen
- Translation table showing all languages
- AI translation of missing entries
- Dead entry detection and filtering
- Search filtering
- Edit and delete entries

## Shortcuts

| Shortcut | Action |
|--------|------|
| `Enter` | Select/edit |
| `t` | AI-translate missing entries |
| `d` | Toggle dead entry filter |
| `m` | Toggle missing filter |
| `/` | Focus the search box |
| `Delete` | Delete entry |
| `s` | Save changes |
| `r` | Refresh data |
| `Escape` | Back/cancel |
| `q` | Quit |

## Running

```bash
cd locale-tui
uv run python src/main.py
```

## Testing the AI connection

```bash
cd locale-tui
uv run python src/main.py test-connection
```

## Configuration

Edit `config.yml` to configure the module list, language list and translation settings.

Configure the OpenAI API in the `.env` file:

```
OPENAI_API_KEY=your_api_key
OPENAI_BASE_URL=https://api.openai.com/v1
```
