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
-f, --file          FILE        File or dir to proofread.
-o, --output        FORMAT      Output type: group, edn, json, table.
-C, --checks                    List enabled checks.
-c, --config        CONFIG      Set temporary configuration file.
-h, --help                      Prints this help message.
-i, --ignore        IGNORE      EDN file listing specimens to ignore.
-b, --code-blocks               Include code blocks.
-n, --no-cache                  Don't use cached results.
-t, --timer                     Print time elapsed.
-v, --version                   Prints version number.
```

Here's an example command to export results to EDN:

```
$ clerk --file /path/to/drivel.md --output edn
```

Clerk accepts txt, md, org, and tex files.

