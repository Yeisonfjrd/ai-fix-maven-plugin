# ai-fix for Maven

When your Java tests fail, ai-fix asks a **local** model (Ollama) for a fix to `src/main/java` and keeps it only if **every** test passes. In CI it opens a pull request with the fix; on your machine it's one command. No API key, no cost, your code never leaves the runner.

- **Tests are off limits.** It can only change existing files under `src/main/java`, so it can't "fix" a test to make it pass.
- **Measured, not promised.** A benchmark of 10 Java bugs with hidden tests the model never sees, 3 runs per setup: 9–10/10 real fixes with `qwen3-coder:30b` on a GPU, 5–7/10 with a 7B model on a free GitHub runner ([details](#benchmark)). Use your own big model if you can.
- **Nothing half-done.** A failed attempt is reverted; if it can't fix it, your code stays exactly as it was.

```yaml
# .github/workflows/ci.yml, after your test job
  ai-fix:
    needs: test
    if: failure() && !startsWith(github.head_ref || github.ref_name, 'ai-fix/')
    runs-on: ubuntu-latest
    permissions:
      contents: write
      pull-requests: write
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.head_ref || github.ref_name }}
      - uses: Yeisonfjrd/ai-fix-maven-plugin@v1
```

Requirements: a Maven project with JUnit tests (Surefire reports), Java 21 on the runner (the action installs it; your project can target an older `release`), and *Settings → Actions → General → Allow GitHub Actions to create and approve pull requests*. For a fixed, unchangeable version pin a commit SHA instead of `@v1`.

Java 21 · Maven plugin API · JavaParser · Ollama (default model `qwen3-coder:30b` on your machine, `qwen2.5-coder:7b` on GitHub runners).

```
$ mvn io.github.yeisonfjrd:ai-fix-maven-plugin:1.1.0:fix
Fallan 3 test(s): shop.CartTest#discountAppliesFromTheThreshold, ...
Intento 1/3: pidiendo un arreglo al modelo…
  los tests que fallaban ya pasan; corriendo todos para ver que no se rompió otra cosa…
-            sum += item.unitPrice();
+            sum += item.unitPrice() * item.quantity();
Arreglado en 1 intento(s). Modelo: 50.2 s · tests: 16.3 s
```

That is a real run on [`examples/shop`](examples/shop), a cart whose subtotal ignores the quantity.

## How it works

1. **Run the tests** in a separate Maven process (the project's `mvnw` if it has one) and read the failures from the Surefire XML reports, not from the console. The failing ones run a second time: if they pass, they are flaky, ai-fix reports `FLAKY` and does not touch the code.
2. **Build the context.** Only the failing test methods (extracted with JavaParser, not whole files), their trimmed stack traces, and the `src/main` classes that are suspects: the ones in the stack trace, the ones the test uses, and one level more (the classes those suspects use). If the project is a git repo, also the last change to `src/main/java` (uncommitted changes, or the last commit): when a push breaks the tests, the bug is usually there.
3. **Ask for search/replace blocks**, not a line-numbered diff: small models miscount lines but copy code well.
4. **Rules enforced by code, not by the prompt:** only existing `.java` files under `src/main/java` can change (tests are off limits, so the model cannot "fix" a test), every SEARCH must match exactly once, and either all edits of an answer apply or none does.
5. **Verify.** Rerun the failing tests, then the whole suite to catch regressions. A failed attempt is reverted before the next one, so every attempt starts from the original code and mistakes do not pile up. The model gets the new failure as feedback.
6. **Reject copied values.** If the fix turns the tests green by using strings or numbers taken from the test (`if (title.equals("Hola  Mundo"))`), it fixes the example, not the rule: it is reverted and the model is asked for the general fix. If the last attempt still does it, the report and the pull request carry a warning.
7. **Review.** With every test green, the model re-reads the Javadoc of what it changed and checks its fix against the whole rule, not only the test's example. If it extends the fix, the extension stays only if every test still passes. Turn it off with `-Daifix.review=false`.
8. **Show the unified diff.** Any exception along the way reverts every file.

## Running it

```bash
./mvnw install                       # installs the plugin in ~/.m2
cd examples/shop
./mvnw io.github.yeisonfjrd:ai-fix-maven-plugin:1.1.0:fix
```

Needs [Ollama](https://ollama.com) running with the model pulled (`ollama pull qwen3-coder:30b`).

Options: `-Daifix.model=qwen3:14b`, `-Daifix.attempts=3`, `-Daifix.dryRun=true` (show the fix, keep the code), `-Daifix.ollamaUrl=...`.

## GitHub Action

Nobody remembers to run a fix command, so the same loop also runs in CI. When the tests fail, the action runs ai-fix and, only if **every** test passes with the change, opens a pull request against the branch that failed. It never pushes to that branch, never runs on its own `ai-fix/*` branches, and skips pull requests from forks.

```yaml
  ai-fix:
    needs: test
    if: failure() && !startsWith(github.head_ref || github.ref_name, 'ai-fix/')
    # Private repo: your own machine. Public repo: a free GitHub runner.
    runs-on: ${{ github.event.repository.private && 'self-hosted' || 'ubuntu-latest' }}
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.head_ref || github.ref_name }}
      - uses: Yeisonfjrd/ai-fix-maven-plugin@v1
```

Full workflow: [examples/workflows/ci-with-ai-fix.yml](examples/workflows/ci-with-ai-fix.yml).

- **Where the model runs.** On a GitHub-hosted runner (no GPU, 16 GB) the action installs Ollama, caches the model and uses `qwen2.5-coder:7b` on the CPU (5–7/10 real fixes in the benchmark). On a self-hosted runner it uses the Ollama already running on that machine and `qwen3-coder:30b` (9–10/10). `model:` overrides it.
- **No publishing needed.** The action carries the plugin's source and installs it on the runner.
- **Every run keeps the transcript, the JSON report and the console log as artifacts**, and writes the result and the diff to the job summary.
- The repository needs *Settings → Actions → General → Allow GitHub Actions to create and approve pull requests*. Pull requests opened with the default `GITHUB_TOKEN` do not trigger other workflows; pass a fine-grained token as `token:` if you want CI to run on them.

## Benchmark

10 small Java bugs, each with **visible tests** (the ones that fail and the model sees) and **hidden tests** the model never sees. A fix only counts as real if the hidden tests pass too, so a fix tuned to the visible test does not count. `java bench/Bench.java --check` verifies the benchmark itself: the buggy code fails the visible tests and the reference fix passes both.

A model does not answer the same way twice, so every setup ran **3 times** (October 2026, all results in [bench/results](bench/results)):

| Setup | Real fixes per run | Average | Model time per bug |
|---|---|---|---|
| `qwen3-coder:30b` on my laptop's GPU (RX 6800M), with review | 9, 9, 10 | **9.3/10** | 22–34 s |
| `qwen2.5-coder:7b` on 4 CPU cores, like a free GitHub runner, with review | 7, 7, 7 | **7.0/10** | 25–34 s |
| Same 7B model, without review | 5, 7, 6 | **6.0/10** | 26–29 s |

What the numbers say:

- **Use a big model if you can.** With `qwen3-coder:30b` ai-fix fixes almost every case. With the 7B model about 3 bugs in 10 end without a real fix: some are not fixed at all, and in every run 1 to 3 cases turn the tests green with an **incomplete** fix (`05-slug`, `06-age`, `10-tax-category-case`): the visible test passes, a hidden one does not. On a real project there are no hidden tests, so treat a 7B pull request as a first draft.
- **The review helps the big model, not the small one.** The 30B model extended its fix in 3–4 cases per run; `05-slug`, incomplete in both earlier runs (it fixed double spaces but not spaces at the ends), came out complete in 2 of 3 runs. The 7B model answered "OK" every single time and never changed anything, so its 6.0 → 7.0 is run-to-run noise, not the review.
- **The 7B model is inconsistent.** The same case is fixed in one run and missed in the next (`04`, `06`, `08`, `10`). An earlier single run reported here as "6/10" was one of those runs, not a typical result.

The cases:

| Case | Bug |
|---|---|
| 01 | off-by-one in a paginator |
| 02 | integer division loses the remainder cents |
| 03 | NullPointerException on a missing nickname |
| 04 | `equals` without `hashCode` |
| 05 | slug with repeated/surrounding spaces |
| 06 | age counted before the birthday |
| 07 | static field shared by every counter |
| 08 | removing from a list while looping by index |
| 09 | versions compared as text |
| 10 | bug in `TaxTable`, but the test only uses `Invoice` |

### How it got here

- **Context.** Case 10 failed with the 30B model until the context included the classes the suspects use (one more level), not only the ones the test touches.
- **Formats the small model actually writes.** The 7B model first scored 2/10: on the paginator it wrote the right fix but as a whole class instead of SEARCH/REPLACE blocks, and the plugin threw it away. Now a whole class, or just the fixed method under `FILE:`, is accepted (same rules: tests off limits, every test must pass or it is reverted), a SEARCH block with different indentation is still found when it matches in one place only, and a format error asks to repeat the same fix instead of "try a different fix".
- **Every run writes the whole conversation** with the model to `target/ai-fix-transcript.md`, which is how each of these problems was found.

## What's unfinished

Each of these has an issue with the details:

- A fix is only as good as the tests: if they do not cover a case, a green fix can still be incomplete. The review and the copied-values check reduce this, they do not remove it ([#14](https://github.com/Yeisonfjrd/ai-fix-maven-plugin/issues/14)).
- The 30B model needs a self-hosted runner with a GPU; the free GitHub runner only fits the 7B one ([#11](https://github.com/Yeisonfjrd/ai-fix-maven-plugin/issues/11)).
- The benchmark has 10 bugs written for it. A benchmark on real bugs (Defects4J) would give a more honest number ([#12](https://github.com/Yeisonfjrd/ai-fix-maven-plugin/issues/12)).
- Asking for several fixes at once and keeping the best could help the small model, at the cost of time on a CPU ([#13](https://github.com/Yeisonfjrd/ai-fix-maven-plugin/issues/13)).
- Out of scope for now: compile errors, bugs spread over several files or modules, wrong tests, environment failures ([#15](https://github.com/Yeisonfjrd/ai-fix-maven-plugin/issues/15)). The dependency walk stops at one level, so a bug two calls away from what the test touches is out of reach.
- On a Windows console the Spanish log can show broken accents, depending on the console code page.

## License

[MIT](LICENSE). The Maven wrapper scripts (`mvnw`, `mvnw.cmd`) keep their Apache-2.0 header from Apache Maven.
