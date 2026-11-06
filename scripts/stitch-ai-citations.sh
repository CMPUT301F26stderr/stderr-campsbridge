#!/usr/bin/env bash
# Formats the team's selected conversations as one Wiki page.
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
wiki_root="${1:?Usage: bash scripts/stitch-ai-citations.sh WIKI_CHECKOUT}"
page="$wiki_root/AI-Usage.md"

python3 - "$repo_root/citations" "$page" <<'PY'
from pathlib import Path
import re
import sys

root, page = map(Path, sys.argv[1:])
message = re.compile(
    r'^#{2,3} ((?:User|Codex|Agent message)(?: — [^\n]+)?|Assistant message \d+)\s*$',
    re.M,
)
turn = re.compile(r'^## Turn [^\n]+\n?', re.M)

with page.open('w') as output:
    for member in sorted(path for path in root.iterdir() if path.is_dir()):
        transcripts = sorted(member.rglob('*.md'))
        if not transcripts:
            continue
        output.write(f'# {member.name}\n\n')
        for number, transcript in enumerate(transcripts, 1):
            text = turn.sub('', transcript.read_text())
            matches = list(message.finditer(text))
            if not matches:
                continue
            output.write(f'## Conversation {number}\n\n')
            metadata = text[:matches[0].start()].splitlines()
            fields = {}
            for line in metadata:
                key, separator, value = line.partition(':')
                if separator:
                    fields[key] = value.strip()
            provider = fields.get('Provider', 'Not recorded')
            tool = fields.get('Tool', 'Not recorded')
            recorded = fields.get('Recorded models', 'Not recorded')
            if tool == 'Codex':
                model = 'GPT 6.1 SOL'
                if recorded.lower() != 'gpt-6.1-sol':
                    model += ' (team supplied)'
            elif member.name == 'Guy1000' and tool == 'Not recorded':
                provider, tool = 'Google (team supplied)', 'Gemini (team supplied)'
                model = 'Gemini 3.8 (team supplied)'
            else:
                model = recorded
            date_line = next((line for line in metadata if line.startswith('Dates')), '')
            dates = re.findall(r'\d{4}-\d{2}-\d{2}', date_line)
            date = 'Not recorded'
            if dates:
                date = dates[0] if dates[0] == dates[-1] else f'{dates[0]} to {dates[-1]}'
            output.write(f'> Provider: {provider}  \n> Tool: {tool}  \n')
            output.write(f'> Recorded models: {model}  \n> Date: {date}\n\n')
            dialogue = []
            for index, match in enumerate(matches):
                end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
                body = text[match.end():end].strip('\n')
                speaker = 'User' if match.group(1).startswith('User') else 'LLM'
                dialogue.append(f'[{speaker}]\n{body}')
            body = '\n\n'.join(dialogue)
            longest = max((len(run) for run in re.findall(r'`+', body)), default=0)
            fence = '`' * max(3, longest + 1)
            output.write(f'{fence}text\n{body}\n{fence}\n\n')
PY
