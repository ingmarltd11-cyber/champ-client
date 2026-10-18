import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Maakt gedecompileerde intermediary-code leesbaar: class_123/method_123/field_123
 * -> Mojang-namen (Minecraft, setScreen, player, ...). Pass 2: descriptor-based
 * join (lost overloads als setScreen op), inner-class-import-tracking en
 * mixin-target-strings ("Lnet/minecraft/class_...;method_...").
 *
 * Gebruik: java ReadableRemap <client-mojmap.txt> <mappings.tiny> <srcdir> <dstdir>
 * Werkt op een KOPIE (dstdir wordt opnieuw aangemaakt), nooit in place.
 */
public class ReadableRemap {
    final MapConvert mc = new MapConvert();
    final Map<String, String> classRev = new HashMap<>();       // inter internal -> moj dotted
    final Map<String, String> classRevDollar = new HashMap<>(); // inter internal -> moj internal (/ + $)
    final Map<String, String> offToMoj = new HashMap<>();       // official internal -> moj dotted
    final Map<String, String> offToMojSimple = new HashMap<>(); // official (ook $inners) -> moj simple
    final Map<String, String> methodRev = new HashMap<>();
    final Map<String, Set<Integer>> methodArities = new HashMap<>();
    final Set<String> methodAmbig = new HashSet<>();
    final Map<String, String> fieldRev = new HashMap<>();
    final Set<String> fieldAmbig = new HashSet<>();

    int files = 0, classRepl = 0, memberRepl = 0, strRepl = 0, vfRemoved = 0;
    final List<String> warnings = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        ReadableRemap r = new ReadableRemap();
        String mojTxt = Files.readString(Paths.get(args[0]));
        String tinyTxt = Files.readString(Paths.get(args[1]));
        r.mc.loadMappings(mojTxt, tinyTxt);
        r.buildReverse(mojTxt, tinyTxt);
        Path src = Paths.get(args[2]), dst = Paths.get(args[3]);
        if (Files.exists(dst)) deleteRec(dst);
        List<Path> fileList = new ArrayList<>();
        try (var s = Files.walk(src)) {
            s.filter(p -> p.toString().endsWith(".java")).forEach(fileList::add);
        }
        Collections.sort(fileList);
        for (Path f : fileList) r.process(src, f, dst);
        System.out.println("files=" + r.files + " classRepl=" + r.classRepl
            + " memberRepl=" + r.memberRepl + " strRepl=" + r.strRepl + " vfRemoved=" + r.vfRemoved);
        Map<String, Integer> groups = new TreeMap<>();
        for (String w : r.warnings) {
            int c = w.indexOf(":");
            groups.merge(c < 0 ? w : w.substring(0, c + 4), 1, Integer::sum);
        }
        for (var e : groups.entrySet()) System.out.println("WARNx" + e.getValue() + " " + e.getKey());
        for (int i = 0; i < Math.min(60, r.warnings.size()); i++) System.out.println("WARN " + r.warnings.get(i));
    }

    static void deleteRec(Path p) throws Exception {
        if (Files.isDirectory(p)) { try (var s = Files.list(p)) { for (Path c : s.toList()) deleteRec(c); } }
        Files.delete(p);
    }

    // ---------- reverse tabellen ----------
    static class MojRec { String simple, name; List<String> args; }
    static class TinyRec { String inter; List<String> args; }

    void buildReverse(String mojTxt, String tinyTxt) {
        for (var e : mc.classMojToOff.entrySet()) {
            String mojDot = e.getKey().replace('/', '.');
            offToMoj.put(e.getValue(), mojDot);
            String simple = mojDot.contains(".") ? mojDot.substring(mojDot.lastIndexOf('.') + 1) : mojDot;
            // inner: com.foo.Outer$Inner -> simple Inner (na laatste $ of .)
            int d = Math.max(simple.lastIndexOf('$'), -1);
            String s2 = d >= 0 ? simple.substring(d + 1) : simple;
            offToMojSimple.putIfAbsent(e.getValue(), s2);
            String inter = mc.classOffToInter.get(e.getValue());
            if (inter != null && !classRev.containsKey(inter)) {
                classRev.put(inter, mojDot.replace('$', '.'));
                classRevDollar.put(inter, mojDot.replace('.', '/'));
            }
        }
        // mojmap methoden met arg-typen
        Pattern cls = Pattern.compile("^(\\S+) -> (\\S+):$");
        Pattern mem = Pattern.compile("^    (?:\\d+:\\d+:)?(?:\\S+ )?(\\S+) ?\\((.*)\\) -> (\\S+)$");
        Map<String, List<MojRec>> mojM = new HashMap<>();
        String mojOwner = null, offOwner = null;
        for (String line : mojTxt.split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            Matcher m = cls.matcher(line);
            if (m.matches()) {
                mojOwner = m.group(1).replace('.', '/'); offOwner = m.group(2);
                String simple = m.group(1).contains(".") ? m.group(1).substring(m.group(1).lastIndexOf('.') + 1) : m.group(1);
                int dl = simple.indexOf('$');
                if (dl < 0) outerToInternal.putIfAbsent(simple, mojOwner);
                continue;
            }
            Matcher mm = mem.matcher(line);
            if (mm.matches() && mojOwner != null) {
                String name = mm.group(1);
                if (name.equals("<init>") || name.equals("<clinit>")) continue;
                List<String> argTypes = splitArgs(MapConvert.stripGenerics(mm.group(2)));
                int arity = argTypes.isEmpty() && mm.group(2).trim().isEmpty() ? 0 : argTypes.size();
                MojRec rec = new MojRec();
                rec.simple = mojOwner.substring(mojOwner.lastIndexOf('/') + 1);
                rec.name = name; rec.args = argTypes;
                mojM.computeIfAbsent(offOwner + "|" + mm.group(3) + "|" + arity, k -> new ArrayList<>()).add(rec);
            }
        }
        // tiny methoden met descriptor-args
        Map<String, List<TinyRec>> tinyM = new HashMap<>();
        String towner = null;
        for (String line : tinyTxt.split("\n")) {
            if (line.startsWith("\t")) line = line.substring(1);
            String[] p = line.split("\t");
            if (p.length == 0) continue;
            if (p[0].equals("c") && p.length >= 3) towner = p[1];
            else if (p[0].equals("m") && p.length >= 4 && towner != null) {
                List<String> dargs = parseDescArgs(p[1]);
                TinyRec rec = new TinyRec();
                rec.inter = p[3]; rec.args = dargs;
                tinyM.computeIfAbsent(towner + "|" + p[2] + "|" + dargs.size(), k -> new ArrayList<>()).add(rec);
            }
        }
        // join met descriptor-vergelijking
        int exact = 0, descSolved = 0;
        Set<String> allKeys = new HashSet<>(mojM.keySet());
        allKeys.addAll(tinyM.keySet());
        for (String key : allKeys) {
            List<MojRec> mrs = mojM.getOrDefault(key, List.of());
            List<TinyRec> trs = tinyM.getOrDefault(key, List.of());
            if (mrs.isEmpty() || trs.isEmpty()) continue;
            if (mrs.size() == 1 && trs.size() == 1) {
                putMethod(trs.get(0).inter, mrs.get(0).name, key);
                exact++;
                continue;
            }
            // N x M: match op arg-typen
            Map<String, Set<String>> interToNames = new HashMap<>();
            for (MojRec mr : mrs) {
                for (TinyRec tr : trs) {
                    if (argsMatch(mr.args, tr.args)) interToNames.computeIfAbsent(tr.inter, k -> new HashSet<>()).add(mr.name);
                }
            }
            for (var e : interToNames.entrySet()) {
                if (e.getValue().size() == 1) { putMethod(e.getKey(), e.getValue().iterator().next(), key); descSolved++; }
                else methodAmbig.add(e.getKey());
            }
            for (TinyRec tr : trs) if (!interToNames.containsKey(tr.inter)) methodAmbig.add(tr.inter);
        }
        System.out.println("revClasses=" + classRev.size() + " revMethods=" + methodRev.size()
            + " (exact=" + exact + " desc=" + descSolved + ") mAmbig=" + methodAmbig.size());
        // velden: ongewijzigd (zeldzaam ambigue)
        Map<String, Set<String>> fByInter = new HashMap<>();
        for (var e : mc.fieldMap.entrySet()) {
            String[] kp = e.getKey().split("\\|");
            if (kp.length < 2) continue;
            fByInter.computeIfAbsent(e.getValue(), k -> new HashSet<>()).add(kp[1]);
        }
        for (var e : fByInter.entrySet()) {
            if (e.getValue().size() == 1) fieldRev.put(e.getKey(), e.getValue().iterator().next());
            else fieldAmbig.add(e.getKey());
        }
        System.out.println("revFields=" + fieldRev.size() + " fAmbig=" + fieldAmbig.size());
    }

    void putMethod(String inter, String moj, String key) {
        String old = methodRev.get(inter);
        if (old != null && !old.equals(moj)) { methodAmbig.add(inter); methodRev.remove(inter); return; }
        methodRev.put(inter, moj);
        int arity = Integer.parseInt(key.substring(key.lastIndexOf('|') + 1));
        methodArities.computeIfAbsent(inter, k -> new HashSet<>()).add(arity);
    }

    static List<String> splitArgs(String s) {
        s = s.trim();
        if (s.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) { out.add(simpleOf(s.substring(start, i).trim())); start = i + 1; }
        }
        out.add(simpleOf(s.substring(start).trim()));
        return out;
    }

    static String simpleOf(String t) {
        t = t.replace('$', '.');
        int i = t.lastIndexOf('.');
        return i >= 0 ? t.substring(i + 1) : t;
    }

    static List<String> parseDescArgs(String desc) {
        List<String> out = new ArrayList<>();
        int i = desc.indexOf('(') + 1;
        while (i < desc.length() && desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            if (c == 'L') { int j = desc.indexOf(';', i); out.add(desc.substring(i + 1, j)); i = j + 1; }
            else if (c == '[') { int j = i; while (desc.charAt(j) == '[') j++; if (desc.charAt(j) == 'L') { int k = desc.indexOf(';', j); out.add(desc.substring(i, k + 1)); i = k + 1; } else { out.add(desc.substring(i, j + 1)); i = j + 1; } }
            else { out.add(String.valueOf(c)); i++; }
        }
        return out;
    }

    static final Map<String, String> PRIM = Map.of(
        "byte", "B", "char", "C", "double", "D", "float", "F",
        "int", "I", "long", "J", "short", "S", "boolean", "Z");

    boolean argsMatch(List<String> mojArgs, List<String> tinyArgs) {
        if (mojArgs.size() != tinyArgs.size()) return false;
        for (int i = 0; i < mojArgs.size(); i++) {
            String m = mojArgs.get(i);
            String t = tinyArgs.get(i);
            boolean arrM = m.endsWith("[]") || m.endsWith("...");
            boolean arrT = t.startsWith("[");
            if (arrM != arrT) return false;
            if (arrM) { m = m.replace("...", "[]"); m = m.substring(0, m.length() - 2); t = t.replaceAll("^\\[+L?", "").replaceAll(";$", ""); }
            String prim = PRIM.get(m);
            if (prim != null) { if (!prim.equals(t)) return false; continue; }
            // tiny official internal (evt met $inners of slashes) -> moj simple
            String tNorm = t.replace('/', '.');
            String mojSimple = offToMojSimple.get(t.replace('/', '/'));
            if (mojSimple == null) {
                int dl = tNorm.lastIndexOf('.');
                String tl = dl >= 0 ? tNorm.substring(dl + 1) : tNorm;
                int dd = tl.indexOf('$');
                if (dd >= 0) tl = tl.substring(dd + 1);
                if (!tl.equals(m)) return false;
            } else if (!mojSimple.equals(m)) return false;
        }
        return true;
    }

    // ---------- per bestand ----------
    void process(Path srcRoot, Path f, Path dstRoot) throws Exception {
        String src = Files.readString(f);
        // 0) decompiler-markers weg
        String[] lines = src.split("\n", -1);
        StringBuilder nols = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String L = lines[i];
            if (L.contains("$VF:") || L.contains("$FF:")) { vfRemoved++; continue; }
            if (L.contains("Please report this to the") && L.contains("issue tracker")) { vfRemoved++; continue; }
            nols.append(L);
            if (i < lines.length - 1) nols.append("\n");
        }
        src = nols.toString();
        boolean[] code = MapConvert.codeMask(src);

        // 1a) FQN-ketens
        Matcher fq = Pattern.compile("net\\.minecraft\\.((?:class_\\d+)(?:\\.class_\\d+)*)").matcher(src);
        Map<String, String> fqnRepl = new LinkedHashMap<>();
        while (fq.find()) {
            if (!code[fq.start()]) continue;
            String moj = resolveChain(fq.group(1).split("\\."));
            if (moj != null) fqnRepl.putIfAbsent(fq.group(0), moj);
            else warnings.add(f.getFileName() + ": NOCLASS-FQN " + fq.group(0));
        }
        // 1b) imports (ook Outer.Inner, ook Mojang-libs als VertexFormat.class_5596)
        Matcher im = Pattern.compile("(?m)^import ((?:[A-Za-z_][A-Za-z0-9_$]*\\.)+class_\\d+(?:\\.class_\\d+)*);").matcher(src);
        Map<String, String> importRepl = new LinkedHashMap<>();
        Map<String, String> innerBare = new HashMap<>(); // kale inner-naam -> moj simple
        while (im.find()) {
            String full = im.group(1);
            String moj;
            if (full.startsWith("net.minecraft.class_")) {
                moj = resolveChain(full.substring("net.minecraft.".length()).split("\\."));
            } else {
                moj = resolveDottedInternal(full);
            }
            if (moj != null) {
                importRepl.putIfAbsent(im.group(0), "import " + moj + ";");
                String[] fsegs = full.split("\\.");
                if (fsegs.length > 1) innerBare.putIfAbsent(fsegs[fsegs.length - 1], moj.substring(moj.lastIndexOf('.') + 1));
            } else warnings.add(f.getFileName() + ": NOCLASS-IMPORT " + im.group(1));
        }
        for (var e : fqnRepl.entrySet()) { src = src.replace(e.getKey(), e.getValue()); classRepl++; }
        for (var e : importRepl.entrySet()) { src = src.replace(e.getKey(), e.getValue()); classRepl++; }
        code = MapConvert.codeMask(src);

        // 1c) kale class_NNN (eerst inner-imports, dan top-level)
        Set<String> bareTokens = new LinkedHashSet<>();
        Matcher bt = Pattern.compile("class_\\d+").matcher(src);
        while (bt.find()) { if (code[bt.start()]) bareTokens.add(bt.group(0)); }
        Map<String, String> existingImp = new HashMap<>();
        Matcher eim = Pattern.compile("(?m)^import ([A-Za-z0-9_$.]+);").matcher(src);
        while (eim.find()) { String q = eim.group(1); existingImp.put(q.substring(q.lastIndexOf('.') + 1), q); }
        Set<String> localNames = new HashSet<>();
        MapConvert.collectNames(src, localNames);
        Map<String, String> bareRepl = new LinkedHashMap<>();
        Set<String> needImports = new LinkedHashSet<>();
        for (String tok : bareTokens) {
            if (innerBare.containsKey(tok)) { bareRepl.put(tok, innerBare.get(tok)); continue; }
            String mojFqn = classRev.get("net/minecraft/" + tok);
            if (mojFqn == null) { warnings.add(f.getFileName() + ": NOCLASS-BARE " + tok); continue; }
            String simple = mojFqn.substring(mojFqn.lastIndexOf('.') + 1);
            if (localNames.contains(simple)) { bareRepl.put(tok, mojFqn); continue; }
            String have = existingImp.get(simple);
            if (have != null && !have.equals(mojFqn)) { bareRepl.put(tok, mojFqn); continue; }
            bareRepl.put(tok, simple);
            if (have == null) needImports.add(mojFqn);
        }
        StringBuilder out = new StringBuilder();
        {
            int i = 0, n = src.length();
            while (i < n) {
                if (code[i] && Character.isJavaIdentifierStart(src.charAt(i))) {
                    int j = i + 1;
                    while (j < n && Character.isJavaIdentifierPart(src.charAt(j))) j++;
                    String t = src.substring(i, j);
                    String r = bareRepl.get(t);
                    if (r != null && !(i > 0 && src.charAt(i - 1) == '.')) { out.append(r); classRepl++; }
                    else out.append(t);
                    i = j; continue;
                }
                out.append(src.charAt(i)); i++;
            }
        }
        src = out.toString();
        if (!needImports.isEmpty()) {
            List<String> imps = new ArrayList<>(needImports);
            Collections.sort(imps);
            StringBuilder ib = new StringBuilder();
            for (String q : imps) ib.append("import ").append(q).append(";\n");
            Matcher lastImp = Pattern.compile("(?m)^import .*;$").matcher(src);
            int at = -1;
            while (lastImp.find()) at = lastImp.end();
            if (at >= 0) src = src.substring(0, at) + "\n" + ib.toString().trim() + "\n" + src.substring(at);
            else {
                Matcher pkg = Pattern.compile("(?m)^package .*;$").matcher(src);
                if (pkg.find()) src = src.substring(0, pkg.end()) + "\n\n" + ib.toString().trim() + "\n" + src.substring(pkg.end());
            }
        }
        code = MapConvert.codeMask(src);

        // 2) members
        Set<String> ownMembers = new HashSet<>();
        MapConvert.collectNames(src, ownMembers);
        Matcher mm = Pattern.compile("(\\.|::)(method_\\d+|field_\\d+)(\\s*\\()?").matcher(src);
        StringBuffer sb = new StringBuffer();
        while (mm.find()) {
            if (!code[mm.start()]) { mm.appendReplacement(sb, Matcher.quoteReplacement(mm.group(0))); continue; }
            String name = mm.group(2);
            boolean isCall = mm.group(3) != null;
            boolean isMethod = name.startsWith("method_");
            if (!isMethod && isCall) {
                warnings.add(f.getFileName() + ": FIELD-AS-CALL " + name);
                mm.appendReplacement(sb, Matcher.quoteReplacement(mm.group(0))); continue;
            }
            String moj = isMethod ? methodRev.get(name) : fieldRev.get(name);
            if (moj == null) {
                warnings.add(f.getFileName() + ": "
                    + (((isMethod && methodAmbig.contains(name)) || (!isMethod && fieldAmbig.contains(name))) ? "AMBIG " : "NOMAP ") + name);
                mm.appendReplacement(sb, Matcher.quoteReplacement(mm.group(0))); continue;
            }
            // GEEN SHADOW/STOP-gates: bron-token method_/field_NNNN is per definitie
            // een MC-methode (eigen methoden heten nooit zo), dus unieke mapping = juist.
            if (isMethod) {
                int ar = callArityFixed(src, mm.end());
                Set<Integer> ok = methodArities.get(name);
                if (ok != null && !ok.contains(ar)) {
                    warnings.add(f.getFileName() + ": ARITY " + name + " call=" + ar + " known=" + ok);
                    mm.appendReplacement(sb, Matcher.quoteReplacement(mm.group(0))); continue;
                }
            }
            mm.appendReplacement(sb, Matcher.quoteReplacement(mm.group(1) + moj + (mm.group(3) == null ? "" : mm.group(3))));
            memberRepl++;
        }
        mm.appendTail(sb);
        src = sb.toString();
        code = MapConvert.codeMask(src);

        // 2b) Outer.class_NNN (outer al Mojang of net.minecraft): los op via outer
        src = resolveOuterInners(src, code, f, existingImp);
        code = MapConvert.codeMask(src);

        // 2c) record-accessors: veld zonder mapping maar met 0-arg accessor op zelfde official
        src = resolveAccessors(src, code, f);

        // 3) mixin-target-strings: "Lnet/minecraft/class_1$class_2;...method_3...field_4"
        src = remapStrings(src);

        Path rel = srcRoot.relativize(f);
        Path dst = dstRoot.resolve(rel);
        Files.createDirectories(dst.getParent());
        Files.writeString(dst, src);
        files++;
    }

    // simpele outer-naam -> internal pad (ook Mojang-libs als com.mojang...)
    final Map<String, String> outerToInternal = new HashMap<>();

    // Outer.class_NNN -> Outer.MojInner. Werkt voor net.minecraft- én Mojang-lib-
    // outers (VertexFormat.class_5596), via imports of de mojmap-klassenlijst.
    String resolveOuterInners(String src, boolean[] code, Path f, Map<String, String> existingImp) {
        Matcher m = Pattern.compile("((?:[A-Za-z_][A-Za-z0-9_$]*\\.)*[A-Za-z_][A-Za-z0-9_$]*)\\.(class_\\d+(?:\\.class_\\d+)*)\\b").matcher(src);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            if (!code[m.start()]) { m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0))); continue; }
            String outer = m.group(1), inners = m.group(2); // inners: class_A[.class_B]*
            // eerst: hele keten als gepunt pad (dekt net.minecraft.- én Mojang-outers)
            String fullHit = resolveDottedInternal(outer + "." + inners);
            String outerInter = null;
            if (fullHit != null) {
                int want = outer.split("\\.").length + inners.split("\\.").length;
                String[] hp = fullHit.split("\\.");
                m.appendReplacement(sb, Matcher.quoteReplacement(String.join(".", Arrays.copyOfRange(hp, Math.max(0, hp.length - want), hp.length))));
                classRepl++;
                continue;
            }
            if (outer.matches("(class_\\d+\\.)*class_\\d+")) {
                // inter-outerketen, bv. class_638 of class_8113.class_8123
                outerInter = "net/minecraft/" + outer.replace('.', '$');
            } else {
                // Mojang-outer, evt. gepunt (Display.TextDisplay): probeer vol, dan last-seg
                String mojOuter = outerToInternal.get(outer.replace('.', '/'));
                if (mojOuter == null) mojOuter = outerToInternal.get(outer.substring(outer.lastIndexOf('.') + 1));
                if (mojOuter == null) {
                    String head = outer.contains(".") ? outer.substring(0, outer.indexOf('.')) : outer;
                    String outerFqn = existingImp.get(head);
                    if (outerFqn != null) {
                        mojOuter = outerFqn.replace('.', '/');
                        if (outer.contains(".")) mojOuter += "/" + outer.substring(outer.indexOf('.') + 1).replace('.', '/');
                    }
                }
                if (mojOuter != null) outerInter = interOuterOf(mojOuter);
            }
            String mojRepl = null;
            if (outerInter != null) {
                String hit = classRev.get(outerInter + "$" + inners.replace('.', '$'));
                if (hit != null) {
                    // evenveel segmenten houden als de match had (bv. 3 -> Display.TextDisplay.Foo)
                    int want = outer.split("\\.").length + inners.split("\\.").length;
                    String[] hp = hit.split("\\.");
                    int from = Math.max(0, hp.length - want);
                    mojRepl = String.join(".", Arrays.copyOfRange(hp, from, hp.length));
                }
            }
            if (mojRepl != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(mojRepl));
                classRepl++;
            } else {
                warnings.add(f.getFileName() + ": NOCLASS-OUTER " + m.group(0));
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // als MapConvert.callArity, maar string/char-args tellen WEL mee
    // ("a", "b" -> 2, niet 0).
    static int callArityFixed(String src, int from) {
        int depth = 1, commas = 0, i = from, n = src.length();
        boolean blank = true;
        int state = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (state == 1) { blank = false; if (c == '\\' && i + 1 < n) i += 2; else { if (c == '"') state = 0; i++; } continue; }
            if (state == 2) { blank = false; if (c == '\\' && i + 1 < n) i += 2; else { if (c == '\'') state = 0; i++; } continue; }
            if (state == 3) { i++; if (c == '\n') state = 0; continue; }
            if (state == 4) { if (c == '*' && i + 1 < n && src.charAt(i + 1) == '/') { i += 2; state = 0; } else i++; continue; }
            if (c == '"') { state = 1; blank = false; i++; continue; }
            if (c == '\'') { state = 2; blank = false; i++; continue; }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') { state = 3; i += 2; continue; }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') { state = 4; i += 2; continue; }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')') { depth--; if (depth == 0) break; }
            else if (c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 1) commas++;
            else if (depth == 1 && !Character.isWhitespace(c)) blank = false;
            i++;
        }
        return blank ? 0 : commas + 1;
    }

    // Veld zonder mapping maar met eenduidige 0-arg accessor (zelfde official-naam,
    // bv. Vec3-record): obj.field_NNN -> obj.x(). Nooit bij toekenning (geen `=` erna).
    String resolveAccessors(String src, boolean[] code, Path f) {
        // tiny veld-regels: offOwner|offNaam -> inter (herbouw compact)
        Matcher mm = Pattern.compile("(?<![A-Za-z0-9_$])(field_\\d+)\\b").matcher(src);
        StringBuffer sb = new StringBuffer();
        while (mm.find()) {
            if (!code[mm.start()]) { mm.appendReplacement(sb, Matcher.quoteReplacement(mm.group(0))); continue; }
            String name = mm.group(1);
            if (fieldRev.containsKey(name) || fieldAmbig.contains(name)) {
                mm.appendReplacement(sb, Matcher.quoteReplacement(mm.group(0))); continue; // al afgehandeld/gewaarschuwd
            }
            // toekenning? -> overslaan
            int j = mm.end();
            while (j < src.length() && Character.isWhitespace(src.charAt(j))) j++;
            if (j < src.length() && src.charAt(j) == '=' && (j + 1 >= src.length() || src.charAt(j + 1) != '=')) {
                warnings.add(f.getFileName() + ": FIELD-ASSIGN " + name);
                mm.appendReplacement(sb, Matcher.quoteReplacement(mm.group(0))); continue;
            }
            String acc = accessorFor(name);
            if (acc != null) {
                mm.appendReplacement(sb, Matcher.quoteReplacement(acc + "()"));
                memberRepl++;
            } else {
                warnings.add(f.getFileName() + ": NOACCESSOR " + name);
                mm.appendReplacement(sb, Matcher.quoteReplacement(mm.group(0)));
            }
        }
        mm.appendTail(sb);
        return sb.toString();
    }

    // inter veld -> accessor-naam via tiny offOwner|offNaam + mojmap-methode met zelfde official
    final Map<String, String> accessorCache = new HashMap<>();
    String accessorFor(String interField) {
        if (accessorCache.containsKey(interField)) return accessorCache.get(interField);
        String found = null;
        // vind tiny veld-regel(s) met dit inter-veld: herleid offOwner|offNaam via rawTinyField
        for (var e : mc.rawTinyField.entrySet()) {
            if (e.getValue().contains(interField)) {
                String offKey = e.getKey(); // offOwner|offNaam
                // mojmap-methode met zelfde offOwner|offNaam, ariteit 0
                Set<String> cands = new HashSet<>();
                for (var me : mc.rawMojMem.entrySet()) {
                    // rawMojMem: mojKey -> set offKey|arity... formaat offOwner|offNaam|arity
                    for (String off : me.getValue()) {
                        if (off.equals(offKey + "|0")) {
                            String mojKey = me.getKey();
                            cands.add(mojKey.substring(mojKey.indexOf('|') + 1, mojKey.lastIndexOf('|')));
                        }
                    }
                }
                if (cands.size() == 1) found = cands.iterator().next();
            }
        }
        accessorCache.put(interField, found);
        return found;
    }

    // vervangt class_/method_/field_-tokens binnen string-literals (mixin @At targets)
    String remapStrings(String src) {
        List<int[]> ranges = new ArrayList<>();
        int i = 0, n = src.length(), state = 0, start = -1;
        while (i < n) {
            char c = src.charAt(i);
            if (state == 0 && c == '"') { state = 1; start = i + 1; i++; continue; }
            if (state == 0 && c == '\'') { state = 2; i++; continue; }
            if (state == 0 && c == '/' && i + 1 < n && src.charAt(i + 1) == '/') { state = 3; i += 2; continue; }
            if (state == 0 && c == '/' && i + 1 < n && src.charAt(i + 1) == '*') { state = 4; i += 2; continue; }
            if (state == 1) {
                if (c == '\\' && i + 1 < n) { i += 2; continue; }
                if (c == '"') { ranges.add(new int[]{start, i}); state = 0; }
                i++; continue;
            }
            if (state == 2) { if (c == '\\' && i + 1 < n) i += 2; else { i++; if (c == '\'') state = 0; } continue; }
            if (state == 3) { i++; if (c == '\n') state = 0; continue; }
            if (state == 4) { if (c == '*' && i + 1 < n && src.charAt(i + 1) == '/') { i += 2; state = 0; } else i++; continue; }
            i++;
        }
        if (ranges.isEmpty()) return src;
        StringBuilder out = new StringBuilder();
        int pos = 0;
        for (int[] rg : ranges) {
            out.append(src, pos, rg[0]);
            String lit = src.substring(rg[0], rg[1]);
            if (lit.contains("class_") || lit.contains("method_") || lit.contains("field_")) {
                StringBuffer lb = new StringBuffer();
                Matcher m = Pattern.compile("net/minecraft/((?:class_\\d+)(?:\\$class_\\d+)*)").matcher(lit);
                while (m.find()) {
                    String moj = classRevDollar.get("net/minecraft/" + m.group(1));
                    m.appendReplacement(lb, Matcher.quoteReplacement(moj != null ? moj : m.group(0)));
                    if (moj != null) strRepl++;
                }
                m.appendTail(lb);
                lit = lb.toString();
                StringBuffer lb2 = new StringBuffer();
                Matcher m2 = Pattern.compile("(method_\\d+|field_\\d+)").matcher(lit);
                while (m2.find()) {
                    String t = m2.group(1);
                    String moj = t.startsWith("method_") ? methodRev.get(t) : fieldRev.get(t);
                    m2.appendReplacement(lb2, Matcher.quoteReplacement(moj != null ? moj : t));
                    if (moj != null) strRepl++;
                }
                m2.appendTail(lb2);
                lit = lb2.toString();
            }
            out.append(lit);
            pos = rg[1];
        }
        out.append(src.substring(pos));
        return out.toString();
    }

    // mojmap-lookup met $-tolerantie: Display/TextDisplay -> Display$TextDisplay.
    String mojToOffFlex(String mojInternal) {
        String hit = mc.classMojToOff.get(mojInternal);
        if (hit != null) return hit;
        String[] parts = mojInternal.split("/");
        for (int k = parts.length - 1; k >= Math.max(0, parts.length - 3) && k > 0; k--) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(i >= k ? '$' : '/');
                sb.append(parts[i]);
            }
            hit = mc.classMojToOff.get(sb.toString());
            if (hit != null) return hit;
        }
        return null;
    }

    // inter-outerpad bij een outer-FQN (Mojang) of kaal class_NNN.
    // Mojang-libs (com.mojang/...) zijn nooit obfuscated: official == Mojang.
    String interOuterOf(String mojInternal) {
        String off = mojToOffFlex(mojInternal);
        String io = off != null ? mc.classOffToInter.get(off) : null;
        if (io == null && (mojInternal.startsWith("com/mojang") || mojInternal.startsWith("com/google")
                || mojInternal.startsWith("it/unimi") || mojInternal.startsWith("org/"))) io = mojInternal;
        return io;
    }

    // P1.P2....Pn.class_A[.class_B] (outer al Mojang/MC-naam) -> Mojang-FQN.
    // net.minecraft.class_A... gaat via resolveChain (oude logica met tail).
    String resolveDottedInternal(String full) {
        String[] segs = full.split("\\.");
        int first = -1;
        for (int i = 0; i < segs.length; i++) if (segs[i].matches("class_\\d+")) { first = i; break; }
        if (first < 0) return null;
        if (first == 2 && segs[0].equals("net") && segs[1].equals("minecraft")) {
            return resolveChain(Arrays.copyOfRange(segs, 2, segs.length));
        }
        String mojOuter = String.join("/", Arrays.copyOf(segs, first));
        // outer kan zelf gepunte inners hebben (Display.TextDisplay): $ varianten proberen
        String io = interOuterOf(mojOuter);
        if (io == null) {
            String[] op = Arrays.copyOf(segs, first);
            for (int k = op.length - 1; io == null && k > 0; k--) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < op.length; i++) {
                    if (i > 0) sb.append(i >= k ? '$' : '/');
                    sb.append(op[i]);
                }
                io = interOuterOf(sb.toString());
            }
        }
        if (io == null) return null;
        StringBuilder key = new StringBuilder(io);
        for (int i = first; i < segs.length; i++) key.append('$').append(segs[i]);
        return classRev.get(key.toString());
    }

    String resolveChain(String[] segs) {
        for (int n = segs.length; n >= 1; n--) {
            StringBuilder inter = new StringBuilder("net/minecraft/");
            for (int k = 0; k < n; k++) { if (k > 0) inter.append("$"); inter.append(segs[k]); }
            String moj = classRev.get(inter.toString());
            if (moj != null) {
                String tail = "";
                if (n < segs.length) tail = "." + String.join(".", Arrays.copyOfRange(segs, n, segs.length));
                return moj + tail;
            }
        }
        return null;
    }
}
