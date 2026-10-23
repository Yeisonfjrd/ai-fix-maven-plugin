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

## What's unfinished

- No benchmark yet. The next step is 10 Java bugs with hidden tests the model never sees, to measure how often a fix is real and not just tuned to the visible test.
- Compilation errors are out of scope: if the project does not compile, it stops.
- On a Windows console the Spanish log can show broken accents, depending on the console code page.
