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
-o, --output           FORMAT      Output type: group, edn, json, table, verbose.
-t, --timer                        Print time elapsed.
-v, --version                      Prints version number.
-A, --add-ignore       SPECIMEN    Add specimen to ignore list.
-R, --remove-ignore    SPECIMEN    Remove specimen from ignore list.
-L, --list-ignored                 List all ignored specimens.
-X, --clear-ignored                Clear all ignored specimens.
-D, --restore-defaults             Restore default checks from GitHub.
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

### Managing ignored specimens

Proserunner allows you to ignore specific specimens (words or phrases) that you don't want flagged. This is useful for domain-specific terminology, proper nouns, or stylistic choices. These are stored in `~/.proserunner/ignore.edn`.

```bash
# Add a specimen to the ignore list
$ proserunner --add-ignore "hopefully"
Added to ignore list: hopefully
Ignored specimens: 1

# List all ignored specimens
$ proserunner --list-ignored
Ignored specimens:
   hopefully

# Remove a specimen from the ignore list
$ proserunner --remove-ignore "hopefully"
Removed from ignore list: hopefully
Ignored specimens: 0

# Clear all ignored specimens
$ proserunner --clear-ignored
Cleared all ignored specimens.
```

The ignore list is stored in `~/.proserunner/ignore.edn`. You can also edit this file directly:

```clojure
;; ~/.proserunner/ignore.edn
#{"hopefully"
  "FIX THIS"
  "my-custom-term"}
```

### Dialogue handling

By default, Proserunner ignores text within quotation marks (dialogue) when checking prose.

To include dialogue in your checks, use the `-d` or `--check-dialogue` flag:

```bash
$ proserunner -f document.md --check-dialogue
```

**Example:**

Given this text:

```markdown
She said "hopefully we'll arrive soon."
Hopefully this will be checked.
```

Default behavior (dialogue ignored):

- "hopefully" in the first line is ignored (it's in dialogue)
- "hopefully" in the second line is flagged (it's narrative)

With `--check-dialogue`:

- Both instances of "hopefully" are flagged

### Restoring default checks

If you've modified your default checks and want to restore them to their original state, use the `--restore-defaults` flag. For more information about managing baselines, see [docs/baseline-management.md](docs/baseline-management.md).

```bash
$ proserunner --restore-defaults

=== Restoring Default Checks ===

Backing up existing checks...
Created backup at: /home/user/.proserunner-backup-20251026-110441

Downloading fresh default checks...
Downloading default checks from:  https://github.com/jeff-bruemmer/proserunner-default-checks/archive/main.zip .
Preserved your config.edn
Preserved your ignore.edn

✓ Default checks restored successfully.

Your custom checks in ~/.proserunner/custom/ were not modified.
```

This command:

- Creates a timestamped backup of your current default checks in `~/.proserunner-backup-TIMESTAMP/`
- Downloads fresh default checks from GitHub
- Preserves your `config.edn` and `ignore.edn` files
- Leaves your custom checks in `~/.proserunner/custom/` untouched

## Adding custom checks

You can easily import custom checks from a local directory or GitHub repository using the `--add-checks` command. For complete details on creating checks, see [docs/checks.md](docs/checks.md).

### Import from local directory

```bash
proserunner --add-checks ~/my-project/style-checks
```

This will:

- Copy all `.edn` check files from the source directory to `~/.proserunner/custom/style-checks/`
- Automatically update `~/.proserunner/config.edn` to enable the new checks
- Display which checks were added

### Import from GitHub

```bash
proserunner --add-checks https://github.com/company/writing-style
```

This will:

- Clone the repository
- Extract all `.edn` check files
- Copy them to `~/.proserunner/custom/writing-style/`
- Update your config automatically

### Custom directory name

Use the `--name` flag to specify a custom directory name:

```bash
proserunner --add-checks ./checks --name my-company
```

The checks will be installed to `~/.proserunner/custom/my-company/` instead of using the source directory name.

### Check file format

Custom check files must be in EDN format. See [docs/checks.md](docs/checks.md) for details on available check types and formats.

## Custom editors

Proserunner supports custom editor types through a dynamic registry system. This allows you to extend Proserunner with your own check types without modifying the core codebase. For detailed information on all available editor types, see [docs/checks.md](docs/checks.md).

To create a custom editor, add a Clojure file to `~/.proserunner/custom/` that registers your editor function:

```clojure
;; ~/.proserunner/custom/my-editor.clj
(ns my-editor
  (:require [editors.registry :as registry]
            [proserunner.text :as text]))

(defn my-proofread
  "Custom editor function. Takes a line and check, returns updated line."
  [line check]
  (let [issue-found? (some-logic line check)]
    (if issue-found?
      (assoc line
             :issue? true
             :issues (conj (:issues line)
                          (text/->Issue (:file line)
                                       (:name check)
                                       (:kind check)
                                       "matched-text"
                                       42
                                       (:message check))))
      line)))

;; Register the editor
(registry/register-editor! "my-check-type" my-proofread)
```

Then create a check file in `~/.proserunner/default/` or `~/.proserunner/custom/` that uses your custom editor:

```clojure
;; ~/.proserunner/custom/my-check.edn
{:name "My Custom Check"
 :kind "my-check-type"
 :message "Custom issue found."
 :enabled true}
```

Editor functions must have the signature `[line check] -> line`, where:

- `line` is a `Line` record from `proserunner.text`
- `check` is a `Check` record from `proserunner.checks`
- Return the line unchanged if no issue, or with `:issue?` set to `true` and updated `:issues` vector

Proserunner includes 6 built-in editor types: `existence`, `case`, `recommender`, `case-recommender`, `repetition`, and `regex`. See [docs/checks.md](docs/checks.md) for details on these types.
