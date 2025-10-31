# P R O S E R U N N E R

Proserunner is a customizable prose linter.

```
$ proserunner -f resources

 ~/proserunner/resources/drivel.md
3:13	"quick as lightning" -> A tired phrase.
7:15	"software program" -> Pleonastic phrase.
11:49	"hopefully" -> Skunked term: consider rephrasing.
13:8	"Female booksalesman" -> Sexist or ridiculous term.
17:13	"FIX THIS" -> Draft litter.


 ~/proserunner/resources/more/words.md
3:9	"I would argue" -> Omit this phrase.
3:55	", so to speak" -> Omit this phrase.
5:11	"Squelch" -> Prefer: quell.
7:10	"Perchance" -> Modernize.
9:31	"abolishment" -> Prefer: abolition.
9:69	"ironical" -> Prefer: ironic.
11:20	"the the" -> Consecutive word repetition.
11:113	"punctuation punctuation" -> Consecutive word repetition.
13:17	"intense apathy" -> Oxymoron.
15:29	"zero sum game" -> Prefer: zero-sum game.
17:8	"Per your request" -> Jargon.
19:36	"no brainer" -> Avoid corporate-speak.
```

You can edit what Proserunner checks for, and create your own checks for Proserunner to run. You can use Proserunner to vet documents against your style guide, or simply guard against your writing tics.

[Rationale](#rationale) | [Usage](#usage) | [Checks](docs/checks.md) | [Installation](docs/installation.md) | [Benchmarks](docs/benchmarks.md) | [Building](docs/building.md)

## Rationale

Proserunner was inspired by [Proselint](https://github.com/amperser/proselint), an excellent command line tool. The bulk of Proselint's value lies in the research the developers conducted to curate a set of checks that would yield a useful signal for editing. Proserunner builds on that work to make a faster, more customizable linter.

### Advantages

- **Extendable.** Proserunner separates code and data. [Checks](docs/checks.md) are formatted as EDN, so you can toggle checks on or off, add new specimens of awkward prose to existing checks, or create custom checks to cover your own writing tics or issues specific to your domain or style guide. The dynamic editor registry allows you to add entirely new check types without modifying Proserunner's source code.
- **Fast.** Proserunner analyzes multiple lines of text in parallel using optimized chunked processing (up to 4.85x faster on typical documents). Proserunner caches results and only checks lines of text that have changed since the last proofreading. Version-based check caching eliminates redundant downloads. See [docs/benchmarks.md](docs/benchmarks.md) for performance details.
- **Flexible.** Proserunner outputs results as a text table, but it can also format results as EDN or JSON. See [output formats](#output-formats) for details.

### Tradeoffs

- **Mammoth binary**. Compiling the native image with Graal improves startup time and memory usage, but the binary is larger than the typical command-line tool. See [docs/building.md](docs/building.md) for build details.

Proserunner includes many of Proselint's default checks, but Proserunner pruned some checks that were either too noisy, or unlikely to ever get hits. Proserunner also includes checks from other sources (and hard experience).

## Usage

Proserunner outputs a table of issues with the prose. Proserunner does not alter text. To proofread a document or directory:

```
$ proserunner -f /path/to/thing-to-lint
```

CLI options:

```
-b, --code-blocks                  Include code blocks.
-C, --checks                       List enabled checks.
-c, --config           CONFIG      Set temporary configuration file.
-d, --check-dialogue               Include dialogue in checks.
-e, --exclude          PATTERN     Exclude files/dirs matching pattern (glob).
-f, --file             FILE        File or dir to proofread.
-h, --help                         Prints this help message.
-i, --ignore           IGNORE      EDN file listing specimens to ignore.
-n, --no-cache                     Don't use cached results.
-s, --skip-ignore                  Skip all ignore lists (global and project).
-o, --output           FORMAT      Output type: group, edn, json, table, verbose.
-t, --timer                        Print time elapsed.
-v, --version                      Prints version number.
-A, --add-ignore       SPECIMEN    Add specimen to ignore list.
-R, --remove-ignore    SPECIMEN    Remove specimen from ignore list.
-L, --list-ignored                 List all ignored specimens.
-X, --clear-ignored                Clear all ignored specimens.
-D, --restore-defaults             Restore default checks from GitHub.
-I, --init-project                 Initialize .proserunner/ directory in current directory.
-G, --global                       Add to global config (~/.proserunner/).
-P, --project                      Add to project config (.proserunner/).
```

Here's an example command to export results to EDN:

```
$ proserunner --file /path/to/drivel.md --output edn
```

Proserunner accepts txt, md, org, and tex files. For installation instructions, see [docs/installation.md](docs/installation.md). To build from source, see [docs/building.md](docs/building.md).

### Excluding files and directories

When running Proserunner on a directory, you can exclude specific files or directories from being checked:

**Using .proserunnerignore file:**

Create a `.proserunnerignore` file in the directory you're checking:

```
# Ignore draft files and directories
drafts/
*.draft.md
temp.md

# Ignore build outputs
build/
dist/
```

**Using --exclude flag:**

```bash
$ proserunner -f documents/ --exclude "drafts/*"
```

**Pattern syntax:**

- `*` matches any characters except `/`
- `**` matches any characters including `/`
- `filename.md` matches that specific file
- `*.draft.md` matches any file ending in `.draft.md`
- `drafts/` matches the drafts directory and all its contents
- `**/temp/*` matches temp directories at any level

Lines starting with `#` are comments and blank lines are ignored.

### Output formats

Proserunner supports several output formats via the `--output` flag:

- **group** (default): Groups issues by file in a concise format
- **table**: Displays results in a formatted table
- **edn**: Outputs results as EDN data
- **json**: Outputs results as JSON
- **verbose**: Detailed markdown format with numbered issues, guidance, and ignore commands

The verbose format is particularly useful for understanding issues in detail:

```bash
$ proserunner -f document.md --output verbose
```

This will show:

- Numbered list of all issues
- File path with line and column numbers
- The problematic text
- For recommender checks, shows what to replace it with
- Detailed guidance on how to fix each type of issue
- Summary statistics
- Ready-to-run commands to ignore specific issues

### Project Configuration

Proserunner supports project-level configuration that can be versioned alongside your code. Initialize a project configuration with:

```bash
$ proserunner --init-project
```

This creates a `.proserunner/` directory with:

- **config.edn** - Project configuration (check sources, ignores, modes)
- **checks/** - Directory for project-specific custom checks

You can configure check sources, ignore lists, and how project config merges with global config. The `.proserunner/` directory can be committed to version control, allowing teams to share prose guidelines.

For complete details on project configuration options, see [docs/checks.md#project-configuration](docs/checks.md#project-configuration).

### Managing ignored specimens

Ignore specific words or phrases that you don't want flagged:

```bash
# Add to ignore list
$ proserunner --add-ignore "hopefully"

# List ignored specimens
$ proserunner --list-ignored

# Remove from ignore list
$ proserunner --remove-ignore "hopefully"

# Clear all ignored specimens
$ proserunner --clear-ignored

# Temporarily skip all ignore lists
$ proserunner -f document.md --skip-ignore
```

Proserunner supports both global (`~/.proserunner/ignore.edn`) and project-level (`.proserunner/config.edn`) ignore lists. Use `--global` or `--project` flags to specify scope. The `--skip-ignore` flag temporarily bypasses all ignore lists without modifying them, useful for seeing all issues including ignored ones.

For complete details, see [docs/checks.md#ignoring-specimens](docs/checks.md#ignoring-specimens).

### Dialogue handling

By default, Proserunner ignores text within quotation marks to avoid flagging intentional dialogue choices. Use `--check-dialogue` to include dialogue in checks:

```bash
$ proserunner -f document.md --check-dialogue
```

For more details and examples, see [docs/checks.md#dialogue-handling](docs/checks.md#dialogue-handling).

### Restoring default checks

If you've modified your default checks and want to restore them to their original state:

```bash
$ proserunner --restore-defaults
```

This creates a timestamped backup, downloads fresh default checks from GitHub, and preserves your custom checks and configuration files.

For complete details, see [docs/checks.md#restoring-defaults](docs/checks.md#restoring-defaults).

## Adding custom checks

Import custom checks from a local directory:

```bash
# Add to global config
proserunner --add-checks ~/my-checks --global

# Add to project config
proserunner --add-checks ~/my-checks --project

# Specify custom directory name
proserunner --add-checks ./checks --name company-style
```

This automatically copies `.edn` check files and updates your configuration. For complete details on adding checks, creating custom checks, and check file formats, see [docs/checks.md#adding-checks](docs/checks.md#adding-checks).

## Custom editors

Proserunner supports custom editor types through a dynamic registry system. This allows you to extend Proserunner with entirely new check types without modifying the core codebase.

Proserunner includes 6 built-in editor types: `existence`, `case`, `recommender`, `case-recommender`, `repetition`, and `regex`.

For details on creating custom editors and using built-in types, see [docs/checks.md#custom-editors](docs/checks.md#custom-editors).
