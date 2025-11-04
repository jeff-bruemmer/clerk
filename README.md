# P R O S E R U N N E R

A fast, customizable prose linter.

```
$ proserunner -f resources/drivel.md

3:13	"quick as lightning" -> A tired phrase.
7:15	"software program" -> Pleonastic phrase.
11:49	"hopefully" -> Skunked term: consider rephrasing.
13:8	"Female booksalesman" -> Sexist or ridiculous term.
```

## Quick start

```bash
# Check a file or directory
proserunner -f document.md

# Check with output format
proserunner -f document.md --output json

# Initialize project configuration
proserunner --init-project
```

## Features

- **Customizable** - Add your own checks as EDN data files. Toggle checks on/off without modifying source code.
- **Fast** - Parallel processing with intelligent caching.
- **Flexible** - Multiple output formats: table, JSON, EDN, verbose.

Inspired by [Proselint](https://github.com/amperser/proselint). Checks stored as data in [separate repo](https://github.com/jeff-bruemmer/proserunner-default-checks).

## Default checks

| Name               | Kind        | Explanation                                                                                     |
|--------------------|-------------|-------------------------------------------------------------------------------------------------|
| Annotations        | Case        | Typical markers used to signal problem sites in the text.                                       |
| Archaisms          | Existence   | Outmoded word or phrase.                                                                        |
| Clichés            | Existence   | An overused phrase or opinion that betrays a lack of original thought.                          |
| Compression        | Recommender | Compressing common phrases. From Style: Toward Clarity and Grace by Joseph M. Williams.         |
| Corporate-speak    | Existence   | Words and phrases that make you sound like an automaton.                                        |
| Hedging            | Existence   | Say, or say not. There is no hedging.                                                           |
| Jargon             | Existence   | Phrases infected with bureaucracy.                                                              |
| Needless-variant   | Recommender | Prefer the more common term.                                                                    |
| Non-words          | Recommender | Identifies sequences of letters masquerading as words, and suggests an actual word.             |
| Not the negative.  | Recommender | Prefer the word to the negation of the word's opposite.                                         |
| Overused-adverbs   | Existence   | Use of adverbs that are weak or redundant. Consider using a stronger verb instead.              |
| Oxymorons          | Existence   | Avoid contradictory terms (that aren't funny).                                                  |
| Phrasal adjectives | Recommender | Hyphenate phrasal adjectives.                                                                   |
| Pompous-diction    | Recommender | Pompous diction: use simpler words. From Style: Toward Clarity and Grace by Joseph M. Williams. |
| Redundancies       | Existence   | Avoid phrases that say the same thing more than once.                                           |
| Repetition         | Repetition  | Catches consecutive repetition of words, like _the the_.                                        |
| Sexism             | Existence   | Sexist or ridiculous terms (like _mail person_ instead of _mail carrier_).                      |
| Skunked-terms      | Existence   | Words with controversial correct usage that are best avoided.                                   |

## Documentation

- [Installation](docs/installation.md) - Build and install native binary
- [Usage](docs/checks.md) - Configuring checks, custom checks, ignoring specimens
- [Benchmarks](docs/benchmarks.md) - Performance testing

## Basic usage

```bash
# Check files
proserunner -f /path/to/file

# Ignore specific words
proserunner --add-ignore "hopefully"

# Check quoted text
proserunner -f document.md --quoted-text

# Exclude files and directories (can specify multiple times)
proserunner -f docs/ --exclude "drafts/*"
proserunner -f docs/ --exclude "drafts/*" --exclude "*.backup" --exclude "temp.md"

# Exclude multiple patterns with wildcards
proserunner -f . --exclude "node_modules/*" --exclude "build/*" --exclude "*.log"
```

See [full checks documentation](docs/checks.md).
