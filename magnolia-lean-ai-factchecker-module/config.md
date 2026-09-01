# Configuration

This page documents the configuration properties exposed by the fact-checker module's own Java classes: the module configuration bean (`FactCheckerModule`), the `factChecker` command (`FactCheckerCommand`), and the `factCheckCommandAction` action definition (`FactCheckCommandActionDefinition`). All of these are standard Magnolia content2bean-configured beans — set them via YAML light-module config (or JCR) and restart/redeploy to pick up changes.

- `FactCheckerModule` is bound from `config:/modules/magnolia-lean-ai-factchecker-module`.
- `FactCheckerCommand` is configured per command instance under `config:/modules/magnolia-lean-ai-factchecker-module/commands/default/<commandName>` (the module ships one instance, `factChecker`).
- `FactCheckCommandActionDefinition` is configured per action instance (`$type: factCheckCommandAction`) on an app's actionbar, e.g. under `decorations/pages-app/apps/pages-app.yaml`.

## FactCheckerModule

| Property | Description |
|---|---|
| `claimExtractorChatModelConfg` | **optional**. [`ChatModelConfig`](#chatmodelconfig) for the claim-extraction step, which turns page text into a list of candidate factual claims. _Default is a `ChatModelConfig` with all fields unset;_ the module's deployed `config.yaml` sets this to `providerType: OLLAMA`, `modelName: qwen2.5:7b-instruct`, `numCtx: 8192`, `temperature: 0.1`, `supportedCapabilities: RESPONSE_FORMAT_JSON_SCHEMA`. |
| `factCheckerChatModelConfg` | **optional**. [`ChatModelConfig`](#chatmodelconfig) for the fact-checking step, which verifies each extracted claim against Wikipedia. _Default is a `ChatModelConfig` with all fields unset;_ the module's deployed `config.yaml` sets this to `providerType: OLLAMA`, `modelName: llama3.1:8b-instruct-q4_K_M`, `numCtx: 4096`, `temperature: 0.0`. Unlike the extractor config, `supportedCapabilities` is intentionally left empty here — combining JSON-schema-constrained output with tool-calling in the same request was found to make the model skip the Wikipedia tool call entirely. |

### ChatModelConfig

Nested bean type shared by both `claimExtractorChatModelConfg` and `factCheckerChatModelConfg` above.

| Property | Description |
|---|---|
| `providerType` | **required**. The LLM provider to build a chat model for — one of `OLLAMA`, `OPEN_AI`, or `ANTHROPIC` (langchain4j `ModelProvider`). No default; used by `ChatModelFactory` to select the model-building path. |
| `baseUrl` | **required** for `OLLAMA`/`OPEN_AI`. Base URL of the model endpoint, e.g. `http://localhost:11434` for a local Ollama instance. No default. |
| `apiKey` | **optional**, used only for `OPEN_AI`/`ANTHROPIC`. Not a plaintext secret — a JCR node path into Magnolia's Passwords App keystore, resolved at runtime by `ChatModelFactory` via `PasswordRegistry`. Ignored for `OLLAMA`. No default. |
| `modelName` | **required**. Name of the model to use, e.g. `qwen2.5:7b-instruct` or `llama3.1:8b-instruct-q4_K_M`. No default. |
| `numCtx` | **optional**. Context window size, in tokens, passed to Ollama. _Default is `0`_ (unset — no class-level default; the deployed config sets `8192` for the extractor and `4096` for the checker). Ollama silently truncates input beyond this value with no error, so undersizing it can drop content from the end of long pages. |
| `numPredict` | **optional**. Maximum number of tokens to generate per response. _Default is `0`_ (unset — no class-level default; the deployed config sets `1024` for both models via the shared Ollama include). |
| `temperature` | **optional**. Sampling temperature for the model. _Default is `0.0`_. |
| `logRequests` | **optional**. Whether to log outgoing chat requests (useful for prompt debugging). _Default is `false`_ (the deployed config enables this, `true`). |
| `logResponses` | **optional**. Whether to log incoming chat responses. _Default is `false`_ (the deployed config enables this, `true`). |
| `supportedCapabilities` | **optional**. Array of langchain4j `Capability` values to enable, e.g. `RESPONSE_FORMAT_JSON_SCHEMA` for grammar-constrained JSON output. _Default is an empty array_. |
| `timeoutInSeconds` | **required** in practice — a primitive `long` with no usable default. HTTP client timeout in seconds, passed straight into `Duration.ofSeconds(...)`; leaving it at `0` throws `IllegalArgumentException: Invalid duration: PT0S` when the chat model is built. _Default is `0`_ (the deployed config sets `150` via the shared Ollama include). Not currently applied when `providerType` is `ANTHROPIC`. |

## FactCheckerCommand

| Property | Description |
|---|---|
| `nodePropertyNames` | **optional**. List of node property names whose values are extracted and fact-checked, e.g. `body`, `text`. _Default is an empty list;_ the module's bootstrapped `factChecker` command instance sets this to `[body, text]`. |
| `includeSubnodes` | **optional**. Whether to also recurse into content subnodes belonging to the same page (via `PageSubnodesPredicate`) rather than checking only the target node's own properties. _Default is `false`._ In practice this is set per action invocation via the action's `params` (e.g. `true` in `decorations/pages-app/apps/pages-app.yaml`) rather than on the command definition itself. |
| `checkAllLanguages` | **optional**. Whether to check the property value in every locale configured for the site, instead of only the default locale. _Default is `false`._ As with `includeSubnodes`, this is typically set per action invocation via `params` rather than on the command definition. |

## FactCheckCommandActionDefinition

Extends Magnolia's own `JcrCommandActionDefinition`; inherited properties (`command`, `asynchronous`, `catchExceptions`, `icon`, `label`, `availability`, etc.) are not repeated here — see Magnolia's action-definition documentation for those. The properties below are added by this module.

| Property | Description |
|---|---|
| `messageView` | **optional**. Id of the registered message view used to render the fact-check result message in the editor's message center (see `messageViews/factCheck.yaml`). _Default is `magnolia-lean-ai-factchecker-module:factCheck`_. |
| `messageSubject` | **optional**. Subject line of the in-app message shown after a fact check completes. _Default is `Fact Checker`_. |
| `messagePattern` | **optional**. `java.text.MessageFormat` pattern for the message body summary line; `{0}` is the content workspace, `{1}` is the node path. _Default is `{0}:/{1} has been fact checked.`_. |
| `messageDescriptionNoClaims` | **optional**. Message body text shown when no factual claims could be extracted from the checked content. _Default is `No specific claims could be identified for this content.`_. |
| `messageDescriptionPattern` | **optional**. `java.text.MessageFormat` HTML pattern rendered once per claim result: `{0}` claim, `{1}` verdict, `{2}` explanation, `{3}` source URL. Only `{2}` (explanation, which is LLM-generated and thus untrusted) is HTML-escaped before interpolation. _Default is:_ <br>`<p>`<br>`  <b>Claim: </b>{0}<br/>`<br>`  <b>Verdict: </b>{1}<br/>`<br>`  <b>Explanation: </b>{2}<br/>`<br>`  <b>Source Url: </b>{3}<br/>`<br>`</p>` |
