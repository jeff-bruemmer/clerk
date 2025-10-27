# Clerk

Clerk is a customizable prose linter.

```
$ clerk -f resources

 ~/clerk/resources/drivel.md
3:13	"quick as lightning" -> A tired phrase.
7:15	"software program" -> Pleonastic phrase.
11:49	"hopefully" -> Skunked term: consider rephrasing.
13:8	"Female booksalesman" -> Sexist or ridiculous term.
17:13	"FIX THIS" -> Draft litter.


 ~/clerk/resources/more/words.md
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

You can edit what Clerk checks for, and create your own checks for Clerk to run. You can use Clerk to vet documents against your style guide, or simply guard against your writing tics.

[Rationale](#rationale) | [Usage](#usage) | [Checks](docs/checks.md) | [Installation](docs/installation.md)

## Rationale

Clerk was inspired by [Proselint](https://github.com/amperser/proselint), an excellent command line tool. The bulk of Proselint's value lies in the research the developers conducted to curate a set of checks that would yield a useful signal for editing. Clerk builds on that work to make a faster, more customizable linter.

### Advantages

- **Extendable.** Clerk separates code and data. [Checks](#checks) are formatted as EDN, so you can toggle checks on or off, add new specimens of awkward prose to existing checks, or create custom checks to cover your own writing tics or issues specific to your domain or style guide. The dynamic editor registry allows you to add entirely new check types without modifying Clerk's source code.
- **Fast.** Clerk analyzes multiple lines of text in parallel using optimized chunked processing (up to 4.85x faster on typical documents). Clerk caches results and only checks lines of text that have changed since the last proofreading. Version-based check caching eliminates redundant downloads.
- **Flexible.** Clerk outputs results as a text table, but it can also format results as EDN or JSON.

### Tradeoffs

- **Mammoth binary**. Compiling the native image with Graal improves startup time and memory usage, but the binary is larger than the typical command-line tool.

Clerk includes many of Proselint's default checks, but Clerk pruned some checks that were either too noisy, or unlikely to ever get hits. Clerk also includes checks from other sources (and hard experience).

## Usage

Clerk outputs a table of issues with the prose. Clerk does not alter text. To proofread a document or directory:

```
$ clerk -f /path/to/thing-to-lint
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
$ clerk --file /path/to/drivel.md --output edn
```

Clerk accepts txt, md, org, and tex files.

### Excluding files and directories

When running Clerk on a directory, you can exclude specific files or directories from being checked:

**Using .clerkignore file:**

Create a `.clerkignore` file in the directory you're checking:

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
$ clerk -f documents/ --exclude "drafts/*"
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

Clerk supports several output formats via the `--output` flag:

- **group** (default): Groups issues by file in a concise format
- **table**: Displays results in a formatted table
- **edn**: Outputs results as EDN data
- **json**: Outputs results as JSON
- **verbose**: Detailed markdown format with numbered issues, guidance, and ignore commands

The verbose format is particularly useful for understanding issues in detail:

```bash
$ clerk -f document.md --output verbose
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

Clerk allows you to ignore specific specimens (words or phrases) that you don't want flagged. This is useful for domain-specific terminology, proper nouns, or stylistic choices.

```bash
# Add a specimen to the ignore list
$ clerk --add-ignore "hopefully"
Added to ignore list: hopefully
Ignored specimens: 1

# List all ignored specimens
$ clerk --list-ignored
Ignored specimens:
   hopefully

# Remove a specimen from the ignore list
$ clerk --remove-ignore "hopefully"
Removed from ignore list: hopefully
Ignored specimens: 0

# Clear all ignored specimens
$ clerk --clear-ignored
Cleared all ignored specimens.
```

The ignore list is stored in `~/.clerk/ignore.edn`. You can also edit this file directly:

```clojure
;; ~/.clerk/ignore.edn
#{"hopefully"
  "FIX THIS"
  "my-custom-term"}
```

### Dialogue handling

By default, Clerk ignores text within quotation marks (dialogue) when checking prose.

To include dialogue in your checks, use the `-d` or `--check-dialogue` flag:

```bash
$ clerk -f document.md --check-dialogue
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

If you've modified your default checks and want to restore them to their original state, use the `--restore-defaults` flag:

```bash
$ clerk --restore-defaults

=== Restoring Default Checks ===

Backing up existing checks...
Created backup at: /home/user/.clerk-backup-20251026-110441

Downloading fresh default checks...
Downloading default checks from:  https://github.com/jeff-bruemmer/clerk-default-checks/archive/main.zip .
Preserved your config.edn
Preserved your ignore.edn

✓ Default checks restored successfully.

Your custom checks in ~/.clerk/custom/ were not modified.
```

This command:
- Creates a timestamped backup of your current default checks in `~/.clerk-backup-TIMESTAMP/`
- Downloads fresh default checks from GitHub
- Preserves your `config.edn` and `ignore.edn` files
- Leaves your custom checks in `~/.clerk/custom/` untouched

## Custom editors

Clerk supports custom editor types through a dynamic registry system. This allows you to extend Clerk with your own check types without modifying the core codebase.

To create a custom editor, add a Clojure file to `~/.clerk/custom/` that registers your editor function:

```clojure
;; ~/.clerk/custom/my-editor.clj
(ns my-editor
  (:require [editors.registry :as registry]
            [clerk.text :as text]))

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

Then create a check file in `~/.clerk/default/` or `~/.clerk/custom/` that uses your custom editor:

```clojure
;; ~/.clerk/custom/my-check.edn
{:name "My Custom Check"
 :kind "my-check-type"
 :message "Custom issue found."
 :enabled true}
```

Editor functions must have the signature `[line check] -> line`, where:
- `line` is a `Line` record from `clerk.text`
- `check` is a `Check` record from `clerk.checks`
- Return the line unchanged if no issue, or with `:issue?` set to `true` and updated `:issues` vector

Clerk includes 6 built-in editor types: `existence`, `case`, `recommender`, `case-recommender`, `repetition`, and `regex`. See [docs/checks.md](docs/checks.md) for details on these types.

