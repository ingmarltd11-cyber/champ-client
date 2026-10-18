import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Converteert addon-broncode van MojMap-namen naar intermediary-namen.
 * Gebruik: java MapConvert <client-mojmap.txt> <mappings.tiny> <srcdir> [--write]
 *
 * - Klassen: FQN/import-gebaseerd (veilig).
 * - Members: alleen als de moj-owner tot de in het bestand gebruikte MC-types
 *   behoort EN de naam eenduidig is binnen die types. Stoplijst voor java-namen.
 */
public class MapConvert {
    static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "get","set","is","add","remove","contains","size","clear","put","of","create","copy",
        "equals","hashCode","toString","compareTo","iterator","stream","forEach","addAll",
        "isEmpty","orElse","orElseThrow","orElseGet","ifPresent","value","valueOf","values",
        "keySet","getValue","setValue","getKey","apply","test","accept","run","call","close",
        "read","write","flush","position","limit","remaining","hasRemaining","compare",
        "getAsBoolean","getAsInt","getAsLong","getAsDouble","entries","getOrDefault",
        "removeAll","retainAll","containsAll","compareToIgnoreCase","length","charAt",
        "substring","replace","split","join","format","getOrThrow","isPresent","filter","map",
        "min","max","sorted","findFirst","collect","toList","isBlank","strip","message",
        "getMessage","printStackTrace","getName","getSimpleName","getClass","notify","wait",
        "select","find","has","can","should","will","next","previous","reset","update","tick",
        "register","unregister","listen","dispatch","enable","disable","toggle","show","hide"
    ));

    // mojFQN(internal) -> official(internal)
    final Map<String, String> classMojToOff = new HashMap<>();
    // official(internal) -> inter(internal)
    final Map<String, String> classOffToInter = new HashMap<>();
    // key: mojOwnerSimple|mojName|arity -> interName (alleen eenduidig)
    final Map<String, String> memberMap = new HashMap<>();
    final Map<String, String> globalUnique = new HashMap<>();
    final Set<String> memberAmbigKeys = new HashSet<>();
    // velden: key mojOwnerSimple|mojNaam -> interName (alleen eenduidig)
    final Map<String, String> fieldMap = new HashMap<>();
    final Map<String, String> globalFieldUnique = new HashMap<>();
    final Set<String> fieldAmbigKeys = new HashSet<>();
    // addon-gedefinieerde member-namen: nooit converteren (fail-loud i.p.v. corruptie)
    final Set<String> keepNames = new HashSet<>();
    // mojOwnerSimple -> officialOwner (voor member-join)
    final Map<String, String> ownerSimpleToOff = new HashMap<>();

    int classRepl, memberRepl;
    final List<String> warnings = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        MapConvert mc = new MapConvert();
        String mojTxt = Files.readString(Paths.get(args[0]));
        String tinyTxt = Files.readString(Paths.get(args[1]));
        mc.loadMappings(mojTxt, tinyTxt);
        if (args.length > 2 && args[2].equals("query")) {
            // query <OwnerSimple|class_NNNN> <naam> : toont alle kandidaten
            String owner = args[3], name = args[4];
            if (owner.startsWith("class_")) {
                String inter = "net/minecraft/" + owner;
                for (var e : mc.classOffToInter.entrySet()) {
                    if (e.getValue().equals(inter)) {
                        for (var m : mc.classMojToOff.entrySet()) {
                            if (m.getValue().equals(e.getKey())) {
                                owner = m.getKey().substring(m.getKey().lastIndexOf('/') + 1);
                                System.out.println("owner=" + owner);
                            }
                        }
                    }
                }
            }
            for (int arity = 0; arity <= 6; arity++) {
                String k = owner + "|" + name + "|" + arity;
                if (mc.memberMap.containsKey(k)) System.out.println("METHOD " + k + " -> " + mc.memberMap.get(k));
                if (mc.memberAmbigKeys.contains(k)) System.out.println("METHOD-AMBIG " + k);
            }
            String fk = owner + "|" + name;
            if (mc.fieldMap.containsKey(fk)) System.out.println("FIELD " + fk + " -> " + mc.fieldMap.get(fk));
            if (mc.fieldAmbigKeys.contains(fk)) System.out.println("FIELD-AMBIG " + fk);
            if (mc.globalUnique.containsKey(name)) System.out.println("GLOBAL-METHOD " + name + " -> " + mc.globalUnique.get(name));
            if (mc.globalFieldUnique.containsKey(name)) System.out.println("GLOBAL-FIELD " + name + " -> " + mc.globalFieldUnique.get(name));
            return;
        }
        boolean write = args.length > 3 && args[3].equals("--write");
        Path src = Paths.get(args[2]);
        List<Path> files = new ArrayList<>();
        try (var s = Files.walk(src)) {
            s.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        Collections.sort(files);
        // pre-pass: verzamel ALLE gedeclareerde namen (eigen members beschermen,
        // ook cross-file: addon-methoden worden over bestanden heen aangeroepen)
        for (Path f : files) collectNames(Files.readString(f), mc.keepNames);
        System.out.println("keepNames=" + mc.keepNames.size());
        for (Path f : files) mc.process(f, write);
        System.out.println("files=" + files.size() + " classRepl=" + mc.classRepl
            + " memberRepl=" + mc.memberRepl);
        for (String w : mc.warnings) System.out.println("WARN " + w);
    }

    // client.txt richting: MOJMAP(leesbaar) -> OFFICIAL(obfuscated)
    void loadMappings(String mojTxt, String tinyTxt) {
        // 1) mojmap: klassen + members
        // Methode-regels: '[regels:] [returntype ]naam(args) -> obf' (spatie ok!)
        Pattern cls = Pattern.compile("^(\\S+) -> (\\S+):$");
        Pattern mem = Pattern.compile("^    (?:\\d+:\\d+:)?(?:\\S+ )?(\\S+) ?\\((.*)\\) -> (\\S+)$");
        String mojOwner = null, offOwner = null;
        // mojKey leesbaarOwner|mojNaam|ariteit -> SET offKey (geen overschrijving:
        // superklasse-leden staan inline in subklasse-secties met andermans namen)
        Map<String, Set<String>> mojToOffMember = new HashMap<>();
        // velden: mojOwner|mojNaam -> offOwner|offNaam (geen ariteit)
        Map<String, String> mojToOffField = new HashMap<>();
        Pattern fld = Pattern.compile("^    (?:\\S+ )?(\\S+) -> (\\S+)$");
        for (String line : mojTxt.split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            Matcher m = cls.matcher(line);
            if (m.matches()) {
                mojOwner = m.group(1).replace('.', '/');
                offOwner = m.group(2).replace('.', '/');
                classMojToOff.put(mojOwner, offOwner);
                String simple = mojOwner.substring(mojOwner.lastIndexOf('/') + 1);
                ownerSimpleToOff.putIfAbsent(simple, offOwner);
                if (offToMojSimple == null) offToMojSimple = new HashMap<>();
                offToMojSimple.putIfAbsent(offOwner, simple);
                continue;
            }
            Matcher mm = mem.matcher(line);
            if (mm.matches() && mojOwner != null) {
                String argStr = stripGenerics(mm.group(2)).trim();
                int arity = argStr.isEmpty() ? 0 : argStr.split(",").length;
                mojToOffMember.computeIfAbsent(mojOwner + "|" + mm.group(1) + "|" + arity, k -> new HashSet<>())
                    .add(offOwner + "|" + mm.group(3) + "|" + arity);
                continue;
            }
            // velden hebben geen haakjes: '<type> <naam> -> <obf>'
            if (mojOwner != null && !line.contains("(")) {
                Matcher fm = fld.matcher(line);
                if (fm.matches()) {
                    mojToOffField.put(mojOwner + "|" + fm.group(1), offOwner + "|" + fm.group(2));
                }
            }
        }
        // 2) tiny: klassen + members (methoden en velden gescheiden)
        Map<String, List<String[]>> tinyMems = new HashMap<>(); // key offOwner|offName|arity
        Map<String, Set<String>> tinyFields = new HashMap<>(); // key offOwner|offName
        String towner = null;
        for (String line : tinyTxt.split("\n")) {
            if (line.startsWith("\t")) line = line.substring(1); // member-regels beginnen met tab
            String[] p = line.split("\t");
            if (p.length == 0) continue;
            if (p[0].equals("c") && p.length >= 3) {
                towner = p[1];
                classOffToInter.put(p[1], p[2]);
            } else if (p[0].equals("m") && p.length >= 4 && towner != null) {
                int arity = countArgs(p[1]);
                tinyMems.computeIfAbsent(towner + "|" + p[2] + "|" + arity, k -> new ArrayList<>())
                    .add(new String[]{p[3]});
            } else if (p[0].equals("f") && p.length >= 4 && towner != null) {
                tinyFields.computeIfAbsent(towner + "|" + p[2], k -> new HashSet<>()).add(p[3]);
            }
        }
        // 3) join via mojKey -> offKeys -> inter (elke offKey apart; missers vallen af)
        Map<String, Set<String>> joined = new HashMap<>();
        for (var e : mojToOffMember.entrySet()) {
            String mojKey = e.getKey();
            String mojOwnerFull = mojKey.substring(0, mojKey.indexOf('|'));
            String simple = mojOwnerFull.substring(mojOwnerFull.lastIndexOf('/') + 1);
            String mk = simple + mojKey.substring(mojKey.indexOf('|'));
            for (String offKey : e.getValue()) {
                List<String[]> cands = tinyMems.get(offKey);
                if (cands == null) continue;
                for (String[] c : cands) {
                    joined.computeIfAbsent(mk, k -> new HashSet<>()).add(c[0]);
                }
            }
        }
        for (var e : joined.entrySet()) {
            if (e.getValue().size() == 1) memberMap.put(e.getKey(), e.getValue().iterator().next());
            else memberAmbigKeys.add(e.getKey());
        }
        // globale eenduidige naam -> inter (scope-onafhankelijk)
        Map<String, Set<String>> byName = new HashMap<>();
        for (var e : memberMap.entrySet()) {
            String[] kp = e.getKey().split("\\|");
            if (kp.length < 3) continue;
            byName.computeIfAbsent(kp[1], k -> new HashSet<>()).add(e.getValue());
        }
        for (var e : byName.entrySet()) {
            if (e.getValue().size() == 1 && !STOP.contains(e.getKey())) globalUnique.put(e.getKey(), e.getValue().iterator().next());
        }
        // 4) velden joinen: mojOwner|mojNaam -> offOwner|offNaam -> inter
        Map<String, Set<String>> joinedF = new HashMap<>();
        for (var e : mojToOffField.entrySet()) {
            Set<String> cands = tinyFields.get(e.getValue());
            if (cands == null) continue;
            String mojKey = e.getKey();
            String mojOwnerFull = mojKey.substring(0, mojKey.indexOf('|'));
            String simple = mojOwnerFull.substring(mojOwnerFull.lastIndexOf('/') + 1);
            String mk = simple + mojKey.substring(mojKey.indexOf('|'));
            joinedF.computeIfAbsent(mk, k -> new HashSet<>()).addAll(cands);
        }
        for (var e : joinedF.entrySet()) {
            if (e.getValue().size() == 1) fieldMap.put(e.getKey(), e.getValue().iterator().next());
            else fieldAmbigKeys.add(e.getKey());
        }
        Map<String, Set<String>> byFName = new HashMap<>();
        for (var e : fieldMap.entrySet()) {
            String[] kp = e.getKey().split("\\|");
            if (kp.length < 2) continue;
            byFName.computeIfAbsent(kp[1], k -> new HashSet<>()).add(e.getValue());
        }
        for (var e : byFName.entrySet()) {
            if (e.getValue().size() == 1 && !STOP.contains(e.getKey())) globalFieldUnique.put(e.getKey(), e.getValue().iterator().next());
        }
        // handmatige 26.x -> 1.21.11 verhuizingen (mojInternal -> offInternal)
        classMojToOff.put("net/minecraft/world/entity/projectile/ThrownEnderpearl", "dfc");
        System.out.println("globalUnique=" + globalUnique.size() + " globalFields=" + globalFieldUnique.size());
        System.out.println("classes=" + classMojToOff.size() + " offClasses=" + classOffToInter.size()
            + " mojMems=" + mojToOffMember.size() + " members=" + memberMap.size()
            + " ambig=" + memberAmbigKeys.size()
            + " mojFields=" + mojToOffField.size() + " fields=" + fieldMap.size()
            + " fieldAmbig=" + fieldAmbigKeys.size());
        rawMojMem = mojToOffMember;
        rawMojField = mojToOffField;
        rawTinyMem = tinyMems;
        rawTinyField = tinyFields;
        interToOff = new HashMap<>();
        for (var e : classOffToInter.entrySet()) interToOff.put(e.getValue(), e.getKey());
    }

    static String stripGenerics(String s) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (depth == 0) sb.append(c);
        }
        return sb.toString();
    }

    static int countArgs(String desc) {
        int i = desc.indexOf('(') + 1, n = 0;
        while (i < desc.length() && desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            if (c == 'L') { i = desc.indexOf(';', i) + 1; n++; }
            else if (c == '[') {
                while (desc.charAt(i) == '[') i++;
                if (desc.charAt(i) == 'L') i = desc.indexOf(';', i) + 1; else i++;
                n++;
            } else { i++; n++; }
        }
        return n;
    }

    // Ruwe tabellen voor hergebruik (FixFromErrors): mojKey -> offKeys, tiny tabellen.
    public Map<String, Set<String>> rawMojMem;
    public Map<String, String> rawMojField;
    public Map<String, List<String[]>> rawTinyMem;
    public Map<String, Set<String>> rawTinyField;
    public Map<String, String> offToMojSimple;
    public Map<String, String> interToOff;

    // Let op: ALLEEN volledige match (geen kortere prefix!), anders eet de caller
    // bij Outer.Inner-ketens de Inner op terwijl alleen Outer gematcht is.
    String lookupInter(String[] segs) {
        String base = "net/minecraft/" + String.join("/", segs);
        String off = classMojToOff.get(base);
        if (off == null && segs.length >= 2) {
            String[] pre = java.util.Arrays.copyOf(segs, segs.length - 1);
            off = classMojToOff.get("net/minecraft/" + String.join("/", pre) + "$" + segs[segs.length - 1]);
        }
        if (off != null) {
            String it = classOffToInter.get(off);
            if (it != null) return it.replace('/', '.').replace('$', '.');
        }
        return null;
    }

    // Vervangt bare type-namen via eigen net.minecraft-imports; slaat strings,
    // chars, comments en annotaties over. Eerst Outer.Inner-ketens, dan singles.
    String replaceBareTypes(String src, Map<String, String> fileTypes, Map<String, String[]> mojImp) {
        StringBuilder out = new StringBuilder();
        int i = 0, n = src.length();
        int state = 0; // 0 code, 1 string, 2 char, 3 linecomment, 4 blockcomment
        while (i < n) {
            char c = src.charAt(i);
            if (state == 0 && c == '/' && i + 1 < n && src.charAt(i + 1) == '/') { out.append("//"); i += 2; state = 3; continue; }
            if (state == 0 && c == '/' && i + 1 < n && src.charAt(i + 1) == '*') { out.append("/*"); i += 2; state = 4; continue; }
            if (state == 0 && c == '"') { out.append(c); i++; state = 1; continue; }
            if (state == 0 && c == '\'') { out.append(c); i++; state = 2; continue; }
            if (state == 1) {
                out.append(c); i++;
                if (c == '\\' && i < n) { out.append(src.charAt(i)); i++; }
                else if (c == '"') state = 0;
                continue;
            }
            if (state == 2) {
                out.append(c); i++;
                if (c == '\\' && i < n) { out.append(src.charAt(i)); i++; }
                else if (c == '\'') state = 0;
                continue;
            }
            if (state == 3) { out.append(c); i++; if (c == '\n') state = 0; continue; }
            if (state == 4) {
                out.append(c); i++;
                if (c == '*' && i < n && src.charAt(i) == '/') { out.append('/'); i++; state = 0; }
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(src.charAt(j))) j++;
                String tok = src.substring(i, j);
                boolean atPre = i > 0 && src.charAt(i - 1) == '@';
                boolean dotPre = i > 0 && src.charAt(i - 1) == '.';
                if (!atPre && Character.isUpperCase(tok.charAt(0)) && fileTypes.containsKey(tok)) {
                    // keten Outer.Inner?
                    int k = j;
                    while (k < n && src.charAt(k) == ' ') k++;
                    if (!dotPre && k < n && src.charAt(k) == '.' && k + 1 < n && Character.isUpperCase(src.charAt(k + 1))) {
                        int m2 = k + 1;
                        while (m2 < n && Character.isJavaIdentifierPart(src.charAt(m2))) m2++;
                        String inner = src.substring(k + 1, m2);
                        String[] outerSegs = mojImp.get(tok);
                        String[] full = java.util.Arrays.copyOf(outerSegs, outerSegs.length + 1);
                        full[full.length - 1] = inner;
                        String li = lookupInter(full);
                        if (li != null) { out.append(li); i = m2; memberRepl++; continue; }
                    }
                    if (!dotPre) { out.append(fileTypes.get(tok)); i = j; memberRepl++; continue; }
                }
                out.append(tok); i = j; continue;
            }
            out.append(c); i++;
        }
        return out.toString();
    }

    // Telt argumenten van een call: index net na '('; respecteert nesting,
    // strings, chars en comments. Lege aanroep -> 0.
    static int callArity(String src, int from) {
        int depth = 1, commas = 0, i = from, n = src.length();
        boolean blank = true;
        int state = 0; // 0 code, 1 string, 2 char, 3 linecomment, 4 blockcomment
        while (i < n) {
            char c = src.charAt(i);
            if (state == 1) { if (c == '\\' && i + 1 < n) i += 2; else { if (c == '"') state = 0; i++; } continue; }
            if (state == 2) { if (c == '\\' && i + 1 < n) i += 2; else { if (c == '\'') state = 0; i++; } continue; }
            if (state == 3) { i++; if (c == '\n') state = 0; continue; }
            if (state == 4) { if (c == '*' && i + 1 < n && src.charAt(i + 1) == '/') { i += 2; state = 0; } else i++; continue; }
            if (c == '"') { state = 1; i++; continue; }
            if (c == '\'') { state = 2; i++; continue; }
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

    // true = code; false = string/char/comment/textblock (leden daar nooit converteren).
    static boolean[] codeMask(String src) {
        int n = src.length();
        boolean[] code = new boolean[n];
        java.util.Arrays.fill(code, true);
        int i = 0, state = 0; // 0 code, 1 string, 2 char, 3 line, 4 block, 5 textblock
        while (i < n) {
            char c = src.charAt(i);
            if (state == 0 && c == '"' && i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                code[i] = code[i + 1] = code[i + 2] = false; i += 3; state = 5; continue;
            }
            if (state == 0 && c == '"') { code[i] = false; i++; state = 1; continue; }
            if (state == 0 && c == '\'') { code[i] = false; i++; state = 2; continue; }
            if (state == 0 && c == '/' && i + 1 < n && src.charAt(i + 1) == '/') { code[i] = code[i + 1] = false; i += 2; state = 3; continue; }
            if (state == 0 && c == '/' && i + 1 < n && src.charAt(i + 1) == '*') { code[i] = code[i + 1] = false; i += 2; state = 4; continue; }
            if (state == 1) {
                code[i] = false;
                if (c == '\\' && i + 1 < n) { code[i + 1] = false; i += 2; }
                else { i++; if (c == '"') state = 0; }
                continue;
            }
            if (state == 2) {
                code[i] = false;
                if (c == '\\' && i + 1 < n) { code[i + 1] = false; i += 2; }
                else { i++; if (c == '\'') state = 0; }
                continue;
            }
            if (state == 3) { code[i] = false; i++; if (c == '\n') state = 0; continue; }
            if (state == 4) {
                code[i] = false;
                if (c == '*' && i + 1 < n && src.charAt(i + 1) == '/') { code[i + 1] = false; i += 2; state = 0; }
                else i++;
                continue;
            }
            if (state == 5) {
                code[i] = false;
                if (c == '"' && i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') { code[i + 1] = code[i + 2] = false; i += 3; state = 0; }
                else if (c == '\\' && i + 1 < n) { code[i + 1] = false; i += 2; }
                else i++;
                continue;
            }
            i++;
        }
        return code;
    }

    static void collectNames(String t, Set<String> out) {
        Matcher mdm = Pattern.compile("(?m)^\\s*(?:public|protected|private|static|final|synchronized|abstract|native|default|transient|volatile|sealed|non-sealed|strictfp|\\s)+[\\w$<>\\[\\].,? ]+\\s+(\\w+)\\s*\\(").matcher(t);
        while (mdm.find()) {
            // type-deel mag geen expressie zijn ('X hit = new X(' is geen declaratie)
            String pre = mdm.group(0).substring(0, mdm.group(0).length() - mdm.group(1).length() - 1).trim();
            int lastSp = pre.lastIndexOf(' ');
            String typePart = lastSp < 0 ? pre : pre.substring(lastSp + 1);
            java.util.function.Predicate<String> hasWord = w -> pre.equals(w) || pre.startsWith(w + " ")
                    || pre.endsWith(" " + w) || pre.contains(" " + w + " ");
            if (typePart.contains("=") || typePart.contains("(") || hasWord.test("new")
                    || pre.contains("= new") || hasWord.test("return") || hasWord.test("else")
                    || hasWord.test("if") || hasWord.test("while") || hasWord.test("for")
                    || hasWord.test("throw") || hasWord.test("case")) continue;
            out.add(mdm.group(1));
        }
        Matcher fdm = Pattern.compile("(?m)^\\s*(?:public|protected|private|static|final|transient|volatile|\\s)+[\\w$<>\\[\\].,? ]+\\s+(\\w+)\\s*(=[^;]*|;)\\s*$").matcher(t);
        while (fdm.find()) out.add(fdm.group(1));
        // enum-constanten (BURST;) en multi-declarators (double px, py, pz;)
        Matcher edm = Pattern.compile("(?m)^\\s*([A-Z][A-Z0-9_]*)(?:\\([^;{}]*\\))?\\s*[,;]\\s*$").matcher(t);
        while (edm.find()) out.add(edm.group(1));
        Matcher cdm = Pattern.compile("(?m)^\\s*(?:public|protected|private|static|final|transient|volatile|\\s)+?[\\w$<>\\[\\].,? ]+?\\s+([\\w$]+(?:\\s*,\\s*[\\w$]+)+)\\s*(=[^;]*|;)\\s*$").matcher(t);
        while (cdm.find()) for (String v : cdm.group(1).split(",")) out.add(v.trim());
        Matcher tdm = Pattern.compile("(?m)^\\s*(?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp|\\s)*(?:class|interface|enum|record|@interface)\\s+(\\w+)").matcher(t);
        while (tdm.find()) out.add(tdm.group(1));
    }

    void process(Path f, boolean write) throws Exception {
        String src = Files.readString(f);
        String orig = src;
        // file-lokale namen voor R1 (eigen declaraties schaduwen kale type-namen)
        Set<String> localNames = new HashSet<>();
        collectNames(src, localNames);
        // eigen net.minecraft-imports bewaren voor bare-naam-resolutie
        java.util.List<String[]> mcImports = new java.util.ArrayList<>();
        Matcher impc = Pattern.compile("(?m)^import net\\.minecraft\\.([A-Za-z0-9_$.]+);").matcher(src);
        while (impc.find()) mcImports.add(impc.group(1).split("\\."));
        // verzamel gebruikte moj-owners (imports + FQN)
        Set<String> usedOwners = new HashSet<>();
        Matcher im = Pattern.compile("import (net\\.minecraft\\.([A-Za-z0-9_$.]+));").matcher(src);
        while (im.find()) {
            String cls = im.group(1).replace('.', '/').replace('$', '/');
            int last = cls.lastIndexOf('/');
            usedOwners.add(last < 0 ? cls : cls.substring(last + 1));
        }
        // 1) FQN + imports herschrijven (langste klasse-prefix; member-tail blijft staan)
        Matcher fq = Pattern.compile("net\\.minecraft\\.([A-Za-z0-9_$]+(?:\\.[A-Za-z0-9_$]+)*)").matcher(src);
        StringBuffer sb = new StringBuffer();
        while (fq.find()) {
            String[] segs = fq.group(1).split("\\.");
            String found = null;
            int used = 0;
            String mojSimple = null;
            for (int n = segs.length; n >= 1; n--) {
                String base = "net/minecraft/" + String.join("/", java.util.Arrays.copyOf(segs, n));
                String off = classMojToOff.get(base);
                if (off == null && n >= 2) {
                    String[] pre = java.util.Arrays.copyOf(segs, n - 1);
                    off = classMojToOff.get("net/minecraft/" + String.join("/", pre) + "$" + segs[n - 1]);
                }
                if (off != null) {
                    String it = classOffToInter.get(off);
                    if (it != null) { found = it; used = n; mojSimple = segs[n - 1]; break; }
                }
            }
            if (found != null) {
                // inter is volledig pad (bv. net/minecraft/class_1713); $Inner -> .Inner
                String tail = used < segs.length ? "." + String.join(".", java.util.Arrays.copyOfRange(segs, used, segs.length)) : "";
                fq.appendReplacement(sb, Matcher.quoteReplacement(found.replace('/', '.').replace('$', '.') + tail));
                usedOwners.add(mojSimple);
                classRepl++;
            } else {
                fq.appendReplacement(sb, Matcher.quoteReplacement(fq.group(0)));
                if (fq.group(1).contains(".")) warnings.add(f.getFileName() + ": NOCLASS " + fq.group(0));
            }
        }
        fq.appendTail(sb);
        src = sb.toString();
        // 1b) bare type-namen via eigen imports oplossen
        Map<String, String> fileTypes = new HashMap<>();
        Map<String, String[]> mojImp = new HashMap<>();
        Set<String> typeAmbig = new HashSet<>();
        Set<String> unresolvedImp = new HashSet<>();
        for (String[] segs : mcImports) {
            String inter = lookupInter(segs);
            if (inter == null) { unresolvedImp.add(String.join(".", segs)); continue; }
            String simple = segs[segs.length - 1];
            if (keepNames.contains(simple)) continue;
            if (fileTypes.containsKey(simple) && !fileTypes.get(simple).equals(inter)) {
                typeAmbig.add(simple);
                fileTypes.remove(simple);
                mojImp.remove(simple);
            } else if (!typeAmbig.contains(simple)) {
                fileTypes.put(simple, inter);
                mojImp.put(simple, segs);
            }
        }
        // R1: kale moj-namen helen via reeds-geconverteerde inter-imports
        // (import net.minecraft.class_243 + kale Vec3 -> FQN). Binding komt uit
        // de eigen import en is dus exact.
        Matcher iimp = Pattern.compile("(?m)^import net\\.minecraft\\.(class_\\d+)\\s*;").matcher(src);
        while (iimp.find()) {
            String off = interToOff.get("net/minecraft/" + iimp.group(1));
            if (off == null) continue;
            String mojSimple = offToMojSimple.get(off);
            if (mojSimple == null || localNames.contains(mojSimple)) continue;
            String fqn = "net.minecraft." + iimp.group(1);
            if (fileTypes.containsKey(mojSimple) && !fileTypes.get(mojSimple).equals(fqn)) {
                typeAmbig.add(mojSimple);
                fileTypes.remove(mojSimple);
                mojImp.remove(mojSimple);
            } else if (!typeAmbig.contains(mojSimple)) {
                fileTypes.put(mojSimple, fqn);
                mojImp.put(mojSimple, new String[]{mojSimple});
            }
        }
        if (!typeAmbig.isEmpty()) warnings.add(f.getFileName() + ": ambigue type-imports: " + typeAmbig);
        if (!unresolvedImp.isEmpty()) warnings.add(f.getFileName() + ": UNRESOLVED-IMPORT: " + unresolvedImp);
        if (!fileTypes.isEmpty()) src = replaceBareTypes(src, fileTypes, mojImp);
        // 2) members, gescopeerd op gebruikte owners
        // Bescherm ALLE net.minecraft-paden tegen member-conversie: na stap 1 zijn
        // FQNs als net.minecraft.class_1713 laaghangend fruit voor corruptie
        // ('minecraft'/'class_1713' als member-namen). Mojmap-leden aan FQN-ketens
        // blijven staan en worden via compiler-errors met de hand gefixt.
        java.util.List<String> savedFqn = new java.util.ArrayList<>();
        // Ook ketens van ANDERE packages beschermen (net.fabricmc.fabric.api,
        // io.netty.bootstrap, ...): package-segmenten zijn geen members.
        // net.minecraft.* wordt overgeslagen (afgehandeld door fqp hieronder).
        Matcher tldp = Pattern.compile("\\b(?!net\\.minecraft\\.)(?:java|javax|jakarta|kotlin|net|com|org|io|it|cn|ru|dev|me|gg|club|xyz|tech|top|app|oshi)(?:\\.[A-Za-z0-9_$]+)+").matcher(src);
        StringBuffer tldb = new StringBuffer();
        while (tldp.find()) {
            savedFqn.add(tldp.group(0));
            tldp.appendReplacement(tldb, Matcher.quoteReplacement("\u0000" + (savedFqn.size() - 1) + "\u0000"));
        }
        tldp.appendTail(tldb);
        src = tldb.toString();
        Matcher fqp = Pattern.compile("net\\.minecraft\\.(?:[A-Za-z0-9_$]+\\.)*(?:[A-Z][A-Za-z0-9_$]*|class_[0-9]+)").matcher(src);
        StringBuffer tmpb = new StringBuffer();
        while (fqp.find()) {
            savedFqn.add(fqp.group(0));
            fqp.appendReplacement(tmpb, Matcher.quoteReplacement("\u0000" + (savedFqn.size() - 1) + "\u0000"));
        }
        fqp.appendTail(tmpb);
        src = tmpb.toString();
        // call-vs-field: met '(' erna alleen methoden, zonder alleen velden
        // (voorkomt method<->field swaps als field_6002() of var method_68876).
        Matcher mm = Pattern.compile("\\.([A-Za-z_$][A-Za-z0-9_$]*)(\\s*\\()?").matcher(src);
        StringBuffer sb2 = new StringBuffer();
        boolean[] code = codeMask(src);
        while (mm.find()) {
            if (!code[mm.start()] || (mm.start() > 0 && src.charAt(mm.start() - 1) == '@')) {
                mm.appendReplacement(sb2, Matcher.quoteReplacement(mm.group(0)));
                continue;
            }
            String name = mm.group(1);
            boolean isCall = mm.group(2) != null;
            String tail = isCall ? mm.group(2) : "";
            if (STOP.contains(name)) { mm.appendReplacement(sb2, Matcher.quoteReplacement(mm.group(0))); continue; }
            if (keepNames.contains(name)) {
                warnings.add(f.getFileName() + ": KEEP-skipped ." + name);
                mm.appendReplacement(sb2, Matcher.quoteReplacement(mm.group(0)));
                continue;
            }
            // 1) gescopeerde methoden (alleen bij calls): eerst exacte ariteit,
            // dan pas andere ariteiten (een ambigue overload met een ANDERE
            // ariteit mag een eenduidige match niet blokkeren).
            Set<String> hits = new HashSet<>();
            Set<String> ambigHit = new HashSet<>();
            int callAr = -1;
            if (isCall) {
                callAr = callArity(src, mm.end());
                for (String owner : usedOwners) {
                    String key = owner + "|" + name + "|" + callAr;
                    String v = memberMap.get(key);
                    if (v != null) hits.add(v);
                    if (memberAmbigKeys.contains(key)) ambigHit.add(key);
                }
                if (hits.size() == 1 && ambigHit.isEmpty()) {
                    mm.appendReplacement(sb2, Matcher.quoteReplacement("." + hits.iterator().next() + tail));
                    memberRepl++;
                    continue;
                }
                if (hits.isEmpty() && ambigHit.isEmpty()) {
                    for (String owner : usedOwners) {
                        for (int arity = 0; arity <= 9; arity++) {
                            if (arity == callAr) continue;
                            String key = owner + "|" + name + "|" + arity;
                            String v = memberMap.get(key);
                            if (v != null) hits.add(v);
                            if (memberAmbigKeys.contains(key)) ambigHit.add(key);
                        }
                    }
                    if (hits.size() == 1 && ambigHit.isEmpty()) {
                        mm.appendReplacement(sb2, Matcher.quoteReplacement("." + hits.iterator().next() + tail));
                        memberRepl++;
                        continue;
                    }
                }
                // Record-accessor: call zonder methode-match, wel eenduidig veld
                // (accessor deelt de inter-naam van het veld, bv. Equippable.slot()).
                if (hits.isEmpty() && ambigHit.isEmpty()) {
                    Set<String> ahits = new HashSet<>();
                    boolean aambig = false;
                    for (String owner : usedOwners) {
                        String key = owner + "|" + name;
                        String v = fieldMap.get(key);
                        if (v != null) ahits.add(v);
                        if (fieldAmbigKeys.contains(key)) aambig = true;
                    }
                    if (ahits.size() == 1 && !aambig) {
                        mm.appendReplacement(sb2, Matcher.quoteReplacement("." + ahits.iterator().next() + tail));
                        memberRepl++;
                        continue;
                    }
                }
            }
            // 2) gescopeerde velden (alleen zonder '(')
            Set<String> fhits = new HashSet<>();
            Set<String> fambig = new HashSet<>();
            if (!isCall) {
                for (String owner : usedOwners) {
                    String key = owner + "|" + name;
                    String v = fieldMap.get(key);
                    if (v != null) fhits.add(v);
                    if (fieldAmbigKeys.contains(key)) fambig.add(key);
                }
                if (fhits.size() == 1 && fambig.isEmpty() && hits.isEmpty() && ambigHit.isEmpty()) {
                    mm.appendReplacement(sb2, Matcher.quoteReplacement("." + fhits.iterator().next()));
                    memberRepl++;
                    continue;
                }
            }
            // 3) GEEN globale fallback meer: naam-eenduidigheid zonder receiver
            // is onveilig (Math.abs->method_40471, sort->method_38154, ...).
            // Missers blijven moj-namen en komen als compile-errors terug,
            // waar FixFromErrors ze met exact receiver-type oplost.
            if (hits.size() > 1 || !ambigHit.isEmpty() || fhits.size() > 1 || !fambig.isEmpty()) {
                warnings.add(f.getFileName() + ": skip member ." + name + (callAr >= 0 ? "/" + callAr : "") + " (hits=" + hits + " ambig=" + ambigHit
                    + " fhits=" + fhits + " fambig=" + fambig + ")");
            }
            mm.appendReplacement(sb2, Matcher.quoteReplacement(mm.group(0)));
        }
        mm.appendTail(sb2);
        src = sb2.toString();
        for (int i = 0; i < savedFqn.size(); i++) src = src.replace("\u0000" + i + "\u0000", savedFqn.get(i));
        if (write && !src.equals(orig)) Files.writeString(f, src);
    }
}
