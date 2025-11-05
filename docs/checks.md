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
proserunner --file document.md --quoted-text
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

## Ignoring

Two types of ignores: **simple** (ignore everywhere) and **contextual** (ignore specific occurrences).

### Simple ignores

```bash
proserunner --add-ignore "hopefully"
proserunner --remove-ignore "hopefully"
proserunner --list-ignored
```

### Contextual ignores

Ignore specific issues by number:

```bash
proserunner --file document.md
# [1]  10:5   "utilize" -> Consider using "use" instead.
# [2]  15:12  "leverage" -> Consider using "use" instead.

proserunner --file document.md --ignore-issues 1,2  # Ignore issues 1 and 2
proserunner --file document.md --ignore-issues 1-5,8  # Ranges supported
proserunner --file document.md --ignore-all  # Ignore all current findings
```

**Scope:**
- Default: project if `.proserunner/config.edn` exists, else global
- Use `--global` or `--project` to force

### Maintaining ignores

```bash
proserunner --audit-ignores  # Audit for stale ignores
proserunner --clean-ignores  # Clean stale ignores
```

### Manual editing

Global (`~/.proserunner/ignore.edn`):
```clojure
["hopefully"  ; Simple ignore
 {:file "docs/api.md" :line-num 42 :specimen "utilize"}]  ; Contextual
```

Project (`.proserunner/config.edn`):
```clojure
{:ignore #{"project-term"
           {:file "docs/internal.md" :line-num 5 :specimen "utilize"}}
 :ignore-mode :extend}  ; :extend (combine with global) or :replace
```

Contextual ignore keys: `:file` (required), `:specimen` (required), `:line-num` (optional), `:check` (optional)

## Project configuration

Create project-specific configuration:

```bash
proserunner --init-project
```

Creates `.proserunner/` with `config.edn` and `checks/` directory.

### config.edn options

```clojure
{:check-sources ["default" "checks"]      ; Check locations
 :ignore #{"TODO" "FIXME"}                ; Simple ignores (apply everywhere)
 :ignore-issues [{:file "docs/guide.md"   ; Contextual ignores (specific locations)
                  :line 42
                  :specimen "very"}]
 :ignore-mode :extend                     ; :extend or :replace
 :config-mode :merged}                    ; :merged or :project-only
```

**check-sources:**
- `"default"` - Global checks from `~/.proserunner/default/`
- `"checks"` - Project checks from `.proserunner/checks/`
- Paths (relative or absolute)

**ignore:**
- Set of strings to ignore everywhere in your project
- Case-insensitive matching

**ignore-issues:**
- Vector of maps specifying file/line/specimen to ignore
- Created automatically with `--ignore-issues` command

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
