# Checks

[Editors](#editors) | [Default checks](#default-checks) | [Modifying checks](#modifying-checks) | [Adding checks](#adding-checks)

Checks are stored as data in EDN format. Clerk's default set of checks are in the [default checks repository](https://github.com/jeff-bruemmer/clerk-default-checks). You can list enabled checks with the `--checks` flag:

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

## Editors

Clerk has different editors, each for a different type of check. You can disable any check, and can extend Existence, Case, and Recommender checks.

| **Editor**       | **Description**                          | **Customizable?** |
| :--------------- | :--------------------------------------- | :---------------- |
| Existence        | Does the line contain the specimen?      | Yes               |
| Case             | Same as Existence, only case sensitive.  | Yes               |
| Recommender      | Avoid X; prefer Y.                       | Yes               |
| Case-recommender | Same as Recommender, only case sensitive | Yes               |
| Repetition       | Consecutive word repetition.             | No                |
| Regex            | Run raw regular expressions.             | Yes               |

## Default checks

When Clerk first runs, it will download the [default checks](https://github.com/jeff-bruemmer/clerk-default-checks), and store them in the `.clerk` directory in your home directory:

```
├── README.md
├── config.edn
├── ignore.edn
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

## Modifying checks

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

## Adding checks

Add custom checks in the `custom` directory . If your check fits the API of one of Clerk's [editors](#editors), you can create custom checks to vet for your own personal (or organizational) tics.

[Existence](#existence-checks) | [Case](#case-checks) | [Recommender](#recommender-checks) | [Regex](#regex-checks)

### Existence checks

```edn
{:name "Writing tics"
 :kind "existence"
 :message "Stop using this phrase already."
 :specimens ["Phrases that"
             "I want"
             "Clerk to check for"]}
```

### Case checks

Same as Existence, except case-sensitive. Change the kind to "case": `:kind "case"`.

### Recommender checks

For Recommender checks, add maps to the Recommendations vector:

```edn
{:name "Handsome",
 :kind "recommender",
 :recommendations [{:avoid "Unseemly phrase",
                    :prefer "Handsome phrase"}]}
```

### Regex checks

If you want to use raw regular expressions to do more sophisticated checks, use the Regex check. Each check requires an expression `:re` and a message `:message`.

```
{:name "regex"
 :kind "regex"
 :explanation "Raw regular expressions."
 :expressions
 [
  {:re "(##+.*\\.(\\s)*(?!.))" :message "Headings shouldn't end with period."}
  ]}
```

