# Checks

[Overview](#overview) | [Editor types](#editor-types) | [Default checks](#default-checks) | [Managing checks](#managing-checks) | [Ignoring specimens](#ignoring-specimens) | [Project configuration](#project-configuration) | [Adding checks](#adding-checks) | [Custom checks](#custom-checks) | [Custom editors](#custom-editors) | [Restoring defaults](#restoring-defaults)

## Overview

Checks are stored as data in EDN format. Proserunner's default set of checks are in the [default checks repository](https://github.com/jeff-bruemmer/proserunner-default-checks). You can list enabled checks with the `--checks` flag:

```bash
$ proserunner --checks
```
You can enable, modify, or disable any check by commenting out entire checks or individual specimens.

## Editor types

Proserunner has different editors, each for a different type of check. You can disable any check, and can extend Existence, Case, and Recommender checks.

| **Editor**       | **Description**                          | **Customizable?** |
| :--------------- | :--------------------------------------- | :---------------- |
| Existence        | Does the line contain the specimen?      | Yes               |
| Case             | Same as Existence, only case sensitive.  | Yes               |
| Recommender      | Avoid X; prefer Y.                       | Yes               |
| Case-recommender | Same as Recommender, only case sensitive | Yes               |
| Repetition       | Consecutive word repetition.             | No                |
| Regex            | Run raw regular expressions.             | Yes               |

### Dialogue handling

By default, Proserunner **ignores text within quotation marks** (dialogue) when checking prose. This prevents false positives from:

- Intentionally informal language in dialogue
- Character-appropriate grammar choices
- Deliberate repetition or word choices for effect

You can control this behavior with the `--check-dialogue` flag:

```bash
# Default: ignore dialogue
$ proserunner -f document.md

# Check dialogue too
$ proserunner -f document.md --check-dialogue
```

#### How dialogue detection works

Proserunner detects text within single or double quotes as dialogue. Apostrophes and possessives are not treated as dialogue.

#### Example

Given this text:

```markdown
She said "hopefully we'll arrive soon."
Hopefully this will be checked.
```

**Default behavior** (`proserunner -f document.md`):

- First line: "hopefully" ignored (it's in dialogue)
- Second line: "hopefully" flagged (it's narrative)

**With dialogue checking** (`proserunner -f document.md --check-dialogue`):

- Both lines: "hopefully" flagged in both dialogue and narrative

## Default checks

When Proserunner first runs, it will download the [default checks](https://github.com/jeff-bruemmer/proserunner-default-checks), and store them in the `.proserunner` directory in your home directory:

```
~/.proserunner/
├── config.edn
├── ignore.edn
├── custom/
│   └── example.edn
└── default/
    ├── annotations.edn
    ├── archaisms.edn
    ├── cliches.edn
    ├── compression.edn
    ├── corporate-speak.edn
    └── ... more checks
```

Once Proserunner has downloaded the default checks, consider versioning your `.proserunner` directory.

## Managing checks

### Enabling and disabling checks

You can disable entire checks by commenting out lines in the `:files` vector in `~/.proserunner/config.edn`. Here we've disabled the `jargon` and `oxymorons` checks.

```clojure
;; Comment out a check to disable it.
;; E.g., ;; "redundancies"
;; You can also use semi-colons to disable specimens within a check.
{:checks
 [{:name "default",
   :directory "default",
   :files
   ["cliches"
    "redundancies"
    "skunked-terms"
    "sexism"
    "non-words"
    "hedging"
    "archaisms"
;;    "jargon"
    "corporate-speak"
    "annotations"
    "needless-variants"
    "repetition"
;;    "oxymorons"
    "phrasal-adjectives"
    "not-the-negative"
    "pompous-diction"
    "compression"]}
   ;; Place custom checks in :files vector.
  {:name "Custom checks"
   :directory "custom"
   :files ["example"]}]}
```

### Modifying checks

You can add new specimens to existing checks, or disable individual checks by commenting out lines within the checks, e.g., if you only want to disable some oxymorons. For Existence and Case checks, you can add examples of text for Proserunner to search for to a check's specimens vector. See `~/.proserunner/custom/example.edn` for an example Existence check.

## Ignoring specimens

Sometimes you want to keep a check enabled but ignore specific words or phrases. This is useful for domain-specific terminology, proper nouns, or stylistic choices that would otherwise trigger checks.

### Global vs project scope

Proserunner supports two levels of ignoring:

- **Global ignore list** (`~/.proserunner/ignore.edn`) - Applies to all your documents
- **Project ignore list** (`.proserunner/config.edn`) - Applies only to a specific project

By default, commands operate on project scope if you're in a project directory, otherwise global scope.

### Command-line management

```bash
# Add to project ignore list (if in project) or global (if not)
$ proserunner --add-ignore "hopefully"

# Force global scope
$ proserunner --add-ignore "hopefully" --global

# Force project scope
$ proserunner --add-ignore "hopefully" --project

# View ignored specimens
$ proserunner --list-ignored

# Remove from ignore list
$ proserunner --remove-ignore "hopefully"

# Clear all ignored specimens
$ proserunner --clear-ignored
```

### Manual editing

#### Global ignore list

The global ignore list is stored in `~/.proserunner/ignore.edn`:

```clojure
;; Put specimens for Proserunner to ignore between the braces.
;; Use semicolons to comment out lines.
#{"hopefully"
  "FIX THIS"
  "my-custom-term"}
```

#### Project ignore list

Project-specific ignores are stored in `.proserunner/config.edn`:

```clojure
{:check-sources ["default" "checks"]
 :ignore #{"project-specific-term" "TODO"}
 :ignore-mode :extend  ; or :replace
 :config-mode :merged}
```

### How ignoring works

- Ignored specimens are matched **exactly** (case-sensitive)
- Ignoring applies to **all checks** across your entire document
- Project ignores can extend or replace global ignores (see `:ignore-mode` below)
- The ignore list persists across Proserunner runs
- You can use the `-i` flag to specify a different ignore file: `proserunner -f document.md -i my-ignore-file`

## Project configuration

Proserunner supports project-level configuration that can be versioned alongside your code. This allows teams to share prose guidelines and custom checks.

### Initializing a project

Initialize a project configuration with:

```bash
$ proserunner --init-project
```

This creates a `.proserunner/` directory containing:

- **config.edn** - Project configuration file
- **checks/** - Directory for custom checks specific to this project

### Configuration options

Edit `.proserunner/config.edn` to customize:

```clojure
{:check-sources ["default" "checks"]
 :ignore #{"TODO" "FIXME"}
 :ignore-mode :extend
 :config-mode :merged}
```

#### :check-sources

Vector of check sources to use:

- `"default"` - Built-in default checks from `~/.proserunner/default/`
- `"checks"` - Project-local checks from `.proserunner/checks/`
- `"./path"` - Relative path to check directory (relative to project root)
- `"/absolute/path"` - Absolute path to check directory

**Examples:**

```clojure
;; Use only default checks
{:check-sources ["default"]}

;; Use default checks + project checks
{:check-sources ["default" "checks"]}

;; Use project checks only (no defaults)
{:check-sources ["checks"]}

;; Use checks from a custom location
{:check-sources ["default" "./company-style"]}
```

#### :ignore

Set of specimens (words/phrases) to ignore in this project:

```clojure
{:ignore #{"TODO" "FIXME" "internal-jargon"}}
```

#### :ignore-mode

Controls how project ignores interact with global ignores:

- `:extend` (default) - Merge project ignores with global `~/.proserunner/ignore.edn`
- `:replace` - Use only project ignores (ignore global ignore list)

**Example:**

```clojure
;; Combine global and project ignores
{:ignore #{"project-term"}
 :ignore-mode :extend}
;; Result: global ignores + "project-term"

;; Use only project ignores
{:ignore #{"project-term"}
 :ignore-mode :replace}
;; Result: only "project-term" (global ignores not used)
```

#### :config-mode

Controls how project config interacts with global config:

- `:merged` (default) - Use both global and project configuration
- `:project-only` - Use only project configuration (ignore global config)

**Example:**

```clojure
;; Use global defaults + project overrides
{:check-sources ["default" "checks"]
 :config-mode :merged}

;; Use only project configuration
{:check-sources ["checks"]
 :config-mode :project-only}
```

### Adding project-specific checks

Create custom check files in `.proserunner/checks/`:

```bash
$ cat > .proserunner/checks/company-terms.edn <<EOF
{:name "company-terminology"
 :kind "recommender"
 :recommendations [{:avoid "legacy term"
                    :prefer "preferred term"}
                   {:avoid "old API"
                    :prefer "new API"}]
 :message "Use current company terminology"}
EOF
```

Then reference it in your config:

```clojure
{:check-sources ["default" "checks"]
 :ignore #{}
 :ignore-mode :extend
 :config-mode :merged}
```

The `"checks"` source automatically includes all `.edn` files in `.proserunner/checks/`.

### Committing project configuration

The `.proserunner/` directory can be committed to version control:

```bash
$ git add .proserunner/
$ git commit -m "Add project prose guidelines"
```

This allows teams to:

- Share custom checks for domain-specific terminology
- Maintain consistent writing standards across the team
- Version control ignore lists for project-specific terms
- Enforce style guide requirements in CI/CD

## Adding checks

You can add custom checks in three ways:

1. **Quick import to global** - Add checks to `~/.proserunner/custom/` for use across all your documents
2. **Quick import to project** - Add checks to `.proserunner/checks/` for a specific project
3. **Manual creation** - Create check files directly

### Quick import with --add-checks

#### Global scope

Add checks to your global configuration (`~/.proserunner/custom/`):

```bash
# Add to global (default if not in a project)
$ proserunner --add-checks ~/my-checks

# Force global scope (even if in a project)
$ proserunner --add-checks ~/my-checks --global
```

#### Project scope

Add checks to a project configuration (`.proserunner/checks/`):

```bash
# Add to project (if in a project directory)
$ proserunner --add-checks ~/my-checks

# Force project scope
$ proserunner --add-checks ~/my-checks --project
```

#### Custom directory name

Use the `--name` flag to specify a custom directory name:

```bash
$ proserunner --add-checks ./checks --name company-style
```

### Manual creation

You can also manually create check files in the appropriate directory:

- **Global:** `~/.proserunner/custom/`
- **Project:** `.proserunner/checks/`

If your check fits the API of one of Proserunner's [editor types](#editor-types), you can create custom checks to vet for your own personal (or organizational) tics.

## Custom checks

### Existence checks

Check for the presence of specific words or phrases:

```clojure
{:name "Writing tics"
 :kind "existence"
 :message "Stop using this phrase already."
 :specimens ["Phrases that"
             "I want"
             "Proserunner to check for"]}
```

### Case checks

Same as Existence, except case-sensitive:

```clojure
{:name "Proper nouns"
 :kind "case"
 :message "Check proper noun usage."
 :specimens ["GitHub"  ; Will catch "Github" but not "github"
             "JavaScript"]}
```

### Recommender checks

Suggest replacements for problematic words or phrases:

```clojure
{:name "Handsome"
 :kind "recommender"
 :message "Use preferred terminology."
 :recommendations [{:avoid "Unseemly phrase"
                    :prefer "Handsome phrase"}
                   {:avoid "old term"
                    :prefer "new term"}]}
```

**Alternative format:**

```clojure
{:name "Company terminology"
 :kind "recommender"
 :specimens {"old API" "new API"
             "legacy term" "preferred term"}
 :message "Use current company terminology"}
```

### Regex checks

Use raw regular expressions for sophisticated pattern matching:

```clojure
{:name "regex"
 :kind "regex"
 :explanation "Raw regular expressions."
 :expressions
 [{:re "(##+.*\\.(\\s)*(?!.))"
   :message "Headings shouldn't end with period."}
  {:re "\\b(very|really|just)\\b"
   :message "Avoid weak intensifiers."}]}
```

### Check file structure

All check files must include:

- `:name` - Display name for the check
- `:kind` - Editor type (existence, case, recommender, case-recommender, repetition, regex)
- `:message` - Error message to display when check triggers

Optional fields:

- `:explanation` - Longer description of why this check exists
- `:enabled` - Set to `false` to disable (default: `true`)

## Custom editors

Proserunner supports custom editor types through a dynamic registry system. This allows you to extend Proserunner with your own check types without modifying the core codebase.

### Creating a custom editor

Add a Clojure file to `~/.proserunner/custom/` that registers your editor function:

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

### Using a custom editor

Create a check file that uses your custom editor:

```clojure
;; ~/.proserunner/custom/my-check.edn
{:name "My Custom Check"
 :kind "my-check-type"  ; Must match registered editor name
 :message "Custom issue found."
 :enabled true}
```

### Editor function signature

Editor functions must have the signature `[line check] -> line`, where:

- `line` is a `Line` record from `proserunner.text` with fields:
  - `:text` - The line text
  - `:file` - Source file path
  - `:line-number` - Line number
  - `:issue?` - Boolean indicating if line has issues
  - `:issues` - Vector of Issue records
- `check` is a `Check` record from `proserunner.checks` with fields:
  - `:name` - Check name
  - `:kind` - Editor type
  - `:message` - Error message
  - Additional fields based on check type
- Return the line unchanged if no issue, or with `:issue?` set to `true` and updated `:issues` vector

### Built-in editors

Proserunner includes 6 built-in editor types:

1. **existence** - Simple word/phrase detection
2. **case** - Case-sensitive word/phrase detection
3. **recommender** - Suggest alternatives
4. **case-recommender** - Case-sensitive alternative suggestions
5. **repetition** - Consecutive word repetition
6. **regex** - Regular expression patterns

You can use these as examples when building custom editors. See `src/editors/` for implementation details.

## Restoring defaults

If you've modified your default checks and want to restore them to their original state:

```bash
$ proserunner --restore-defaults
```

This command:

1. Creates a timestamped backup of your current default checks in `~/.proserunner-backup-TIMESTAMP/`
2. Downloads fresh default checks from the [default checks repository](https://github.com/jeff-bruemmer/proserunner-default-checks)
3. Preserves your `config.edn` and `ignore.edn` files
4. Leaves your custom checks in `~/.proserunner/custom/` untouched

This is useful when:

- You've accidentally modified a default check and want to reset it
- You want to get the latest updates to the default checks
- Your checks directory has become corrupted

Your custom checks in `~/.proserunner/custom/` are never affected by this command. Only checks in `~/.proserunner/default/` are restored.
