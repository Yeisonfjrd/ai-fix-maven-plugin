# ai-fix · Maven plugin

`mvn ai-fix:fix` runs your tests and, if some fail, asks a local model (Ollama) for a fix to `src/main/java`. It keeps the fix only if the whole test suite passes; otherwise the code is left exactly as it was. No API key, no cost, the code never leaves your machine.

Java 21 · Maven plugin API · JavaParser · Ollama (default model `qwen3-coder:30b`).

```
$ mvn io.github.yeisonfjrd:ai-fix-maven-plugin:0.1.0-SNAPSHOT:fix
Fallan 3 test(s): shop.CartTest#discountAppliesFromTheThreshold, ...
Intento 1/3: pidiendo un arreglo al modelo…
  los tests que fallaban ya pasan; corriendo todos para ver que no se rompió otra cosa…
-            sum += item.unitPrice();
+            sum += item.unitPrice() * item.quantity();
Arreglado en 1 intento(s). Modelo: 50.2 s · tests: 16.3 s
```

That is a real run on [`examples/shop`](examples/shop), a cart whose subtotal ignores the quantity.

## How it works

1. **Run the tests** in a separate Maven process (the project's `mvnw` if it has one) and read the failures from the Surefire XML reports, not from the console.
2. **Build the context.** Only the failing test methods (extracted with JavaParser, not whole files), their trimmed stack traces, and the `src/main` classes that are suspects: the ones in the stack trace, the ones the test uses, and one level more (the classes those suspects use).
3. **Ask for search/replace blocks**, not a line-numbered diff: small models miscount lines but copy code well.
4. **Rules enforced by code, not by the prompt:** only existing `.java` files under `src/main/java` can change (tests are off limits, so the model cannot "fix" a test), every SEARCH must match exactly once, and either all edits of an answer apply or none does.
5. **Verify.** Rerun the failing tests, then the whole suite to catch regressions. A failed attempt is reverted before the next one, so every attempt starts from the original code and mistakes do not pile up. The model gets the new failure as feedback.
6. **Show the unified diff.** Any exception along the way reverts every file.

## Running it

```bash
./mvnw install                       # installs the plugin in ~/.m2
cd examples/shop
./mvnw io.github.yeisonfjrd:ai-fix-maven-plugin:0.1.0-SNAPSHOT:fix
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
      - uses: Yeisonfjrd/ai-fix-maven-plugin@main
```

Full workflow: [examples/workflows/ci-with-ai-fix.yml](examples/workflows/ci-with-ai-fix.yml).

- **Where the model runs.** On a GitHub-hosted runner (no GPU, 16 GB) the action installs Ollama, caches the model and uses `qwen2.5-coder:7b` on the CPU (6/10 real fixes in the benchmark). On a self-hosted runner it uses the Ollama already running on that machine and `qwen3-coder:30b` (9/10). `model:` overrides it.
- **No publishing needed.** The action carries the plugin's source and installs it on the runner.
- **Every run keeps the transcript, the JSON report and the console log as artifacts**, and writes the result and the diff to the job summary.
- The repository needs *Settings → Actions → General → Allow GitHub Actions to create and approve pull requests*. Pull requests opened with the default `GITHUB_TOKEN` do not trigger other workflows; pass a fine-grained token as `token:` if you want CI to run on them.

## Benchmark

10 small Java bugs, each with **visible tests** (the ones that fail and the model sees) and **hidden tests** the model never sees. A fix only counts as real if the hidden tests pass too, so a fix tuned to the visible test does not count. `java bench/Bench.java --check` verifies the benchmark itself: the buggy code fails the visible tests and the reference fix passes both.

With `qwen3-coder:30b` on my laptop (RX 6800M, Ryzen 9 5900HX):

**10/10 fixed, 9/10 real fixes, 19–27 s of model time per bug on average** (two runs; the model does not answer the same way every time, case 10 took 1 attempt in one run and 3 in the other).

The first run gave 9/10 fixed and 8/10 real. Case 10 failed because the context only had the classes the test touches; adding the `src/main` classes those suspects use fixed it on the first attempt, with no other case getting worse. Both runs are in [bench/results](bench/results). (The time also dropped, but part of that is the model already being loaded, so I would not credit the change for it.)

| Case | Bug | Result |
|---|---|---|
| 01 | off-by-one in a paginator | real fix |
| 02 | integer division loses the remainder cents | real fix |
| 03 | NullPointerException on a missing nickname | real fix |
| 04 | `equals` without `hashCode` | real fix (2nd attempt) |
| 05 | slug with repeated/surrounding spaces | **incomplete**: fixed the inner spaces, ignored "no dash at either end"; a hidden test caught it |
| 06 | age counted before the birthday | real fix |
| 07 | static field shared by every counter | real fix |
| 08 | removing from a list while looping by index | real fix |
| 09 | versions compared as text | real fix |
| 10 | bug in `TaxTable`, but the test only uses `Invoice` | real fix (was not fixed before the context change) |

Full table, timings and every diff: [bench/results/qwen3-coder_30b.md](bench/results/qwen3-coder_30b.md).

### A small model, like on a free GitHub runner

GitHub's free runners have no GPU and 16 GB of RAM, so `qwen3-coder:30b` does not fit. To know if a free CI version is worth it, I ran `qwen2.5-coder:7b` with the GPU off and 4 threads (`-Daifix.numGpu=0 -Daifix.numThread=4`), the same as the runner:

| | First run | After the fix below |
|---|---|---|
| Fixed (visible tests) | 2/10 | **8/10** |
| Real fixes (hidden tests too) | 2/10 | **6/10** |
| Model time per bug | 63 s | 31 s |

The first run looked like "the small model can't do it". The transcript said otherwise: on the paginator it wrote the right fix (`from + size`), but as the whole class instead of SEARCH/REPLACE blocks, and the plugin threw it away without testing it. Worse, the feedback told it to "try a different fix". Now, when there are no SEARCH/REPLACE blocks, a complete class for an existing `src/main/java` file is applied as a whole (same rules: tests off limits, all tests must pass or it is reverted), and a format error asks to repeat the same fix in the right format. The big model's score did not change.

Every run now writes the whole conversation with the model to `target/ai-fix-transcript.md`.

## What's unfinished

- The dependency walk stops at one level, to keep the prompt small. A bug two calls away from what the test touches is still out of reach.
- Compilation errors are out of scope: if the project does not compile, it stops.
- On a Windows console the Spanish log can show broken accents, depending on the console code page.
