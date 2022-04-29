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

[Rationale](#rationale) | [Usage](#usage) | [Checks](#checks) | [Installation](#installation)

## Rationale

Clerk was inspired by [Proselint](https://github.com/amperser/proselint), an excellent command line tool. The bulk of Proselint's value lies in the research the developers conducted to curate a set of checks that would yield a useful signal for editing. Clerk builds on that work to make a faster, more customizable linter.

### Advantages

- **Extendable.** Clerk separates code and data. [Checks](#checks) are formatted as EDN, so you can toggle checks on or off, add new specimens of awkward prose to existing checks, or create custom checks to cover your own writing tics or issues specific to your domain or style guide.
- **Fast.** Clerk analyzes multiple lines of text in parallel. Large documents that would take Proselint more than five minutes to process Clerk can do in seconds on multi-core machines. Clerk also caches results, and only checks lines of text that have changed since the last proofreading.
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

-f, --file FILE File or dir to proofread.
-o, --output FORMAT Output type: table, EDN, or JSON.
-C, --checks List enabled checks.
-c, --config CONFIG Set temporary configuration file.
-h, --help Prints this help message.

```

Here's an example command to export results to EDN:

```
$ clerk -f /path/to/drivel.md -o edn
```

Clerk accepts txt, md, org, and tex files.

## Checks

[Editors](#editors) | [Default checks](#default-checks) | [Modifying checks](#modifying-checks) | [Adding checks](#adding-checks)

Checks are stored as data in EDN format. Clerk's default set of checks are in the [default checks repository](https://github.com/jeff-bruemmer/clerk-default-checks).
You can list enabled checks with the `--checks` flag:

```
$ clerk --checks

Enabled checks:

| Name               | Kind        | Explanation                                                                                     |
|--------------------+-------------+-------------------------------------------------------------------------------------------------|
| Annotations        | Case        | Typical markers used to signal problem sites in the text.                                       |
| Archaisms          | Existence   | Outmoded word or phrase.                                                                        |
| Clichés            | Existence   | An overused phrase or opinion that betrays a lack of original thought.                          |
| Compression        | Recommender | Compressing common phrases. From Style: Toward Clarity and Grace by Joseph M. Williams.         |
| Corporate-speak    | Existence   | Words and phrases that make you sound like an automaton.                                        |
| Hedging            | Existence   | Say, or say not. There is no hedging.                                                           |
| Jargon             | Existence   | Phrases infected with bureaucracy.                                                              |
| Links              | Links       | The Markdown/Org link should return a status code in the 200-299.                               |
| Needless-variant   | Recommender | Prefer the more common term.                                                                    |
| Non-words          | Recommender | Identifies sequences of letters masquerading as words, and suggests an actual word.             |
| Not the negative.  | Recommender | Prefer the word to the negation of the word's opposite.                                         |
| Oxymorons          | Existence   | Avoid contradictory terms (that aren't funny).                                                  |
| Phrasal adjectives | Recommender | Hyphenate phrasal adjectives.                                                                   |
| Pompous-diction    | Recommender | Pompous diction: use simpler words. From Style: Toward Clarity and Grace by Joseph M. Williams. |
| Redundancies       | Existence   | Avoid phrases that say the same thing more than once.                                           |
| Repetition         | Repetition  | Catches consecutive repetition of words, like _the the_.                                        |
| Sexism             | Existence   | Sexist or ridiculous terms (like _mail person_ instead of _mail carrier_).                      |
```

You can enable, modify, or disable any check by commenting out entire checks or individual specimens.

### Editors

Clerk has different editors, each for a different type of check. You can disable any check, and can extend Existence, Case, and Recommender checks.

| **Editor**       | **Description**                          | **Customizable?** |
| :--------------- | :--------------------------------------- | :---------------- |
| Existence        | Does the line contain the specimen?      | Yes               |
| Case             | Same as Existence, only case sensitive.  | Yes               |
| Recommender      | Avoid X; prefer Y.                       | Yes               |
| Case-recommender | Same as Recommender, only case sensitive | Yes               |
| Repetition       | Consecutive word repetition.             | No                |
| Links            | Checks for broken links.                 | No                |

### Default checks

When Clerk first runs, it will download the [default checks](https://github.com/jeff-bruemmer/clerk-default-checks), and store them in the `.clerk` directory in your home directory:

```
├── README.md
├── config.edn
├── custom
│   └── example.edn
└── default
    ├── annotations.edn
    ├── archaisms.edn
    ├── cliches.edn
    ├── compression.edn
    ├── corporate-speak.edn
    ... and more checks
```

Once Clerk has downloaded the default checks, consider versioning your `.clerk` directory.

### Modifying checks

You can disable entire checks by commenting out lines in the `:files` vector in `~/.clerk/config.edn`. Here we've disabled the `jargon` and `oxymorons` checks.

```edn
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
    "links"
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

You can add new specimens to existing checks, or disable individual checks by commenting out lines within the checks, e.g., if you only want to disable some oxymorons. For Existence and Case checks, you can add examples of text for Clerk to search for to a check's specimens vector. See `~/.clerk/custom/example.edn` for an example Existence check.

### Adding checks

Add custom checks in the `custom` directory . If your check fits the API of one of Clerk's [editors](#editors), you can create custom checks to vet for your own personal (or organizational) tics.

#### Existence checks

```edn
{:name "Writing tics"
 :kind "existence"
 :message "Stop using this phrase already."
 :specimens ["Phrases that"
             "I want"
             "Clerk to check for"]}
```

#### Case checks

Same as Existence, except case-sensitive. Change the kind to "case": `:kind "case"`.

#### Recommender checks

For Recommender checks, add maps to the Recommendations vector:

```edn
{:name "Handsome",
 :kind "recommender",
 :recommendations [{:avoid "Unseemly phrase",
                    :prefer "Handsome phrase"}]}
```

## Installation

[Download](#download) | [Build from source](#build-from-source) | [Clojure CLI tools](#clojure-cli-tools)

### Download

Clerk uses GraalVM's Native Image utility to compile to a binary executable.

1. Download the [latest binary](https://github.com/jeff-bruemmer/clerk-prototype/releases) for your system. Linux and Mac OS (Darwin) binaries are available. Windows users can run Clerk on Windows Subsystem for Linux.
2. `cd` into your Downloads directory and rename download to `clerk`.
3. `chmod +x clerk`.
4. Add Clerk to your \$PATH, e.g., `sudo cp clerk /usr/local/bin`.

### Build from source

If you've installed [GraalVM](https://www.graalvm.org/) and [Native Image](https://www.graalvm.org/reference-manual/native-image/), you can build the binary yourself:

```
clj -M:build
```

Or install it with:

```
./tools/install.sh
```

### Clojure CLI tools

Run with Clojure's CLI tools.

```
clj -M:run -f /path/to/file
```
