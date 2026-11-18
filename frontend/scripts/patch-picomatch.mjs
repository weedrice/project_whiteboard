import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// Temporary local security patch; the npm version stays 4.0.7 so audit findings
// remain visible until an official fixed release replaces this patch.
// Reviewed upstream proposal: https://github.com/micromatch/picomatch/pull/237
// CVE-2026-112366 / GHSA-fc5q-39xc-vjpf. Upstream code is MIT licensed.
const ORIGINAL_SHA256 = '4e1e05447668aa1e94366c5516a2bcd8f5fff800cc92b1d0cd7d77703b8618c4'
const PATCHED_SHA256 = '93c5b566c2ade86d634892d5d3f11a62c264dd387b0e937eed0e9c402822170a'
const PATCH = String.raw`@@ -270,6 +270,53 @@
   return chars;
 };
${' '}
+// Local hardening of upstream PR #237: reuse the parser so quoted stars and
+// POSIX classes are not mistaken for wildcards. Ambiguous repetitions use the
+// existing fail-closed risky-extglob path rather than an unsafe nested repeat.
+const analyzeSingleStarBranch = (branch, options) => {
+  const parsed = parse(branch, {
+    ...options,
+    fastpaths: false,
+    strictSlashes: true,
+    maxExtglobRecursion: false
+  });
+
+  if (!parsed.tokens.some((token, index, tokens) => token.type === 'star'
+    && !(tokens[index + 1]?.type === 'paren' && tokens[index + 1]?.extglob))) return;
+  if (parsed.quotes || parsed.brackets || options.bash === true) return { risky: true };
+
+  for (const token of parsed.tokens) {
+    if (token.type === 'star' || token.type === 'qmark' || token.type === 'dot') continue;
+
+    if (token.type === 'bracket') {
+      try {
+        const character = new RegExp('^(?:' + (token.output || token.value) + ')$');
+        if (character.test('/') || (options.windows && character.test('\\'))) {
+          return { risky: true };
+        }
+      } catch {
+        return { risky: true };
+      }
+      continue;
+    }
+
+    if (token.type !== 'bos' && token.type !== 'text' && token.type !== 'comma') return { risky: true };
+    const text = token.value;
+    for (let i = 0; i < text.length; i++) {
+      let ch = text[i];
+      if (ch === '\\') {
+        ch = text[++i];
+        // Only known escaped punctuation is a proven literal, never a hidden
+        // separator such as a hexadecimal/unicode escape or Windows backslash.
+        if (!ch || !'.*+?^$()[]{}|-"'.includes(ch)) return { risky: true };
+      }
+      if (ch === '/') return { risky: true };
+    }
+  }
+
+  return { risky: false, collapse: true };
+};
+
 const repeatedExtglobRecursion = pattern => {
   let depth = 0;
   let value = pattern.trim();
@@ -341,6 +388,11 @@
     return combinable
       ? { risky: true, safeOutput: buildCharClassStar([...new Set(safeChars)]) }
       : { risky: true };
+  }
+
+  if (branches.length === 1) {
+    const singleStar = analyzeSingleStarBranch(branches[0], options);
+    if (singleStar) return singleStar;
   }
${' '}
   return { risky: false };
@@ -565,6 +617,10 @@
       return;
     }
${' '}
+    if (analysis.collapse === true && (token.type === 'plus' || token.type === 'star')) {
+      token.close = token.type === 'plus' ? ')' : ')?';
+    }
+
     let output = token.close + (opts.capture ? ')' : '');
     let rest;
${' '}
`

const digest = source => createHash('sha256').update(source).digest('hex')

export function patchPicomatch(root, { checkOnly = false } = {}) {
    const lock = JSON.parse(readFileSync(resolve(root, 'package-lock.json'), 'utf8'))
    const entries = Object.entries(lock.packages).filter(([name]) =>
        name === 'node_modules/picomatch' || name.endsWith('/node_modules/picomatch'))
    if (entries.length === 0) throw new Error('No locked picomatch found; review and retire the temporary patch')

    for (const [name, info] of entries) {
        if (info.version !== '4.0.7') throw new Error(`Review picomatch security patch for ${name}@${info.version}`)
        const path = resolve(root, name, 'lib/parse.js')
        const installed = JSON.parse(readFileSync(resolve(root, name, 'package.json'), 'utf8'))
        if (installed.version !== info.version) throw new Error(`Installed picomatch differs from lock: ${name}`)
        let source = readFileSync(path, 'utf8').replace(/\r\n/g, '\n')
        if (digest(source) === PATCHED_SHA256) continue
        if (checkOnly) throw new Error(`Missing verified picomatch security patch: ${name}; run npm run security:patch`)
        if (digest(source) !== ORIGINAL_SHA256) throw new Error(`Unrecognized picomatch source: ${name}; refusing to modify`)

        for (const hunk of PATCH.split(/^@@.*@@.*\n/m).slice(1)) {
            const lines = hunk.split('\n')
            const before = lines.filter(line => /^[ -]/.test(line)).map(line => line.slice(1)).join('\n')
            const after = lines.filter(line => /^[ +]/.test(line)).map(line => line.slice(1)).join('\n')
            if (source.split(before).length !== 2) throw new Error(`Unexpected patch context: ${name}`)
            source = source.replace(before, () => after)
        }
        if (digest(source) !== PATCHED_SHA256) throw new Error(`Security patch verification failed: ${name}`)
        writeFileSync(path, source, 'utf8')
    }
    return entries.length
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
    const checkOnly = process.argv.includes('--check')
    const count = patchPicomatch(root, { checkOnly })
    console.log(`Verified picomatch security patch in ${count} locked installation(s)`)
}
