# ai-fix benchmark · qwen2.5-coder:7b

Options: `aifix.numGpu=0 aifix.numThread=4`

Run on 2026-10-24. **2/10 fixed** (visible tests green), **2/10 real fixes** (hidden tests green too). Average per case: 63 s in the model, 79 s in total.

| Case | Bug | Result | Attempts | Model | Hidden tests | Total |
|---|---|---|---|---|---|---|
| 01-paginator | Off-by-one: each page drops its last item. | not fixed | 3 | 97 s | - | 107 s |
| 02-money-split | Integer division: splitting money loses the remainder cents. | not fixed | 3 | 79 s | - | 89 s |
| 03-null-nickname | NullPointerException: a user without a nickname breaks the display name. | real fix | 2 | 30 s | 3/3 | 63 s |
| 04-missing-hashcode | equals without hashCode: equal coordinates are not equal inside a HashSet. | not fixed | 3 | 100 s | - | 123 s |
| 05-slug | String handling: repeated or surrounding spaces produce double dashes. | not fixed | 3 | 44 s | - | 53 s |
| 06-age | Dates: age counts a year before the birthday has happened. | not fixed | 3 | 46 s | - | 72 s |
| 07-shared-counter | Shared state: a static field makes every counter share one count. | real fix | 1 | 13 s | 2/2 | 39 s |
| 08-remove-while-iterating | Collections: removing by index while looping skips the next element. | not fixed | 3 | 50 s | - | 59 s |
| 09-version-compare | Comparison: versions compared as text put 1.10 before 1.9. | not fixed | 3 | 90 s | - | 99 s |
| 10-tax-category-case | Bug in another class: the tax table is case-sensitive, the test only uses Invoice. | not fixed | 3 | 78 s | - | 86 s |

## Diffs

### 03-null-nickname

```diff
--- a/src/main/java/bench/Names.java
+++ b/src/main/java/bench/Names.java
@@ -6,7 +6,7 @@
 
     /** The nickname if the user has one, otherwise first and last name. */
     public static String displayName(User user) {
-        if (user.nickname().isBlank()) {
+        if (user.nickname() == null || user.nickname().isBlank()) {
             return user.first() + " " + user.last();
         }
         return user.nickname();
```

### 07-shared-counter

```diff
--- a/src/main/java/bench/Counter.java
+++ b/src/main/java/bench/Counter.java
@@ -2,7 +2,7 @@
 
 public final class Counter {
 
-    private static int count;
+    private int count;
 
     public void increment() {
         count++;
```

