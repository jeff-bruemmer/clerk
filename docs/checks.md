# Checks

Proserunner checks are EDN data files. View enabled checks with `proserunner --checks`.

Default checks: [github.com/jeff-bruemmer/proserunner-default-checks](https://github.com/jeff-bruemmer/proserunner-default-checks)

## Editor types

| **Type**         | **Description**                     |
| :--------------- | :---------------------------------- |
| existence        | Flag specific words/phrases         |
| case             | Case-sensitive existence check      |
| recommender      | Suggest replacements (avoid X → prefer Y) |
| case-recommender | Case-sensitive recommender          |
| repetition       | Detect consecutive word repetition  |
| regex            | Custom regular expressions          |

## Quoted text handling

By default, Proserunner strips quoted text from lines before checking. This allows checking the narrative portions of dialogue while ignoring the quoted speech itself.

For example, this line:
```
She said "obviously this is wrong" and walked away.
```

Will be checked as:
```
She said                         and walked away.
```

The quoted portion is replaced with spaces to preserve column positions for error reporting. Entire lines are kept—only the quoted portions are removed.

To check quoted text, use `--quoted-text`:

```bash
proserunner -f document.md --quoted-text
```

This includes both straight quotes (`"..."`, `'...'`) and curly quotes (`"..."`, `'...'`).

## Configuration

On first run, Proserunner downloads checks to `~/.proserunner/`:

```
~/.proserunner/
├── config.edn      # Main configuration
├── ignore.edn      # Global ignore list
├── custom/         # Your custom checks
└── default/        # Default checks
```

### Disabling checks

Edit `~/.proserunner/config.edn` and comment out checks in the `:files` vector:

```clojure
{:checks
 [{:name "default"
   :directory "default"
   :files ["cliches"
           ;; "jargon"      ; disabled
           "corporate-speak"]}]}
```

## Ignoring specimens

Ignore specific words/phrases without disabling checks. Proserunner supports both **simple ignores** (ignore a word everywhere) and **contextual ignores** (ignore specific occurrences in specific files).

### Quick start: Ignore all current findings

The easiest way to ignore remaining issues after fixing the ones you care about:

```bash
# Run proserunner and see what it finds
proserunner -f docs/

# Fix the issues you want to fix, then ignore the rest
proserunner -f docs/ --ignore-all
```

This creates contextual ignores that remember the exact file and line number of each issue, so the same word elsewhere won't be ignored.

### Simple ignores (global)

Ignore a word or phrase everywhere:

```bash
# Add/remove from ignore list
proserunner --add-ignore "hopefully"
proserunner --remove-ignore "hopefully"

# List ignored items
proserunner --list-ignored

# Temporarily skip all ignores
proserunner -f document.md --skip-ignore
```

### Contextual ignores (file and line specific)

Contextual ignores let you ignore specific occurrences without ignoring the word everywhere:

```bash
# Ignore all current findings in a file
proserunner -f document.md --ignore-all
# or shorthand:
proserunner -f document.md -Z

# Add to project ignore list instead of global
proserunner -f document.md --ignore-all --project
# or shorthand:
proserunner -f document.md -Z -P
```

### Maintaining ignore lists

Over time, files change and some ignores may become stale:

```bash
# Check for stale ignores (files that no longer exist)
proserunner --audit-ignores
# or shorthand:
proserunner -U

# Remove stale ignores
proserunner --clean-ignores
# or shorthand:
proserunner -W
```

### Manual editing

Global ignore list (`~/.proserunner/ignore.edn`):

```clojure
["hopefully"  ; Simple ignore - ignores "hopefully" everywhere
 "TODO"
 {:file "docs/api.md"          ; Contextual ignore
  :line 42
  :specimen "utilize"}
 {:file "README.md"
  :line 10
  :specimen "leverage"}]
```

Project ignore list (`.proserunner/config.edn`):

```clojure
{:ignore #{"project-term"      ; Can mix simple and contextual
           {:file "docs/internal.md"
            :line 5
            :specimen "utilize"}}
 :ignore-mode :extend}  ; :extend or :replace
```

**Ignore format:**

- **Simple:** Just a string - ignores that word/phrase everywhere
- **Contextual:** A map with:
  - `:file` - Required. File path.
  - `:specimen` - Required. The word/phrase to ignore.
  - `:line` - Optional. Specific line number.
  - `:check` - Optional. Only ignore for this specific check.

**Ignore scopes:**

- **Global** (`~/.proserunner/ignore.edn`): Applies to all projects
- **Project** (`.proserunner/config.edn`): Applies only to this project

**Ignore modes:**

- `:extend` - Combine project and global ignores (default)
- `:replace` - Use only project ignores, ignore global

## Project configuration

Create project-specific configuration:

```bash
proserunner --init-project
```

Creates `.proserunner/` with `config.edn` and `checks/` directory.

### config.edn options

```clojure
{:check-sources ["default" "checks"]  ; Check locations
 :ignore #{"TODO" "FIXME"}            ; Project ignores
 :ignore-mode :extend                 ; :extend or :replace
 :config-mode :merged}                ; :merged or :project-only
```

**check-sources:**
- `"default"` - Global checks from `~/.proserunner/default/`
- `"checks"` - Project checks from `.proserunner/checks/`
- Paths (relative or absolute)

**ignore-mode:**
- `:extend` - Combine with global ignores
- `:replace` - Use only project ignores

**config-mode:**
- `:merged` - Combine with global config
- `:project-only` - Use only project config

## Adding custom checks

Import checks from a directory:

```bash
# Global scope
proserunner --add-checks ~/my-checks --global

# Project scope
proserunner --add-checks ~/my-checks --project

# Custom name
proserunner --add-checks ./checks --name company-style
```

Or manually create `.edn` files in:
- Global: `~/.proserunner/custom/`
- Project: `.proserunner/checks/`

## Custom check examples

### Existence

```clojure
{:name "Writing tics"
 :kind "existence"
 :message "Stop using this phrase."
 :specimens ["phrase one" "phrase two"]}
```

### Case-sensitive

```clojure
{:name "Proper nouns"
 :kind "case"
 :message "Check proper noun usage."
 :specimens ["GitHub" "JavaScript"]}
```

### Recommender

```clojure
{:name "Terminology"
 :kind "recommender"
 :message "Use preferred terms."
 :recommendations [{:avoid "old term"
                    :prefer "new term"}]}
```

### Regex

```clojure
{:name "Custom patterns"
 :kind "regex"
 :expressions [{:re "\\b(very|really)\\b"
                :message "Avoid weak intensifiers."}]}
```

All checks require: `:name`, `:kind`, `:message`

## Custom editors

Extend Proserunner with custom check types. Create a Clojure file in `~/.proserunner/custom/`:

```clojure
;; my-editor.clj
(ns my-editor
  (:require [editors.registry :as registry]
            [proserunner.text :as text]))

(defn my-proofread [line check]
  ;; Return line with :issue? true and updated :issues vector
  ...)

(registry/register-editor! "my-check-type" my-proofread)
```

Use in check file:

```clojure
{:name "My Check"
 :kind "my-check-type"
 :message "Issue found."}
```

See `src/editors/` for built-in implementations.

## Restoring defaults

Reset default checks to original state:

```bash
proserunner --restore-defaults
```

This creates a backup, downloads fresh defaults, and preserves your custom checks and config files.
