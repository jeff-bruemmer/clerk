(ns proserunner.constants
  "Configuration paths, remote URLs, file size limits, and other constant values.")

(set! *warn-on-reflection* true)

;;; Directory and file names

(def proserunner-dir
  "Name of the proserunner configuration directory."
  ".proserunner")

(def default-checks-dir
  "Subdirectory name for default checks."
  "default")

(def custom-checks-dir
  "Subdirectory name for custom user checks."
  "custom")

(def config-filename
  "Name of the configuration file."
  "config.edn")

(def ignore-filename
  "Name of the ignore file."
  "ignore.edn")

(def version-filename
  "Name of the version tracking file."
  ".version")

(def cache-subdir
  "Subdirectory name for cache storage."
  "cache")

;;; Remote repository configuration

(def remote-checks-repo
  "GitHub repository for default checks (owner/repo format)."
  "jeff-bruemmer/proserunner-default-checks")

(def remote-checks-branch
  "Branch name for default checks."
  "main")

(def remote-checks-url
  "URL for downloading default checks as a zip archive."
  "https://github.com/jeff-bruemmer/proserunner-default-checks/archive/main.zip")

(def remote-checks-api-url
  "GitHub API URL for checking latest commit of default checks."
  "https://api.github.com/repos/jeff-bruemmer/proserunner-default-checks/commits/main")

(def remote-zip-dirname
  "Directory name inside the downloaded zip file."
  "proserunner-default-checks-main")

;;; File processing limits

(def max-file-size-bytes
  "Maximum individual file size in bytes (10MB using decimal: 1MB = 1,000,000 bytes).
  Directories are exempt from this limit."
  10000000)

(def max-file-size-display
  "Human-readable display of maximum file size."
  "10MB")

;;; Network timeouts

(def http-timeout-ms
  "Default HTTP timeout in milliseconds for downloading checks."
  5000)

(def api-timeout-ms
  "HTTP timeout in milliseconds for GitHub API calls."
  3000)

;;; Text processing

(def code-block-delimiter
  "Markdown code block delimiter."
  "```")

(def supported-file-extensions
  "Set of supported file extensions for prose checking."
  #{"txt" "tex" "md" "markdown" "org"})

;;; Default templates

(def default-global-config-template
  "Default content for newly created global config.edn file."
  "{:checks []}")

(def default-ignore-template
  "Default content for newly created ignore.edn file."
  "{:ignore #{}\n :ignore-issues #{}}")

;;; Environment variables

(def env-cache-dir
  "Environment variable name for cache directory override."
  "PROSERUNNER_CACHE_DIR")

(def env-xdg-cache-home
  "XDG cache home environment variable name."
  "XDG_CACHE_HOME")

(def env-tmpdir
  "Temporary directory environment variable name."
  "TMPDIR")

(def env-debug
  "Debug mode environment variable name."
  "PROSERUNNER_DEBUG")
