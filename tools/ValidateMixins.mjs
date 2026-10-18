#!/usr/bin/env node
/**
 * Pre-flight check voor mixins.
 *
 * Controleert per mixin:
 *  - bestaat de target-class;
 *  - bestaat elke `method = "..."` selector (naam en/of volledige descriptor);
 *  - past de handler-parameterlijst bij (exact) één overload — anders kiest
 *    Mixin's refmap stilzwijgend de eerste overload en crasht de client;
 *  - bestaan @Accessor/@Invoker namen.
 *
 * Gebruik:
 *   node tools/ValidateMixins.mjs <src-root> <minecraft.jar> [--verbose]
 *
 * Exitcode 1 bij mismatch.
 */
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const srcRoot = process.argv[2] ?? "src/main/java";
const mcJar = process.argv[3];
const verbose = process.argv.includes("--verbose");

if (!mcJar || !fs.existsSync(mcJar)) {
  console.error("usage: node tools/ValidateMixins.mjs <src-root> <minecraft.jar>");
  process.exit(2);
}

const INJECTORS = [
  "Inject", "Redirect", "ModifyReturnValue", "ModifyVariable", "ModifyExpressionValue",
  "ModifyArg", "ModifyArgs", "WrapOperation", "WrapWithCondition", "ModifyReceiver",
];
const CALLBACK_TYPES = /^(CallbackInfo|CallbackInfoReturnable)$/;
const PRIMS = {
  int: "I", boolean: "Z", void: "V", long: "J", double: "D",
  float: "F", char: "C", byte: "B", short: "S",
};

/* ---------------------------------------------------------------- helpers */

function stripComments(s) {
  return s
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/(^|[^:])\/\/[^\n]*/g, "$1");
}

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (entry.name.endsWith(".java")) out.push(full);
  }
  return out;
}

function parseImports(src) {
  const map = new Map();
  const re = /^\s*import\s+(?:static\s+)?([\w.$]+);/gm;
  let m;
  while ((m = re.exec(src))) {
    const fqn = m[1];
    map.set(fqn.slice(fqn.lastIndexOf(".") + 1), fqn);
  }
  return map;
}

/** Leest `( ... )` op `openIndex`; negeert haakjes binnen string-literals. */
function balanced(src, openIndex) {
  let depth = 0;
  let inString = false;
  for (let i = openIndex; i < src.length; i++) {
    const c = src[i];
    if (inString) {
      if (c === "\\") i++;
      else if (c === '"') inString = false;
      continue;
    }
    if (c === '"') inString = true;
    else if (c === "(") depth++;
    else if (c === ")") {
      depth--;
      if (depth === 0) return { text: src.slice(openIndex + 1, i), end: i + 1 };
    }
  }
  return null;
}

/** Splist op top-level komma's (respecteert <>, (), []). */
function splitTopLevel(s) {
  const out = [];
  let depth = 0;
  let start = 0;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === "<" || c === "(" || c === "[") depth++;
    else if (c === ">" || c === ")" || c === "]") depth--;
    else if (c === "," && depth === 0) {
      out.push(s.slice(start, i));
      start = i + 1;
    }
  }
  out.push(s.slice(start));
  return out.map((p) => p.trim()).filter(Boolean);
}

const JAVA_LANG = new Set([
  "String", "Object", "Integer", "Long", "Double", "Float", "Boolean", "Byte",
  "Short", "Character", "Number", "Throwable", "Exception", "RuntimeException",
  "Runnable", "CharSequence", "Iterable", "Comparable",
]);

/** FQN -> internal name; geneste classes worden `Outer$Inner`. */
function fqnToInternal(name) {
  const segs = name.split(".");
  let i = 0;
  while (i < segs.length - 1 && /^[a-z]/.test(segs[i])) i++;
  const pkg = segs.slice(0, i).join("/");
  const cls = segs.slice(i).join("$");
  return pkg ? `${pkg}/${cls}` : cls;
}

function typeToDesc(t, imports, typeVars) {
  t = t.trim();
  const generic = t.indexOf("<");
  if (generic >= 0) t = t.slice(0, generic).trim();
  if (t.endsWith("...")) return "[" + typeToDesc(t.slice(0, -3), imports, typeVars);
  if (t.endsWith("[]")) return "[" + typeToDesc(t.slice(0, -2), imports, typeVars);
  if (PRIMS[t]) return PRIMS[t];
  if (typeVars?.has(t)) return typeVars.get(t);

  let fqn = imports.get(t) ?? t;
  if (!fqn.includes(".")) {
    // geen import: java.lang of een onopgeloste simpele naam
    if (JAVA_LANG.has(t)) fqn = `java.lang.${t}`;
    else return null;
  }
  return `L${fqnToInternal(fqn)};`;
}

/** @Mixin target classes. */
function extractTargets(src, imports) {
  const idx = src.indexOf("@Mixin");
  if (idx < 0) return [];
  const open = src.indexOf("(", idx);
  if (open < 0) return [];
  const args = balanced(src, open)?.text ?? "";
  const targets = [];
  for (const s of args.match(/"([\w.$]+)"/g) ?? []) targets.push(s.slice(1, -1));
  for (const m of args.match(/([\w$]+)\s*\.\s*class/g) ?? []) {
    const simple = m.replace(/\s*\.\s*class$/, "").trim();
    targets.push(imports.get(simple) ?? simple);
  }
  return [...new Set(targets)];
}

/**
 * `target = "Lfoo/Bar;baz(...)V"` / `"Lfoo/Bar;field:Z"` uit dezelfde annotatie.
 * Wildcards en niet-descriptor targets worden overgeslagen.
 */
function extractAtTargets(text) {
  const out = [];
  for (const m of text.matchAll(/target\s*=\s*"([^"]+)"/g)) {
    const raw = m[1];
    const ref = raw.startsWith("L") ? raw : raw.split("|").find((p) => p.startsWith("L"));
    if (!ref || ref.includes("*")) continue;
    const semi = ref.indexOf(";");
    if (semi < 0) continue;
    out.push({ cls: ref.slice(1, semi).replace(/\//g, "."), member: ref.slice(semi + 1) });
  }
  return out;
}

/**
 * Alle injector-annotaties met hun handler-signatuur.
 * -> [{ annotation, selectors: [{ selector, optional }], params: [desc|null] }]
 */
function extractInjections(src, imports) {
  const out = [];
  for (const injector of INJECTORS) {
    const re = new RegExp(`@${injector}\\s*\\(`, "g");
    let m;
    while ((m = re.exec(src))) {
      const open = src.indexOf("(", m.index);
      const body = balanced(src, open);
      if (!body) continue;
      const text = body.text;

      // selectors
      const selectors = [];
      const methodAttr = text.match(/\bmethod\s*=\s*/);
      if (methodAttr) {
        const rest = text.slice(methodAttr.index + methodAttr[0].length);
        const isArray = rest.trimStart().startsWith("{");
        const literal = isArray
          ? rest.match(/^\{([\s\S]*?)\}/)
          : rest.match(/^("[^"]*"(?:\s*\+\s*"[^"]*")*)/);
        if (literal) {
          const parts = literal[1].match(/"[^"]*"/g) ?? [];
          const values = isArray
            ? parts.map((p) => JSON.parse(p))
            : [parts.map((p) => JSON.parse(p)).join("")];
          const optional = /\brequire\s*=\s*0\b/.test(text);
          for (const v of values) if (v.trim()) selectors.push({ selector: v.trim(), optional });
        }
      }
      if (selectors.length === 0) continue;

      // handler-declaratie na de annotatie (alleen echte method-declaraties,
      // niet een `if (` in de body). Params met balanced() omdat @Local(...)
      // zelf haakjes bevat.
      const after = src.slice(body.end);
      const declRe = /\b(?:private|public|protected)\s+(?:static\s+)?(?:final\s+)?[\w$.<>\[\],\s]+?\s+([\w$]+)\s*\(/;
      const decl = after.match(declRe);
      let handlerName = "?";
      const params = [];
      let usesLocals = false;
      if (decl) {
        handlerName = decl[1];
        const pOpen = decl.index + decl[0].length - 1;
        const pBody = balanced(after, pOpen);
        if (pBody) {
          usesLocals = /@Local\b/.test(pBody.text);
          for (const raw of splitTopLevel(pBody.text)) {
            // `@Local(argsOnly = true) Camera camera` -> `Camera`
            const cleaned = raw.replace(/@[\w$.]+(\s*\([^)]*\))?/g, "").trim();
            const type = cleaned.replace(/\s+\w+$/, "").trim();
            const simple = type.split(/[<\[ ]/)[0].trim();
            if (CALLBACK_TYPES.test(simple)) continue;
            params.push(typeToDesc(type, imports));
          }
        }
      }
      out.push({
        annotation: injector,
        selectors,
        params,
        handler: handlerName,
        usesLocals,
        atTargets: extractAtTargets(text),
      });
    }
  }
  return out;
}

/* ------------------------------------------------------------- java model */

const classCache = new Map();
const MODIFIERS = new Set([
  "public", "private", "protected", "static", "final", "abstract", "native",
  "synchronized", "transient", "volatile", "default", "strictfp", "sealed",
]);

function loadClass(fqn) {
  if (classCache.has(fqn)) return classCache.get(fqn);
  let result = { methods: [], fields: [], typeVars: new Map(), supers: [] };
  try {
    const out = execFileSync("javap", ["-p", "-classpath", mcJar, fqn], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
      maxBuffer: 64 << 20,
    });

    // superclass + interfaces (voor overgeërfde members)
    const declLine = out.split(/\r?\n/).find((l) => /\b(class|interface|enum)\b/.test(l) && l.includes("{"));
    if (declLine) {
      const sup = declLine.match(/\bextends\s+([\w.$]+)/);
      if (sup) result.supers.push(sup[1]);
      const impl = declLine.match(/\bimplements\s+([^{]+)/);
      if (impl) {
        for (const i of impl[1].split(",")) {
          const name = i.replace(/<.*$/, "").trim();
          if (/^[\w.$]+$/.test(name)) result.supers.push(name);
        }
      }
    }

    // type-variabelen uit de class-header: <T extends net.minecraft...Foo & ...>
    const header = out.split(/\r?\n/).find((l) => /\bclass\b|\binterface\b/.test(l) && l.includes("<"));
    if (header) {
      const gen = header.slice(header.indexOf("<") + 1, header.lastIndexOf(">"));
      for (const decl of splitTopLevel(gen)) {
        const m = decl.match(/^(\w+)\s+extends\s+([\w.$]+)/);
        if (m) result.typeVars.set(m[1], `L${fqnToInternal(m[2])};`);
      }
    }

    for (const raw of out.split(/\r?\n/)) {
      let line = raw.trim().replace(/;$/, "");
      if (!line || line === "}" || line.startsWith("Compiled from")) continue;
      if (line.startsWith("static {}")) continue;

      if (line.includes("(")) {
        line = line.replace(/\)\s+throws\s+.*$/, ")");
        const open = line.indexOf("(");
        const close = line.lastIndexOf(")");
        if (close < open) continue;
        const paramsRaw = line.slice(open + 1, close).trim();
        const before = line.slice(0, open).trim().split(/\s+/);
        let name = before.pop() ?? "";
        name = name.includes(".") ? "<init>" : name.replace(/<.*$/, "");

        // method-level generics: <E extends Foo, S extends Bar>
        const vars = new Map(result.typeVars);
        const headerText = before.join(" ");
        const gOpen = headerText.indexOf("<");
        const gClose = headerText.lastIndexOf(">");
        if (gOpen >= 0 && gClose > gOpen) {
          for (const decl of splitTopLevel(headerText.slice(gOpen + 1, gClose))) {
            const g = decl.match(/^(\w+)\s+extends\s+([\w.$]+)/);
            if (g) vars.set(g[1], `L${fqnToInternal(g[2])};`);
          }
        }

        const retToken = before.filter((t) => !MODIFIERS.has(t)).pop() ?? "void";
        const params = paramsRaw
          ? splitTopLevel(paramsRaw).map((p) => typeToDesc(p, new Map(), vars))
          : [];
        const ret = typeToDesc(retToken, new Map(), vars);
        result.methods.push({
          name,
          params: `(${params.join("")})`,
          ret,
          full: `${name}(${params.join("")})${ret}`,
        });
        continue;
      }

      const parts = line.split(/\s+/).filter((t) => !MODIFIERS.has(t));
      const name = (parts.pop() ?? "").replace(/\[\]$/, "");
      if (name && /^[\w$]+$/.test(name)) result.fields.push(name);
    }
  } catch {
    result = null; // class bestaat niet (optionele mod / andere versie)
  }
  classCache.set(fqn, result);
  return result;
}


/** Zoekt een member in de class en al zijn supertypes. */
function findInHierarchy(cls, predicate) {
  const seen = new Set();
  const queue = [cls];
  while (queue.length) {
    const fqn = queue.shift();
    if (seen.has(fqn)) continue;
    seen.add(fqn);
    const loaded = loadClass(fqn);
    if (!loaded) continue;
    if (predicate(loaded)) return true;
    for (const s of loaded.supers) queue.push(s);
  }
  return false;
}

/** class bestaat in de Minecraft-jar (of is van een andere classpath -> skip). */
function isMinecraftClass(fqn) {
  return fqn.startsWith("net.minecraft.") || fqn.startsWith("com.mojang.");
}

/* ------------------------------------------------------------------- main */

const problems = [];
const stats = { classes: 0, selectors: 0, accessors: 0, handlers: 0, atTargets: 0 };

for (const file of walk(srcRoot)) {
  const src = stripComments(fs.readFileSync(file, "utf8"));
  if (!src.includes("@Mixin")) continue;
  const rel = path.relative(process.cwd(), file);
  const imports = parseImports(src);
  const targets = extractTargets(src, imports);
  const injections = extractInjections(src, imports);

  for (const target of targets) {
    const cls = loadClass(target);
    if (!cls) {
      if (verbose) console.log(`skip ${rel}: ${target} niet gevonden`);
      continue;
    }
    stats.classes++;

    for (const inj of injections) {
      for (const { selector, optional } of inj.selectors) {
        stats.selectors++;
        const paren = selector.indexOf("(");
        const name = paren < 0 ? selector : selector.slice(0, paren);
        const candidates = cls.methods.filter((m) => m.name === name);
        if (candidates.length === 0) {
          if (!optional) problems.push(`${rel}\n  ${target} heeft geen methode '${name}'`);
          continue;
        }

        if (paren >= 0) {
          const want = selector;
          const hit = candidates.find((m) => m.full === want || `${m.name}${m.params}` === want);
          if (!hit && !optional) {
            problems.push(
              `${rel}\n  selector : ${want}\n  werkelijk: ${candidates.map((m) => m.full).join("\n             ")}`,
            );
          }
          continue;
        }

        // Naam-only selector op een overloaded methode is altijd riskant: de
        // refmap-AP kiest één overload (de eerste), ook als de handler bij een
        // andere hoort -> InvalidInjectionException bij het opstarten.
        if (candidates.length > 1) {
          problems.push(
            `${rel}\n  '${name}' heeft ${candidates.length} overloads; geef een volledige descriptor:` +
              `\n             ${candidates.map((m) => m.full).join("\n             ")}`,
          );
          continue;
        }

        // Enkel overload: handler-params moeten er exact op passen.
        // Alleen bij @Inject is de handler-signatuur gelijk aan de target-methode;
        // @Redirect/@WrapOperation/@ModifyExpressionValue hangen van de call af.
        // Handlers met @Local(...) nemen niet de target-params over -> overslaan.
        if (inj.annotation !== "Inject" || inj.usesLocals) continue;
        if (inj.params.length === 0 || inj.params.includes(null)) continue;
        stats.handlers++;
        const wantParams = `(${inj.params.join("")})`;
        const matches = candidates.filter((m) => m.params === wantParams);
        if (matches.length === 1) continue;
        if (!optional) {
          problems.push(
            `${rel}\n  handler ${inj.handler}${wantParams} past op geen enkele '${name}'\n` +
              `  overloads: ${candidates.map((m) => m.full).join("\n             ")}`,
          );
        }
      }

      const accessors = [...inj.selectors];
      void accessors;
    }

    // @At targets (INVOKE/FIELD): bestaat de class + member nog?
    for (const inj of injections) {
      for (const { cls, member } of inj.atTargets) {
        stats.atTargets++;
        const owner = loadClass(cls);
        if (!owner) {
          if (isMinecraftClass(cls)) {
            problems.push(`${rel}\n  @At target class ${cls} bestaat niet (meer)`);
          } else if (verbose) {
            console.log(`skip ${rel}: @At target ${cls} zit niet in de MC-jar`);
          }
          continue;
        }
        const paren = member.indexOf("(");
        if (paren >= 0) {
          const mName = member.slice(0, paren);
          const rest = member.slice(paren);
          const paramEnd = rest.lastIndexOf(")");
          const params = rest.slice(0, paramEnd + 1);
          const ret = rest.slice(paramEnd + 1);
          const ok = findInHierarchy(cls, (c) =>
            c.methods.some(
              (m) => m.full === `${mName}${params}${ret}` || `${m.name}${m.params}` === `${mName}${params}`,
            ),
          );
          if (!ok) {
            problems.push(
              `${rel}\n  @At INVOKE target ${mName}${params}${ret} niet in ${cls} (of supertypes)\n` +
                `   kandidaten: ${owner.methods.filter((m) => m.name === mName).map((m) => m.full).join(", ") || "(geen)"}`,
            );
          }
          continue;
        }
        const [fName] = member.split(":");
        if (!findInHierarchy(cls, (c) => c.fields.includes(fName))) {
          problems.push(`${rel}\n  @At FIELD target ${fName} niet in ${cls} (of supertypes)`);
        }
      }
    }

    const accessorRe = /@(Accessor|Invoker)\s*\(\s*(?:value\s*=\s*)?"([^"]+)"\s*\)/g;
    let am;
    while ((am = accessorRe.exec(src))) {
      stats.accessors++;
      const member = am[2];
      const hit = findInHierarchy(target, (c) => c.methods.some((m) => m.name === member) || c.fields.includes(member));
      if (!hit) problems.push(`${rel}\n  @${am[1]}('${member}') bestaat niet in ${target} (of supertypes)`);
    }
  }
}

console.log(
  `gecontroleerd: ${stats.classes} targets, ${stats.selectors} selectors, ` +
    `${stats.handlers} handlers, ${stats.atTargets} @At-targets, ${stats.accessors} accessors`,
);
if (problems.length === 0) {
  console.log("OK - geen mixin-mismatches");
  process.exit(0);
}
console.error(`\n${problems.length} probleem(en):\n`);
for (const p of problems) console.error(`- ${p}\n`);
process.exit(1);
