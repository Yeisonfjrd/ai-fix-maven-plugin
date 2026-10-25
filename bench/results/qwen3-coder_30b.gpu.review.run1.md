# ai-fix benchmark · qwen3-coder:30b

Run on 2026-10-25. **10/10 fixed** (visible tests green), **9/10 real fixes** (hidden tests green too). Average per case: 34 s in the model, 61 s in total.

| Case | Bug | Result | Attempts | Review | Model | Hidden tests | Total |
|---|---|---|---|---|---|---|---|
| 01-paginator | Off-by-one: each page drops its last item. | real fix | 1 | ok | 58 s | 4/4 | 83 s |
| 02-money-split | Integer division: splitting money loses the remainder cents. | real fix | 1 | ok | 22 s | 4/4 | 48 s |
| 03-null-nickname | NullPointerException: a user without a nickname breaks the display name. | real fix | 1 | ok | 21 s | 3/3 | 45 s |
| 04-missing-hashcode | equals without hashCode: equal coordinates are not equal inside a HashSet. | real fix | 1 | ok | 17 s | 2/2 | 41 s |
| 05-slug | String handling: repeated or surrounding spaces produce double dashes. | real fix | 1 | improved | 37 s | 3/3 | 67 s |
| 06-age | Dates: age counts a year before the birthday has happened. | real fix | 1 | improved | 28 s | 3/3 | 58 s |
| 07-shared-counter | Shared state: a static field makes every counter share one count. | real fix | 1 | ok | 15 s | 2/2 | 40 s |
| 08-remove-while-iterating | Collections: removing by index while looping skips the next element. | real fix | 1 | improved | 26 s | 3/3 | 57 s |
| 09-version-compare | Comparison: versions compared as text put 1.10 before 1.9. | real fix | 1 | improved | 46 s | 4/4 | 76 s |
| 10-tax-category-case | Bug in another class: the tax table is case-sensitive, the test only uses Invoice. | **incomplete** | 2 | ok | 66 s | 2/3 | 97 s |

## Diffs

### 01-paginator

```diff
--- a/src/main/java/bench/Paginator.java
+++ b/src/main/java/bench/Paginator.java
@@ -11,7 +11,7 @@
         if (page < 1 || size < 1) throw new IllegalArgumentException("page and size start at 1");
         int from = (page - 1) * size;
         if (from >= items.size()) return List.of();
-        int to = Math.min(from + size - 1, items.size());
+        int to = Math.min(from + size, items.size());
         return items.subList(from, to);
     }
 }
```

### 02-money-split

```diff
--- a/src/main/java/bench/Money.java
+++ b/src/main/java/bench/Money.java
@@ -9,8 +9,9 @@
         if (parts < 1) throw new IllegalArgumentException("parts must be positive");
         long[] out = new long[parts];
         long each = cents / parts;
+        long remainder = cents % parts;
         for (int i = 0; i < parts; i++) {
-            out[i] = each;
+            out[i] = each + (i < remainder ? 1 : 0);
         }
         return out;
     }
```

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

### 04-missing-hashcode

```diff
--- a/src/main/java/bench/Coordinate.java
+++ b/src/main/java/bench/Coordinate.java
@@ -22,4 +22,9 @@
     public boolean equals(Object o) {
         return o instanceof Coordinate c && c.x == x && c.y == y;
     }
+
+    @Override
+    public int hashCode() {
+        return 31 * x + y;
+    }
 }
```

### 05-slug

```diff
--- a/src/main/java/bench/Slug.java
+++ b/src/main/java/bench/Slug.java
@@ -8,6 +8,8 @@
 
     /** Lowercase words joined by a single dash, with no dash at either end. */
     public static String of(String title) {
-        return title.toLowerCase(Locale.ROOT).replace(" ", "-");
+        String result = title.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
+        // Remove leading and trailing dashes
+        return result.replaceAll("^-+|-+$", "");
     }
 }
```

### 06-age

```diff
--- a/src/main/java/bench/Age.java
+++ b/src/main/java/bench/Age.java
@@ -8,6 +8,14 @@
 
     /** Full years lived on `today`. */
     public static int years(LocalDate birth, LocalDate today) {
-        return today.getYear() - birth.getYear();
+        if (birth == null || today == null) {
+            throw new IllegalArgumentException("Birth date and today cannot be null");
+        }
+        int age = today.getYear() - birth.getYear();
+        if (today.getMonthValue() < birth.getMonthValue() || 
+            (today.getMonthValue() == birth.getMonthValue() && today.getDayOfMonth() < birth.getDayOfMonth())) {
+            age--;
+        }
+        return age;
     }
 }
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

### 08-remove-while-iterating

```diff
--- a/src/main/java/bench/Inventory.java
+++ b/src/main/java/bench/Inventory.java
@@ -9,8 +9,12 @@
 
     /** Removes the products that expired before `today`. A product that expires today stays. */
     public static void removeExpired(List<Product> products, LocalDate today) {
-        for (int i = 0; i < products.size(); i++) {
-            if (products.get(i).expires().isBefore(today)) {
+        if (products == null || today == null) {
+            return;
+        }
+        for (int i = products.size() - 1; i >= 0; i--) {
+            Product product = products.get(i);
+            if (product != null && product.expires() != null && product.expires().isBefore(today)) {
                 products.remove(i);
             }
         }
```

### 09-version-compare

```diff
--- a/src/main/java/bench/Versions.java
+++ b/src/main/java/bench/Versions.java
@@ -9,6 +9,33 @@
      * Negative if a is older, positive if a is newer, 0 if equal.
      */
     public static int compare(String a, String b) {
-        return a.compareTo(b);
+        if (a == null && b == null) return 0;
+        if (a == null) return -1;
+        if (b == null) return 1;
+        
+        String[] partsA = a.split("\\.");
+        String[] partsB = b.split("\\.");
+        
+        int maxLength = Math.max(partsA.length, partsB.length);
+        
+        for (int i = 0; i < maxLength; i++) {
+            int numA = i < partsA.length ? parseVersionPart(partsA[i]) : 0;
+            int numB = i < partsB.length ? parseVersionPart(partsB[i]) : 0;
+            
+            if (numA != numB) {
+                return Integer.compare(numA, numB);
+            }
+        }
+        
+        return 0;
     }
+    
+    private static int parseVersionPart(String part) {
+        try {
+            return Integer.parseInt(part);
+        } catch (NumberFormatException e) {
+            // If parsing fails, treat as 0
+            return 0;
+        }
+    }
 }
```

### 10-tax-category-case

```diff
--- a/src/main/java/bench/Invoice.java
+++ b/src/main/java/bench/Invoice.java
@@ -18,7 +18,7 @@
     public long tax() {
         long total = 0;
         for (Line line : lines) {
-            total += line.cents() * taxes.perMille(line.category()) / 1000;
+            total += line.cents() * taxes.perMille(line.category().toUpperCase()) / 1000;
         }
         return total;
     }
```

