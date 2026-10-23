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
2. **Build the context.** Only the failing test methods (extracted with JavaParser, not whole files), their trimmed stack traces, and the `src/main` classes that are suspects: the ones in the stack trace and the ones the test uses.
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

## Benchmark

10 small Java bugs, each with **visible tests** (the ones that fail and the model sees) and **hidden tests** the model never sees. A fix only counts as real if the hidden tests pass too, so a fix tuned to the visible test does not count. `java bench/Bench.java --check` verifies the benchmark itself: the buggy code fails the visible tests and the reference fix passes both.

With `qwen3-coder:30b` on my laptop (RX 6800M, Ryzen 9 5900HX):

**9/10 fixed, 8/10 real fixes, 41 s of model time per bug on average.**

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
| 10 | bug in `TaxTable`, but the test only uses `Invoice` | **not fixed**: the context only included the classes the test touches, so the model never saw `TaxTable` |

Full table, timings and every diff: [bench/results/qwen3-coder_30b.md](bench/results/qwen3-coder_30b.md).

## What's unfinished

- Case 10 shows the main gap: the context should also include the `src/main` classes that the suspects call, not only the ones the test touches.
- Compilation errors are out of scope: if the project does not compile, it stops.
- On a Windows console the Spanish log can show broken accents, depending on the console code page.
